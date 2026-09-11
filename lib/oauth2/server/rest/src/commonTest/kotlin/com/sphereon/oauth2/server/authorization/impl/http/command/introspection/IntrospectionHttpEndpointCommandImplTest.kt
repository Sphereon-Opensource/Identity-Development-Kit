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

package com.sphereon.oauth2.server.authorization.impl.http.command.introspection

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.server.authorization.audit.NoOpOAuth2AuditEmitter
import com.sphereon.oauth2.server.authorization.command.introspection.HandleIntrospectionRequestArgs
import com.sphereon.oauth2.server.authorization.command.introspection.HandleIntrospectionRequestCommand
import com.sphereon.oauth2.server.authorization.impl.http.DefaultOAuth2ServerBaseUrlResolver
import com.sphereon.oauth2.server.authorization.impl.http.command.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.http.command.TestSessionExecution
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntrospectionHttpEndpointCommandImplTest {
    private class FakeIntrospectionCommand(
        private val handler: suspend (HandleIntrospectionRequestArgs) -> IdkResult<TokenIntrospectionResponse, IdkError>,
    ) : HandleIntrospectionRequestCommand {
        override val commandId: String get() = HandleIntrospectionRequestCommand.COMMAND_ID
        override val inputTypeToken get() = typeToken<HandleIntrospectionRequestArgs>()
        override val outputTypeToken get() = typeToken<TokenIntrospectionResponse>()
        override val isEnabled: Boolean = true

        override suspend fun supports(args: Any): Boolean = args is HandleIntrospectionRequestArgs

        override suspend fun execute(args: HandleIntrospectionRequestArgs): IdkResult<TokenIntrospectionResponse, IdkError> = handler(args)
    }

    @Test
    fun success_renders200WithJsonBody() =
        runTest {
            var captured: HandleIntrospectionRequestArgs? = null
            val command =
                IntrospectionHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handleIntrospectionRequestCommand =
                        FakeIntrospectionCommand { args ->
                            captured = args
                            Ok(TokenIntrospectionResponse(active = true, scope = "read"))
                        },
                    configProvider = TestOAuth2ServersConfigProvider(),
                    baseUrlResolver = DefaultOAuth2ServerBaseUrlResolver(),
                    auditEmitter = NoOpOAuth2AuditEmitter,
                )

            val request =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/introspect",
                    body = "token=opaque-token",
                    headers = mapOf("Host" to "as.example"),
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(200, result.value.statusCode)
            assertEquals("application/json", result.value.headers["Content-Type"])
            assertTrue(result.value.body!!.contains("\"active\":true"))
            assertEquals("http://as.example/introspect", captured!!.httpUrl)
        }

    @Test
    fun missingBody_renders400() =
        runTest {
            val command =
                IntrospectionHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handleIntrospectionRequestCommand =
                        FakeIntrospectionCommand { error("Should not be called") },
                    configProvider = TestOAuth2ServersConfigProvider(),
                    baseUrlResolver = DefaultOAuth2ServerBaseUrlResolver(),
                    auditEmitter = NoOpOAuth2AuditEmitter,
                )

            val request = GenericHttpRequest(method = "POST", path = "/introspect")
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(400, result.value.statusCode)
        }

    @Test
    fun invalidClientWithBasic_addsWwwAuthenticateHeader() =
        runTest {
            val command =
                IntrospectionHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handleIntrospectionRequestCommand =
                        FakeIntrospectionCommand {
                            Err(IdkError.fromString(code = "invalid_client", message = "bad creds"))
                        },
                    configProvider = TestOAuth2ServersConfigProvider(),
                    baseUrlResolver = DefaultOAuth2ServerBaseUrlResolver(),
                    auditEmitter = NoOpOAuth2AuditEmitter,
                )

            val request =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/introspect",
                    body = "token=x",
                    headers = mapOf("Authorization" to "Basic xxx"),
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(401, result.value.statusCode)
            assertEquals("Basic realm=\"oauth2\"", result.value.headers["WWW-Authenticate"])
        }
}
