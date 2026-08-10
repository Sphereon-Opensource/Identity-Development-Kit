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

package com.sphereon.openid.oid4vci.issuer.impl.format

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.jose.jws.JwsJsonFlattened
import com.sphereon.crypto.jose.jws.JwsJsonGeneral
import com.sphereon.crypto.jose.jws.JwsValidationResult
import com.sphereon.crypto.jose.jws.JwtCompactResult
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.PreparedJwsObject
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsJsonArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialDefinition
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import com.sphereon.openid.oid4vci.issuer.impl.signing.IssuerKeyIdResolver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Contract for the key a credential is signed under.
 *
 * The name arrives already resolved server side. A format handler consumes it and nothing else:
 *
 * 1. An issuance that carries no resolved name refuses, and no signature is produced.
 * 2. The credential configuration id never stands in for a key name, so a credential cannot be
 *    signed under whatever key happens to sit at a caller-visible identifier.
 * 3. Every unusable shape of the name refuses with the same message, so the refusal says nothing
 *    about which credential configurations hold which key material.
 */
class CredentialSigningKeyRefusalTest {
    @Test
    fun issuanceRefusesAndSignsNothingWithoutAServerResolvedKeyName() =
        runTest {
            val signer = RecordingJwtService()
            val handler = handler(signer)

            val result = handler.issueCredential(request(), context(signingKeyName = null))

            assertTrue(result.isErr)
            assertEquals(CREDENTIAL_SIGNING_KEY_UNAVAILABLE, result.error.message.defaultMessage)
            assertNull(signer.lastIssuerIdentifier, "a refused issuance must never reach the signer")
        }

    @Test
    fun theCredentialConfigurationIdIsNeverUsedAsAKeyName() =
        runTest {
            val signer = RecordingJwtService()
            val handler = handler(signer)

            val result = handler.issueCredential(request(), context(signingKeyName = SERVER_RESOLVED_KEY_NAME))

            assertTrue(result.isOk)
            assertEquals(SERVER_RESOLVED_KEY_NAME, signer.lastIssuerIdentifier)
        }

    @Test
    fun everyUnusableKeyNameShapeRefusesIdentically() =
        runTest {
            val messages =
                listOf(null, "", "   ").map { name ->
                    val result = handler(RecordingJwtService()).issueCredential(request(), context(signingKeyName = name))
                    assertTrue(result.isErr)
                    result.error.message.defaultMessage
                }

            assertEquals(1, messages.toSet().size, "unusable key names must refuse identically, got $messages")
            assertEquals(CREDENTIAL_SIGNING_KEY_UNAVAILABLE, messages.first())
        }

    private fun handler(jwtService: JwtService) =
        JwtVcJsonFormatHandler(
            jwtService = jwtService,
            kms = TestKmsMock(),
            issuerKeyIdResolver = RefusingIssuerKeyIdResolver,
        )

    private fun request() = CredentialRequest(format = "jwt_vc_json")

    private fun context(signingKeyName: String?) =
        IssuanceContext(
            subject = "did:example:holder123",
            clientId = "client-1",
            issuerIdentifier = "https://issuer.example.com",
            credentialConfigurationId = CREDENTIAL_CONFIGURATION_ID,
            credentialConfiguration =
                CredentialConfigurationSupported(
                    format = "jwt_vc_json",
                    credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential")),
                ),
            holderBindingKey = null,
            attributes = mapOf("given_name" to JsonPrimitive("Alice")),
            signingKeyAlias = signingKeyName,
        )

    private companion object {
        const val CREDENTIAL_CONFIGURATION_ID = "EuPid"
        const val SERVER_RESOLVED_KEY_NAME = "issuer-signing-acme"
    }
}

/** Records the key the signer was asked for, so a refusal that still signed would be visible. */
private class RecordingJwtService : JwtService {
    var lastIssuerIdentifier: String? = null
        private set

    override val commands: JwtService.Commands
        get() = throw UnsupportedOperationException("not used in this test")

    override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> {
        lastIssuerIdentifier = (args.issuer as? ManagedOptsAlias)?.identifier
        return Ok(JwtCompactResult(jwt = "eyJhbGciOiJFUzI1NiJ9.eyJ2YyI6e319.fakesignature"))
    }

    override suspend fun prepareJws(args: CreateJwsJsonArgs): IdkResult<PreparedJwsObject, IdkError> =
        throw UnsupportedOperationException("not used in this test")

    override suspend fun createJwsJsonFlattened(args: CreateJwsJsonArgs): IdkResult<JwsJsonFlattened, IdkError> =
        throw UnsupportedOperationException("not used in this test")

    override suspend fun createJwsJsonGeneral(args: CreateJwsJsonArgs): IdkResult<JwsJsonGeneral, IdkError> =
        throw UnsupportedOperationException("not used in this test")

    override suspend fun verifyJws(args: VerifyJwsArgs): IdkResult<JwsValidationResult, IdkError> =
        throw UnsupportedOperationException("not used in this test")

    override fun assembleJwsGeneral(
        prepared: PreparedJwsObject,
        signatureBytes: ByteArray,
    ) = throw UnsupportedOperationException("not used in this test")

    override fun assembleJwsFlattened(
        prepared: PreparedJwsObject,
        signatureBytes: ByteArray,
    ) = throw UnsupportedOperationException("not used in this test")

    override fun assembleJwsCompact(
        prepared: PreparedJwsObject,
        signatureBytes: ByteArray,
    ) = throw UnsupportedOperationException("not used in this test")
}

/** The default [com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode] emits no key identifier. */
private object RefusingIssuerKeyIdResolver : IssuerKeyIdResolver {
    override suspend fun resolveDidVerificationMethodId(
        keyAlias: String,
        didMethod: String,
    ): IdkResult<String, IdkError> = error("no key identifier is requested under the default signing key mode")

    override suspend fun resolvePublicJwk(keyAlias: String): IdkResult<JsonObject, IdkError> =
        error("no key identifier is requested under the default signing key mode")
}
