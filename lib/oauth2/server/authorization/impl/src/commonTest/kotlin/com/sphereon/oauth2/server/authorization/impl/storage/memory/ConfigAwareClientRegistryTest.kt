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

package com.sphereon.oauth2.server.authorization.impl.storage.memory

import com.sphereon.oauth2.common.config.InternalClientConfig
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.impl.config.OAuth2ClientsConfigBinder
import com.sphereon.oauth2.server.authorization.impl.config.TestSessionExecution
import com.sphereon.oauth2.server.authorization.impl.config.TypeAwarePrincipalConfigService
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ConfigAwareClientRegistryTest {
    @Test
    fun configuredClientAudiencePolicyIsPropagatedToClientRegistration() {
        val binder =
            OAuth2ClientsConfigBinder(
                TestSessionExecution(
                    TypeAwarePrincipalConfigService(
                        mapOf(
                            "oauth2.clients.issuer.client-id" to "issuer-service",
                            "oauth2.clients.issuer.client-secret" to "issuer-secret",
                            "oauth2.clients.issuer.grant-types" to "client_credentials",
                            "oauth2.clients.issuer.default-access-token-audience" to "enterprise-platform",
                            "oauth2.clients.issuer.allowed-access-token-audiences" to
                                "enterprise-tenant-kms,enterprise-wallet-interaction",
                        ),
                        normalizeKeys = true,
                    ),
                ),
            )

        val result = binder.loadClientRegistrations()

        assertTrue(result.isOk)
        val registration = assertNotNull(result.value["issuer-service"])
        assertEquals("enterprise-platform", registration.defaultAccessTokenAudience)
        assertEquals(
            setOf("enterprise-tenant-kms", "enterprise-wallet-interaction"),
            registration.allowedAccessTokenAudiences,
        )
    }

    @Test
    fun internalClientDefaultAudienceIsPropagatedToClientRegistration() =
        runTest {
            val serverId = "platform"
            val config =
                OAuth2ServersConfig(
                    defaultServer = serverId,
                    servers =
                        mapOf(
                            serverId to
                                OAuth2ServerInstanceConfig(
                                    internalClients =
                                        mapOf(
                                            "tenantas" to
                                                InternalClientConfig(
                                                    clientId = "tenant-as-service",
                                                    clientSecret = "tenant-as-secret",
                                                    defaultAccessTokenAudience = "enterprise-platform",
                                                    allowedAccessTokenAudiences = setOf("enterprise-tenant-kms"),
                                                ),
                                        ),
                                ),
                        ),
                )
            val registry =
                ConfigAwareClientRegistry(
                    backingStorage = InMemoryOAuth2BackingStorageImpl(),
                    configBinder =
                        OAuth2ClientsConfigBinder(
                            TestSessionExecution(TypeAwarePrincipalConfigService(emptyMap())),
                        ),
                    asInstanceIdProvider =
                        object : OAuth2ServerInstanceIdProvider {
                            override fun currentAsInstanceId(): String = serverId
                        },
                    serversConfigProvider = TestOAuth2ServersConfigProvider(config),
                )

            val result = registry.getClient("tenant-as-service")

            assertTrue(result.isOk)
            val registration = assertNotNull(result.value)
            assertEquals("tenant-as-secret", registration.clientSecret)
            assertEquals(listOf(GrantType.CLIENT_CREDENTIALS), registration.grantTypes)
            assertEquals(ClientAuthenticationMethod.CLIENT_SECRET_BASIC, registration.tokenEndpointAuthMethod)
            assertEquals("enterprise-platform", registration.defaultAccessTokenAudience)
            assertEquals(setOf("enterprise-tenant-kms"), registration.allowedAccessTokenAudiences)
        }
}
