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
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialDefinition
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JwtVcJsonFormatHandlerTest {
    private val handler = JwtVcJsonFormatHandler(jwtService = FakeJwtService())

    private fun makeConfig(
        format: String,
        types: List<String>? = null,
    ) = CredentialConfigurationSupported(
        format = format,
        credentialDefinition = types?.let { CredentialDefinition(type = it) },
    )

    private fun makeRequest(format: String? = null) = CredentialRequest(format = format)

    private fun makeContext(
        config: CredentialConfigurationSupported,
        attributes: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    ) = IssuanceContext(
        subject = "did:example:holder123",
        clientId = "client-1",
        issuerIdentifier = "https://issuer.example.com",
        credentialConfigurationId = "jwt-vc-config-1",
        credentialConfiguration = config,
        holderBindingKey = null,
        attributes = attributes,
    )

    @Test
    fun canHandleReturnsTrueForJwtVcJsonFormat() =
        runTest {
            val config = makeConfig("jwt_vc_json")
            val request = makeRequest()
            assertTrue(handler.canHandle(request, config))
        }

    @Test
    fun canHandleReturnsFalseForSdJwtDcFormat() =
        runTest {
            val config = makeConfig("dc+sd-jwt")
            val request = makeRequest()
            assertFalse(handler.canHandle(request, config))
        }

    @Test
    fun canHandleReturnsFalseForMsoMdocFormat() =
        runTest {
            val config = makeConfig("mso_mdoc")
            val request = makeRequest()
            assertFalse(handler.canHandle(request, config))
        }

    @Test
    fun issueCredentialProducesJwtWithCorrectFormat() =
        runTest {
            val config = makeConfig("jwt_vc_json", listOf("VerifiableCredential", "UniversityDegreeCredential"))
            val context =
                makeContext(
                    config = config,
                    attributes =
                        mapOf(
                            "degree" to JsonPrimitive("Bachelor of Science"),
                            "university" to JsonPrimitive("Example University"),
                        ),
                )
            val request = makeRequest("jwt_vc_json")

            val result = handler.issueCredential(request, context)

            assertTrue(result.isOk)
            val envelope = result.getOrThrow()
            assertEquals("jwt_vc_json", envelope.format)

            // The credential should be a JWT compact string (3 dot-separated parts)
            val jwtString = (envelope.credential as JsonPrimitive).content
            val parts = jwtString.split(".")
            assertEquals(3, parts.size, "JWT compact serialization should have 3 parts")
        }

    @Test
    fun issueCredentialDefaultsToVerifiableCredentialType() =
        runTest {
            // No credentialDefinition means types default to ["VerifiableCredential"]
            val config = makeConfig("jwt_vc_json")
            val context =
                makeContext(
                    config = config,
                    attributes = mapOf("name" to JsonPrimitive("Alice")),
                )
            val request = makeRequest("jwt_vc_json")

            val result = handler.issueCredential(request, context)

            assertTrue(result.isOk)
        }

    /**
     * Fake JwtService that produces deterministic JWT compact output for testing.
     */
    private class FakeJwtService : JwtService {
        override val commands: JwtService.Commands
            get() = throw UnsupportedOperationException("not used in tests")

        override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> {
            // Produce a fake but structurally valid JWT (3 dot-separated parts)
            return Ok(JwtCompactResult(jwt = "eyJhbGciOiJFUzI1NiJ9.eyJ2YyI6e319.fakesignature"))
        }

        override suspend fun prepareJws(args: CreateJwsJsonArgs): IdkResult<PreparedJwsObject, IdkError> = throw UnsupportedOperationException("not used in tests")

        override suspend fun createJwsJsonFlattened(args: CreateJwsJsonArgs): IdkResult<JwsJsonFlattened, IdkError> = throw UnsupportedOperationException("not used in tests")

        override suspend fun createJwsJsonGeneral(args: CreateJwsJsonArgs): IdkResult<JwsJsonGeneral, IdkError> = throw UnsupportedOperationException("not used in tests")

        override suspend fun verifyJws(args: VerifyJwsArgs): IdkResult<JwsValidationResult, IdkError> = throw UnsupportedOperationException("not used in tests")

        override fun assembleJwsGeneral(
            prepared: PreparedJwsObject,
            signatureBytes: ByteArray,
        ) = throw UnsupportedOperationException("not used in tests")

        override fun assembleJwsFlattened(
            prepared: PreparedJwsObject,
            signatureBytes: ByteArray,
        ) = throw UnsupportedOperationException("not used in tests")

        override fun assembleJwsCompact(
            prepared: PreparedJwsObject,
            signatureBytes: ByteArray,
        ) = throw UnsupportedOperationException("not used in tests")
    }
}
