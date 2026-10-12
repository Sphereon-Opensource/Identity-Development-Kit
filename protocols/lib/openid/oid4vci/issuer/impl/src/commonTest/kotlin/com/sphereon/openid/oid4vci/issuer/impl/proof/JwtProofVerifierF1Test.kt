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

package com.sphereon.openid.oid4vci.issuer.impl.proof

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwsJsonGeneralWithIdentifiers
import com.sphereon.crypto.jose.jws.JwsJsonSignatureWithIdentifier
import com.sphereon.crypto.jose.jws.JwsValidationResult
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommand
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.MultiExternalIdentifierService
import com.sphereon.crypto.resolution.IIdentifierMethod
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.impl.nonce.NonceManager
import com.sphereon.openid.oid4vci.issuer.store.CredentialNonceStore
import com.sphereon.openid.oid4vci.issuer.store.NonceEntry
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises Appendix F.1 validation after the JWS command has verified the compact signature. */
class JwtProofVerifierF1Test {
    @Test
    fun rejectsEveryCombinationOfMutuallyExclusiveKeySelectors() = runTest {
        val conflictingHeaders =
            listOf(
                buildJsonObject { putBaseHeader(); put("kid", "holder-key"); put("jwk", publicJwk()) },
                buildJsonObject { putBaseHeader(); put("kid", "holder-key"); put("x5c", "certificate") },
                buildJsonObject { putBaseHeader(); put("jwk", publicJwk()); put("x5c", "certificate") },
            )

        val nonceStore = CountingNonceStore()
        val verifier = verifier(nonceStore)
        conflictingHeaders.forEach { header ->
            val result = verifier.verify(proof(header), ISSUER, CLIENT_ID, CONFIG_ID, null, null, NONCE, consumeNonce = true)
            assertTrue(result.isErr)
            assertEquals("invalid_proof", result.error.code)
            assertTrue(result.error.message.defaultMessage.contains("mutually exclusive"))
        }
        assertEquals(0, nonceStore.consumeCalls, "conflicting key selectors must be rejected before nonce consumption")
    }

    @Test
    fun proofWithUnknownClientIdRejectsIssuerClaimItCannotValidate() = runTest {
        val absent = verifier().verify(proof(validHeader()), ISSUER, null, CONFIG_ID, null, null, NONCE, consumeNonce = false)
        assertTrue(absent.isOk)

        val present = verifier().verify(proof(validHeader(), issuer = JsonPrimitive(CLIENT_ID)), ISSUER, null, CONFIG_ID, null, null, NONCE, consumeNonce = false)
        assertTrue(present.isErr)
        assertEquals("invalid_proof", present.error.code)
        assertTrue(present.error.message.defaultMessage.contains("without a token-bound client_id"))
    }

    @Test
    fun identifiedProofAllowsOptionalIssuerOnlyWhenItMatchesTokenClient() = runTest {
        val absent = verifier().verify(proof(validHeader()), ISSUER, CLIENT_ID, CONFIG_ID, null, null, NONCE, consumeNonce = false)
        assertTrue(absent.isOk, "Appendix F.1 makes iss optional outside anonymous pre-authorized access")

        val matching = verifier().verify(proof(validHeader(), issuer = JsonPrimitive(CLIENT_ID)), ISSUER, CLIENT_ID, CONFIG_ID, null, null, NONCE, consumeNonce = false)
        assertTrue(matching.isOk)

        val mismatched = verifier().verify(proof(validHeader(), issuer = JsonPrimitive("another-client")), ISSUER, CLIENT_ID, CONFIG_ID, null, null, NONCE, consumeNonce = false)
        assertTrue(mismatched.isErr)
        assertEquals("invalid_proof", mismatched.error.code)
    }

    @Test
    fun issuerClaimMustBeAStringWhenPresent() = runTest {
        val malformedClaims =
            listOf(
                JsonPrimitive(7),
                buildJsonObject { put("client", "wallet-client") },
                JsonNull,
            )

        malformedClaims.forEach { issuer ->
            val result = verifier().verify(proof(validHeader(), issuer), ISSUER, CLIENT_ID, CONFIG_ID, null, null, NONCE, consumeNonce = false)
            assertTrue(result.isErr)
            assertEquals("invalid_proof", result.error.code)
            assertTrue(result.error.message.defaultMessage.contains("iss must be a string"))
        }
    }

    private fun verifier(nonceStore: CredentialNonceStore = CountingNonceStore()): JwtProofVerifier {
        val resolver = UnusedExternalIdentifierResolver
        return JwtProofVerifier(
            nonceManager = NonceManager(nonceStore),
            verifyJwsCommand = VerifiedJwsBoundaryCommand,
            externalIdentifierResolver = resolver,
            keyAttestationVerifier = KeyAttestationVerifier(VerifiedJwsBoundaryCommand, resolver),
            issuerConfigProvider = TestIssuerConfigProvider,
        )
    }

    @Test
    fun malformedAudienceAndNonceReturnTypedErrorsWithoutConsumingNonce() = runTest {
        val malformed = listOf(JsonPrimitive(7), JsonPrimitive(true), JsonNull, JsonObject(emptyMap()), JsonArray(emptyList()))
        for ((claim, code) in listOf("aud" to "invalid_proof", "nonce" to "invalid_nonce")) {
            for (value in malformed) {
                val nonceStore = CountingNonceStore()
                val result = verifier(nonceStore).verify(
                    proof(validHeader(), overrides = mapOf(claim to value)), ISSUER, CLIENT_ID, CONFIG_ID, null, null, NONCE, consumeNonce = true,
                )
                assertTrue(result.isErr)
                assertEquals(code, result.error.code)
                assertEquals(0, nonceStore.consumeCalls)
            }
        }
    }

    @Test
    fun issuedAtRequiresNumericDateAndAllowsFractionalSeconds() = runTest {
        val malformed = listOf(JsonPrimitive("1700000000"), JsonPrimitive(true), JsonNull, JsonObject(emptyMap()), JsonArray(emptyList()))
        for (value in malformed) {
            val nonceStore = CountingNonceStore()
            val result = verifier(nonceStore).verify(
                proof(validHeader(), overrides = mapOf("iat" to value)), ISSUER, CLIENT_ID, CONFIG_ID, null, null, NONCE, consumeNonce = true,
            )
            assertTrue(result.isErr)
            assertEquals("invalid_proof", result.error.code)
            assertEquals(0, nonceStore.consumeCalls)
        }
        // Encode the legal JSON exponent directly: reserializing its parsed Double
        // would reject Infinity in the fixture before the verifier sees the wire input.
        val overflowNonceStore = CountingNonceStore()
        val overflow = verifier(overflowNonceStore).verify(
            proof(validHeader(), rawPayload = """{"aud":"https://issuer.example","iat":1e309,"nonce":"test-c-nonce"}"""),
            ISSUER, CLIENT_ID, CONFIG_ID, null, null, NONCE, consumeNonce = true,
        )
        assertTrue(overflow.isErr)
        assertEquals("invalid_proof", overflow.error.code)
        assertEquals(0, overflowNonceStore.consumeCalls)
        val fractional = verifier().verify(
            proof(validHeader(), overrides = mapOf("iat" to JsonPrimitive(1_700_000_000.5))), ISSUER, CLIENT_ID, CONFIG_ID, null, null, NONCE, consumeNonce = false,
        )
        assertTrue(fractional.isOk, "NumericDate permits non-integer seconds")
    }

    private fun proof(header: JsonObject, issuer: JsonElement? = null, overrides: Map<String, JsonElement> = emptyMap(), rawPayload: String? = null): JsonPrimitive {
        val payload = buildJsonObject {
            put("aud", ISSUER)
            put("iat", 1_700_000_000L)
            put("nonce", NONCE)
            issuer?.let { put("iss", it) }
            overrides.forEach { (name, value) -> put(name, value) }
        }
        val headerPart = Json.encodeToString(JsonObject.serializer(), header).encodeToByteArray().encodeToBase64Url()
        val payloadPart = (rawPayload ?: Json.encodeToString(JsonObject.serializer(), payload)).encodeToByteArray().encodeToBase64Url()
        return JsonPrimitive("$headerPart.$payloadPart.${"signature".encodeToByteArray().encodeToBase64Url()}")
    }

    private fun validHeader(): JsonObject = buildJsonObject {
        put("typ", JwtProofVerifier.PROOF_TYPE_HEADER)
        put("alg", "ES256")
        put("jwk", publicJwk())
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putBaseHeader() {
        put("typ", JwtProofVerifier.PROOF_TYPE_HEADER)
        put("alg", "ES256")
    }

    private fun publicJwk(): JsonObject = buildJsonObject {
        put("kty", "EC")
        put("crv", "P-256")
        put("x", "AA")
        put("y", "AA")
    }

    private companion object {
        const val ISSUER = "https://issuer.example"
        const val CLIENT_ID = "wallet-client"
        const val CONFIG_ID = "eupid"
        const val NONCE = "test-c-nonce"
    }
}

/** The verifier's crypto boundary is stubbed so these cases isolate F.1 claim/header policy. */
private object VerifiedJwsBoundaryCommand : VerifyJwsCommand {
    override val inputTypeToken: TypeToken<VerifyJwsArgs> = typeToken()
    override val outputTypeToken: TypeToken<JwsValidationResult> = typeToken()
    override val isEnabled: Boolean = true

    override suspend fun execute(args: VerifyJwsArgs): IdkResult<JwsValidationResult, IdkError> {
        val compact = args.jws as? JwsCompact ?: return Err(IdkError.fromString(code = "test_invalid_jws", message = "Expected compact JWS"))
        val segments = compact.value.split('.')
        if (segments.size != 3) return Err(IdkError.fromString(code = "test_invalid_jws", message = "Malformed compact JWS"))
        val header = Json.parseToJsonElement(segments[0].decodeFromBase64Url().decodeToString()).jsonObject
        val payload = Json.parseToJsonElement(segments[1].decodeFromBase64Url().decodeToString()).jsonObject
        return Ok(
            JwsValidationResult(
                jws = JwsJsonGeneralWithIdentifiers(
                    payload = segments[1],
                    signatures = listOf(JwsJsonSignatureWithIdentifier(segments[0], parsedProtectedHeader = header, signature = segments[2])),
                ),
                isValid = true,
                parsedPayload = payload,
            ),
        )
    }
}

private class CountingNonceStore : CredentialNonceStore {
    var consumeCalls: Int = 0

    override suspend fun create(nonce: String, ttlSeconds: Long): IdkResult<NonceEntry, IdkError> =
        Err(IdkError.fromString(code = "unused_test_operation", message = "Nonce store is not used in these tests"))

    override suspend fun consume(nonce: String): IdkResult<NonceEntry?, IdkError> {
        consumeCalls++
        return Err(IdkError.fromString(code = "unused_test_operation", message = "Nonce store is not used in these tests"))
    }
}

private object UnusedExternalIdentifierResolver : MultiExternalIdentifierService {
    override val supportedIdentifierMethods: List<IIdentifierMethod> = emptyList()
    override suspend fun isSupportedIdentifier(identifier: Any): Boolean = false
    override suspend fun isSupportedIdentifierMethod(identifierMethod: IIdentifierMethod): Boolean = false
    override suspend fun isSupportedOpts(opts: ExternalIdentifierOptsOrResult): Boolean = false
    override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierOpts, IdkErrorType> =
        Err(IdkError.fromString(code = "unused_test_operation", message = "External identifier resolution is not used in these tests"))
    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult, IdkErrorType> =
        Err(IdkError.fromString(code = "unused_test_operation", message = "External identifier resolution is not used in these tests"))
}

private object TestIssuerConfigProvider : Oid4vciIssuerConfigProvider {
    override val issuerIdentifier: String = "https://issuer.example"
    override val credentialConfigurations: Map<String, CredentialConfigurationSupported> = emptyMap()
    override val authorizationServers: List<String>? = null
    override val display: List<com.sphereon.openid.oid4vc.common.DisplayProperties>? = null
}
