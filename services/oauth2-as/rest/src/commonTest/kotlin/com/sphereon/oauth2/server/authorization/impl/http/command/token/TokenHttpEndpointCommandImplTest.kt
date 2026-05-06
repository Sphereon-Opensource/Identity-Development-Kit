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
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.audit.NoOpOAuth2AuditEmitter
import com.sphereon.oauth2.server.authorization.command.token.HandleTokenRequestArgs
import com.sphereon.oauth2.server.authorization.command.token.HandleTokenRequestCommand
import com.sphereon.oauth2.server.authorization.dpop.DpopNonceManager
import com.sphereon.oauth2.server.authorization.impl.http.command.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.http.command.TestSessionExecution
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TokenHttpEndpointCommandImplTest {
    private class FakeHandleTokenRequestCommand(
        private val handler: suspend (HandleTokenRequestArgs) -> IdkResult<TokenResponse, IdkError>,
    ) : HandleTokenRequestCommand {
        override val commandId: String get() = HandleTokenRequestCommand.COMMAND_ID
        override val inputTypeToken get() = typeToken<HandleTokenRequestArgs>()
        override val outputTypeToken get() = typeToken<TokenResponse>()
        override val isEnabled: Boolean = true

        override suspend fun supports(args: Any): Boolean = args is HandleTokenRequestArgs

        override suspend fun execute(args: HandleTokenRequestArgs): IdkResult<TokenResponse, IdkError> = handler(args)
    }

    @Test
    fun success_renders200WithJsonBodyAndCacheControl() =
        runTest {
            var captured: HandleTokenRequestArgs? = null
            val command =
                TokenHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handleTokenRequestCommand =
                        FakeHandleTokenRequestCommand { args ->
                            captured = args
                            Ok(TokenResponse(accessToken = "at", tokenType = "Bearer", expiresIn = 3600))
                        },
                    configProvider = TestOAuth2ServersConfigProvider(),
                    dpopNonceManager = TokenTestNonceManager,
                    clientCertificateExtractor = NoOpClientCertificateExtractor,
                    auditEmitter = NoOpOAuth2AuditEmitter,
                )

            val request =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/token",
                    body = "grant_type=client_credentials",
                    headers = mapOf("Host" to "as.example", "Content-Type" to "application/x-www-form-urlencoded"),
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            val response = result.value
            assertEquals(200, response.statusCode)
            assertEquals("application/json", response.headers["Content-Type"])
            assertEquals("no-store", response.headers["Cache-Control"])
            assertEquals("no-cache", response.headers["Pragma"])
            assertTrue(response.body!!.contains("access_token"))
            assertEquals(
                "grant_type" to listOf("client_credentials"),
                captured
                    ?.requestBody
                    ?.entries
                    ?.first()
                    ?.toPair(),
            )
            assertEquals("http://as.example/token", captured?.httpUrl)
        }

    @Test
    fun missingBody_rendersInvalidRequest() =
        runTest {
            val command =
                TokenHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handleTokenRequestCommand =
                        FakeHandleTokenRequestCommand {
                            error("Should not be called when body is missing")
                        },
                    configProvider = TestOAuth2ServersConfigProvider(),
                    dpopNonceManager = TokenTestNonceManager,
                    clientCertificateExtractor = NoOpClientCertificateExtractor,
                    auditEmitter = NoOpOAuth2AuditEmitter,
                )

            val request = GenericHttpRequest(method = "POST", path = "/token")
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(400, result.value.statusCode)
            assertTrue(result.value.body!!.contains("invalid_request"))
        }

    @Test
    fun serviceCommandError_rendersErrorWithBasicChallenge() =
        runTest {
            val command =
                TokenHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    handleTokenRequestCommand =
                        FakeHandleTokenRequestCommand {
                            Err(IdkError.fromString(code = "invalid_client", message = "bad creds"))
                        },
                    configProvider = TestOAuth2ServersConfigProvider(),
                    dpopNonceManager = TokenTestNonceManager,
                    clientCertificateExtractor = NoOpClientCertificateExtractor,
                    auditEmitter = NoOpOAuth2AuditEmitter,
                )

            val request =
                GenericHttpRequest.withTextBody(
                    method = "POST",
                    path = "/token",
                    body = "grant_type=client_credentials",
                    headers = mapOf("Authorization" to "Basic dXNlcjpwYXNz"),
                )

            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(401, result.value.statusCode)
            assertEquals("Basic realm=\"oauth2\"", result.value.headers["WWW-Authenticate"])
            assertTrue(result.value.body!!.contains("invalid_client"))
        }
}

/** Test stub: no-op nonce manager that returns fixed values; used by tests not exercising nonces. */
private object TokenTestNonceManager : DpopNonceManager {
    override suspend fun currentNonce(): String = "test-nonce-current"

    override suspend fun rotate(): String = "test-nonce-rotated"

    override suspend fun isValid(nonce: String): Boolean = true
}

/** Test stub: no-cert extractor used by tests that don't exercise mTLS client auth. */
private object NoOpClientCertificateExtractor :
    com.sphereon.oauth2.server.authorization.command.clientauth.ClientCertificateExtractor {
    override suspend fun extractCertificate(
        request: com.sphereon.core.api.http.GenericHttpRequest,
    ): com.sphereon.core.api.IdkResult<ByteArray?, com.sphereon.oauth2.server.authorization.error.AuthorizationServerError> =
        com.sphereon.core.api
            .Ok(null)
}
