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

package com.sphereon.oauth2.server.authorization.impl.command.introspection

import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.OpaqueSecretResolver
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.server.authorization.impl.config.OAuth2ClientsConfigBinder
import com.sphereon.oauth2.server.authorization.impl.config.OAuth2ClientMetadataCache
import com.sphereon.oauth2.server.authorization.impl.config.OAuth2ServersConfigBinder
import com.sphereon.oauth2.server.authorization.impl.config.TestSessionExecution
import com.sphereon.oauth2.server.authorization.impl.config.TypeAwarePrincipalConfigService
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfigBackedInternalIntrospectionClientAuthorizerTest {
    @Test
    fun tenantWorkloadRegisteredOnDefaultServerIsInternalForAnotherHostedServer() =
        runTest {
            val authorizer = authorizer(activeServerId = "wallet-proxy")

            assertEquals(Ok(true), authorizer.isInternalClient("issuer-service:tenant-acme"))
        }

    @Test
    fun tenantWorkloadIsInternalOnItsOwnServer() =
        runTest {
            val authorizer = authorizer(activeServerId = "acme")

            assertEquals(Ok(true), authorizer.isInternalClient("issuer-service:tenant-acme"))
        }

    @Test
    fun unknownClientIsNotInternalOnAnyServer() =
        runTest {
            val authorizer = authorizer(activeServerId = "wallet-proxy")

            assertEquals(Ok(false), authorizer.isInternalClient("postman-wallet"))
        }

    @Test
    fun blankClientIsNeverInternal() =
        runTest {
            val authorizer = authorizer(activeServerId = "wallet-proxy")

            assertEquals(Ok(false), authorizer.isInternalClient(""))
        }

    @Test
    fun registrationOnAnotherNonDefaultServerDoesNotLeak() =
        runTest {
            val authorizer =
                authorizer(
                    activeServerId = "wallet-proxy",
                    extraProperties =
                        mapOf(
                            "oauth2.servers.other-proxy.mode" to "HOSTED",
                            "oauth2.servers.other-proxy.internal-clients.issuer.client-id" to "issuer-proxy:tenant-acme",
                            "oauth2.servers.other-proxy.internal-clients.issuer.tenant-id" to "tenant-acme",
                        ),
                )

            assertEquals(Ok(false), authorizer.isInternalClient("issuer-proxy:tenant-acme"))
        }

    private fun authorizer(
        activeServerId: String,
        extraProperties: Map<String, Any> = emptyMap(),
    ): ConfigBackedInternalIntrospectionClientAuthorizer {
        val configService =
            TypeAwarePrincipalConfigService(
                properties =
                    mapOf(
                        "oauth2.servers.default-server" to "acme",
                        "oauth2.servers.acme.mode" to "HOSTED",
                        "oauth2.servers.wallet-proxy.mode" to "HOSTED",
                        "oauth2.servers.acme.internal-clients.issuer.client-id" to "issuer-service:tenant-acme",
                        "oauth2.servers.acme.internal-clients.issuer.tenant-id" to "tenant-acme",
                    ) + extraProperties,
                normalizeKeys = true,
            )
        val execution = TestSessionExecution(configService, tenantId = "tenant-acme")
        val instanceIdProvider =
            object : OAuth2ServerInstanceIdProvider {
                override fun currentAsInstanceId(): String = activeServerId
            }
        val serversConfigBinder = OAuth2ServersConfigBinder(execution, instanceIdProvider)
        val clientsConfigBinder =
            OAuth2ClientsConfigBinder(
                execution,
                OpaqueSecretResolver { _ ->
                    Err(IdkError.NOT_FOUND_ERROR(resource = "secret", message = "Secret not found"))
                },
                OAuth2ClientMetadataCache(),
            )

        return ConfigBackedInternalIntrospectionClientAuthorizer(
            configBinder = clientsConfigBinder,
            asInstanceIdProvider = instanceIdProvider,
            serversConfigProvider = serversConfigBinder,
        )
    }
}
