/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.oauth2.server.authorization.impl.command.authorization

import com.sphereon.core.api.encodeToBase64Url
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Behavioural coverage for [decodeIdTokenHintSub]. The decoder must extract `sub` only when the
 * payload's `iss` claim matches the configured issuer, and must return `null` for malformed
 * input rather than throwing — callers treat the hint as advisory and degrade gracefully when
 * it cannot be interpreted.
 */
class IdTokenHintDecoderTest {
    private val expectedIssuer = "https://as.example.com"

    /** Build a faux compact JWS with the supplied payload JSON. The signature is filler since
     * the decoder explicitly does NOT verify it. */
    private fun jws(
        payloadJson: String,
        header: String = "{\"alg\":\"none\"}"
    ): String {
        val headerB64 = header.encodeToByteArray().encodeToBase64Url()
        val payloadB64 = payloadJson.encodeToByteArray().encodeToBase64Url()
        val sigB64 = "sig".encodeToByteArray().encodeToBase64Url()
        return "$headerB64.$payloadB64.$sigB64"
    }

    @Test
    fun returnsSubForMatchingIssuer() {
        val hint = jws("{\"iss\":\"$expectedIssuer\",\"sub\":\"alice\"}")
        assertEquals("alice", decodeIdTokenHintSub(hint, expectedIssuer))
    }

    @Test
    fun returnsNullForIssuerMismatch() {
        val hint = jws("{\"iss\":\"https://malicious.example.com\",\"sub\":\"alice\"}")
        assertNull(decodeIdTokenHintSub(hint, expectedIssuer))
    }

    @Test
    fun returnsNullForMalformedJws() {
        assertNull(decodeIdTokenHintSub("not.a.jwt.at.all", expectedIssuer))
        assertNull(decodeIdTokenHintSub("only-one-segment", expectedIssuer))
        assertNull(decodeIdTokenHintSub("two.segments", expectedIssuer))
        assertNull(decodeIdTokenHintSub("header.<<not-base64>>.sig", expectedIssuer))
    }

    @Test
    fun returnsNullWhenSubAbsent() {
        val hint = jws("{\"iss\":\"$expectedIssuer\"}")
        assertNull(decodeIdTokenHintSub(hint, expectedIssuer))
    }

    @Test
    fun acceptsAnyIssuerWhenExpectedIssuerNull() {
        val hint = jws("{\"iss\":\"https://anywhere.example.com\",\"sub\":\"bob\"}")
        assertEquals("bob", decodeIdTokenHintSub(hint, expectedIssuer = null))
    }

    /** Logout flow needs `sub`, `aud`, `sid` from the hint. Verify all three round-trip. */
    @Test
    fun decodeIdTokenHintClaimsReturnsSubAudSid() {
        val hint =
            jws(
                "{\"iss\":\"$expectedIssuer\"," +
                    "\"sub\":\"alice\",\"aud\":\"client-1\"," +
                    "\"sid\":\"sess-42\"}",
            )
        val claims = decodeIdTokenHintClaims(hint, expectedIssuer)
        assertEquals("alice", claims?.sub)
        assertEquals("client-1", claims?.aud)
        assertEquals("sess-42", claims?.sid)
    }

    @Test
    fun decodeIdTokenHintClaimsHandlesArrayAud() {
        val hint =
            jws(
                "{\"iss\":\"$expectedIssuer\"," +
                    "\"sub\":\"alice\",\"aud\":[\"client-1\",\"client-2\"]}",
            )
        val claims = decodeIdTokenHintClaims(hint, expectedIssuer)
        assertEquals("alice", claims?.sub)
        assertEquals("client-1", claims?.aud, "first audience must be exposed for single-client matching")
    }
}
