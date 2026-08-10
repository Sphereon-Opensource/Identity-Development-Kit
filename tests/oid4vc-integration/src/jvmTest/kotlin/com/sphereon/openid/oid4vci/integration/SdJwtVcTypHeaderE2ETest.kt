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

package com.sphereon.openid.oid4vci.integration

import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.issuer.format.CredentialFormatHandler
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Graph accessor so the test can pull the IETF SD-JWT VC format handler out of SessionScope.
 */
@ContributesTo(SessionScope::class)
interface SdJwtVcFormatHandlerTestGraph {
    val sdJwtVcFormatHandler:
        com.sphereon.openid.oid4vci.issuer.impl.format.SdJwtVcFormatHandler
}

/**
 * Locks in draft-ietf-oauth-sd-jwt-vc §3.1: the SD-JWT VC's JWT protected header MUST
 * `dc+sd-jwt` identifies IETF SD-JWT VC, while `vc+sd-jwt` identifies a W3C VCDM
 * credential secured using SD-JWT. These identifiers are distinct, not aliases.
 */
class SdJwtVcTypHeaderE2ETest {
    private val ctx = Oid4vciTestContext(this, protocolBasePath = "/oid4vci")
    private val json = Json { ignoreUnknownKeys = true }

    @OptIn(ExperimentalEncodingApi::class)
    private fun decodeHeader(sdJwt: String): JsonObject {
        val headerB64 = sdJwt.substringBefore('~').substringBefore('.')
        val padded = headerB64 + "=".repeat((4 - headerB64.length % 4) % 4)
        return json.parseToJsonElement(Base64.UrlSafe.decode(padded).decodeToString()) as JsonObject
    }

    private suspend fun issue(
        format: String,
        keyAlias: String
    ): String {
        val graph = ctx.session.graph as SdJwtVcFormatHandlerTestGraph
        val handler: CredentialFormatHandler = graph.sdJwtVcFormatHandler
        val kms =
            ctx.session.graph
                .asKeyManagerServiceGraph()
                .keyManagerService

        assertTrue(
            kms
                .generateKeyResult(
                    alias = keyAlias,
                    use = JwkUse.sig,
                    alg = SignatureAlgorithm.ECDSA_SHA256,
                ).isOk,
        )

        val holderJwk =
            buildJsonObject {
                put("kty", JsonPrimitive("EC"))
                put("crv", JsonPrimitive("P-256"))
                put("x", JsonPrimitive("x89Yr0mXBY2vYtDjeUuvK1QtawhSSFN3u3TURnEojEg"))
                put("y", JsonPrimitive("wB9mebM-vuLK7lkrw0UU4APHi5YQJu1Hh7wpqTlw-w8"))
            }

        val issuanceContext =
            IssuanceContext(
                subject = "test-subject",
                clientId = "test-client",
                issuerIdentifier = "https://issuer.example.com/oid4vci",
                credentialConfigurationId = keyAlias,
                credentialConfiguration =
                    CredentialConfigurationSupported(
                        format = format,
                        vct = "https://issuer.example.com/public/schema/vct/TestCredential",
                    ),
                holderBindingKey = holderJwk,
                holderIdentifier = null,
                holderKeyId = null, // jwk-bound, not DID
                attributes = mapOf("given_name" to JsonPrimitive("Alice")),
                signingKeyAlias = keyAlias,
                signingKeyMode = SigningKeyMode.Did("jwk"),
            )

        val result =
            handler.issueCredential(
                request =
                    com.sphereon.openid.oid4vci.common.model.CredentialRequest(
                        format = format,
                    ),
                context = issuanceContext,
            )
        assertTrue(
            result.isOk,
            "Issuance should succeed: ${if (result.isErr) result.error.message.defaultMessage else ""}",
        )
        return result.value.credential.jsonPrimitive.content
    }

    @Test
    fun dcSdJwtFormatProducesDcHeaderType() =
        runTest {
            val sdJwt = issue(format = CredentialFormat.SD_JWT_VC.value, keyAlias = "typ-test-dc")
            val header = decodeHeader(sdJwt)
            assertEquals(
                "dc+sd-jwt",
                header["typ"]?.jsonPrimitive?.content,
                "Format `dc+sd-jwt` must produce JWT header `typ: dc+sd-jwt` per SD-JWT VC §3.1",
            )
        }

    @Test
    fun dcHandlerDoesNotClaimW3cVcSdJwtFormat() =
        runTest {
            val handler = (ctx.session.graph as SdJwtVcFormatHandlerTestGraph).sdJwtVcFormatHandler
            assertFalse(
                handler.canHandle(
                    request = com.sphereon.openid.oid4vci.common.model.CredentialRequest(format = CredentialFormat.W3C_VC_SD_JWT.value),
                    configuration = CredentialConfigurationSupported(format = CredentialFormat.W3C_VC_SD_JWT.value),
                ),
                "The IETF SD-JWT VC handler must not treat W3C `vc+sd-jwt` as a `dc+sd-jwt` alias",
            )
        }

    @Test
    fun didJwkSigningModeAlsoSetsKidInHeader() =
        runTest {
            val sdJwt = issue(format = CredentialFormat.SD_JWT_VC.value, keyAlias = "typ-test-kid")
            val header = decodeHeader(sdJwt)
            assertTrue(
                header["typ"] != null,
                "typ header must be present alongside kid (both are mandatory for DID-signed SD-JWT VCs)",
            )
            assertTrue(
                header["kid"]?.jsonPrimitive?.content?.startsWith("did:jwk:") == true,
                "kid header must be a did:jwk:...#vm URL under Did(jwk) signing mode",
            )
        }
}
