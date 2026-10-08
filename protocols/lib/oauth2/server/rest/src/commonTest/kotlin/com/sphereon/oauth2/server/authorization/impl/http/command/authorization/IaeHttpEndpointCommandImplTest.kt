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

package com.sphereon.oauth2.server.authorization.impl.http.command.authorization

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.server.authorization.command.HandleIaeFollowUpArgs
import com.sphereon.oauth2.server.authorization.command.HandleIaeFollowUpCommand
import com.sphereon.oauth2.server.authorization.command.HandleIaeInitialRequestArgs
import com.sphereon.oauth2.server.authorization.command.HandleIaeInitialRequestCommand
import com.sphereon.oauth2.server.authorization.command.IaeResult
import com.sphereon.oauth2.server.authorization.impl.http.command.TestSessionExecution
import com.sphereon.oauth2.server.authorization.model.IaeAuthorizationCodeResponse
import com.sphereon.oauth2.server.authorization.model.IaeErrorResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IaeHttpEndpointCommandImplTest {
    private class FakeIaeInitialCommand(
        private val handler: suspend (HandleIaeInitialRequestArgs) -> IdkResult<IaeResult, IdkError>,
    ) : HandleIaeInitialRequestCommand {
        override val commandId: String get() = HandleIaeInitialRequestCommand.COMMAND_ID
        override val inputTypeToken get() = typeToken<HandleIaeInitialRequestArgs>()
        override val outputTypeToken get() = typeToken<IaeResult>()
        override val isEnabled: Boolean = true

        override suspend fun supports(args: Any): Boolean = args is HandleIaeInitialRequestArgs

        override suspend fun execute(args: HandleIaeInitialRequestArgs): IdkResult<IaeResult, IdkError> = handler(args)
    }

    private class FakeIaeFollowUpCommand(
        private val handler: suspend (HandleIaeFollowUpArgs) -> IdkResult<IaeResult, IdkError>,
    ) : HandleIaeFollowUpCommand {
        override val commandId: String get() = HandleIaeFollowUpCommand.COMMAND_ID
        override val inputTypeToken get() = typeToken<HandleIaeFollowUpArgs>()
        override val outputTypeToken get() = typeToken<IaeResult>()
        override val isEnabled: Boolean = true

        override suspend fun supports(args: Any): Boolean = args is HandleIaeFollowUpArgs

        override suspend fun execute(args: HandleIaeFollowUpArgs): IdkResult<IaeResult, IdkError> = handler(args)
    }

    @Test
    fun missingAuthSession_dispatchesToInitial() =
        runTest {
            var capturedInitial: HandleIaeInitialRequestArgs? = null
            val command =
                IaeHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handleIaeInitialRequestCommand =
                        FakeIaeInitialCommand { args ->
                            capturedInitial = args
                            Ok(IaeResult.AuthorizationCode(IaeAuthorizationCodeResponse(code = "abc")))
                        },
                    handleIaeFollowUpCommand = FakeIaeFollowUpCommand { error("Should not be called") },
                )

            val request =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/iae",
                    body = "client_id=demo&redirect_uri=https%3A%2F%2Fc.example%2Fcb",
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(200, result.value.statusCode)
            assertTrue(result.value.body!!.contains("\"code\":\"abc\""))
            assertEquals("demo", capturedInitial!!.clientId)
        }

    @Test
    fun authSessionPresent_dispatchesToFollowUp() =
        runTest {
            var capturedFollowUp: HandleIaeFollowUpArgs? = null
            val command =
                IaeHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handleIaeInitialRequestCommand = FakeIaeInitialCommand { error("Should not be called") },
                    handleIaeFollowUpCommand =
                        FakeIaeFollowUpCommand { args ->
                            capturedFollowUp = args
                            Ok(IaeResult.AuthorizationCode(IaeAuthorizationCodeResponse(code = "follow-code")))
                        },
                )

            val request =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/iae",
                    body = "auth_session=abc-123",
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(200, result.value.statusCode)
            assertTrue(result.value.body!!.contains("follow-code"))
            assertEquals("abc-123", capturedFollowUp?.authSession)
            assertNull(capturedFollowUp?.openid4vpResponse)
        }

    @Test
    fun missingClientIdInitial_returns400() =
        runTest {
            val command =
                IaeHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handleIaeInitialRequestCommand = FakeIaeInitialCommand { error("Should not be called") },
                    handleIaeFollowUpCommand = FakeIaeFollowUpCommand { error("Should not be called") },
                )

            val request =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/iae",
                    body = "redirect_uri=https%3A%2F%2Fc.example%2Fcb",
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(400, result.value.statusCode)
            assertTrue(result.value.body!!.contains("client_id"))
        }

    @Test
    fun iaeError_returns400WithErrorBody() =
        runTest {
            val command =
                IaeHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handleIaeInitialRequestCommand =
                        FakeIaeInitialCommand {
                            Ok(IaeResult.Error(IaeErrorResponse(error = "invalid_request", errorDescription = "bad shape")))
                        },
                    handleIaeFollowUpCommand = FakeIaeFollowUpCommand { error("Should not be called") },
                )

            val request =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/iae",
                    body = "client_id=demo&redirect_uri=https%3A%2F%2Fc.example%2Fcb",
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(400, result.value.statusCode)
            assertTrue(result.value.body!!.contains("invalid_request"))
        }
}
