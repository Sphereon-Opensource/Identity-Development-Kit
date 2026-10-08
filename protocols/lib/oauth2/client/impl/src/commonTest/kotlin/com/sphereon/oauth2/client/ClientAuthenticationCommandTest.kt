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

package com.sphereon.oauth2.client

import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.oauth2.client.impl.clientauth.ApplyClientAuthenticationCommandImpl
import com.sphereon.oauth2.client.testutil.OAuth2ClientTestContext
import com.sphereon.oauth2.client.testutil.createOAuth2ClientTestAppGraph
import com.sphereon.oauth2.common.command.ApplyClientAuthenticationArgs
import com.sphereon.oauth2.common.model.ClientAssertion
import com.sphereon.oauth2.common.model.ClientAttestation
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientCredentials
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for ApplyClientAuthenticationCommand implementation
 */
class ClientAuthenticationCommandTest {
    val app = createOAuth2ClientTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("client-auth-test", principalType = com.sphereon.di.context.PrincipalType.USER)
    val execution = session.asCoreApiServiceGraph().serviceExecution

    @Test
    fun testBasicAuthentication() =
        runTest {
            val command = ApplyClientAuthenticationCommandImpl(execution)
            val config =
                ClientAuthenticationConfig.Basic(
                    credentials =
                        ClientCredentials(
                            clientId = "test-client-id",
                            clientSecret = "test-client-secret",
                        ),
                )

            val result = command.execute(ApplyClientAuthenticationArgs(config, "https://as.example.com/token"))

            assertTrue(result.isOk, "Should succeed")
            val authResult = result.value

            // Verify Authorization header exists
            assertTrue(authResult.headers.containsKey("Authorization"))
            val authHeader = authResult.headers["Authorization"]!!
            assertTrue(authHeader.startsWith("Basic "))

            // Decode and verify credentials
            val base64Part = authHeader.removePrefix("Basic ")
            val decoded = base64Part.decodeFromBase64().decodeToString()
            assertEquals("test-client-id:test-client-secret", decoded)

            // Verify no body parameters
            assertTrue(authResult.bodyParameters.isEmpty())
        }

    @Test
    fun testBasicAuthenticationFormEncodesCredentialComponents() =
        runTest {
            val command = ApplyClientAuthenticationCommandImpl(execution)
            val config =
                ClientAuthenticationConfig.Basic(
                    ClientCredentials(
                        clientId = "tenant-as-service:tenant-123",
                        clientSecret = "secret:value",
                    ),
                )

            val result = command.execute(ApplyClientAuthenticationArgs(config, "https://as.example.com/token"))

            assertTrue(result.isOk)
            val encoded = result.value.headers.getValue("Authorization").removePrefix("Basic ")
            assertEquals(
                "tenant-as-service%3Atenant-123:secret%3Avalue",
                encoded.decodeFromBase64().decodeToString(),
            )
        }

    @Test
    fun testPostAuthentication() =
        runTest {
            val command = ApplyClientAuthenticationCommandImpl(execution)
            val config =
                ClientAuthenticationConfig.Post(
                    credentials =
                        ClientCredentials(
                            clientId = "test-client-id",
                            clientSecret = "test-client-secret",
                        ),
                )

            val result = command.execute(ApplyClientAuthenticationArgs(config, "https://as.example.com/token"))

            assertTrue(result.isOk, "Should succeed")
            val authResult = result.value

            // Verify no headers
            assertTrue(authResult.headers.isEmpty())

            // Verify body parameters
            assertEquals("test-client-id", authResult.bodyParameters["client_id"])
            assertEquals("test-client-secret", authResult.bodyParameters["client_secret"])
            assertEquals(2, authResult.bodyParameters.size)
        }

    @Test
    fun testSecretJwtAuthentication() =
        runTest {
            val command = ApplyClientAuthenticationCommandImpl(execution)
            val config =
                ClientAuthenticationConfig.SecretJwt(
                    assertion =
                        ClientAssertion(
                            clientId = "test-client-id",
                            assertionType = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                            assertion = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature",
                        ),
                )

            val result = command.execute(ApplyClientAuthenticationArgs(config, "https://as.example.com/token"))

            assertTrue(result.isOk, "Should succeed")
            val authResult = result.value

            // Verify no headers
            assertTrue(authResult.headers.isEmpty())

            // Verify body parameters
            assertEquals(
                "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                authResult.bodyParameters["client_assertion_type"],
            )
            assertEquals(
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature",
                authResult.bodyParameters["client_assertion"],
            )
            assertEquals(2, authResult.bodyParameters.size)
        }

    @Test
    fun testPrivateKeyJwtAuthentication() =
        runTest {
            val command = ApplyClientAuthenticationCommandImpl(execution)
            val config =
                ClientAuthenticationConfig.PrivateKeyJwt(
                    assertion =
                        ClientAssertion(
                            clientId = "test-client-id",
                            assertionType = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                            assertion = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.test.signature",
                        ),
                )

            val result = command.execute(ApplyClientAuthenticationArgs(config, "https://as.example.com/token"))

            assertTrue(result.isOk, "Should succeed")
            val authResult = result.value

            // Verify no headers
            assertTrue(authResult.headers.isEmpty())

            // Verify body parameters
            assertEquals(
                "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                authResult.bodyParameters["client_assertion_type"],
            )
            assertEquals(
                "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.test.signature",
                authResult.bodyParameters["client_assertion"],
            )
            assertEquals(2, authResult.bodyParameters.size)
        }

    @Test
    fun testNoneAuthentication() =
        runTest {
            val command = ApplyClientAuthenticationCommandImpl(execution)
            val config = ClientAuthenticationConfig.None(clientId = "public-client-id")

            val result = command.execute(ApplyClientAuthenticationArgs(config, "https://as.example.com/token"))

            assertTrue(result.isOk, "Should succeed")
            val authResult = result.value

            // Verify no headers
            assertTrue(authResult.headers.isEmpty())

            // Verify only client_id in body
            assertEquals("public-client-id", authResult.bodyParameters["client_id"])
            assertEquals(1, authResult.bodyParameters.size)
        }

    @Test
    fun testAttestationJwtAuthentication() =
        runTest {
            val command = ApplyClientAuthenticationCommandImpl(execution)
            val config =
                ClientAuthenticationConfig.AttestationJwt(
                    attestation =
                        ClientAttestation(
                            clientAttestationJwt = "attestation.jwt.here",
                            clientAttestationPopJwt = "pop.jwt.here",
                        ),
                )

            val result = command.execute(ApplyClientAuthenticationArgs(config, "https://as.example.com/token"))

            assertTrue(result.isOk, "Should succeed")
            val authResult = result.value

            // Verify headers
            assertEquals("attestation.jwt.here", authResult.headers["OAuth-Client-Attestation"])
            assertEquals("pop.jwt.here", authResult.headers["OAuth-Client-Attestation-PoP"])
            assertEquals(2, authResult.headers.size)

            // Verify no body parameters
            assertTrue(authResult.bodyParameters.isEmpty())
        }

    @Test
    fun testAnonymousAuthentication() =
        runTest {
            val command = ApplyClientAuthenticationCommandImpl(execution)
            val config = ClientAuthenticationConfig.Anonymous

            val result = command.execute(ApplyClientAuthenticationArgs(config, "https://as.example.com/token"))

            assertTrue(result.isOk, "Should succeed")
            val authResult = result.value

            // Verify no headers
            assertTrue(authResult.headers.isEmpty())

            // Verify no body parameters
            assertTrue(authResult.bodyParameters.isEmpty())
        }

    @Test
    fun testBasicAuthWithSpecialCharacters() =
        runTest {
            val command = ApplyClientAuthenticationCommandImpl(execution)
            val config =
                ClientAuthenticationConfig.Basic(
                    credentials =
                        ClientCredentials(
                            clientId = "client@example.com",
                            clientSecret = "p@ssw0rd:with:colons",
                        ),
                )

            val result = command.execute(ApplyClientAuthenticationArgs(config, "https://as.example.com/token"))

            assertTrue(result.isOk, "Should succeed")
            val authResult = result.value

            val authHeader = authResult.headers["Authorization"]!!
            val base64Part = authHeader.removePrefix("Basic ")
            val decoded = base64Part.decodeFromBase64().decodeToString()
            assertEquals("client%40example.com:p%40ssw0rd%3Awith%3Acolons", decoded)
        }
}
