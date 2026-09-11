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

package com.sphereon.oauth2.server.authorization.impl.command.federation

import com.sphereon.oauth2.server.authorization.command.federation.ListEnabledFederationProvidersArgs
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import com.sphereon.oauth2.server.authorization.provider.FederationProviderRuntimeResolver
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListEnabledFederationProvidersCommandImplTest {
    private val ctx = OAuth2ServerTestContext("list-federation-providers-test", this)

    private class FakeRegistry(
        private val providers: List<FederationProviderConfig>,
    ) : FederationProviderRuntimeResolver {
        override suspend fun resolve(bindingId: String): IdkResult<FederationProviderConfig, AuthenticationError> =
            providers.firstOrNull { it.id == bindingId }?.let(::Ok)
                ?: Err(AuthenticationError.Generic(description = "not found"))

        override suspend fun listEnabled(): IdkResult<List<FederationProviderConfig>, AuthenticationError> =
            Ok(providers.filter(FederationProviderConfig::enabled))

        override suspend fun clientAuthentication(bindingId: String, audience: String): IdkResult<ClientAuthenticationConfig, AuthenticationError> =
            Ok(ClientAuthenticationConfig.None(bindingId))
    }

    @Test
    fun returnsEnabledProvidersFromRegistry() =
        runTest {
            val provider1 = FederationProviderConfig(id = "p1", name = "Provider 1", issuerUrl = "https://idp1.example", clientId = "c1", enabled = true)
            val provider2 = FederationProviderConfig(id = "p2", name = "Provider 2", issuerUrl = "https://idp2.example", clientId = "c2", enabled = false)
            val provider3 = FederationProviderConfig(id = "p3", name = "Provider 3", issuerUrl = "https://idp3.example", clientId = "c3", enabled = true)
            val registry = FakeRegistry(listOf(provider1, provider2, provider3))
            val command = ListEnabledFederationProvidersCommandImpl(ctx.execution, registry)

            val result = command.execute(ListEnabledFederationProvidersArgs)

            assertTrue(result.isOk)
            val ids = result.value.providers.map { it.id }
            assertEquals(listOf("p1", "p3"), ids)
        }

    @Test
    fun returnsEmptyListWhenRegistryIsEmpty() =
        runTest {
            val command = ListEnabledFederationProvidersCommandImpl(ctx.execution, FakeRegistry(emptyList()))

            val result = command.execute(ListEnabledFederationProvidersArgs)

            assertTrue(result.isOk)
            assertTrue(result.value.providers.isEmpty())
        }
}
