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
import com.sphereon.oauth2.server.authorization.command.RevocationRequestData
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthentication
import com.sphereon.oauth2.server.authorization.command.revocation.HandleRevocationRequestArgs
import com.sphereon.oauth2.server.authorization.impl.command.revocation.HandleRevocationRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandleRevocationRequestCommandImplTest {
    private val ctx = OAuth2ServerTestContext("handle-revocation-test", this)

    @Test
    fun authenticatesAndDelegatesToRevokeToken() =
        runTest {
            var seenClientId: String? = null
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
                    parseRevocationRequestStub =
                        stubParseRevocationRequest {
                            Ok(RevocationRequestData(token = "rev-tok", clientId = "client-1"))
                        },
                    revokeTokenStub =
                        stubRevokeToken { args ->
                            seenClientId = args.clientId
                            Ok(Unit)
                        },
                )
            val command = HandleRevocationRequestCommandImpl(ctx.execution, service)

            val result =
                command.execute(
                    HandleRevocationRequestArgs(
                        requestBody = mapOf("token" to listOf("rev-tok")),
                        // "client-1:secret" Base64 encoded
                        requestHeaders = mapOf("Authorization" to "Basic Y2xpZW50LTE6c2VjcmV0"),
                        httpUrl = "https://as.example.com/revoke",
                    ),
                )

            assertTrue(result.isOk, "expected success but got ${if (!result.isOk) result.error else "ok"}")
            assertEquals("client-1", seenClientId)
        }

    @Test
    fun rejectsAnonymousRevocation() =
        runTest {
            val service = StubAuthorizationServerService()
            val command = HandleRevocationRequestCommandImpl(ctx.execution, service)

            val result =
                command.execute(
                    HandleRevocationRequestArgs(
                        requestBody = mapOf("token" to listOf("rev-tok")),
                        requestHeaders = emptyMap(),
                        httpUrl = "https://as.example.com/revoke",
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("UNAUTHORIZED", result.error.code)
        }
}
