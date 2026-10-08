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

package com.sphereon.openid.oid4vp.universal.impl.command

import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialSetQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.sdJwtVcMeta
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vp.verifier.CredentialIssuerRef
import com.sphereon.openid.oid4vp.verifier.CredentialTrustValidation
import com.sphereon.openid.oid4vp.verifier.CredentialTrustValidationMode
import com.sphereon.openid.oid4vp.verifier.MatchedCredential
import com.sphereon.openid.oid4vp.verifier.ParsedAuthorizationResponse
import com.sphereon.openid.oid4vp.verifier.ValidationResult
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VerifiedDataBuilderTest {
    @Test
    fun `build verified data reconstructs authorization response and credential set refs`() {
        val session =
            AuthorizationSession(
                instanceId = "verifier-instance-verified-data-builder",
                sessionId = "session-1",
                correlationId = "corr-1",
                queryId = "kw1c-enrollment",
                dcqlQuery =
                    DcqlQuery(
                        credentials =
                            listOf(
                                DcqlCredentialQuery(id = "passport", format = "dc+sd-jwt", meta = sdJwtVcMeta("urn:test:passport")),
                            ),
                        credential_sets =
                            listOf(
                                DcqlCredentialSetQuery(
                                    required = true,
                                    options =
                                        listOf(
                                            listOf("passport"),
                                        ),
                                ),
                            ),
                    ),
                authorizationRequest =
                    AuthorizationRequest(
                        clientId = "https://verifier.example.com",
                        redirectUri = "https://verifier.example.com/callback",
                        state = "state-123",
                    ),
                status = AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_VERIFIED,
                parsedResponse =
                    ParsedAuthorizationResponse(
                        vpToken =
                            com.sphereon.openid.oid4vp.common.vpTokenOf(
                                "passport",
                                "eyJhbGciOiJFUzI1NiJ9.payload.signature",
                            ),
                        state = "state-123",
                        rawVpToken = """{"passport":["eyJhbGciOiJFUzI1NiJ9.payload.signature"]}""",
                    ),
                validationResult =
                    ValidationResult(
                        valid = true,
                        matchedCredentials =
                            listOf(
                                MatchedCredential(
                                    credentialQueryId = "passport",
                                    credentialFormat = CredentialFormat.SD_JWT_VC,
                                    presentation = "eyJhbGciOiJFUzI1NiJ9.payload.signature",
                                    issuer =
                                        CredentialIssuerRef(
                                            issuer = "https://issuer.example.com",
                                            method = "openid_federation",
                                            oidfedEntityId = "https://issuer.example.com",
                                        ),
                                    trust =
                                        CredentialTrustValidation(
                                            enabled = true,
                                            trusted = true,
                                            mode = CredentialTrustValidationMode.DEFAULT_ENFORCE,
                                            method = "openid_federation",
                                            trustDomainIds = listOf("domain-1"),
                                            matchedTrustDomainId = "domain-1",
                                            matchedAnchorId = "anchor-1",
                                            status = "TRUSTED",
                                        ),
                                    disclosedClaims =
                                        mapOf(
                                            "given_name" to "Ada",
                                            "family_name" to "Lovelace",
                                        ),
                                ),
                            ),
                    ),
                createdAt = 1L,
                updatedAt = 2L,
                expiresAt = 3L,
            )

        val verifiedData = buildVerifiedData(session)
        assertNotNull(verifiedData)

        val authorizationResponse = verifiedData.authorizationResponse
        assertNotNull(authorizationResponse)
        assertEquals("state-123", authorizationResponse["state"]?.toString()?.trim('"'))
        assertNotNull(authorizationResponse["vp_token"])

        assertEquals(null, authorizationResponse["dcql_response"])
        // A valid historical result does not fabricate the newly required provenance.
        assertEquals(null, verifiedData.credentialClaims?.single()?.verificationEvidence)
        val evidence = com.sphereon.openid.oid4vp.verifier.VerifiedCredentialEvidence(
            presentationSha256 = "server-digest",
            verifiedAtEpochMillis = 2L,
            trust = CredentialTrustValidation(enabled = true, trusted = false,
                mode = CredentialTrustValidationMode.AUDIT, details = "private-diagnostic",
                diagnostics = listOf("private-address")),
            status = com.sphereon.openid.oid4vp.verifier.VerifiedCredentialStatus(
                com.sphereon.openid.oid4vp.verifier.VerifiedCredentialStatusOutcome.SKIPPED,
                required = false, rejectOnUnresolvable = true),
        )
        val original = session.validationResult!!.matchedCredentials.single()
        val withEvidence = session.copy(validationResult = ValidationResult(true, listOf(original.copy(
            verificationEvidence = evidence,
            disclosedClaims = mapOf("verificationEvidence" to "forged", "verifiedAtEpochMillis" to 999L),
        ))))
        val exposed = assertNotNull(buildVerifiedData(withEvidence)?.credentialClaims?.single()?.verificationEvidence)
        assertEquals("server-digest", exposed.presentationSha256)
        assertEquals(2L, exposed.verifiedAtEpochMillis)
        assertEquals(CredentialTrustValidationMode.AUDIT, exposed.trust?.mode)
        assertEquals(false, exposed.trust?.trusted)
        assertEquals(null, exposed.trust?.details)
        assertEquals(emptyList(), exposed.trust?.diagnostics)
    }
}
