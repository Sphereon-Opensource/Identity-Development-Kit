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
import com.sphereon.oauth2.server.authorization.command.JwksResult
import com.sphereon.oauth2.server.authorization.command.jwks.HandleJwksRequestArgs
import com.sphereon.oauth2.server.authorization.command.jwks.HandleJwksRequestCommand
import com.sphereon.oauth2.server.authorization.impl.http.command.TestSessionExecution
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JwksHttpEndpointCommandImplTest {
    private class FakeJwksCommand(
        private val handler: suspend () -> IdkResult<JwksResult, IdkError>,
    ) : HandleJwksRequestCommand {
        override val commandId: String get() = HandleJwksRequestCommand.COMMAND_ID
        override val inputTypeToken get() = typeToken<HandleJwksRequestArgs>()
        override val outputTypeToken get() = typeToken<JwksResult>()
        override val isEnabled: Boolean = true

        override suspend fun supports(args: Any): Boolean = args is HandleJwksRequestArgs

        override suspend fun execute(args: HandleJwksRequestArgs): IdkResult<JwksResult, IdkError> = handler()
    }

    @Test
    fun success_returnsJwksJson() =
        runTest {
            val command =
                JwksHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handleJwksRequestCommand = FakeJwksCommand { Ok(JwksResult(keys = emptyList())) },
                )

            val request = GenericHttpRequest(method = "GET", path = "/.well-known/jwks.json")
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(200, result.value.statusCode)
            assertEquals("application/json", result.value.headers["Content-Type"])
            assertEquals("max-age=3600", result.value.headers["Cache-Control"])
            assertTrue(result.value.body!!.contains("\"keys\""))
        }

    @Test
    fun error_returns500() =
        runTest {
            val command =
                JwksHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handleJwksRequestCommand = FakeJwksCommand { Err(IdkError.fromString(code = "server_error", message = "kms down")) },
                )

            val request = GenericHttpRequest(method = "GET", path = "/.well-known/jwks.json")
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(500, result.value.statusCode)
        }
}
