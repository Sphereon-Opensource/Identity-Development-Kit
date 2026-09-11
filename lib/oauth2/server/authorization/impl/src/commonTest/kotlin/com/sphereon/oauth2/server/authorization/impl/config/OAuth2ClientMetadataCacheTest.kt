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

package com.sphereon.oauth2.server.authorization.impl.config

import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.model.ClientType
import com.sphereon.oauth2.server.authorization.impl.storage.memory.OpaqueInternalClientCredential
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OAuth2ClientMetadataCacheTest {
    @Test
    fun sameKeyReusesMetadataWhileEverySecurityDimensionSeparatesEntries() =
        runTest {
            val cache = OAuth2ClientMetadataCache()
            val principalView = TypeAwarePrincipalConfigService(emptyMap())
            val base =
                ClientMetadataKey(
                    tenantId = "tenant-one",
                    principalId = "principal-one",
                    principalType = "WORKLOAD",
                    configViewIdentity = PrincipalConfigViewIdentity(principalView),
                    asInstanceId = "platform",
                    configRevision = 7,
                    configPartition = "clients:oauth2.clients",
                )
            var parses = 0
            suspend fun resolve(key: ClientMetadataKey) =
                cache.configuredClients(key) {
                    parses += 1
                    mapOf(
                        "entry" to
                            ConfiguredOAuth2Client(
                                configKey = "entry",
                                clientId = "client-$parses",
                                clientSecretId = "sec_metadata_locator_0001",
                                grantTypes = listOf(GrantType.CLIENT_CREDENTIALS),
                            ),
                    )
                }

            val first = resolve(base)
            val repeated = resolve(base.copy())
            assertEquals(first, repeated)
            assertEquals(1, parses)

            resolve(base.copy(tenantId = "tenant-two"))
            resolve(base.copy(principalId = "principal-two"))
            resolve(base.copy(principalType = "USER"))
            resolve(base.copy(asInstanceId = "tenant-as"))
            resolve(base.copy(configRevision = 8))
            resolve(base.copy(configPartition = "opaque:oauth2.servers.platform.internal-clients"))
            resolve(
                base.copy(
                    configViewIdentity =
                        PrincipalConfigViewIdentity(TypeAwarePrincipalConfigService(emptyMap())),
                ),
            )

            assertEquals(8, parses)
            assertEquals(
                "sec_metadata_locator_0001",
                first.getValue("entry").clientSecretId,
                "the shared snapshot contains only the opaque locator, never resolved plaintext",
            )
        }

    @Test
    fun tenantOpaqueMetadataReusesOnlyTheExactTenantRevisionServerAndPartition() =
        runTest {
            val cache = OAuth2ClientMetadataCache()
            val base =
                TenantClientMetadataKey(
                    tenantId = "tenant-one",
                    asInstanceId = "platform",
                    configRevision = 11,
                    configPartition = "opaque:oauth2.servers.platform.internal-clients",
                )
            var parses = 0
            suspend fun resolve(key: TenantClientMetadataKey) =
                cache.opaqueInternalClients(key) {
                    parses += 1
                    mapOf(
                        "workload" to
                            OpaqueInternalClientRegistration(
                                registration =
                                    ClientRegistration(
                                        clientId = "workload-client",
                                        clientSecret = null,
                                        clientType = ClientType.CONFIDENTIAL,
                                        grantTypes = listOf(GrantType.CLIENT_CREDENTIALS),
                                    ),
                                credential =
                                    OpaqueInternalClientCredential(
                                        clientId = "workload-client",
                                        tenantId = key.tenantId,
                                        secretId = "sec_workload_client_0001",
                                    ),
                            ),
                    )
                }

            val first = resolve(base)
            assertEquals(first, resolve(base.copy()))
            assertEquals(1, parses)

            resolve(base.copy(tenantId = "tenant-two"))
            resolve(base.copy(asInstanceId = "tenant-as"))
            resolve(base.copy(configRevision = 12))
            resolve(base.copy(configPartition = "opaque:another-partition"))

            assertEquals(5, parses)
            assertEquals(
                null,
                first.getValue("workload").registration.clientSecret,
                "tenant metadata cache must never retain resolved or plaintext client secrets",
            )
        }
}
