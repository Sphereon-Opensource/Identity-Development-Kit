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

package com.sphereon.openid.oid4vp.verifier.impl.http

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.AppConfigService
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.ConfigService
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.conf.TenantConfigService
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.log.AsyncLogService
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.openid.oid4vp.verifier.impl.http.command.DirectPostResponseEndpointCommand
import com.sphereon.openid.oid4vp.verifier.impl.http.command.GetRequestObjectEndpointCommand
import com.sphereon.openid.oid4vp.verifier.impl.http.command.PostRequestObjectEndpointCommand
import com.sphereon.openid.oid4vp.verifier.impl.http.command.ReadyEndpointCommand
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class NoOpSessionLogService(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionLogService {
    override val id: String = "test-log"
    override val isEnabled: Boolean = false
    override val scope = com.sphereon.core.api.context.IdkScope.SESSION
    override val logManager: SessionLogManager get() = throw NotImplementedError("Not needed for test")

    override suspend fun setConfig(config: com.sphereon.core.api.log.LoggerConfig): com.sphereon.core.api.log.LogService = this

    override fun executeAsync(message: LogMessage): IdkResult<Unit, IdkErrorType> = Ok(Unit)

    override fun toAsync(): AsyncLogService = throw NotImplementedError("Not needed for test")
}

private class NoOpContextConfig : ContextConfig {
    override val app: AppConfigService get() = throw NotImplementedError("Not needed for test")
    override val tenant: TenantConfigService get() = throw NotImplementedError("Not needed for test")
    override val principal: PrincipalConfigService get() = throw NotImplementedError("Not needed for test")

    override fun conf(level: ConfigLevel): ConfigService = throw NotImplementedError("Not needed for test")
}

private class TestSessionExecution(
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionExecution {
    override val sessionContextManager: SessionContextManager get() = throw NotImplementedError("Not needed for test")
    override val log: SessionLogService = NoOpSessionLogService(sessionContext)
    override val conf: ContextConfig = NoOpContextConfig()
}

class Oid4VpVerifierHttpAdapterRequestUriTest {
    @Test
    fun `GET request_uri returns oauth-authz-req+jwt body`() =
        runTest {
            val execution: SessionExecution = TestSessionExecution()
            val correlationId = "corr-123"

            // Create mock GET command that returns the expected JWT response
            val getCommand =
                object : GetRequestObjectEndpointCommand {
                    override val id: String = GetRequestObjectEndpointCommand.COMMAND_ID
                    override val endpoint: HttpEndpointDescriptor = GetRequestObjectEndpointCommand.ENDPOINT
                    override val isEnabled: Boolean = true

                    override suspend fun execute(args: GenericHttpRequest): IdkResult<GenericHttpResponse, IdkError> =
                        Ok(
                            GenericHttpResponse(
                                statusCode = 200,
                                headers =
                                    mapOf(
                                        "Content-Type" to "application/oauth-authz-req+jwt",
                                        "Cache-Control" to "no-store",
                                    ),
                                body = "signed.jwt.payload",
                            ),
                        )
                }

            // Create mock POST command (not used in this test but required by adapter)
            val postCommand =
                object : PostRequestObjectEndpointCommand {
                    override val id: String = PostRequestObjectEndpointCommand.COMMAND_ID
                    override val endpoint: HttpEndpointDescriptor = PostRequestObjectEndpointCommand.ENDPOINT
                    override val isEnabled: Boolean = true

                    override suspend fun execute(args: GenericHttpRequest): IdkResult<GenericHttpResponse, IdkError> = Ok(GenericHttpResponse(statusCode = 405, body = "Method not allowed"))
                }

            // Create mock direct_post command (not used in this test but required by adapter)
            val directPostCommand =
                object : DirectPostResponseEndpointCommand {
                    override val id: String = DirectPostResponseEndpointCommand.COMMAND_ID
                    override val endpoint: HttpEndpointDescriptor = DirectPostResponseEndpointCommand.ENDPOINT
                    override val isEnabled: Boolean = true

                    override suspend fun execute(args: GenericHttpRequest): IdkResult<GenericHttpResponse, IdkError> = Ok(GenericHttpResponse(statusCode = 405, body = "Method not allowed"))
                }

            // Create mock readiness command (not used in this test but required by adapter)
            val readyCommand =
                object : ReadyEndpointCommand {
                    override val id: String = ReadyEndpointCommand.COMMAND_ID
                    override val endpoint: HttpEndpointDescriptor = ReadyEndpointCommand.ENDPOINT
                    override val isEnabled: Boolean = true

                    override suspend fun execute(args: GenericHttpRequest): IdkResult<GenericHttpResponse, IdkError> = Ok(GenericHttpResponse(statusCode = 200, body = "{\"status\":\"ready\"}"))
                }

            val adapter =
                Oid4vpVerifierHttpAdapter(
                    execution = execution,
                    getRequestObjectCommand = getCommand,
                    postRequestObjectCommand = postCommand,
                    directPostResponseCommand = directPostCommand,
                    readyCommand = readyCommand,
                )

            val response =
                adapter.handleRequest(
                    GenericHttpRequest(
                        method = "GET",
                        path = "/oid4vp/request-uri/$correlationId",
                    ),
                )

            assertEquals(200, response.statusCode)
            assertEquals("application/oauth-authz-req+jwt", response.headers["Content-Type"])
            assertTrue(response.headers["Cache-Control"]?.contains("no-store") == true)
            assertEquals("signed.jwt.payload", response.body)
        }
}
