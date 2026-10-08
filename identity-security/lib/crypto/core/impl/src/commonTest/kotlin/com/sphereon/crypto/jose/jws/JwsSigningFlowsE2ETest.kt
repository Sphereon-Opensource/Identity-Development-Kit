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
 *
 */

package com.sphereon.crypto.jose.jws

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.sign.DefaultSignatureService
import com.sphereon.crypto.core.sign.SignatureService
import com.sphereon.crypto.core.sign.model.CompleteSignatureRequest
import com.sphereon.crypto.core.sign.model.DigestRequest
import com.sphereon.crypto.core.sign.model.JwsSignatureParameters
import com.sphereon.crypto.core.sign.model.RawSignatureParameters
import com.sphereon.crypto.core.sign.model.SignInput
import com.sphereon.crypto.core.sign.model.SignatureLevel
import com.sphereon.crypto.core.testutil.createCryptoTestAppGraph
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

fun Any.asSignatureServiceGraph(): DefaultSignatureService.Graph = this as DefaultSignatureService.Graph

private fun <V, E> IdkResult<V, E>.getOrFail(context: String): V {
    if (!isOk) throw AssertionError("$context: $error")
    return value
}

class JwsSigningFlowsE2ETest {
    private lateinit var keyManagerService: KeyManagerService
    private lateinit var jwtService: JwtService
    private lateinit var signatureService: SignatureService
    private lateinit var ecKeyInfo: ManagedKeyInfoType<*>
    private lateinit var issuer: ManagedOptsKeyInfo

    val app = createCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("jws-signing-flows-test", principalType = com.sphereon.di.context.PrincipalType.USER)

    @BeforeTest
    fun setUp() {
        // Given: a software KMS provider is registered
        val config =
            SoftwareKmsProviderConfig(
                id = "signing-flows-test-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as SoftwareKmsProviderFactoryImpl.Graph
        val softwareKmsProvider = app.softwareKmsProvider.create(config, session.asCoreApiServiceGraph().serviceExecution)

        keyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)

        jwtService = (session.graph as JwtServiceImpl.Graph).jwtService
        signatureService = session.graph.asSignatureServiceGraph().signatureService
    }

    // Async key generation must happen inside runTest, not in @BeforeTest
    // (wasmJs @BeforeTest doesn't await Promises from runTest)
    private suspend fun initKeys() {
        val managedKeyPair = keyManagerService.generateKeyAsync(alg = SignatureAlgorithm.ECDSA_SHA256)
        ecKeyInfo = managedKeyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
        issuer =
            ManagedOptsKeyInfo(
                identifier = ecKeyInfo,
                context =
                    IdentifierContext(
                        clientId = "signing-flows-test",
                        clientIdScheme = "jwt_vc_json",
                        issuer = "https://example.com/signing-flows",
                    ),
            )
    }

    private val testPayload =
        JsonObject(
            mapOf(
                "sub" to JsonPrimitive("1234567890"),
                "name" to JsonPrimitive("John Doe"),
                "iat" to JsonPrimitive(1516239022),
            ),
        )

    // ========================================================================
    // Two-step JWS: prepareJws → createRawSignature → assemble → verify
    // ========================================================================

    @Test
    fun twoStepJwsCompactPrepareSignAssemble() =
        runTest {
            initKeys()
            // Given: a prepared JWS object
            val prepareArgs = CreateJwsJsonArgs(issuer = issuer, payload = testPayload)
            val prepared = jwtService.prepareJws(prepareArgs).getOrFail("prepareJws")

            // When: signing the signingInput externally via SignatureService
            val signatureBytes = signatureService.createRawSignature(ecKeyInfo, prepared.signingInput, requireX5Chain = false)

            // When: assembling a compact JWS
            val compactResult = prepared.assembleCompact(signatureBytes)

            // Then: verification succeeds
            val validationResult = jwtService.verifyJws(VerifyJwsArgs(jws = JwsCompact(compactResult.jwt))).getOrFail("verifyJws")
            assertTrue(validationResult.isValid, "JWS signature verification failed: ${validationResult.errorMessages}")
        }

    @Test
    fun twoStepJwsFlattenedPrepareSignAssemble() =
        runTest {
            initKeys()
            // Given: a prepared JWS object
            val prepareArgs = CreateJwsJsonArgs(issuer = issuer, payload = testPayload)
            val prepared = jwtService.prepareJws(prepareArgs).getOrFail("prepareJws")

            // When: signing the signingInput externally via SignatureService
            val signatureBytes = signatureService.createRawSignature(ecKeyInfo, prepared.signingInput, requireX5Chain = false)

            // When: assembling a flattened JSON JWS
            val flattenedResult = prepared.assembleFlattened(signatureBytes)

            // Then: verification succeeds
            val validationResult = jwtService.verifyJws(VerifyJwsArgs(jws = flattenedResult, identifier = issuer)).getOrFail("verifyJws")
            assertTrue(validationResult.isValid, "Flattened JWS signature verification failed: ${validationResult.errorMessages}")
        }

    @Test
    fun twoStepJwsGeneralPrepareSignAssemble() =
        runTest {
            initKeys()
            // Given: a prepared JWS object
            val prepareArgs = CreateJwsJsonArgs(issuer = issuer, payload = testPayload)
            val prepared = jwtService.prepareJws(prepareArgs).getOrFail("prepareJws")

            // When: signing the signingInput externally via SignatureService
            val signatureBytes = signatureService.createRawSignature(ecKeyInfo, prepared.signingInput, requireX5Chain = false)

            // When: assembling a general JSON JWS
            val generalResult = prepared.assembleGeneral(signatureBytes)

            // Then: verification succeeds
            val validationResult = jwtService.verifyJws(VerifyJwsArgs(jws = generalResult)).getOrFail("verifyJws")
            assertTrue(validationResult.isValid, "General JWS signature verification failed: ${validationResult.errorMessages}")
        }

    @Test
    fun twoStepJwsViaJwtServiceAssembleMethods() =
        runTest {
            initKeys()
            // Given: a prepared JWS object
            val prepareArgs = CreateJwsJsonArgs(issuer = issuer, payload = testPayload)
            val prepared = jwtService.prepareJws(prepareArgs).getOrFail("prepareJws")

            // When: signing the signingInput externally
            val signatureBytes = signatureService.createRawSignature(ecKeyInfo, prepared.signingInput, requireX5Chain = false)

            // When: assembling via JwtService methods (not extension functions)
            val compactResult = jwtService.assembleJwsCompact(prepared, signatureBytes)

            // Then: verification succeeds
            val validationResult = jwtService.verifyJws(VerifyJwsArgs(jws = JwsCompact(compactResult.jwt))).getOrFail("verifyJws")
            assertTrue(validationResult.isValid, "JWS via JwtService.assembleJwsCompact verification failed: ${validationResult.errorMessages}")
        }

    // ========================================================================
    // SignatureService one-step and two-step raw signing
    // ========================================================================

    @Test
    fun signatureServiceOneStepRawSign() =
        runTest {
            initKeys()
            // Given: input data to sign
            val data = "Hello, world!".encodeToByteArray()
            val signInput = SignInput(input = data, name = "test-doc")
            val params = RawSignatureParameters()

            // When: one-step signing via SignatureService
            val signOutput = signatureService.sign(signInput, ecKeyInfo, params).getOrFail("sign")
            assertNotNull(signOutput.signedData)

            // Then: validation succeeds
            val isValid = signatureService.validate(signInput, signOutput.signedData, ecKeyInfo).getOrFail("validate")
            assertTrue(isValid, "Signature validation returned false")
        }

    @Test
    fun signatureServiceTwoStepCreateDigestComplete() =
        runTest {
            initKeys()
            // Given: input data and a digest request
            val data = "Two-step signing test data".encodeToByteArray()
            val signInput = SignInput(input = data, name = "digest-test-doc")
            val params = RawSignatureParameters()
            val digestRequest = DigestRequest(input = signInput, keyInfo = ecKeyInfo, parameters = params)

            // When: step 1 - create digest
            val digestResponse = signatureService.createDigest(digestRequest).getOrFail("createDigest")

            // When: step 2 - sign the digest externally via KeyManagerService
            val signatureBytes = keyManagerService.createRawSignature(ecKeyInfo, digestResponse.digestToSign, requireX5Chain = false)

            // When: step 3 - complete the signature
            val completeRequest = CompleteSignatureRequest(digestResponse = digestResponse, signatureValue = signatureBytes)
            val signOutput = signatureService.completeSignature(completeRequest).getOrFail("completeSignature")
            assertNotNull(signOutput.signedData)

            // Then: validation against the original input succeeds
            val isValid = signatureService.validate(signInput, signOutput.signedData, ecKeyInfo).getOrFail("validate")
            assertTrue(isValid, "Signature validation returned false")
        }

    @Test
    fun signatureServiceTwoStepWithJwsSigningInput() =
        runTest {
            initKeys()
            // Given: a prepared JWS object
            val prepareArgs = CreateJwsJsonArgs(issuer = issuer, payload = testPayload)
            val prepared = jwtService.prepareJws(prepareArgs).getOrFail("prepareJws")

            // Given: wrap the JWS signingInput in a DigestRequest
            val signInput = SignInput(input = prepared.signingInput, name = "jws-digest")
            val params = RawSignatureParameters()
            val digestRequest = DigestRequest(input = signInput, keyInfo = ecKeyInfo, parameters = params)

            // When: create digest
            val digestResponse = signatureService.createDigest(digestRequest).getOrFail("createDigest")

            // When: sign the digest
            val signatureBytes = keyManagerService.createRawSignature(ecKeyInfo, digestResponse.digestToSign, requireX5Chain = false)

            // When: complete the signature
            val completeRequest = CompleteSignatureRequest(digestResponse = digestResponse, signatureValue = signatureBytes)
            val signOutput = signatureService.completeSignature(completeRequest).getOrFail("completeSignature")

            // When: assemble the JWS compact from the completed signature
            val compactResult = prepared.assembleCompact(signOutput.signedData)

            // Then: verification succeeds
            val validationResult = jwtService.verifyJws(VerifyJwsArgs(jws = JwsCompact(compactResult.jwt))).getOrFail("verifyJws")
            assertTrue(validationResult.isValid, "JWS via two-step digest flow verification failed: ${validationResult.errorMessages}")
        }

    // ========================================================================
    // SignatureService one-step JWS signing
    // ========================================================================

    @Test
    fun signatureServiceJwsOneStepSign() =
        runTest {
            initKeys()
            // Given: input data and JWS signature parameters
            val data = "Hello, JWS world!".encodeToByteArray()
            val signInput = SignInput(input = data, name = "jws-test-doc")
            val params = JwsSignatureParameters(issuer = issuer)

            // When: one-step JWS signing via SignatureService
            val signOutput = signatureService.sign(signInput, ecKeyInfo, params).getOrFail("sign JWS")

            // Then: output is a valid JWT
            assertNotNull(signOutput.signedData)
            assertTrue(signOutput.signatureLevel == SignatureLevel.JWS, "Expected JWS signature level")

            val jwt = signOutput.signedData.decodeToString()
            assertTrue(jwt.count { it == '.' } == 2, "Expected compact JWT format (header.payload.signature)")

            // Then: verification succeeds
            val validationResult = jwtService.verifyJws(VerifyJwsArgs(jws = JwsCompact(jwt))).getOrFail("verifyJws")
            assertTrue(validationResult.isValid, "JWS signature verification failed: ${validationResult.errorMessages}")
        }

    @Test
    fun signatureServiceJwsOneStepWithJsonPayload() =
        runTest {
            initKeys()
            // Given: a JSON payload provided explicitly in the parameters
            val signInput = SignInput(input = ByteArray(0), name = "jws-json-payload-doc")
            val params =
                JwsSignatureParameters(
                    issuer = issuer,
                    payload = testPayload,
                )

            // When: one-step JWS signing via SignatureService with explicit JSON payload
            val signOutput = signatureService.sign(signInput, ecKeyInfo, params).getOrFail("sign JWS with JSON payload")

            // Then: output is a valid JWT
            assertNotNull(signOutput.signedData)
            val jwt = signOutput.signedData.decodeToString()
            assertTrue(jwt.count { it == '.' } == 2, "Expected compact JWT format")

            // Then: verification succeeds
            val validationResult = jwtService.verifyJws(VerifyJwsArgs(jws = JwsCompact(jwt))).getOrFail("verifyJws")
            assertTrue(validationResult.isValid, "JWS with JSON payload verification failed: ${validationResult.errorMessages}")
        }

    // ========================================================================
    // Multiple algorithms
    // ========================================================================

    @Test
    fun twoStepJwsWithMultipleAlgorithms() =
        runTest {
            val algorithms =
                listOf(
                    SignatureAlgorithm.ECDSA_SHA256,
                    SignatureAlgorithm.ECDSA_SHA384,
                    SignatureAlgorithm.ECDSA_SHA512,
                )

            for (alg in algorithms) {
                // Given: a key pair for this algorithm
                val keyPair = keyManagerService.generateKeyAsync(alg = alg)
                val keyInfo = keyPair.joseToManagedKeyInfo(KeyVisibility.PRIVATE)
                val algName = alg.jose?.value ?: alg.toString()

                val algIssuer =
                    ManagedOptsKeyInfo(
                        identifier = keyInfo,
                        context =
                            IdentifierContext(
                                clientId = "multi-alg-test-$algName",
                                clientIdScheme = "jwt_vc_json",
                                issuer = "https://example.com/multi-alg-$algName",
                            ),
                    )

                val payload =
                    JsonObject(
                        mapOf(
                            "alg" to JsonPrimitive(algName),
                            "test" to JsonPrimitive("two-step-multi-alg"),
                        ),
                    )

                // Given: a prepared JWS
                val prepareArgs = CreateJwsJsonArgs(issuer = algIssuer, payload = payload)
                val prepared = jwtService.prepareJws(prepareArgs).getOrFail("prepareJws for $algName")

                // When: sign externally and assemble
                val signatureBytes = signatureService.createRawSignature(keyInfo, prepared.signingInput, requireX5Chain = false)
                val compactResult = prepared.assembleCompact(signatureBytes)

                // Then: verification succeeds
                val validationResult = jwtService.verifyJws(VerifyJwsArgs(jws = JwsCompact(compactResult.jwt))).getOrFail("verifyJws for $algName")
                assertTrue(validationResult.isValid, "JWS verification failed for $algName: ${validationResult.errorMessages}")
            }
        }
}
