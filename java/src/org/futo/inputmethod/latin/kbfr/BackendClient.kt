package org.futo.inputmethod.latin.kbfr

import android.util.Base64
import java.io.IOException
import java.net.URI
import java.net.URL
import java.security.SecureRandom
import javax.net.ssl.HttpsURLConnection

enum class PingResult { REGISTERED, NOT_REGISTERED, INVALID_ENDPOINT, UNREACHABLE, UNEXPECTED }

// kbfr fork: the only network code of the keyboard (spec R5). It talks only to the host of the
// endpoint set in the settings, only over HTTPS, and only with requests signed by DeviceKey.
object BackendClient {
    // "https://host[:port]" when the endpoint is a bare HTTPS origin, else null.
    fun baseUrl(endpoint: String): String? {
        val uri = try {
            URI(endpoint.trim())
        } catch (_: Exception) {
            return null
        }
        if (uri.scheme != "https" || uri.host.isNullOrEmpty() || uri.userInfo != null) return null
        if (!(uri.path.isNullOrEmpty() || uri.path == "/") || uri.query != null || uri.fragment != null) return null
        return "https://${uri.host}" + if (uri.port != -1) ":${uri.port}" else ""
    }

    class Response(val status: Int, val body: String)

    // Blocking: call it off the main thread. Throws IOException when the backend is unreachable.
    fun signedPost(baseUrl: String, path: String, body: ByteArray, timeoutMs: Int): Response {
        val timestamp = System.currentTimeMillis() / 1000
        val nonce = Base64.encodeToString(
            ByteArray(16).also { SecureRandom().nextBytes(it) },
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
        val signature = DeviceKey.sign(RequestSigning.canonical("POST", path, timestamp, nonce, body).toByteArray())

        val connection = URL(baseUrl + path).openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = false
            connection.doOutput = true
            if (body.isNotEmpty()) connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("X-KB-Key-Id", RequestSigning.keyId(DeviceKey.publicKeySpki()))
            connection.setRequestProperty("X-KB-Timestamp", timestamp.toString())
            connection.setRequestProperty("X-KB-Nonce", nonce)
            connection.setRequestProperty("X-KB-Signature", Base64.encodeToString(signature, Base64.NO_WRAP))
            connection.setFixedLengthStreamingMode(body.size)
            connection.outputStream.use { it.write(body) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            return Response(status, stream?.use { it.readBytes().decodeToString() } ?: "")
        } finally {
            connection.disconnect()
        }
    }

    // Blocking: call it off the main thread.
    fun ping(endpoint: String, timeoutMs: Int): PingResult {
        val base = baseUrl(endpoint) ?: return PingResult.INVALID_ENDPOINT
        return try {
            when (signedPost(base, "/v1/ping", ByteArray(0), timeoutMs).status) {
                204 -> PingResult.REGISTERED
                401 -> PingResult.NOT_REGISTERED
                else -> PingResult.UNEXPECTED
            }
        } catch (_: IOException) {
            PingResult.UNREACHABLE
        }
    }
}
