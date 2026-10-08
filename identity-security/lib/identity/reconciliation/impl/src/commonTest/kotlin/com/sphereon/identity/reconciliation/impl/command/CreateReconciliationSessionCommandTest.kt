/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.identity.reconciliation.impl.command

import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.reconciliation.impl.testutil.ReconciliationTestContext
import com.sphereon.identity.reconciliation.model.CreateReconciliationSessionArgs
import com.sphereon.identity.reconciliation.model.ReconciliationProvider
import com.sphereon.identity.reconciliation.model.ReconciliationSessionStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CreateReconciliationSessionCommandTest {
    private val ctx = ReconciliationTestContext("create-session-test", this)

    private val testProvider =
        ReconciliationProvider(
            id = "test-provider",
            name = "Test OIDC Provider",
            oidcClientId = "test-oidc-client",
            identifierAttributeName = "sub",
        )

    @Test
    fun createSessionSuccessfully() =
        runTest {
            ctx.providerStore.save(testProvider)

            val result =
                ctx.createSessionCommand.execute(
                    CreateReconciliationSessionArgs(
                        identifierHash = "sha256:abc123",
                        identifierType = IdentifierType.EMAIL,
                        providerId = "test-provider",
                        tenantId = "tenant-1",
                        redirectUri = "https://app.example.com/callback",
                    ),
                )

            assertTrue(result.isOk, "Should succeed: ${result.errorOrNull()}")
            val session = result.value.session
            assertEquals(ReconciliationSessionStatus.CREATED, session.status)
            assertEquals("test-provider", session.providerId)
            assertEquals("tenant-1", session.tenantId)
            assertEquals("sha256:abc123", session.identifierHash)
            assertEquals("https://app.example.com/callback", session.redirectUri)
            assertNotNull(session.codeVerifier, "Should have PKCE code verifier")
            assertTrue(session.codeVerifier!!.length > 40, "Code verifier should be proper length (RFC 7636)")
            assertNotNull(session.state)
            assertNotNull(session.nonce)
            assertNotNull(session.tokenEndpoint, "Should have cached token endpoint from OIDC discovery")

            val authUrl = result.value.authorizationUrl
            assertTrue(authUrl.startsWith("https://idp.example.com/authorize?"), "Auth URL should start with discovered endpoint")
            assertTrue(authUrl.contains("code_challenge="), "Auth URL should contain code_challenge")
            assertTrue(authUrl.contains("code_challenge_method=S256"), "Auth URL should contain code_challenge_method=S256")
            assertTrue(authUrl.contains("client_id=test-client-id"))
            assertTrue(
                authUrl.contains("redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback"),
                "Auth URL should contain URL-encoded redirect_uri, got: $authUrl",
            )
            assertTrue(authUrl.contains("scope=openid+profile+email"))
        }

    @Test
    fun createSessionFailsWithUnknownProvider() =
        runTest {
            val result =
                ctx.createSessionCommand.execute(
                    CreateReconciliationSessionArgs(
                        identifierHash = "sha256:abc123",
                        identifierType = IdentifierType.EMAIL,
                        providerId = "nonexistent-provider",
                        tenantId = "tenant-1",
                        redirectUri = "https://app.example.com/callback",
                    ),
                )

            assertTrue(result.isErr, "Should fail with unknown provider")
            assertTrue(
                result.error.message.defaultMessage
                    ?.contains("not found") == true,
                "Error should mention provider not found",
            )
        }

    @Test
    fun createSessionWithDifferentProvider() =
        runTest {
            val otherProvider =
                testProvider.copy(
                    id = "other-provider",
                    name = "Other OIDC Provider",
                )
            ctx.providerStore.save(otherProvider)

            val result =
                ctx.createSessionCommand.execute(
                    CreateReconciliationSessionArgs(
                        identifierHash = "sha256:other-test",
                        identifierType = IdentifierType.EMAIL,
                        providerId = "other-provider",
                        tenantId = "tenant-1",
                        redirectUri = "https://app.example.com/callback",
                    ),
                )

            assertTrue(result.isOk, "Should succeed with different provider: ${result.errorOrNull()}")
            val session = result.value.session
            assertEquals("other-provider", session.providerId)
        }

    @Test
    fun createSessionPkceVerifierIsRfc7636Compliant() =
        runTest {
            ctx.providerStore.save(testProvider)

            val result =
                ctx.createSessionCommand.execute(
                    CreateReconciliationSessionArgs(
                        identifierHash = "sha256:pkce-test",
                        identifierType = IdentifierType.EMAIL,
                        providerId = "test-provider",
                        tenantId = "tenant-1",
                        redirectUri = "https://app.example.com/callback",
                    ),
                )

            assertTrue(result.isOk)
            val verifier = result.value.session.codeVerifier
            assertNotNull(verifier)
            // RFC 7636: code verifier must be 43-128 characters
            assertTrue(verifier.length in 43..128, "Code verifier length must be 43-128, was ${verifier.length}")
            // Should NOT be a UUID (was the old bug)
            assertTrue(
                !verifier.matches(Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")),
                "Code verifier should not be a UUID",
            )
        }
}
