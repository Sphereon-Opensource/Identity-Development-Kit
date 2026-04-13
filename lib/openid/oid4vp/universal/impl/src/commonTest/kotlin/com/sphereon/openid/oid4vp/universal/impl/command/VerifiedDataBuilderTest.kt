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
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialSetOption
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialSetQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
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
                sessionId = "session-1",
                correlationId = "corr-1",
                queryId = "kw1c-enrollment",
                dcqlQuery =
                    DcqlQuery(
                        credentials =
                            listOf(
                                DcqlCredentialQuery(id = "passport"),
                            ),
                        credential_sets =
                            listOf(
                                DcqlCredentialSetQuery(
                                    required = true,
                                    options =
                                        listOf(
                                            DcqlCredentialSetOption(credential_ids = listOf("passport")),
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
                        rawVpToken = """{"passport":"eyJhbGciOiJFUzI1NiJ9.payload.signature"}""",
                    ),
                validationResult =
                    ValidationResult(
                        valid = true,
                        matchedCredentials =
                            listOf(
                                MatchedCredential(
                                    credentialQueryId = "passport",
                                    format = "dc+sd-jwt",
                                    presentation = "eyJhbGciOiJFUzI1NiJ9.payload.signature",
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

        val dcqlResponse = authorizationResponse["dcql_response"]
        assertNotNull(dcqlResponse)
        val dcqlResponseObject = dcqlResponse.toString()
        assertEquals(true, dcqlResponseObject.contains("credential_matches"))
        assertEquals(true, dcqlResponseObject.contains("credential_set_matches"))
        assertEquals(true, dcqlResponseObject.contains("\"credential_set_id\":\"0\""))
        assertEquals(true, dcqlResponseObject.contains("\"credential_id\":\"passport\""))
    }
}
