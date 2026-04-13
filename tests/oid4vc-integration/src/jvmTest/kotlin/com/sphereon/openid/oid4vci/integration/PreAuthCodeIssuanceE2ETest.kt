/*
 * (c) 2026 Sphereon International B.V.
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

import com.sphereon.openid.oid4vci.common.Oid4vciJson
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialDefinition
import com.sphereon.openid.oid4vci.common.model.CredentialIssuerMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.common.model.CredentialOfferGrants
import com.sphereon.openid.oid4vci.common.model.PreAuthorizedCodeOfferGrant
import com.sphereon.openid.oid4vci.common.model.ProofTypeSupported
import com.sphereon.openid.oid4vci.common.model.TxCodeConfig
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end integration tests for the OID4VCI pre-authorized code issuance flow.
 *
 * These tests exercise the OID4VCI data models and serialization paths that underpin
 * the full issuance flow:
 *
 * 1. Issuer creates credential offer with pre-authorized code
 * 2. Holder parses offer (JSON deserialization)
 * 3. Holder resolves issuer metadata
 * 4. Holder exchanges pre-auth code for token at AS
 * 5. AS returns token with authorization_details + credential_identifiers
 * 6. Holder creates proof (JWT key binding)
 * 7. Holder requests credential from issuer
 * 8. Issuer validates token, verifies proof, issues credential
 * 9. Holder receives credential
 *
 * The serialization tests below validate the data contract independently.
 * DI-based flow tests that exercise the full wired graph are aspirational
 * and require command interface bindings (see TODO at end of file).
 */
class PreAuthCodeIssuanceE2ETest {
    private val json: Json = Oid4vciJson.lenientNoDefaults

    // =========================================================================
    // Test fixture data
    // =========================================================================

    private val issuerUrl = "https://issuer.example.com"
    private val asUrl = "https://as.example.com"

    private val universityDegreeConfig =
        CredentialConfigurationSupported(
            format = "jwt_vc_json",
            scope = "degree",
            cryptographicBindingMethodsSupported = listOf("did:key", "did:jwk"),
            credentialSigningAlgValuesSupported = listOf("ES256"),
            credentialDefinition =
                CredentialDefinition(
                    type = listOf("VerifiableCredential", "UniversityDegreeCredential"),
                ),
            proofTypesSupported =
                mapOf(
                    "jwt" to
                        ProofTypeSupported(
                            proofSigningAlgValuesSupported = listOf("ES256"),
                        ),
                ),
        )

    private val pidConfig =
        CredentialConfigurationSupported(
            format = "dc+sd-jwt",
            vct = "https://credentials.example.com/identity_credential",
            scope = "pid",
            cryptographicBindingMethodsSupported = listOf("did:key"),
            credentialSigningAlgValuesSupported = listOf("ES256"),
            proofTypesSupported =
                mapOf(
                    "jwt" to
                        ProofTypeSupported(
                            proofSigningAlgValuesSupported = listOf("ES256"),
                        ),
                ),
        )

    private val issuerMetadata =
        CredentialIssuerMetadata(
            credentialIssuer = issuerUrl,
            credentialEndpoint = "$issuerUrl/credential",
            nonceEndpoint = "$issuerUrl/nonce",
            deferredCredentialEndpoint = "$issuerUrl/deferred",
            notificationEndpoint = "$issuerUrl/notification",
            authorizationServers = listOf(asUrl),
            credentialConfigurationsSupported =
                mapOf(
                    "UniversityDegree" to universityDegreeConfig,
                    "PID" to pidConfig,
                ),
        )

    // =========================================================================
    // Step 1: Credential offer creation and parsing
    // =========================================================================

    @Test
    fun credentialOfferWithPreAuthCodeRoundTrips() =
        runTest {
            // Issuer creates a credential offer with pre-authorized code grant
            val offer =
                CredentialOffer(
                    credentialIssuer = issuerUrl,
                    credentialConfigurationIds = listOf("UniversityDegree"),
                    grants =
                        CredentialOfferGrants(
                            preAuthorizedCode =
                                PreAuthorizedCodeOfferGrant(
                                    preAuthorizedCode = "pre-auth-code-abc123",
                                    authorizationServer = asUrl,
                                ),
                        ),
                )

            // Serialize to JSON (as it would appear in the offer URI)
            val offerJson = json.encodeToString(CredentialOffer.serializer(), offer)

            // Holder parses the offer
            val parsed = json.decodeFromString(CredentialOffer.serializer(), offerJson)

            assertEquals(issuerUrl, parsed.credentialIssuer)
            assertEquals(listOf("UniversityDegree"), parsed.credentialConfigurationIds)
            assertNotNull(parsed.grants?.preAuthorizedCode)
            assertEquals("pre-auth-code-abc123", parsed.grants?.preAuthorizedCode?.preAuthorizedCode)
            assertEquals(asUrl, parsed.grants?.preAuthorizedCode?.authorizationServer)
            assertNull(parsed.grants?.authorizationCode, "No auth code grant in pre-auth flow")
        }

    @Test
    fun credentialOfferWithTxCodeRoundTrips() =
        runTest {
            val offer =
                CredentialOffer(
                    credentialIssuer = issuerUrl,
                    credentialConfigurationIds = listOf("PID"),
                    grants =
                        CredentialOfferGrants(
                            preAuthorizedCode =
                                PreAuthorizedCodeOfferGrant(
                                    preAuthorizedCode = "pre-auth-code-txcode-456",
                                    txCode =
                                        TxCodeConfig(
                                            inputMode = "numeric",
                                            length = 6,
                                            description = "Enter the 6-digit code sent to your email",
                                        ),
                                    authorizationServer = asUrl,
                                ),
                        ),
                )

            val offerJson = json.encodeToString(CredentialOffer.serializer(), offer)
            val parsed = json.decodeFromString(CredentialOffer.serializer(), offerJson)

            assertNotNull(parsed.grants?.preAuthorizedCode?.txCode)
            assertEquals(
                "numeric",
                parsed.grants
                    ?.preAuthorizedCode
                    ?.txCode
                    ?.inputMode,
            )
            assertEquals(
                6,
                parsed.grants
                    ?.preAuthorizedCode
                    ?.txCode
                    ?.length,
            )
            assertNotNull(
                parsed.grants
                    ?.preAuthorizedCode
                    ?.txCode
                    ?.description,
            )
        }

    @Test
    fun credentialOfferWithMultipleConfigIdsRoundTrips() =
        runTest {
            val offer =
                CredentialOffer(
                    credentialIssuer = issuerUrl,
                    credentialConfigurationIds = listOf("UniversityDegree", "PID"),
                    grants =
                        CredentialOfferGrants(
                            preAuthorizedCode =
                                PreAuthorizedCodeOfferGrant(
                                    preAuthorizedCode = "pre-auth-code-multi-789",
                                ),
                        ),
                )

            val offerJson = json.encodeToString(CredentialOffer.serializer(), offer)
            val parsed = json.decodeFromString(CredentialOffer.serializer(), offerJson)

            assertEquals(2, parsed.credentialConfigurationIds.size)
            assertTrue("UniversityDegree" in parsed.credentialConfigurationIds)
            assertTrue("PID" in parsed.credentialConfigurationIds)
        }

    // =========================================================================
    // Step 2: Issuer metadata resolution
    // =========================================================================

    @Test
    fun issuerMetadataRoundTripsWithAllEndpoints() =
        runTest {
            val metadataJson = json.encodeToString(CredentialIssuerMetadata.serializer(), issuerMetadata)
            val parsed = json.decodeFromString(CredentialIssuerMetadata.serializer(), metadataJson)

            assertEquals(issuerUrl, parsed.credentialIssuer)
            assertEquals("$issuerUrl/credential", parsed.credentialEndpoint)
            assertEquals("$issuerUrl/nonce", parsed.nonceEndpoint)
            assertEquals("$issuerUrl/deferred", parsed.deferredCredentialEndpoint)
            assertEquals("$issuerUrl/notification", parsed.notificationEndpoint)
            assertEquals(listOf(asUrl), parsed.authorizationServers)
            assertEquals(2, parsed.credentialConfigurationsSupported.size)

            // Verify credential configurations
            val degree = parsed.credentialConfigurationsSupported["UniversityDegree"]
            assertNotNull(degree)
            assertEquals("jwt_vc_json", degree.format)
            assertEquals("degree", degree.scope)
            assertEquals(listOf("did:key", "did:jwk"), degree.cryptographicBindingMethodsSupported)
            assertEquals(listOf("ES256"), degree.credentialSigningAlgValuesSupported)
            val degreeDef = degree.credentialDefinition
            assertNotNull(degreeDef)
            assertEquals(listOf("VerifiableCredential", "UniversityDegreeCredential"), degreeDef.type)

            val pid = parsed.credentialConfigurationsSupported["PID"]
            assertNotNull(pid)
            assertEquals("dc+sd-jwt", pid.format)
            assertEquals("https://credentials.example.com/identity_credential", pid.vct)
        }

    @Test
    fun holderCanMatchOfferConfigIdsToMetadata() =
        runTest {
            // Simulate the holder matching credential_configuration_ids from the offer
            // against the issuer metadata
            val offer =
                CredentialOffer(
                    credentialIssuer = issuerUrl,
                    credentialConfigurationIds = listOf("UniversityDegree"),
                    grants =
                        CredentialOfferGrants(
                            preAuthorizedCode =
                                PreAuthorizedCodeOfferGrant(
                                    preAuthorizedCode = "pre-auth-match-test",
                                ),
                        ),
                )

            // Holder resolves metadata (simulated)
            val resolvedMetadata = issuerMetadata

            // Holder matches offer config IDs to metadata configurations
            for (configId in offer.credentialConfigurationIds) {
                val config = resolvedMetadata.credentialConfigurationsSupported[configId]
                assertNotNull(config, "Issuer metadata must contain config for offered '$configId'")
                assertNotNull(config.format, "Credential configuration must specify format")
            }

            // Verify the holder can determine proof requirements
            val targetConfig = resolvedMetadata.credentialConfigurationsSupported.getValue("UniversityDegree")
            val jwtProofType = targetConfig.proofTypesSupported?.get("jwt")
            assertNotNull(jwtProofType, "UniversityDegree must support jwt proof type")
            assertTrue("ES256" in jwtProofType.proofSigningAlgValuesSupported)
        }

    // =========================================================================
    // Step 3: Wire format validation for credential request/response
    // =========================================================================

    @Test
    fun credentialRequestWireFormatMatchesSpec() =
        runTest {
            // OID4VCI Section 9.2 — Credential Request wire format with proofs (plural)
            // Wire format: {"proofs": {"jwt": ["eyJ..."]}}
            val requestJson =
                """
                {
                    "format": "jwt_vc_json",
                    "credential_configuration_id": "UniversityDegree",
                    "proofs": {
                        "jwt": ["eyJhbGciOiJFUzI1NiIsInR5cCI6Im9wZW5pZDR2Y2ktcHJvb2Yrand0Iiwia2lkIjoiZGlkOmtleTp6Nk1raGFYZ0JaRHZvdERrTDVMUEdwZUtHZVBCdDRMa0RjZXNYNkxMcW12aDRSajkifQ.eyJpc3MiOiJkaWQ6a2V5Ono2TWtoYVhnQlpEdm90RGtMNUxQR3BlS0dlUEJ0NExrRGNlc1g2TExlbXZoNFJqOSIsImF1ZCI6Imh0dHBzOi8vaXNzdWVyLmV4YW1wbGUuY29tIiwiaWF0IjoxNjgxOTk4MDAwLCJub25jZSI6InRaV3lzN2VNWXF4bE1PR29MSHlCSXcifQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"]
                    }
                }
                """.trimIndent()

            // Verify the JSON can be parsed
            val parsed =
                json.decodeFromString(
                    com.sphereon.openid.oid4vci.common.model.CredentialRequest
                        .serializer(),
                    requestJson,
                )
            assertEquals("jwt_vc_json", parsed.format)
            assertEquals("UniversityDegree", parsed.credentialConfigurationId)
            assertNotNull(parsed.proofs)
            assertEquals("jwt", parsed.proofs?.proofType)
            assertEquals(1, parsed.proofs?.proofValues?.size)
        }

    @Test
    fun credentialResponseWireFormatMatchesSpec() =
        runTest {
            // OID4VCI Section 7.3 — Credential Response wire format
            val responseJson =
                """
                {
                    "credential": "eyJhbGciOiJFUzI1NiJ9.eyJpc3MiOiJodHRwczovL2lzc3Vlci5leGFtcGxlLmNvbSIsInN1YiI6ImRpZDprZXk6ejZNa2hhWGdCWkR2b3REa0w1TFBHcGVLR2VQQnQ0TGtEY2VzWDZMTGVtdmg0Umo5IiwidmMiOnsiQGNvbnRleHQiOlsiaHR0cHM6Ly93d3cudzMub3JnLzIwMTgvY3JlZGVudGlhbHMvdjEiXSwidHlwZSI6WyJWZXJpZmlhYmxlQ3JlZGVudGlhbCIsIlVuaXZlcnNpdHlEZWdyZWVDcmVkZW50aWFsIl19fQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c",
                    "c_nonce": "tZWys7eMYqxlMOGoLHyBIw",
                    "c_nonce_expires_in": 300
                }
                """.trimIndent()

            val parsed =
                json.decodeFromString(
                    com.sphereon.openid.oid4vci.common.model.CredentialResponse
                        .serializer(),
                    responseJson,
                )
            assertNotNull(parsed.credential)
            assertEquals("tZWys7eMYqxlMOGoLHyBIw", parsed.cNonce)
            assertEquals(300, parsed.cNonceExpiresIn)
            assertNull(parsed.transactionId, "Immediate response has no transaction_id")
        }

    @Test
    fun deferredCredentialResponseWireFormat() =
        runTest {
            // OID4VCI Section 9 — Deferred issuance: transaction_id instead of credential
            val responseJson =
                """
                {
                    "transaction_id": "txn_abc123",
                    "c_nonce": "new-nonce-for-retry",
                    "c_nonce_expires_in": 600
                }
                """.trimIndent()

            val parsed =
                json.decodeFromString(
                    com.sphereon.openid.oid4vci.common.model.CredentialResponse
                        .serializer(),
                    responseJson,
                )
            assertNull(parsed.credential, "Deferred response has no credential")
            assertEquals("txn_abc123", parsed.transactionId)
            assertNotNull(parsed.cNonce)
        }

    // =========================================================================
    // Step 4: Pre-auth code offer → parse → resolve → match (integrated flow)
    // =========================================================================

    @Test
    fun fullPreAuthFlowDataContractValidation() =
        runTest {
            // Step 1: Issuer creates credential offer
            val offer =
                CredentialOffer(
                    credentialIssuer = issuerUrl,
                    credentialConfigurationIds = listOf("UniversityDegree"),
                    grants =
                        CredentialOfferGrants(
                            preAuthorizedCode =
                                PreAuthorizedCodeOfferGrant(
                                    preAuthorizedCode = "pre-auth-code-e2e-test",
                                    authorizationServer = asUrl,
                                ),
                        ),
                )

            // Serialize as if creating the offer URI
            val offerJson = json.encodeToString(CredentialOffer.serializer(), offer)

            // Step 2: Holder parses the offer
            val parsedOffer = json.decodeFromString(CredentialOffer.serializer(), offerJson)
            assertEquals(issuerUrl, parsedOffer.credentialIssuer)
            assertNotNull(parsedOffer.grants?.preAuthorizedCode)

            // Step 3: Holder resolves issuer metadata (simulated)
            val metadataJson = json.encodeToString(CredentialIssuerMetadata.serializer(), issuerMetadata)
            val resolvedMetadata = json.decodeFromString(CredentialIssuerMetadata.serializer(), metadataJson)

            // Step 4: Holder matches offer to metadata
            val requestedConfigId = parsedOffer.credentialConfigurationIds.first()
            val matchedConfig = resolvedMetadata.credentialConfigurationsSupported[requestedConfigId]
            assertNotNull(matchedConfig, "Metadata must contain the offered credential configuration")
            assertEquals("jwt_vc_json", matchedConfig.format)

            // Step 5: Holder identifies the token endpoint from AS metadata
            val preAuthGrant = parsedOffer.grants!!.preAuthorizedCode!!
            val tokenEndpointAs = preAuthGrant.authorizationServer ?: resolvedMetadata.authorizationServers?.firstOrNull()
            assertNotNull(tokenEndpointAs, "Must be able to determine AS for token exchange")

            // Step 6: Verify proof type requirements
            val proofTypes = matchedConfig.proofTypesSupported
            assertNotNull(proofTypes, "Credential configuration should declare proof types")
            assertTrue("jwt" in proofTypes, "jwt proof type must be supported")
            assertTrue("ES256" in proofTypes.getValue("jwt").proofSigningAlgValuesSupported)

            // Step 7: Verify nonce endpoint availability
            assertNotNull(resolvedMetadata.nonceEndpoint, "Nonce endpoint should be available")

            // Step 8: Verify credential endpoint availability
            assertNotNull(resolvedMetadata.credentialEndpoint, "Credential endpoint must be present")

            // Step 9: Verify notification endpoint for post-issuance notification
            assertNotNull(resolvedMetadata.notificationEndpoint, "Notification endpoint should be available")
        }

    // =========================================================================
    // Step 5: Nonce wire format
    // =========================================================================

    @Test
    fun nonceResponseWireFormat() =
        runTest {
            val nonceJson =
                """
                {
                    "c_nonce": "tZWys7eMYqxlMOGoLHyBIw",
                    "c_nonce_expires_in": 300
                }
                """.trimIndent()

            val parsed =
                json.decodeFromString(
                    com.sphereon.openid.oid4vci.common.model.NonceResponse
                        .serializer(),
                    nonceJson,
                )
            assertEquals("tZWys7eMYqxlMOGoLHyBIw", parsed.cNonce)
            assertEquals(300, parsed.cNonceExpiresIn)
        }

    // =========================================================================
    // Step 6: Notification wire format
    // =========================================================================

    @Test
    fun credentialNotificationWireFormat() =
        runTest {
            val notificationJson =
                """
                {
                    "notification_id": "notif-123",
                    "event": "credential_accepted"
                }
                """.trimIndent()

            val parsed =
                json.decodeFromString(
                    com.sphereon.openid.oid4vci.common.model.CredentialNotification
                        .serializer(),
                    notificationJson,
                )
            assertEquals("notif-123", parsed.notificationId)
            assertEquals(
                com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent.CREDENTIAL_ACCEPTED,
                parsed.event,
            )
        }

    @Test
    fun credentialNotificationFailureWireFormat() =
        runTest {
            val notificationJson =
                """
                {
                    "notification_id": "notif-456",
                    "event": "credential_failure",
                    "event_description": "Holder could not verify issuer signature"
                }
                """.trimIndent()

            val parsed =
                json.decodeFromString(
                    com.sphereon.openid.oid4vci.common.model.CredentialNotification
                        .serializer(),
                    notificationJson,
                )
            assertEquals("notif-456", parsed.notificationId)
            assertEquals(
                com.sphereon.openid.oid4vci.common.model.CredentialNotificationEvent.CREDENTIAL_FAILURE,
                parsed.event,
            )
            assertEquals("Holder could not verify issuer signature", parsed.eventDescription)
        }

    // =========================================================================
    // DI graph smoke test (aspirational — requires command interface bindings
    // to be added to the OID4VCI holder/issuer modules via @ContributesBinding)
    // =========================================================================
    // TODO: Enable when OID4VCI modules add @ContributesBinding for command interfaces.
    //       Currently, the command impls are only registered into the command map via
    //       @Provides @IntoMap, but the service impls inject the command interfaces directly.
    //       See TestCommandBindings.kt for the workaround pattern.
}
