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

package com.sphereon.oauth2.common.command

import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Focused unit tests for the shared DPoP assembly seam: both
 * [com.sphereon.oauth2.client.impl.dpop.CreateDpopProofCommandImpl] (KMS-backed signing) and the
 * wallet WSCA local-signing path build their DPoP proofs through this single implementation.
 */
class DpopProofAssemblyTest {
    private val assembly = DpopProofAssembly(defaultSecureRandom())

    private val publicJwk =
        Jwk(
            kty = JwaKeyType.EC,
            crv = JwaCurve.P_256,
            x = "test-x",
            y = "test-y",
        )

    @Test
    fun assembleBuildsAConformantHeaderAndPayload() =
        runTest {
            val assembled =
                assembly.assemble(
                    DpopProofAssemblyRequest(
                        httpMethod = "post",
                        httpUrl = "https://as.example.com/token?ignored=true#fragment",
                        nonce = "server-nonce",
                        issuedAt = 1704067200L,
                    ),
                    publicJwk,
                )

            assertEquals("dpop+jwt", assembled.headerJson["typ"]?.jsonPrimitive?.content)
            assertEquals("ES256", assembled.headerJson["alg"]?.jsonPrimitive?.content)
            assertEquals("POST", assembled.payloadJson["htm"]?.jsonPrimitive?.content, "httpMethod must be uppercased")
            assertEquals("https://as.example.com/token", assembled.payloadJson["htu"]?.jsonPrimitive?.content, "htu must drop query and fragment")
            assertEquals("server-nonce", assembled.payloadJson["nonce"]?.jsonPrimitive?.content)
            assertEquals(1704067200L, assembled.payloadJson["iat"]?.jsonPrimitive?.content?.toLong())
            assertNull(assembled.payloadJson["ath"], "no access token was supplied, so ath must be absent")
            assertTrue(assembled.jwkThumbprint.isNotBlank())
        }

    @Test
    fun assembleComputesAccessTokenHashOnlyWhenTokenIsPresent() =
        runTest {
            val withToken =
                assembly.assemble(
                    DpopProofAssemblyRequest(httpMethod = "GET", httpUrl = "https://rs.example.com/resource", accessToken = "token-1"),
                    publicJwk,
                )
            assertTrue(withToken.payloadJson.containsKey("ath"))

            val withoutToken =
                assembly.assemble(
                    DpopProofAssemblyRequest(httpMethod = "GET", httpUrl = "https://rs.example.com/resource"),
                    publicJwk,
                )
            assertTrue(!withoutToken.payloadJson.containsKey("ath"))
        }

    @Test
    fun assembleProducesAUniqueJtiOnEveryCall() =
        runTest {
            val request = DpopProofAssemblyRequest(httpMethod = "GET", httpUrl = "https://rs.example.com/resource")
            val first = assembly.assemble(request, publicJwk)
            val second = assembly.assemble(request, publicJwk)

            assertNotEquals(first.signingInput.decodeToString(), second.signingInput.decodeToString())
        }

    @Test
    fun signingInputIsTheBase64UrlHeaderDotPayload() =
        runTest {
            val assembled =
                assembly.assemble(
                    DpopProofAssemblyRequest(httpMethod = "GET", httpUrl = "https://rs.example.com/resource"),
                    publicJwk,
                )

            assertEquals("${assembled.encodedHeader}.${assembled.encodedPayload}", assembled.signingInput.decodeToString())
            // Round-trips: decoding the signing input's payload segment recovers the exposed
            // payloadJsonString exactly.
            assertEquals(assembled.payloadJsonString, assembled.encodedPayload.decodeFromBase64Url().decodeToString())
        }

    @Test
    fun finishAppendsTheBase64UrlEncodedSignatureToProduceACompactJws() =
        runTest {
            val assembled =
                assembly.assemble(
                    DpopProofAssemblyRequest(httpMethod = "GET", httpUrl = "https://rs.example.com/resource"),
                    publicJwk,
                )
            val signature = "fake-signature-bytes".encodeToByteArray()

            val compact = assembly.finish(assembled, signature)
            val parts = compact.split('.')

            assertEquals(3, parts.size, "a DPoP proof must be a compact JWS")
            assertEquals(assembled.encodedHeader, parts[0])
            assertEquals(assembled.encodedPayload, parts[1])
            assertTrue(signature.contentEquals(parts[2].decodeFromBase64Url()))
        }
}
