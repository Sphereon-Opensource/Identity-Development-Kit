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

package com.sphereon.oauth2.server.authorization.impl.http.command.token

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.server.authorization.command.token.RegisterPreAuthorizedCodeArgs
import com.sphereon.oauth2.server.authorization.command.token.RegisterPreAuthorizedCodeCommand
import com.sphereon.oauth2.server.authorization.command.token.RegisterPreAuthorizedCodeResult
import com.sphereon.oauth2.server.authorization.impl.http.command.TestSessionExecution
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RegisterPreAuthorizedCodeHttpEndpointCommandImplTest {
    private class FakeRegisterCommand(
        private val handler: suspend (RegisterPreAuthorizedCodeArgs) -> IdkResult<RegisterPreAuthorizedCodeResult, IdkError>,
    ) : RegisterPreAuthorizedCodeCommand {
        override val commandId: String get() = RegisterPreAuthorizedCodeCommand.COMMAND_ID
        override val inputTypeToken get() = typeToken<RegisterPreAuthorizedCodeArgs>()
        override val outputTypeToken get() = typeToken<RegisterPreAuthorizedCodeResult>()
        override val isEnabled: Boolean = true

        override suspend fun supports(args: Any): Boolean = args is RegisterPreAuthorizedCodeArgs

        override suspend fun execute(args: RegisterPreAuthorizedCodeArgs): IdkResult<RegisterPreAuthorizedCodeResult, IdkError> = handler(args)
    }

    private fun basicHeader(
        clientId: String,
        secret: String,
    ): String = "Basic " + ("$clientId:$secret").encodeToByteArray().encodeToBase64()

    @Test
    fun success_returns200WithStatus() =
        runTest {
            var captured: RegisterPreAuthorizedCodeArgs? = null
            val command =
                RegisterPreAuthorizedCodeHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    registerPreAuthorizedCodeCommand =
                        FakeRegisterCommand { args ->
                            captured = args
                            Ok(RegisterPreAuthorizedCodeResult(status = "ok"))
                        },
                )

            val request =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/internal/preauth/register",
                    body =
                        """
                        {"code":"pre-auth-1","sessionId":"sess","credentialConfigurationIds":["cfg-1"]}
                        """.trimIndent(),
                    headers = mapOf("Authorization" to basicHeader("issuer-svc", "shh")),
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(200, result.value.statusCode)
            assertTrue(result.value.body!!.contains("\"status\":\"ok\""))
            assertEquals("issuer-svc", captured?.basicAuthClientId)
            assertEquals("shh", captured?.basicAuthClientSecret)
            assertEquals("pre-auth-1", captured?.code)
        }

    @Test
    fun missingBasicHeader_returns401() =
        runTest {
            val command =
                RegisterPreAuthorizedCodeHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    registerPreAuthorizedCodeCommand = FakeRegisterCommand { error("Should not be called") },
                )

            val request =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/internal/preauth/register",
                    body = "{}",
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(401, result.value.statusCode)
        }

    @Test
    fun invalidJson_returns400() =
        runTest {
            val command =
                RegisterPreAuthorizedCodeHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    registerPreAuthorizedCodeCommand = FakeRegisterCommand { error("Should not be called") },
                )

            val request =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/internal/preauth/register",
                    body = "not-json",
                    headers = mapOf("Authorization" to basicHeader("c", "s")),
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(400, result.value.statusCode)
        }

    @Test
    fun invalidClient_returns401() =
        runTest {
            val command =
                RegisterPreAuthorizedCodeHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    registerPreAuthorizedCodeCommand =
                        FakeRegisterCommand { Err(IdkError.fromString(code = "invalid_client", message = "bad creds")) },
                )

            val request =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/internal/preauth/register",
                    body = """{"code":"x","sessionId":"y","credentialConfigurationIds":[]}""",
                    headers = mapOf("Authorization" to basicHeader("c", "wrong")),
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(401, result.value.statusCode)
        }
}
