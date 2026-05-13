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

import com.sphereon.attribute.mapping.AttributeMapping
import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.reconciliation.impl.testutil.ReconciliationTestContext
import com.sphereon.identity.reconciliation.model.CompleteReconciliationArgs
import com.sphereon.identity.reconciliation.model.CreateReconciliationSessionArgs
import com.sphereon.identity.reconciliation.model.ReconciliationProvider
import com.sphereon.identity.reconciliation.model.ReconciliationSessionStatus
import com.sphereon.identity.reconciliation.model.ResolvedIdentity
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CompleteReconciliationCommandTest {
    private val ctx = ReconciliationTestContext("complete-recon-test", this)
    private val json = Json { ignoreUnknownKeys = true }

    private val testProvider =
        ReconciliationProvider(
            id = "test-provider",
            name = "Test OIDC Provider",
            oidcClientId = "test-oidc-client",
            identifierAttributeName = "sub",
        )

    private fun decryptIdentity(session: com.sphereon.identity.reconciliation.model.ReconciliationSession): ResolvedIdentity? {
        val encrypted = session.encryptedIdentity ?: return null
        // Test crypto service passes plaintext through as ciphertext
        return json.decodeFromString(ResolvedIdentity.serializer(), encrypted.ciphertext)
    }

    @Test
    fun completeReconciliationHappyPath() =
        runTest {
            ctx.providerStore.save(testProvider)

            val createResult =
                ctx.createSessionCommand.execute(
                    CreateReconciliationSessionArgs(
                        identifierHash = "sha256:happy-path",
                        identifierType = IdentifierType.EMAIL,
                        providerId = "test-provider",
                        tenantId = "tenant-1",
                        redirectUri = "https://app.example.com/callback",
                    ),
                )
            assertTrue(createResult.isOk, "Create should succeed: ${createResult.errorOrNull()}")
            val session = createResult.value.session

            val result =
                ctx.completeCommand.execute(
                    CompleteReconciliationArgs(
                        sessionId = session.id,
                        tenantId = "tenant-1",
                        authorizationCode = "auth-code-from-idp",
                        state = session.state!!,
                        internalIdentityId = "internal-user-42",
                    ),
                )

            assertTrue(result.isOk, "Complete should succeed: ${result.errorOrNull()}")

            val completedSession = result.value.session
            assertEquals(ReconciliationSessionStatus.COMPLETED, completedSession.status)

            assertNotNull(completedSession.encryptedIdentity, "Should have encrypted identity")
            val resolvedIdentity = decryptIdentity(completedSession)
            assertNotNull(resolvedIdentity, "Should be able to decrypt identity")
            assertEquals("external-user-123", resolvedIdentity.externalSubject)
            assertEquals("https://idp.example.com", resolvedIdentity.externalIssuer)
            assertEquals("internal-user-42", resolvedIdentity.internalIdentityId)

            val match = result.value.match
            assertEquals("sha256:happy-path", match.identifierHash)
            assertEquals(IdentifierType.EMAIL, match.identifierType)
            assertEquals("internal-user-42", match.internalIdentityId)
            assertEquals("tenant-1", match.tenantId)
        }

    @Test
    fun completeReconciliationWithCustomIdentifierClaim() =
        runTest {
            val customProvider =
                testProvider.copy(
                    id = "custom-claim-provider",
                    identifierAttributeName = "email",
                )
            ctx.providerStore.save(customProvider)

            val createResult =
                ctx.createSessionCommand.execute(
                    CreateReconciliationSessionArgs(
                        identifierHash = "sha256:custom-claim",
                        identifierType = IdentifierType.EMAIL,
                        providerId = "custom-claim-provider",
                        tenantId = "tenant-1",
                        redirectUri = "https://app.example.com/callback",
                    ),
                )
            assertTrue(createResult.isOk)
            val session = createResult.value.session

            val result =
                ctx.completeCommand.execute(
                    CompleteReconciliationArgs(
                        sessionId = session.id,
                        tenantId = "tenant-1",
                        authorizationCode = "auth-code",
                        state = session.state!!,
                        internalIdentityId = "internal-user-99",
                    ),
                )

            assertTrue(result.isOk, "Should succeed with custom identifier claim: ${result.errorOrNull()}")
            val resolvedIdentity = decryptIdentity(result.value.session)
            assertNotNull(resolvedIdentity)
            assertEquals("user@example.com", resolvedIdentity.externalSubject)
        }

    @Test
    fun completeReconciliationWithClaimMappings() =
        runTest {
            val mappedProvider =
                testProvider.copy(
                    id = "mapped-provider",
                    attributeMappings =
                        listOf(
                            AttributeMapping(source = "email", target = "email_address"),
                            AttributeMapping(source = "name", target = "display_name"),
                        ),
                )
            ctx.providerStore.save(mappedProvider)

            val createResult =
                ctx.createSessionCommand.execute(
                    CreateReconciliationSessionArgs(
                        identifierHash = "sha256:claim-map",
                        identifierType = IdentifierType.EMAIL,
                        providerId = "mapped-provider",
                        tenantId = "tenant-1",
                        redirectUri = "https://app.example.com/callback",
                    ),
                )
            assertTrue(createResult.isOk)
            val session = createResult.value.session

            val result =
                ctx.completeCommand.execute(
                    CompleteReconciliationArgs(
                        sessionId = session.id,
                        tenantId = "tenant-1",
                        authorizationCode = "auth-code",
                        state = session.state!!,
                        internalIdentityId = "internal-user-88",
                    ),
                )

            assertTrue(result.isOk, "Complete should succeed: ${result.errorOrNull()}")
            val resolvedIdentity = decryptIdentity(result.value.session)
            assertNotNull(resolvedIdentity)

            // Mapped claims present
            assertNotNull(resolvedIdentity.claims["email_address"])
            assertNotNull(resolvedIdentity.claims["display_name"])
            // Original claims also preserved (additive)
            assertNotNull(resolvedIdentity.claims["email"])
        }

    @Test
    fun completeReconciliationRejectsCompletedSession() =
        runTest {
            ctx.providerStore.save(testProvider)

            val createResult =
                ctx.createSessionCommand.execute(
                    CreateReconciliationSessionArgs(
                        identifierHash = "sha256:token-fail",
                        identifierType = IdentifierType.EMAIL,
                        providerId = "test-provider",
                        tenantId = "tenant-1",
                        redirectUri = "https://app.example.com/callback",
                    ),
                )
            assertTrue(createResult.isOk)
            val session = createResult.value.session

            // Mark as completed so complete rejects it
            ctx.sessionStore.update(session.copy(status = ReconciliationSessionStatus.COMPLETED))

            val result =
                ctx.completeCommand.execute(
                    CompleteReconciliationArgs(
                        sessionId = session.id,
                        tenantId = "tenant-1",
                        authorizationCode = "bad-code",
                        state = session.state!!,
                        internalIdentityId = "internal-user-1",
                    ),
                )

            assertTrue(result.isErr, "Should fail with invalid state for completed session")
        }
}
