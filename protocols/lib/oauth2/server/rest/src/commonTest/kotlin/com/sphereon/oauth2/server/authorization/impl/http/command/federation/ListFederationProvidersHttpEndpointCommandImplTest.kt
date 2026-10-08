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

package com.sphereon.oauth2.server.authorization.impl.http.command.federation

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.server.authorization.command.federation.EnabledFederationProviders
import com.sphereon.oauth2.server.authorization.command.federation.ListEnabledFederationProvidersArgs
import com.sphereon.oauth2.server.authorization.command.federation.ListEnabledFederationProvidersCommand
import com.sphereon.oauth2.server.authorization.config.FederationProviderConfig
import com.sphereon.oauth2.server.authorization.impl.http.command.TestSessionExecution
import com.sphereon.oauth2.server.authorization.provider.AuthenticationError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListFederationProvidersHttpEndpointCommandImplTest {
    private class FakeListProvidersCommand(
        private val handler: suspend () -> IdkResult<EnabledFederationProviders, AuthenticationError>,
    ) : ListEnabledFederationProvidersCommand {
        override val commandId: String get() = ListEnabledFederationProvidersCommand.COMMAND_ID
        override val inputTypeToken get() = typeToken<ListEnabledFederationProvidersArgs>()
        override val outputTypeToken get() = typeToken<EnabledFederationProviders>()
        override val isEnabled: Boolean = true

        override suspend fun supports(args: Any): Boolean = args is ListEnabledFederationProvidersArgs

        override suspend fun execute(args: ListEnabledFederationProvidersArgs): IdkResult<EnabledFederationProviders, AuthenticationError> = handler()
    }

    @Test
    fun success_rendersJsonArrayOfProviders() =
        runTest {
            val command =
                ListFederationProvidersHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    listEnabledFederationProvidersCommand =
                        FakeListProvidersCommand {
                            Ok(
                                EnabledFederationProviders(
                                    providers =
                                        listOf(
                                            FederationProviderConfig(
                                                id = "p1",
                                                name = "Provider 1",
                                                issuerUrl = "https://idp1.example",
                                                clientId = "c1",
                                                enabled = true,
                                            ),
                                        ),
                                ),
                            )
                        },
                )

            val request = GenericHttpRequest(method = "GET", path = "/federation/providers")
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(200, result.value.statusCode)
            assertEquals("application/json", result.value.headers["Content-Type"])
            assertTrue(result.value.body!!.contains("\"id\":\"p1\""))
            assertTrue(result.value.body!!.contains("\"name\":\"Provider 1\""))
            assertTrue(result.value.body!!.contains("\"enabled\":true"))
        }

    @Test
    fun error_returns500() =
        runTest {
            val command =
                ListFederationProvidersHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    listEnabledFederationProvidersCommand =
                        FakeListProvidersCommand { Err(AuthenticationError.Generic(description = "registry unavailable")) },
                )

            val request = GenericHttpRequest(method = "GET", path = "/federation/providers")
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(500, result.value.statusCode)
            assertTrue(result.value.body!!.contains("server_error"))
        }
}
