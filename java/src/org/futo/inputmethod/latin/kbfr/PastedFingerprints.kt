package org.futo.inputmethod.latin.kbfr

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// kbfr fork: what R3 remembers of the sensitive clips pasted from the keyboard. Messaging apps use
// one field for every chat, so the field cannot say whether a pasted secret is still in it: the text
// itself decides. Only a fingerprint is kept, an HMAC with a random key of this process, never the
// text, so it can outlive the clip's 60 s (spec 5.5). Main thread only.
class PastedFingerprints {
    private val mac = Mac.getInstance(ALGORITHM).apply {
        init(SecretKeySpec(ByteArray(32).also { SecureRandom().nextBytes(it) }, ALGORITHM))
    }

    // Grouped by text length, so a field is scanned once per length, not once per fingerprint.
    private val byLength = LinkedHashMap<Int, MutableSet<ByteBuffer>>()
    private val order = ArrayDeque<Pair<Int, ByteBuffer>>()

    fun isEmpty(): Boolean = order.isEmpty()

    fun add(text: String) {
        val sample = text.trim().take(MAX_CHARS)
        if (sample.isEmpty()) return
        val print = fingerprint(sample)
        if (!byLength.getOrPut(sample.length) { HashSet() }.add(print)) return
        order.addLast(sample.length to print)
        if (order.size > MAX_PRINTS) {
            val (length, oldest) = order.removeFirst()
            byLength[length]?.let { if (it.remove(oldest) && it.isEmpty()) byLength.remove(length) }
        }
    }

    fun foundIn(text: String): Boolean =
        byLength.any { (length, prints) ->
            (0..text.length - length).any { i -> fingerprint(text.substring(i, i + length)) in prints }
        }

    private fun fingerprint(s: String): ByteBuffer = ByteBuffer.wrap(mac.doFinal(s.toByteArray()))

    companion object {
        private const val ALGORITHM = "HmacSHA256"

        // A longer clip is recognised by its beginning.
        const val MAX_CHARS = 256

        const val MAX_PRINTS = 20
    }
}
