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
    /** Executes a retrofit call and maps transport + protocol failures to [AppError]. */
    suspend fun <T> run(block: suspend () -> T): ApiResult<T> = try {
        ApiResult.Success(block())
    } catch (e: HttpException) {
        val response = e.response()
        val body = response?.errorBody()?.string()
        val info = ErrorBodyParser.parse(body)
        val path = response?.raw()?.request?.url?.encodedPath ?: ""
        // Wrong-OTP / bad-credential 401s carry structured, actionable payloads
        // (attemptsLeft, retryAfterSeconds). Only a 401 on a PROTECTED resource
        // after a failed refresh is a true session-expiry.
        val isIdentityError = path.startsWith("/api/v1/auth")
        if (e.code() == 401 && !isIdentityError) {
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
    } catch (e: IOException) {
        ApiResult.Failure(AppError.Network(e.message ?: "network"))
    } catch (e: Exception) {
        ApiResult.Failure(AppError.Unknown(e.message ?: e.javaClass.simpleName))
    }
}
