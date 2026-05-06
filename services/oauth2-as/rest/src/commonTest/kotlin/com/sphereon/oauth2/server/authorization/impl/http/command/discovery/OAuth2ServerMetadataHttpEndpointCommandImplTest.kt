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

package com.sphereon.oauth2.server.authorization.impl.http.command.discovery

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.command.discovery.HandleDiscoveryRequestArgs
import com.sphereon.oauth2.server.authorization.command.discovery.HandleDiscoveryRequestCommand
import com.sphereon.oauth2.server.authorization.command.discovery.OAuth2ServerMetadataHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.command.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.http.command.TestSessionExecution
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OAuth2ServerMetadataHttpEndpointCommandImplTest {
    private class FakeDiscoveryCommand(
        private val handler: suspend (HandleDiscoveryRequestArgs) -> IdkResult<AuthorizationServerMetadata, IdkError>,
    ) : HandleDiscoveryRequestCommand {
        override val commandId: String get() = HandleDiscoveryRequestCommand.COMMAND_ID
        override val inputTypeToken get() = typeToken<HandleDiscoveryRequestArgs>()
        override val outputTypeToken get() = typeToken<AuthorizationServerMetadata>()
        override val isEnabled: Boolean = true

        override suspend fun supports(args: Any): Boolean = args is HandleDiscoveryRequestArgs

        override suspend fun execute(args: HandleDiscoveryRequestArgs): IdkResult<AuthorizationServerMetadata, IdkError> = handler(args)
    }

    @Test
    fun matchesBareAndTenantPath() =
        runTest {
            val command =
                OAuth2ServerMetadataHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handleDiscoveryRequestCommand = FakeDiscoveryCommand { error("Not invoked here") },
                    configProvider = TestOAuth2ServersConfigProvider(),
                )

            assertTrue(command.supports(GenericHttpRequest(method = "GET", path = "/.well-known/oauth-authorization-server")))
            assertTrue(command.supports(GenericHttpRequest(method = "GET", path = "/.well-known/oauth-authorization-server/acme")))
            assertFalse(command.supports(GenericHttpRequest(method = "POST", path = "/.well-known/oauth-authorization-server")))
        }

    @Test
    fun success_renders200WithMetadata() =
        runTest {
            var captured: HandleDiscoveryRequestArgs? = null
            val command =
                OAuth2ServerMetadataHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handleDiscoveryRequestCommand =
                        FakeDiscoveryCommand { args ->
                            captured = args
                            Ok(
                                AuthorizationServerMetadata(
                                    issuer = "http://as.example",
                                    tokenEndpoint = "http://as.example/token",
                                ),
                            )
                        },
                    configProvider = TestOAuth2ServersConfigProvider(),
                )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/.well-known/oauth-authorization-server",
                    headers = mapOf("Host" to "as.example"),
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(200, result.value.statusCode)
            assertEquals("application/json", result.value.headers["Content-Type"])
            assertTrue(result.value.body!!.contains("\"issuer\""))
            assertEquals("http://as.example", captured!!.baseUrlOverride)
        }

    @Test
    fun error_renders500() =
        runTest {
            val command =
                OAuth2ServerMetadataHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handleDiscoveryRequestCommand =
                        FakeDiscoveryCommand { Err(IdkError.fromString(code = "server_error", message = "boom")) },
                    configProvider = TestOAuth2ServersConfigProvider(),
                )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/.well-known/oauth-authorization-server",
                    headers = mapOf("Host" to "as.example"),
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(500, result.value.statusCode)
        }
}
