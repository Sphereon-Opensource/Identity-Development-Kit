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

package com.sphereon.oauth2.server.authorization.impl.command.token

import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthorization
import com.sphereon.oauth2.server.authorization.command.VerifyClientCredentialsGrantArgs
import com.sphereon.oauth2.server.authorization.impl.TestFixtures
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryClientRegistryImpl
import com.sphereon.oauth2.server.authorization.impl.storage.memory.InMemoryOAuth2BackingStorageImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerifyClientCredentialsGrantCommandImplTest {
    private val ctx = OAuth2ServerTestContext("verify-client-credentials-test", this)
    private val execution = ctx.execution

    private suspend fun registered(client: ClientRegistration): VerifyClientCredentialsGrantCommandImpl {
        val clientRegistry = InMemoryClientRegistryImpl(InMemoryOAuth2BackingStorageImpl())
        assertTrue(clientRegistry.registerClient(client).isOk)
        return VerifyClientCredentialsGrantCommandImpl(execution, clientRegistry)
    }

    private val workloadClient =
        TestFixtures.confidentialClient.copy(
            clientId = "tenant-workload",
            defaultAccessTokenAudience = "enterprise-platform",
            allowedAccessTokenAudiences = setOf("enterprise-tenant-kms", "enterprise-wallet-interaction"),
            principalRoles = listOf("tenant-admin"),
            additionalMetadata = mapOf("tenant_id" to "tenant-acme"),
        )

    @Test
    fun registeredPrincipalRolesAreMintedAsTheRolesClaim() =
        runTest {
            val result =
                registered(workloadClient).execute(
                    VerifyClientCredentialsGrantArgs(clientId = workloadClient.clientId, requestedScope = "read"),
                )

            assertTrue(result.isOk)
            assertEquals("tenant-acme", result.value.additionalClaims["tenant_id"])
            assertEquals(listOf("tenant-admin"), result.value.additionalClaims["roles"])
        }

    @Test
    fun rolesClaimIsAbsentWhenNoPrincipalRolesAreRegistered() =
        runTest {
            val client = workloadClient.copy(principalRoles = emptyList())

            val result =
                registered(client).execute(
                    VerifyClientCredentialsGrantArgs(clientId = client.clientId, requestedScope = "read"),
                )

            assertTrue(result.isOk)
            assertFalse(result.value.additionalClaims.containsKey("roles"))
            assertEquals(mapOf("tenant_id" to "tenant-acme"), result.value.additionalClaims)
        }

    @Test
    fun blankPrincipalRolesDoNotProduceARolesClaim() =
        runTest {
            val client = workloadClient.copy(principalRoles = listOf(" ", ""))

            val result =
                registered(client).execute(
                    VerifyClientCredentialsGrantArgs(clientId = client.clientId, requestedScope = "read"),
                )

            assertTrue(result.isOk)
            assertFalse(result.value.additionalClaims.containsKey("roles"))
        }

    @Test
    fun trustedClientAuthorizationCarriesPrincipalRolesWithoutARegistryReread() =
        runTest {
            val command = VerifyClientCredentialsGrantCommandImpl(execution, InMemoryClientRegistryImpl(InMemoryOAuth2BackingStorageImpl()))

            val result =
                command.verifyWithTrustedClientAuthorization(
                    VerifyClientCredentialsGrantArgs(clientId = workloadClient.clientId),
                    VerifiedClientAuthorization(
                        clientId = workloadClient.clientId,
                        grantTypes = workloadClient.grantTypes,
                        defaultAccessTokenAudience = workloadClient.defaultAccessTokenAudience,
                        principalRoles = listOf("platform-admin"),
                        tenantId = "platform",
                    ),
                )

            assertTrue(result.isOk)
            assertEquals(listOf("platform-admin"), result.value.additionalClaims["roles"])
            assertEquals("platform", result.value.additionalClaims["tenant_id"])
        }

    @Test
    fun severalRegisteredAudiencesAreGrantedTogether() =
        runTest {
            val result =
                registered(workloadClient).execute(
                    VerifyClientCredentialsGrantArgs(
                        clientId = workloadClient.clientId,
                        requestedAudience = listOf("enterprise-platform", "enterprise-tenant-kms", "enterprise-wallet-interaction"),
                    ),
                )

            assertTrue(result.isOk)
            assertEquals(
                listOf("enterprise-platform", "enterprise-tenant-kms", "enterprise-wallet-interaction"),
                result.value.audience,
            )
        }

    @Test
    fun repeatedAudiencesCollapseToOneEntry() =
        runTest {
            val result =
                registered(workloadClient).execute(
                    VerifyClientCredentialsGrantArgs(
                        clientId = workloadClient.clientId,
                        requestedAudience = listOf("enterprise-tenant-kms", " enterprise-tenant-kms "),
                    ),
                )

            assertTrue(result.isOk)
            assertEquals(listOf("enterprise-tenant-kms"), result.value.audience)
        }

    @Test
    fun anyUnregisteredAudienceRejectsTheWholeRequest() =
        runTest {
            val command = registered(workloadClient)

            val single =
                command.execute(
                    VerifyClientCredentialsGrantArgs(
                        clientId = workloadClient.clientId,
                        requestedAudience = listOf("enterprise-wallet-unit"),
                    ),
                )
            val mixed =
                command.execute(
                    VerifyClientCredentialsGrantArgs(
                        clientId = workloadClient.clientId,
                        requestedAudience = listOf("enterprise-tenant-kms", "enterprise-wallet-unit"),
                    ),
                )

            assertTrue(single.isErr)
            assertEquals("invalid_target", single.error.code)
            assertTrue(mixed.isErr)
            assertEquals("invalid_target", mixed.error.code)
        }

    @Test
    fun omittedAudienceFallsBackToTheRegisteredDefault() =
        runTest {
            val result =
                registered(workloadClient).execute(
                    VerifyClientCredentialsGrantArgs(clientId = workloadClient.clientId),
                )

            assertTrue(result.isOk)
            assertEquals(listOf("enterprise-platform"), result.value.audience)
        }
}
