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

package com.sphereon.crypto.jose.jws

import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.testutil.createCryptoTestAppGraph
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Regression test for the OIDF conformance test
 * `oid4vci-1_0-issuer-fail-invalid-client-attestation-signature`. The conformance suite
 * sends a JWT whose signature byte was flipped; the AS MUST reject. A 2026-04-30 production
 * trace showed the AS returning HTTP 201 (success) for a tampered attestation while the AS
 * log line `"Signature verification result: true"` claimed the verifier was happy.
 *
 * Driven entirely through the public [JwtService.verifyJws] entry point so this test pins
 * the same code path the AS attestation flow exercises (`trustedJwks` pinning + JOSE-header
 * `kid` lookup), and a parallel run that resolves the key from the JOSE header itself.
 */
class TamperedJwsSignatureRejectionTest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var jwtService: JwtService

    val app = createCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("tampered-jws-test")

    @BeforeTest
    fun setUp() {
        val config =
            SoftwareKmsProviderConfig(
                id = "tampered-jws-test-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as SoftwareKmsProviderFactoryImpl.Graph
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceGraph().serviceExecution)

        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        jwtService = (session.graph as JwtServiceImpl.Graph).jwtService
    }

    @Test
    fun tamperedCompactJws_throughTrustedJwksPath_isRejected() =
        runTest {
            val (validJwt, publicJwk) = createSignedJwsAndPublicKey()
            val tamperedJwt = flipSignatureByte(validJwt)

            // Pin the JWK with the same `kid` the JWT header carries — the AS attestation
            // flow does this via the JOSE-header `kid` we stamp onto the leaf-cert-derived
            // JWK in VerifyAttestationClientAuthCommandImpl.verifyAttestationX5cChain.
            val headerKid = readJwtHeaderKid(validJwt)
            val publicJwkJson = Json.encodeToJsonElement(Jwk.serializer(), publicJwk).let { it as JsonObject }
            val pinnedJwk =
                if (headerKid != null) {
                    JsonObject(publicJwkJson + ("kid" to JsonPrimitive(headerKid)))
                } else {
                    publicJwkJson
                }
            val trustedJwks =
                buildJsonObject {
                    put("keys", JsonArray(listOf(pinnedJwk)))
                }

            // Sanity check: the un-tampered JWT verifies against this trustedJwks.
            val validResult = jwtService.verifyJws(VerifyJwsArgs(jws = JwsCompact(validJwt), trustedJwks = trustedJwks))
            val validValidation = validResult.getOrNull()
            assertTrue(
                validResult.isOk && validValidation != null && validValidation.isValid,
                "Sanity check failed: untampered JWT should verify, got isOk=${validResult.isOk}, " +
                    "isValid=${validValidation?.isValid}, errors=${validValidation?.errorMessages}",
            )

            // Bit-flipped signature MUST be rejected.
            val tamperedResult = jwtService.verifyJws(VerifyJwsArgs(jws = JwsCompact(tamperedJwt), trustedJwks = trustedJwks))
            val tamperedValidation = tamperedResult.getOrNull()
            assertTrue(
                tamperedResult.isOk && tamperedValidation != null,
                "verifyJws should not return a hard error for a tampered signature; should report isValid=false",
            )
            assertFalse(
                tamperedValidation.isValid,
                "A bit-flipped JWS signature was reported as VALID through the trustedJwks path. " +
                    "errorMessages=${tamperedValidation.errorMessages}",
            )
        }

    @Test
    fun tamperedCompactJws_throughIdentifierResolverPath_isRejected() =
        runTest {
            val (validJwt, _) = createSignedJwsAndPublicKey()
            val tamperedJwt = flipSignatureByte(validJwt)

            // No trustedJwks: VerifyJwsCommand resolves the key through the identifier service
            // (kid → KMS managed key for our test setup).
            val tamperedResult = jwtService.verifyJws(VerifyJwsArgs(jws = JwsCompact(tamperedJwt)))
            val tamperedValidation = tamperedResult.getOrNull()
            assertTrue(
                tamperedResult.isOk && tamperedValidation != null,
                "verifyJws should not return a hard error; should report isValid=false",
            )
            assertFalse(
                tamperedValidation.isValid,
                "A bit-flipped JWS signature was reported as VALID through the identifier-resolver path. " +
                    "errorMessages=${tamperedValidation.errorMessages}",
            )
        }

    private fun readJwtHeaderKid(jwt: String): String? {
        val headerJson = jwt.substringBefore('.').decodeFromBase64Url().decodeToString()
        val headerObj = Json.parseToJsonElement(headerJson) as? JsonObject ?: return null
        return (headerObj["kid"] as? JsonPrimitive)?.content
    }

    private suspend fun createSignedJwsAndPublicKey(): Pair<String, Jwk> {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        val keyInfo: ManagedKeyInfoType<*> = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)

        val issuer =
            ManagedOptsKeyInfo(
                identifier = keyInfo,
                context =
                    IdentifierContext(
                        clientId = "test-client",
                        clientIdScheme = "jwt_vc_json",
                        issuer = "https://example.com/issuer",
                    ),
            )

        val payload =
            JsonObject(
                mapOf(
                    "sub" to JsonPrimitive("test-subject"),
                    "iat" to JsonPrimitive(1700000000),
                ),
            )

        val createResult = jwtService.createJwsCompact(CreateJwsArgs(issuer = issuer, payload = payload, mode = JwsIdentifierMode.AUTO))
        check(createResult.isOk) { "Failed to create JWS: ${createResult.error}" }
        val publicJwk = managedKeyPair.jose.publicJwk
        return createResult.value.jwt to publicJwk
    }

    /**
     * Flips the high bit of the first byte of the signature segment. The result is still
     * a valid base64url string and a 64-byte raw ECDSA P-256 signature, but the maths no
     * longer match — verifiers MUST reject.
     */
    private fun flipSignatureByte(compactJwt: String): String {
        val parts = compactJwt.split(".")
        check(parts.size == 3) { "expected three-segment compact JWS" }
        val sigBytes = parts[2].decodeFromBase64Url()
        sigBytes[0] = (sigBytes[0].toInt() xor 0xFF).toByte()
        val tamperedSig = sigBytes.encodeToBase64Url()
        return "${parts[0]}.${parts[1]}.$tamperedSig"
    }
}
