package org.futo.inputmethod.latin.kbfr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackendContractTest {
    @Test
    fun canonicalStringMatchesTheBackendFormat() {
        // Same layout as backend/src/auth.ts: v1, method, path, timestamp, nonce, hex(sha256(body)).
        assertEquals(
            "v1\nPOST\n/v1/ping\n1700000000\nAAAAAAAAAAAAAAAAAAAAAA\n" +
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            RequestSigning.canonical("POST", "/v1/ping", 1700000000, "AAAAAAAAAAAAAAAAAAAAAA", ByteArray(0)),
        )
    }

    @Test
    fun keyIdIsTheFirst16HexCharsOfSha256() {
        assertEquals("ba7816bf8f01cfea", RequestSigning.keyId("abc".toByteArray()))
    }

    @Test
    fun endpointMustBeABareHttpsOrigin() {
        assertEquals("https://example.scw.cloud", BackendClient.baseUrl("https://example.scw.cloud"))
        assertEquals("https://example.scw.cloud", BackendClient.baseUrl(" https://example.scw.cloud/ "))
        assertEquals("https://example.scw.cloud:8443", BackendClient.baseUrl("https://example.scw.cloud:8443"))
        for (bad in listOf(
            "http://example.scw.cloud",
            "https://example.scw.cloud/v1",
            "https://example.scw.cloud?x=1",
            "https://user@example.scw.cloud",
            "example.scw.cloud",
            "",
            "https://",
        )) {
            assertNull(bad, BackendClient.baseUrl(bad))
        }
    }
}
