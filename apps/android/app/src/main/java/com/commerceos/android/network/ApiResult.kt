package com.commerceos.android.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import retrofit2.HttpException
import java.io.IOException

/**
 * Structured error surfaced to ViewModels. Never leaks retrofit/okhttp types;
 * carries enough server context (code, attempts, retry-after) for the UI to
 * explain exactly what happened instead of "null".
 */
sealed class AppError(val message: String) {
    class Network(val detail: String) : AppError(
        "Unable to reach the server. Please check your connection and try again."
    )

    class Unauthorized : AppError("Your session has expired. Please sign in again.")

    class Server(
        val httpCode: Int,
        val errorCode: String?,
        val serverMessage: String,
        val retryAfterSeconds: Int?,
        val attemptsLeft: Int?
    ) : AppError(serverMessage.ifBlank { "Request failed with status $httpCode" })

    class Unknown(val detail: String) : AppError("Something went wrong. Please try again.")

    override fun toString(): String = message
}

/**
 * Transport-agnostic result for repository methods. [Success] carries data;
 * [Failure] carries a [AppError]. ViewModels MUST distinguish "no data" (a
 * Success with an empty list) from "could not load" (a Failure) — collapsing
 * both to `null`/`emptyList()` silently hides outages.
 */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val error: AppError) : ApiResult<Nothing>()

    inline fun <R> map(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
    }

    inline fun <R> mapCatching(transform: (T) -> R): ApiResult<R> = when (this) {
        is Success -> try {
            Success(transform(data))
        } catch (e: Exception) {
            Failure(AppError.Unknown(e.message ?: e.javaClass.simpleName))
        }
        is Failure -> this
    }
}

data class ServerErrorInfo(
    val errorCode: String?,
    val serverMessage: String,
    val retryAfterSeconds: Int?,
    val attemptsLeft: Int?
)

object ErrorBodyParser {
    private val gson = Gson()

    fun parse(body: String?): ServerErrorInfo? {
        if (body.isNullOrBlank()) return null
        return try {
            val obj: JsonObject = gson.fromJson(body, JsonObject::class.java)
            ServerErrorInfo(
                errorCode = obj.get("code")?.getAsString() ?: obj.get("error")?.getAsString(),
                serverMessage = obj.get("message")?.takeIf { !it.isJsonNull }?.getAsString() ?: "",
                retryAfterSeconds = obj.get("retryAfterSeconds")?.takeIf { !it.isJsonNull }?.getAsInt(),
                attemptsLeft = obj.get("attemptsLeft")?.takeIf { !it.isJsonNull }?.getAsInt()
            )
        } catch (e: Exception) {
            null
        }
    }
}

object Api {
    /** Executes a retrofit call and maps transport + protocol failures to [AppError].
     *  Automatically fails over across candidate gateways (local ADB reverse port 8090,
     *  LAN IP, emulator IP) if Render is 429'd by Cloudflare or unreachable.
     */
    suspend fun <T> run(block: suspend () -> T): ApiResult<T> {
        val candidateBases = listOfNotNull(
            NetworkClient.baseUrl,
            "http://127.0.0.1:8090",
            "http://192.168.1.76:8090",
            "http://10.0.2.2:8090"
        ).distinct()

        var lastHttpException: HttpException? = null
        var lastIoException: IOException? = null
        var lastGeneralException: Exception? = null

        for (candidate in candidateBases) {
            try {
                if (NetworkClient.baseUrl != candidate) {
                    NetworkClient.baseUrl = candidate
                }
                return ApiResult.Success(block())
            } catch (e: HttpException) {
                lastHttpException = e
                val is429orChallenge = e.code() == 429 || e.code() in listOf(502, 503, 504)
                if (!is429orChallenge) {
                    // Application-level error (400, 401, 403, 404, 422, etc.)
                    // Do not failover to another host on real business validation
                    break
                }
                android.util.Log.w("ApiRun", "Endpoint $candidate returned HTTP ${e.code()}, attempting next candidate...")
            } catch (e: IOException) {
                lastIoException = e
                android.util.Log.w("ApiRun", "Endpoint $candidate failed with IOException (${e.message}), attempting next candidate...")
            } catch (e: Exception) {
                lastGeneralException = e
                break
            }
        }

        // If loop exhausted with an HttpException:
        if (lastHttpException != null) {
            val e = lastHttpException
            val response = e.response()
            val body = response?.errorBody()?.string()
            val info = ErrorBodyParser.parse(body)
            val path = response?.raw()?.request?.url?.encodedPath ?: ""
            android.util.Log.e("ApiRun", "HttpException on $path: code=${e.code()}, body=$body", e)
            val isIdentityError = path.startsWith("/api/v1/auth")
            return if (e.code() == 401 && !isIdentityError) {
                ApiResult.Failure(AppError.Unauthorized())
            } else {
                ApiResult.Failure(
                    AppError.Server(
                        httpCode = e.code(),
                        errorCode = info?.errorCode,
                        serverMessage = info?.serverMessage ?: e.message() ?: "",
                        retryAfterSeconds = info?.retryAfterSeconds,
                        attemptsLeft = info?.attemptsLeft
                    )
                )
            }
        }

        if (lastIoException != null) {
            android.util.Log.e("ApiRun", "IOException in Api.run: ${lastIoException.message}", lastIoException)
            return ApiResult.Failure(AppError.Network(lastIoException.message ?: "network"))
        }

        if (lastGeneralException != null) {
            android.util.Log.e("ApiRun", "Unexpected Exception in Api.run: ${lastGeneralException.javaClass.name}: ${lastGeneralException.message}", lastGeneralException)
            return ApiResult.Failure(AppError.Unknown(lastGeneralException.message ?: lastGeneralException.javaClass.simpleName))
        }

        return ApiResult.Failure(AppError.Unknown("Unknown network error"))
    }
}
