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

package com.sphereon.oauth2.server.authorization.impl.command.orchestration

import com.sphereon.core.api.Ok
import com.sphereon.oauth2.common.model.TokenIntrospectionResponse
import com.sphereon.oauth2.server.authorization.command.IntrospectionRequestData
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthentication
import com.sphereon.oauth2.server.authorization.command.introspection.HandleIntrospectionRequestArgs
import com.sphereon.oauth2.server.authorization.impl.command.introspection.HandleIntrospectionRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandleIntrospectionRequestCommandImplTest {
    private val ctx = OAuth2ServerTestContext("handle-introspection-test", this)

    private fun basicAuthHeaders(): Map<String, String> =
        mapOf(
            // "client-1:secret" Base64 encoded
            "Authorization" to "Basic Y2xpZW50LTE6c2VjcmV0",
        )

    @Test
    fun authenticatesAndDelegatesToIntrospectToken() =
        runTest {
            var seenClientId: String? = null
            var seenToken: String? = null
            val service =
                StubAuthorizationServerService(
                    verifyClientAuthenticationStub =
                        stubVerifyClientAuthentication {
                            Ok(
                                VerifiedClientAuthentication(
                                    clientId = it.clientId,
                                    method = com.sphereon.oauth2.common.model.ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                                ),
                            )
                        },
                    parseIntrospectionRequestStub =
                        stubParseIntrospectionRequest {
                            Ok(IntrospectionRequestData(token = "abc", tokenTypeHint = null, clientId = "client-1"))
                        },
                    introspectTokenStub =
                        stubIntrospectToken { args ->
                            seenClientId = args.clientId
                            seenToken = args.token
                            Ok(TokenIntrospectionResponse(active = true, sub = "user"))
                        },
                )
            val command = HandleIntrospectionRequestCommandImpl(ctx.execution, service)

            val result =
                command.execute(
                    HandleIntrospectionRequestArgs(
                        requestBody = mapOf("token" to listOf("abc")),
                        requestHeaders = basicAuthHeaders(),
                        httpUrl = "https://as.example.com/introspect",
                    ),
                )

            assertTrue(result.isOk, "expected success but got ${if (!result.isOk) result.error else "ok"}")
            assertEquals(true, result.value.active)
            assertEquals("client-1", seenClientId)
            assertEquals("abc", seenToken)
        }

    @Test
    fun rejectsMissingClientAuthentication() =
        runTest {
            val service = StubAuthorizationServerService()
            val command = HandleIntrospectionRequestCommandImpl(ctx.execution, service)

            val result =
                command.execute(
                    HandleIntrospectionRequestArgs(
                        // No Authorization header, no client_id, no client_secret in body — anonymous request
                        requestBody = mapOf("token" to listOf("abc")),
                        requestHeaders = emptyMap(),
                        httpUrl = "https://as.example.com/introspect",
                    ),
                )

            assertTrue(result.isErr, "anonymous introspection request must be rejected")
            assertEquals("UNAUTHORIZED", result.error.code)
        }
}
