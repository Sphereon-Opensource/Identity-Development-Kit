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

package com.sphereon.oauth2.server.authorization.impl.http.command.par

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfig
import com.sphereon.oauth2.server.authorization.command.PushedAuthorizationResponse
import com.sphereon.oauth2.server.authorization.command.par.HandlePushedAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.command.par.HandlePushedAuthorizationRequestCommand
import com.sphereon.oauth2.server.authorization.impl.http.command.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.http.command.TestSessionExecution
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParHttpEndpointCommandImplTest {
    private class FakeParCommand(
        private val handler: suspend (HandlePushedAuthorizationRequestArgs) -> IdkResult<PushedAuthorizationResponse, IdkError>,
    ) : HandlePushedAuthorizationRequestCommand {
        override val commandId: String get() = HandlePushedAuthorizationRequestCommand.COMMAND_ID
        override val inputTypeToken get() = typeToken<HandlePushedAuthorizationRequestArgs>()
        override val outputTypeToken get() = typeToken<PushedAuthorizationResponse>()
        override val isEnabled: Boolean = true

        override suspend fun supports(args: Any): Boolean = args is HandlePushedAuthorizationRequestArgs

        override suspend fun execute(args: HandlePushedAuthorizationRequestArgs): IdkResult<PushedAuthorizationResponse, IdkError> = handler(args)
    }

    private fun parEnabledConfig() =
        TestOAuth2ServersConfigProvider(
            OAuth2ServersConfig(
                servers = mapOf("default" to OAuth2ServerInstanceConfig(par = FeaturePolicy.SUPPORTED)),
            ),
        )

    @Test
    fun success_dispatchesToServiceCommand() =
        runTest {
            var captured: HandlePushedAuthorizationRequestArgs? = null
            val command =
                ParHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handlePushedAuthorizationRequestCommand =
                        FakeParCommand { args ->
                            captured = args
                            // Return an error so we don't exercise the JSON encoder for an
                            // un-`@Serializable` data class — the impl's own serialization is
                            // identical to the legacy adapter's; this test verifies pure dispatch
                            // of the ServiceCommand call.
                            Err(IdkError.fromString(code = "server_error", message = "test marker"))
                        },
                    configProvider = parEnabledConfig(),
                )

            val request =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/par",
                    body = "client_id=x&response_type=code",
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(
                "client_id" to listOf("x"),
                captured!!
                    .requestBody.entries
                    .first()
                    .toPair()
            )
        }

    @Test
    fun parDisabled_returns404() =
        runTest {
            val command =
                ParHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handlePushedAuthorizationRequestCommand = FakeParCommand { error("Should not be called") },
                    configProvider =
                        TestOAuth2ServersConfigProvider(
                            OAuth2ServersConfig(
                                servers = mapOf("default" to OAuth2ServerInstanceConfig(par = FeaturePolicy.DISABLED)),
                            ),
                        ),
                )

            val request =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/par",
                    body = "client_id=x",
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(404, result.value.statusCode)
        }

    @Test
    fun serviceCommandError_renders400() =
        runTest {
            val command =
                ParHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handlePushedAuthorizationRequestCommand =
                        FakeParCommand { Err(IdkError.fromString(code = "invalid_request", message = "bad form")) },
                    configProvider = parEnabledConfig(),
                )

            val request =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/par",
                    body = "client_id=x",
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(400, result.value.statusCode)
        }
}
