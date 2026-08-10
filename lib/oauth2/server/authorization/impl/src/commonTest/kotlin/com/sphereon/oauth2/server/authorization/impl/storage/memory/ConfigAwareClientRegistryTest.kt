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

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.OpaqueSecretResolver
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.common.config.InternalClientConfig
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.impl.config.OAuth2ClientsConfigBinder
import com.sphereon.oauth2.server.authorization.impl.config.OAuth2ServersConfigBinder
import com.sphereon.oauth2.server.authorization.impl.config.TestSessionExecution
import com.sphereon.oauth2.server.authorization.impl.config.TypeAwarePrincipalConfigService
import com.sphereon.oauth2.server.authorization.impl.testutil.TestOAuth2ServersConfigProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfigAwareClientRegistryTest {
    @Test
    fun requestViewAssemblesConfigurationOnceAndPreservesCredentialFailureSemantics() =
        runTest {
            var configReadCount = 0
            val configService =
                TypeAwarePrincipalConfigService(
                    properties =
                        mapOf(
                            "oauth2.servers.default-server" to "platform",
                            "oauth2.servers.platform.mode" to "HOSTED",
                            "oauth2.clients.operator.client-id" to "operator-client",
                            "oauth2.clients.operator.client-secret-id" to "sec_operator_client_0001",
                            "oauth2.clients.operator.grant-types" to "client_credentials",
                        ),
                    subPropertiesOverride = { prefixes, stripPrefix, properties ->
                        configReadCount++
                        val prefix = prefixes.single().replace("-", ".")
                        properties
                            .filterKeys { key -> key == prefix || key.startsWith("$prefix.") }
                            .mapKeys { (key, _) ->
                                if (stripPrefix) key.removePrefix("$prefix.") else key
                            }
                    },
                    normalizeKeys = true,
                )
            val execution = TestSessionExecution(configService)
            val secrets = resolvingOpaqueSecrets(mapOf("sec_operator_client_0001" to "operator-secret"))
            val registry =
                ConfigAwareClientRegistry(
                    execution = execution,
                    backingStorage = InMemoryOAuth2BackingStorageImpl(),
                    configBinder = OAuth2ClientsConfigBinder(execution, secrets),
                    asInstanceIdProvider =
                        object : OAuth2ServerInstanceIdProvider {
                            override fun currentAsInstanceId(): String = "platform"
                        },
                    serversConfigProvider =
                        TestOAuth2ServersConfigProvider(
                            OAuth2ServersConfig(
                                defaultServer = "platform",
                                servers = mapOf("platform" to OAuth2ServerInstanceConfig()),
                            ),
                        ),
                    opaqueInternalClientSecretVerifier = DefaultOpaqueInternalClientSecretVerifier(secrets),
                )

            val viewResult = registry.resolveClientRegistryRequestView()

            assertTrue(viewResult.isOk, viewResult.errorOrNull()?.details)
            val view = viewResult.value
            assertNotNull(view.getClient("operator-client").value)
            assertTrue(view.verifyClientCredentials("operator-client", "operator-secret").value)
            assertFalse(view.verifyClientCredentials("operator-client", "wrong-secret").value)
            assertEquals(3, configReadCount)
        }

    @Test
    fun scopedTenantAsClientAppearsAfterInitialMissAndOpaqueProvisioningWithoutRegistryRestart() =
        runTest {
            val tenantId = "9f90ec38-8fbf-41f5-9c67-c45a00845a92"
            val role = "tenant-as-$tenantId"
            val prefix = "oauth2.servers.platform.internal-clients.$role"
            val secretId = "sec_tenant_as_dynamic_0001"
            val configService =
                TypeAwarePrincipalConfigService(
                    mapOf(
                        "oauth2.servers.default-server" to "platform",
                        "oauth2.servers.platform.mode" to "HOSTED",
                    ),
                    normalizeKeys = true,
                )
            val execution = TestSessionExecution(configService)
            val serversConfigProvider = OAuth2ServersConfigBinder(execution)
            val registry =
                ConfigAwareClientRegistry(
                    execution = execution,
                    backingStorage = InMemoryOAuth2BackingStorageImpl(),
                    configBinder =
                        OAuth2ClientsConfigBinder(
                            execution,
                            resolvingOpaqueSecrets(mapOf(secretId to "shared-secret")),
                        ),
                    asInstanceIdProvider =
                        object : OAuth2ServerInstanceIdProvider {
                            override fun currentAsInstanceId(): String = "platform"
                        },
                    serversConfigProvider = serversConfigProvider,
                    opaqueInternalClientSecretVerifier =
                        DefaultOpaqueInternalClientSecretVerifier(
                            resolvingOpaqueSecrets(mapOf(secretId to "shared-secret")),
                        ),
                )

            val clientId = "tenant-as-service:$tenantId"
            assertEquals(null, registry.getClient(clientId).value)

            configService.putProperty("$prefix.client-id", clientId)
            configService.putProperty("$prefix.client-secret-id", secretId)
            configService.putProperty(
                "$prefix.grant-types",
                "client_credentials,urn:ietf:params:oauth:grant-type:token-exchange",
            )
            configService.putProperty("$prefix.tenant-id", tenantId)
            configService.putProperty("$prefix.default-access-token-audience", "enterprise-platform")
            configService.putProperty("$prefix.allowed-access-token-audiences", "enterprise-tenant-kms")

            assertTrue(registry.verifyClientCredentials(clientId, "shared-secret").value)
            assertFalse(registry.verifyClientCredentials(clientId, "wrong-secret").value)
            val registration = assertNotNull(registry.getClient(clientId).value)
            assertEquals(listOf(GrantType.CLIENT_CREDENTIALS, GrantType.TOKEN_EXCHANGE), registration.grantTypes)
            assertEquals(tenantId, registration.additionalMetadata["tenant_id"])
        }

    @Test
    fun repeatedLookupsBindConfigurationOnceUntilConfigurationChanges() =
        runTest {
            var configReadCount = 0
            val configService =
                TypeAwarePrincipalConfigService(
                    properties =
                        mapOf(
                            "oauth2.clients.operator.client-id" to "operator-client",
                            "oauth2.clients.operator.client-secret-id" to "sec_operator_client_0001",
                            "oauth2.clients.operator.grant-types" to "client_credentials",
                        ),
                    subPropertiesOverride = { prefixes, stripPrefix, properties ->
                        configReadCount++
                        val prefix = prefixes.single().replace("-", ".")
                        properties
                            .filterKeys { key -> key == prefix || key.startsWith("$prefix.") }
                            .mapKeys { (key, _) ->
                                if (stripPrefix) key.removePrefix("$prefix.") else key
                            }
                    },
                    normalizeKeys = true,
                )
            val execution = TestSessionExecution(configService)
            val secrets =
                resolvingOpaqueSecrets(
                    mapOf(
                        "sec_operator_client_0001" to "operator-secret",
                        "sec_operator_client_0002" to "rotated-secret",
                    ),
                )
            val registry =
                ConfigAwareClientRegistry(
                    execution = execution,
                    backingStorage = InMemoryOAuth2BackingStorageImpl(),
                    configBinder = OAuth2ClientsConfigBinder(execution, secrets),
                    asInstanceIdProvider =
                        object : OAuth2ServerInstanceIdProvider {
                            override fun currentAsInstanceId(): String? = null
                        },
                    serversConfigProvider = TestOAuth2ServersConfigProvider(OAuth2ServersConfig()),
                    opaqueInternalClientSecretVerifier = DefaultOpaqueInternalClientSecretVerifier(secrets),
                )

            assertNotNull(registry.getClient("operator-client").value)
            val readsAfterFirstLookup = configReadCount

            repeat(20) {
                assertNotNull(registry.getClient("operator-client").value)
                assertTrue(registry.verifyClientCredentials("operator-client", "operator-secret").value)
            }
            assertEquals(readsAfterFirstLookup, configReadCount)

            configService.putProperty("oauth2.clients.operator.client-secret-id", "sec_operator_client_0002")

            assertTrue(registry.verifyClientCredentials("operator-client", "rotated-secret").value)
            assertFalse(registry.verifyClientCredentials("operator-client", "operator-secret").value)
            assertTrue(configReadCount > readsAfterFirstLookup)
        }

    @Test
    fun freshRequestMemoizerObservesClientPublishedBeforeRevisionNotification() =
        runTest {
            val configService = TypeAwarePrincipalConfigService(emptyMap(), normalizeKeys = true)
            val secrets = resolvingOpaqueSecrets(mapOf("sec_docs_playground_0001" to "unused-public-client-secret"))

            fun registry(): ConfigAwareClientRegistry {
                val execution = TestSessionExecution(configService)
                return ConfigAwareClientRegistry(
                    execution = execution,
                    backingStorage = InMemoryOAuth2BackingStorageImpl(),
                    configBinder = OAuth2ClientsConfigBinder(execution, secrets),
                    asInstanceIdProvider =
                        object : OAuth2ServerInstanceIdProvider {
                            override fun currentAsInstanceId(): String? = null
                        },
                    serversConfigProvider = TestOAuth2ServersConfigProvider(OAuth2ServersConfig()),
                    opaqueInternalClientSecretVerifier = DefaultOpaqueInternalClientSecretVerifier(secrets),
                    // A new inbound request receives a new SessionScope memoizer. This models a
                    // just-provisioned docs client whose invalidation event has not yet advanced
                    // the old request's property-source revision.
                    configuredClientSetMemoizer = ConfiguredClientSetMemoizer(),
                )
            }

            assertEquals(null, registry().getClient("docs-playground").value)

            configService.putPropertyWithoutRevision("oauth2.clients.docs-playground.client-id", "docs-playground")
            configService.putPropertyWithoutRevision("oauth2.clients.docs-playground.client-type", "PUBLIC")
            configService.putPropertyWithoutRevision(
                "oauth2.clients.docs-playground.grant-types",
                "authorization_code,refresh_token",
            )

            val registration = assertNotNull(registry().getClient("docs-playground").value)
            assertEquals("docs-playground", registration.clientId)
            assertEquals(ClientAuthenticationMethod.NONE, registration.tokenEndpointAuthMethod)
        }

    @Test
    fun repeatRequestsShareConfiguredClientSetUntilPrincipalConfigurationChanges() =
        runTest {
            var configReadCount = 0
            val configService =
                TypeAwarePrincipalConfigService(
                    properties =
                        mapOf(
                            "oauth2.clients.operator.client-id" to "operator-client",
                            "oauth2.clients.operator.client-secret-id" to "sec_operator_client_0001",
                            "oauth2.clients.operator.grant-types" to "client_credentials",
                        ),
                    subPropertiesOverride = { prefixes, stripPrefix, properties ->
                        configReadCount++
                        val prefix = prefixes.single().replace("-", ".")
                        properties
                            .filterKeys { key -> key == prefix || key.startsWith("$prefix.") }
                            .mapKeys { (key, _) ->
                                if (stripPrefix) key.removePrefix("$prefix.") else key
                            }
                    },
                    normalizeKeys = true,
                )
            val secrets = resolvingOpaqueSecrets(mapOf("sec_operator_client_0001" to "operator-secret"))
            val memoizer = ConfiguredClientSetMemoizer()
            fun registry(): ConfigAwareClientRegistry {
                val execution = TestSessionExecution(configService)
                return ConfigAwareClientRegistry(
                    execution = execution,
                    backingStorage = InMemoryOAuth2BackingStorageImpl(),
                    configBinder = OAuth2ClientsConfigBinder(execution, secrets),
                    asInstanceIdProvider =
                        object : OAuth2ServerInstanceIdProvider {
                            override fun currentAsInstanceId(): String? = null
                        },
                    serversConfigProvider = TestOAuth2ServersConfigProvider(OAuth2ServersConfig()),
                    opaqueInternalClientSecretVerifier = DefaultOpaqueInternalClientSecretVerifier(secrets),
                    configuredClientSetMemoizer = memoizer,
                )
            }

            assertNotNull(registry().getClient("operator-client").value)
            val readsAfterFirstRequest = configReadCount

            assertNotNull(registry().getClient("operator-client").value)
            assertEquals(readsAfterFirstRequest, configReadCount)

            configService.putProperty("oauth2.clients.operator.client-secret-id", "sec_operator_client_0001")
            assertNotNull(registry().getClient("operator-client").value)
            assertTrue(configReadCount > readsAfterFirstRequest)
        }

    @Test
    fun opaqueInternalClientRejectsLegacyProviderReference() =
        runTest {
            val prefix = "oauth2.servers.platform.internal-clients.tenant-as-tenant-123"
            val execution =
                TestSessionExecution(
                    TypeAwarePrincipalConfigService(
                        mapOf(
                            "oauth2.servers.default-server" to "platform",
                            "oauth2.servers.platform.mode" to "HOSTED",
                            "$prefix.client-id" to "tenant-as-service:tenant-123",
                            "$prefix.client-secret-id" to "\${secret:tenant-as/client}",
                        ),
                        normalizeKeys = true,
                    ),
                )
            val registry =
                ConfigAwareClientRegistry(
                    execution = execution,
                    backingStorage = InMemoryOAuth2BackingStorageImpl(),
                    configBinder = OAuth2ClientsConfigBinder(execution, rejectingOpaqueSecrets),
                    asInstanceIdProvider =
                        object : OAuth2ServerInstanceIdProvider {
                            override fun currentAsInstanceId(): String = "platform"
                        },
                    serversConfigProvider = OAuth2ServersConfigBinder(execution),
                    opaqueInternalClientSecretVerifier =
                        DefaultOpaqueInternalClientSecretVerifier(rejectingOpaqueSecrets),
                )

            assertTrue(registry.getClient("tenant-as-service:tenant-123").isErr)
        }

    @Test
    fun configuredClientSecretRefreshesWithoutRecreatingSessionRegistry() =
        runTest {
            val resolvedSecrets =
                mutableMapOf(
                    "sec_issuer_initial_0001" to "initial-secret",
                    "sec_issuer_rotated_0001" to "rotated-secret",
                )
            val configService =
                TypeAwarePrincipalConfigService(
                    mapOf(
                        "oauth2.clients.issuer.client-id" to "issuer-service",
                        "oauth2.clients.issuer.client-secret-id" to "sec_issuer_initial_0001",
                        "oauth2.clients.issuer.grant-types" to "client_credentials",
                    ),
                    normalizeKeys = true,
                )
            val execution = TestSessionExecution(configService)
            val registry =
                ConfigAwareClientRegistry(
                    execution = execution,
                    backingStorage = InMemoryOAuth2BackingStorageImpl(),
                    configBinder =
                        OAuth2ClientsConfigBinder(
                            execution,
                            resolvingOpaqueSecrets(resolvedSecrets),
                        ),
                    asInstanceIdProvider =
                        object : OAuth2ServerInstanceIdProvider {
                            override fun currentAsInstanceId(): String? = null
                        },
                    serversConfigProvider = TestOAuth2ServersConfigProvider(OAuth2ServersConfig()),
                    opaqueInternalClientSecretVerifier =
                        DefaultOpaqueInternalClientSecretVerifier(
                            resolvingOpaqueSecrets(resolvedSecrets),
                        ),
                )

            assertTrue(registry.verifyClientCredentials("issuer-service", "initial-secret").value)

            configService.putProperty("oauth2.clients.issuer.client-secret-id", "sec_issuer_rotated_0001")

            assertFalse(registry.verifyClientCredentials("issuer-service", "initial-secret").value)
            assertTrue(registry.verifyClientCredentials("issuer-service", "rotated-secret").value)
        }

    @Test
    fun compactJwkSetAuthoredByPlatformConfigIsPropagatedToClientRegistration() = runTest {
        val binder =
            OAuth2ClientsConfigBinder(
                TestSessionExecution(
                    TypeAwarePrincipalConfigService(
                        mapOf(
                            "oauth2.servers.default.clients.oidf.client-id" to "oidf-client",
                            "oauth2.servers.default.clients.oidf.client-type" to "confidential",
                            "oauth2.servers.default.clients.oidf.grant-types" to "authorization_code,refresh_token",
                            "oauth2.servers.default.clients.oidf.token-endpoint-auth-method" to "private_key_jwt",
                            "oauth2.servers.default.clients.oidf.token-endpoint-auth-signing-alg" to "ES256",
                            "oauth2.servers.default.clients.oidf.jwks" to
                                """{"keys":[{"kty":"EC","crv":"P-256","kid":"oidf-client-key","x":"yHNp8QgNiVSxSxIH_n_nH23dpUDlNhbgvLKSrjK1hDs","y":"3_rlpW_FXqghp8dKPpkjfvbfACQQFLFZwJXxOr319Ac","use":"sig","alg":"ES256"}]}""",
                        ),
                        normalizeKeys = true,
                    ),
                ),
                rejectingOpaqueSecrets,
            )

        val result = binder.loadClientRegistrations(serverId = "default")

        assertTrue(result.isOk)
        val registration = assertNotNull(result.value["oidf-client"])
        assertEquals(ClientAuthenticationMethod.PRIVATE_KEY_JWT, registration.tokenEndpointAuthMethod)
        assertEquals(listOf("ES256"), registration.tokenEndpointAuthSigningAlg)
        assertEquals("oidf-client-key", registration.jwks?.single()?.kid)
    }

    @Test
    fun configuredClientAudiencePolicyIsPropagatedToClientRegistration() = runTest {
        val binder =
            OAuth2ClientsConfigBinder(
                TestSessionExecution(
                    TypeAwarePrincipalConfigService(
                        mapOf(
                            "oauth2.clients.issuer.client-id" to "issuer-service",
                            "oauth2.clients.issuer.grant-types" to "client_credentials",
                            "oauth2.clients.issuer.default-access-token-audience" to "enterprise-platform",
                            "oauth2.clients.issuer.allowed-access-token-audiences" to
                                "enterprise-tenant-kms,enterprise-wallet-interaction",
                            "oauth2.clients.issuer.token-endpoint-auth-method" to "private_key_jwt",
                            "oauth2.clients.issuer.token-endpoint-auth-signing-alg" to "ES256,PS256",
                        ),
                        normalizeKeys = true,
                    ),
                ),
                rejectingOpaqueSecrets,
            )

        val result = binder.loadClientRegistrations()

        assertTrue(result.isOk)
        val registration = assertNotNull(result.value["issuer-service"])
        assertEquals("enterprise-platform", registration.defaultAccessTokenAudience)
        assertEquals(
            setOf("enterprise-tenant-kms", "enterprise-wallet-interaction"),
            registration.allowedAccessTokenAudiences,
        )
        assertEquals(ClientAuthenticationMethod.PRIVATE_KEY_JWT, registration.tokenEndpointAuthMethod)
        assertEquals(listOf("ES256", "PS256"), registration.tokenEndpointAuthSigningAlg)
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
                                                    grantTypes = setOf(GrantType.CLIENT_CREDENTIALS, GrantType.TOKEN_EXCHANGE),
                                                    tenantId = "tenant-acme",
                                                    defaultAccessTokenAudience = "enterprise-platform",
                                                    allowedAccessTokenAudiences = setOf("enterprise-tenant-kms"),
                                                ),
                                        ),
                                ),
                        ),
                )
            val execution = TestSessionExecution(TypeAwarePrincipalConfigService(emptyMap()))
            val registry =
                ConfigAwareClientRegistry(
                    execution = execution,
                    backingStorage = InMemoryOAuth2BackingStorageImpl(),
                    configBinder =
                        OAuth2ClientsConfigBinder(
                            execution,
                            rejectingOpaqueSecrets,
                        ),
                    asInstanceIdProvider =
                        object : OAuth2ServerInstanceIdProvider {
                            override fun currentAsInstanceId(): String = serverId
                        },
                    serversConfigProvider = TestOAuth2ServersConfigProvider(config),
                    opaqueInternalClientSecretVerifier =
                        DefaultOpaqueInternalClientSecretVerifier(rejectingOpaqueSecrets),
                )

            val result = registry.getClient("tenant-as-service")

            assertTrue(result.isOk)
            val registration = assertNotNull(result.value)
            assertEquals("tenant-as-secret", registration.clientSecret)
            assertEquals(listOf(GrantType.CLIENT_CREDENTIALS, GrantType.TOKEN_EXCHANGE), registration.grantTypes)
            assertEquals(ClientAuthenticationMethod.CLIENT_SECRET_BASIC, registration.tokenEndpointAuthMethod)
            assertEquals("enterprise-platform", registration.defaultAccessTokenAudience)
            assertEquals(setOf("enterprise-tenant-kms"), registration.allowedAccessTokenAudiences)
            assertEquals("tenant-acme", registration.additionalMetadata["tenant_id"])
        }

    @Test
    fun configuredClientSecretRejectsLegacyPlaintextAndProviderReference() =
        runTest {
            listOf("literal-plaintext", "\${secret:oauth/issuer/client-secret}").forEach { legacyValue ->
                val configService =
                    TypeAwarePrincipalConfigService(
                        mapOf(
                            "oauth2.clients.issuer.client-id" to "issuer-service",
                            "oauth2.clients.issuer.client-secret" to legacyValue,
                            "oauth2.clients.issuer.grant-types" to "client_credentials",
                        ),
                        normalizeKeys = true,
                    )
                val binder = OAuth2ClientsConfigBinder(TestSessionExecution(configService), rejectingOpaqueSecrets)

                val result = binder.loadClientRegistrations()

                assertTrue(result.isErr)
            }
        }

    @Test
    fun configuredClientSecretIdRejectsLegacyProviderReference() =
        runTest {
            val configService =
                TypeAwarePrincipalConfigService(
                    mapOf(
                        "oauth2.clients.issuer.client-id" to "issuer-service",
                        "oauth2.clients.issuer.client-secret-id" to "\${secret:oauth/issuer/client-secret}",
                        "oauth2.clients.issuer.grant-types" to "client_credentials",
                    ),
                    normalizeKeys = true,
                )
            val binder = OAuth2ClientsConfigBinder(TestSessionExecution(configService), rejectingOpaqueSecrets)

            val result = binder.loadClientRegistrations()

            assertTrue(result.isErr)
        }

    private companion object {
        val rejectingOpaqueSecrets =
            OpaqueSecretResolver {
                Err(IdkError.NOT_FOUND_ERROR(resource = "secret", message = "Secret not found"))
            }

        fun resolvingOpaqueSecrets(values: Map<String, String>) =
            OpaqueSecretResolver { secretId ->
                values[secretId]
                    ?.let { Ok(it) }
                    ?: Err(IdkError.NOT_FOUND_ERROR(resource = "secret", message = "Secret not found"))
            }
    }
}
