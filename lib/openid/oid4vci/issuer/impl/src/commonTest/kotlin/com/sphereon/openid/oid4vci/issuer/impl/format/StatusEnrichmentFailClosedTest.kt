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
import com.sphereon.statuslist.StatusListBinding
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusListToken
import com.sphereon.statuslist.impl.driver.InMemoryStatusListDriver
import com.sphereon.statuslist.impl.driver.InMemoryStatusListStore
import com.sphereon.statuslist.impl.enrich.CredentialStatusEnricherImpl
import com.sphereon.statuslist.spi.CredentialStatusEnricher
import com.sphereon.statuslist.spi.SignStatusListTokenArgs
import com.sphereon.statuslist.spi.StatusListSigner
import dev.zacsweers.metro.Provider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fail-closed contract of status-list enrichment during issuance: a credential configuration
 * bound to a status list must NEVER issue without its status claim — a credential issued without
 * one can never be revoked. Covers the three issuance outcomes:
 * - binding configured + no [CredentialStatusEnricher] wired → issuance fails,
 * - binding configured + reservation fails (status list missing) → issuance fails,
 * - no binding → issuance proceeds exactly as without status lists.
 *
 * The reservation-failure case runs through the REAL [CredentialStatusEnricherImpl] backed by the
 * real [InMemoryStatusListDriver]/[InMemoryStatusListStore] (no list created), so the propagated
 * error is the genuine driver-level one.
 */
class StatusEnrichmentFailClosedTest {
    private object StubIssuerKeyIdResolver : com.sphereon.openid.oid4vci.issuer.impl.signing.IssuerKeyIdResolver {
        override suspend fun resolveDidVerificationMethodId(
            keyAlias: String,
            didMethod: String,
        ): IdkResult<String, IdkError> = error("StubIssuerKeyIdResolver should not be invoked under SigningKeyMode.None")

        override suspend fun resolvePublicJwk(keyAlias: String,): IdkResult<kotlinx.serialization.json.JsonObject, IdkError> =
            error("StubIssuerKeyIdResolver should not be invoked under SigningKeyMode.None")
    }

    /** Signer that must never run in these tests: reservation fails before any token is minted. */
    private object UnreachableStatusListSigner : StatusListSigner {
        override suspend fun signStatusListToken(args: SignStatusListTokenArgs): IdkResult<StatusListToken, IdkError> =
            error("StatusListSigner must not be invoked when the referenced status list does not exist")
    }

    private val recordingJwtService = RecordingJwtService()

    private fun handler(enricher: CredentialStatusEnricher? = null) =
        JwtVcJsonFormatHandler(
            jwtService = recordingJwtService,
            kms = TestKmsMock(),
            issuerKeyIdResolver = StubIssuerKeyIdResolver,
            statusEnricherProvider = enricher?.let { Provider { it } },
        )

    private fun realEnricherWithEmptyStore(): CredentialStatusEnricher =
        CredentialStatusEnricherImpl(
            driver = InMemoryStatusListDriver(InMemoryStatusListStore(), UnreachableStatusListSigner),
        )

    private val config =
        CredentialConfigurationSupported(
            format = "jwt_vc_json",
            credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "EuPidCredential")),
        )

    private fun makeContext(statusListBinding: StatusListBinding? = null) =
        IssuanceContext(
            subject = "did:example:holder123",
            clientId = "client-1",
            issuerIdentifier = "https://issuer.example.com",
            credentialConfigurationId = "EuPid",
            credentialConfiguration = config,
            holderBindingKey = null,
            attributes = mapOf("given_name" to JsonPrimitive("Alice")),
            statusListBinding = statusListBinding,
        )

    private val binding =
        StatusListBinding(
            statusListCorrelationId = "eupid-revocation",
            spec = StatusListSpec.BITSTRING_STATUS_LIST,
        )

    @Test
    fun statusListConfiguredWithoutEnricherFailsIssuance() =
        runTest {
            val result = handler(enricher = null).issueCredential(CredentialRequest(format = "jwt_vc_json"), makeContext(binding))

            assertTrue(result.isErr, "issuance must fail when the status-list integration is absent")
            assertEquals("STATUSLIST_ENRICHER_UNAVAILABLE", result.error.code)
            assertTrue(
                "EuPid" in result.error.message.defaultMessage,
                "the error must name the credential configuration: ${result.error.message.defaultMessage}",
            )
            assertNull(recordingJwtService.lastPayload, "no credential may be signed when enrichment is unavailable")
        }

    @Test
    fun statusListConfiguredWithFailingReservationFailsIssuance() =
        runTest {
            // Real enricher + real in-memory driver, but the bound list was never created: the
            // genuine driver-level error must abort issuance instead of issuing without a status.
            val result =
                handler(enricher = realEnricherWithEmptyStore())
                    .issueCredential(CredentialRequest(format = "jwt_vc_json"), makeContext(binding))

            assertTrue(result.isErr, "issuance must fail when status reservation fails")
            assertEquals("STATUSLIST_LIST_NOT_FOUND", result.error.code)
            assertNull(recordingJwtService.lastPayload, "no credential may be signed when reservation fails")
        }

    @Test
    fun noStatusListConfiguredIssuesUnchanged() =
        runTest {
            val result = handler(enricher = null).issueCredential(CredentialRequest(format = "jwt_vc_json"), makeContext(statusListBinding = null))

            assertTrue(result.isOk, "issuance without a status-list binding must succeed without the integration")
            val vc = assertNotNull(recordingJwtService.lastPayload)["vc"]?.jsonObject
            assertNotNull(vc, "signed payload must carry the vc claim")
            assertFalse("credentialStatus" in vc, "a configuration without a status list must not gain a status claim")
        }

    @Test
    fun formatWithoutStatusSupportRejectsConfiguredBinding() {
        val mdocConfig = CredentialConfigurationSupported(format = "mso_mdoc", doctype = "eu.europa.ec.eudi.pid.1")
        val context = makeContext(binding).copy(credentialConfiguration = mdocConfig)

        val error = assertNotNull(unsupportedStatusListBinding(context), "a bound status list must be rejected by formats without status support")
        assertEquals("STATUSLIST_ENRICHMENT_UNSUPPORTED_FORMAT", error.code)
        assertTrue("mso_mdoc" in error.message.defaultMessage)

        assertNull(unsupportedStatusListBinding(makeContext(statusListBinding = null)), "no binding means no rejection")
    }

    /**
     * JwtService recording the last signed payload so tests can assert what was (not) signed.
     * Produces a structurally valid compact JWT.
     */
    private class RecordingJwtService : JwtService {
        var lastPayload: kotlinx.serialization.json.JsonObject? = null

        override val commands: JwtService.Commands
            get() = throw UnsupportedOperationException("not used in tests")

        override suspend fun createJwsCompact(args: CreateJwsArgs): IdkResult<JwtCompactResult, IdkError> {
            lastPayload = args.payload as? kotlinx.serialization.json.JsonObject
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
