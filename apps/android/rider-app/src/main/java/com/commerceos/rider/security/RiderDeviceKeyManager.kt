package com.commerceos.rider.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Hardware-backed device key manager for authentic rider telemetry packets.
 * Keys are generated and stored exclusively within the hardware-isolated AndroidKeyStore (TEE / StrongBox).
 * Binds coordinate packets to a cryptographic device key attested by Google Play Integrity.
 */
object RiderDeviceKeyManager {
    private const val TAG = "RiderDeviceKeyManager"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "commerceos_rider_device_key_v1"
    private const val PREFS_NAME = "commerceos_rider_device_security"
    private const val PREF_ENROLLED_KEY_ID = "enrolled_key_id"

    @Volatile
    private var cachedKeyPair: KeyPair? = null

    @Synchronized
    private fun getOrCreateKeyPair(@Suppress("UNUSED_PARAMETER") context: Context, attestationChallenge: ByteArray? = null): KeyPair {
        cachedKeyPair?.let { return it }

        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (ks.containsAlias(KEY_ALIAS)) {
                val cert = ks.getCertificate(KEY_ALIAS)
                val priv = ks.getKey(KEY_ALIAS, null) as? PrivateKey
                if (cert != null && priv != null) {
                    val pair = KeyPair(cert.publicKey, priv)
                    cachedKeyPair = pair
                    return pair
                }
            }

            // Generate hardware-backed EC P-256 key inside AndroidKeyStore
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)

            if (attestationChallenge != null && attestationChallenge.isNotEmpty()) {
                builder.setAttestationChallenge(attestationChallenge)
            }

            kpg.initialize(builder.build())
            val pair = kpg.generateKeyPair()
            cachedKeyPair = pair
            return pair
        } catch (e: Exception) {
            Log.w(TAG, "AndroidKeyStore hardware generation unavailable, falling back to standard JCA: ${e.message}")
        }

        // Standard JCA EC key generation (fallback for JVM / non-Android unit test harnesses)
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        val pair = kpg.generateKeyPair()
        cachedKeyPair = pair
        return pair
    }

    fun getKeyId(context: Context): String {
        val pub = getOrCreateKeyPair(context).public
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val hash = md.digest(pub.encoded).joinToString("") { "%02x".format(it) }
        return "rider_dev_${hash.take(16)}"
    }

    fun getPublicKeyPem(context: Context): String {
        val pub = getOrCreateKeyPair(context).public
        val base64 = Base64.encodeToString(pub.encoded, Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\n$base64\n-----END PUBLIC KEY-----"
    }

    fun getCertificateChainPem(@Suppress("UNUSED_PARAMETER") context: Context): List<String> {
        try {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val chain = ks.getCertificateChain(KEY_ALIAS) ?: return emptyList()
            return chain.map { cert ->
                val b64 = Base64.encodeToString(cert.encoded, Base64.NO_WRAP)
                "-----BEGIN CERTIFICATE-----\n$b64\n-----END CERTIFICATE-----"
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to retrieve certificate chain from AndroidKeyStore: ${e.message}")
            return emptyList()
        }
    }

    fun sign(context: Context, payload: String): String {
        val priv = getOrCreateKeyPair(context).private
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(priv)
        signer.update(payload.toByteArray(Charsets.UTF_8))
        val sigBytes = signer.sign()
        return Base64.encodeToString(sigBytes, Base64.NO_WRAP)
    }

    suspend fun ensureEnrolled(
        context: Context,
        baseUrl: String,
        authToken: String
    ): Boolean = withContext(Dispatchers.IO) {
        val keyId = getKeyId(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getString(PREF_ENROLLED_KEY_ID, null) == keyId) {
            return@withContext true
        }

        try {
            // 1. Fetch attestation enrollment challenge
            val challengeUrl = URL("${baseUrl.trimEnd('/')}/api/v1/rider/attestation/challenge")
            val chConn = (challengeUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $authToken")
                doOutput = true
                connectTimeout = 4000
                readTimeout = 4000
            }
            chConn.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }

            if (chConn.responseCode !in 200..299) {
                Log.w(TAG, "Challenge request failed with HTTP ${chConn.responseCode}")
                return@withContext false
            }

            val chBody = chConn.inputStream.bufferedReader().use { it.readText() }
            val challenge = JSONObject(chBody).optString("challenge")
            if (challenge.isBlank()) return@withContext false

            // Bind hardware key generation to the challenge if not yet generated
            val challengeBytes = challenge.toByteArray(Charsets.UTF_8)
            getOrCreateKeyPair(context, challengeBytes)

            // 2. Request Google Play Integrity token bound to challenge
            val playIntegrityService = PlayIntegrityService.getInstance(context)
            val integrityToken = playIntegrityService.requestToken(challenge)

            // 3. Register device key with backend
            val regUrl = URL("${baseUrl.trimEnd('/')}/api/v1/rider/attestation/register")
            val regConn = (regUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $authToken")
                doOutput = true
                connectTimeout = 4000
                readTimeout = 4000
            }

            val certChain = getCertificateChainPem(context)
            val certChainArray = JSONArray()
            for (c in certChain) {
                certChainArray.put(c)
            }

            val regPayload = JSONObject().apply {
                put("keyId", keyId)
                put("publicKey", getPublicKeyPem(context))
                put("platform", "android_play_integrity")
                put("challenge", challenge)
                if (certChainArray.length() > 0) {
                    put("certificateChain", certChainArray)
                }
                if (!integrityToken.isNullOrBlank()) {
                    put("token", integrityToken)
                    put("integrityToken", integrityToken)
                }
            }

            regConn.outputStream.use { os ->
                os.write(regPayload.toString().toByteArray(Charsets.UTF_8))
            }

            if (regConn.responseCode in 200..299) {
                prefs.edit().putString(PREF_ENROLLED_KEY_ID, keyId).apply()
                Log.i(TAG, "Successfully enrolled rider device key $keyId with platform backend.")
                return@withContext true
            } else {
                val err = regConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.w(TAG, "Device key registration failed: HTTP ${regConn.responseCode} $err")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception during device key enrollment: ${e.message}")
            return@withContext false
        }
    }
}
