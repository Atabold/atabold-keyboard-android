package org.futo.inputmethod.latin.kbfr

import java.security.MessageDigest

// kbfr fork: the signed-request contract shared with backend/src/auth.ts (spec 7.2).
object RequestSigning {
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // X-KB-Key-Id: first 16 hex characters of sha256(SPKI).
    fun keyId(spki: ByteArray): String = sha256Hex(spki).take(16)

    fun canonical(method: String, path: String, timestamp: Long, nonce: String, body: ByteArray): String =
        "v1\n$method\n$path\n$timestamp\n$nonce\n${sha256Hex(body)}"
}
