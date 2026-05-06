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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.oauth2.common.command.VerifyDpopProofCommand
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.OAuth2ResponseMode
import com.sphereon.oauth2.common.model.ResponseType
import com.sphereon.oauth2.common.model.VerifyDpopProofOptions
import com.sphereon.oauth2.common.model.VerifyDpopProofResult
import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestData
import com.sphereon.oauth2.server.authorization.command.PushedAuthorizationResponse
import com.sphereon.oauth2.server.authorization.command.RequestUriData
import com.sphereon.oauth2.server.authorization.command.VerifiedAuthorizationRequest
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthentication
import com.sphereon.oauth2.server.authorization.command.par.HandlePushedAuthorizationRequestArgs
import com.sphereon.oauth2.server.authorization.impl.command.par.HandlePushedAuthorizationRequestCommandImpl
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandlePushedAuthorizationRequestCommandImplTest {
    private val ctx = OAuth2ServerTestContext("handle-par-test", this)

    /**
     * DPoP-verify stub that always rejects: tests in this file never present DPoP headers.
     * `HandlePushedAuthorizationRequestCommandImpl` only invokes the verifier when the inbound
     * request carries a `DPoP` header (none of these tests do), so the rejection branch is dead
     * code in this fixture and the stub exists purely to satisfy the constructor.
     */
    private val rejectingDpopVerify: VerifyDpopProofCommand =
        object : VerifyDpopProofCommand {
            override val inputTypeToken = typeToken<VerifyDpopProofOptions>()
            override val outputTypeToken = typeToken<VerifyDpopProofResult>()
            override val isEnabled = true

            override suspend fun execute(args: VerifyDpopProofOptions): IdkResult<VerifyDpopProofResult, IdkError> = Err(IdkError.fromString(code = "invalid_dpop_proof", message = "test stub"))
        }

    /**
     * Pass-through client-auth stub: PAR per RFC 9126 §2 requires the AS to authenticate the
     * client before parsing the request, so every PAR test must wire one. The orchestration
     * tests in this file are about the parse/verify/createRequestUri pipeline, not about client
     * auth itself; the stub returns success for whatever client_id was extracted so the pipeline
     * runs to completion.
     */
    private val acceptingClientAuth =
        stubVerifyClientAuthentication { args ->
            Ok(
                VerifiedClientAuthentication(
                    clientId = args.clientId,
                    method = ClientAuthenticationMethod.NONE,
                ),
            )
        }

    private fun parsedRequest() =
        AuthorizationRequestData(
            clientId = "client-1",
            redirectUri = "https://app.example.com/cb",
            responseType = listOf(ResponseType.CODE),
            state = "abc",
        )

    private fun verifiedRequest(parsed: AuthorizationRequestData) =
        VerifiedAuthorizationRequest(
            request = parsed,
            clientId = parsed.clientId,
            redirectUri = parsed.redirectUri ?: error("redirect required"),
            grantedScopes = emptyList(),
            pkceRequired = false,
            parRequired = false,
            responseMode = OAuth2ResponseMode.QUERY,
        )

    @Test
    fun runsParseVerifyAndCreatePipeline() =
        runTest {
            val parsed = parsedRequest()
            val verified = verifiedRequest(parsed)
            val service =
                StubAuthorizationServerService(
                    verifyClientAuthenticationStub = acceptingClientAuth,
                    parsePushedAuthorizationRequestStub = stubParsePushedAuthorizationRequest { Ok(parsed) },
                    verifyPushedAuthorizationRequestStub = stubVerifyPushedAuthorizationRequest { Ok(verified) },
                    createRequestUriStub =
                        stubCreateRequestUri {
                            Ok(
                                RequestUriData(
                                    requestUri = "urn:ietf:params:oauth:request_uri:abc",
                                    expiresIn = 90,
                                    authorizationRequest = verified,
                                ),
                            )
                        },
                    createPushedAuthorizationResponseStub =
                        stubCreatePushedAuthorizationResponse { args ->
                            Ok(PushedAuthorizationResponse(requestUri = args.requestUri, expiresIn = args.expiresIn))
                        },
                )
            val command = HandlePushedAuthorizationRequestCommandImpl(ctx.execution, service, rejectingDpopVerify)

            val result =
                command.execute(
                    HandlePushedAuthorizationRequestArgs(
                        requestBody =
                            mapOf(
                                "client_id" to listOf("client-1"),
                                "response_type" to listOf("code"),
                                "redirect_uri" to listOf("https://app.example.com/cb"),
                            ),
                        requestHeaders = emptyMap(),
                    ),
                )

            assertTrue(result.isOk, "expected success but got ${if (!result.isOk) result.error else "ok"}")
            assertEquals("urn:ietf:params:oauth:request_uri:abc", result.value.requestUri)
            assertEquals(90, result.value.expiresIn)
        }

    @Test
    fun propagatesParseError() =
        runTest {
            // PAR per RFC 9126 §2 authenticates the client BEFORE invoking the parser, so the
            // request body must carry a `client_id` that the accepting stub will admit. The test
            // intent is to assert that whatever the parse stage returns is propagated unchanged
            // to the caller; we simulate that with an explicit `invalid_request` from the parse
            // stub (e.g. missing `response_type`, malformed `request` JWT, etc.).
            val service =
                StubAuthorizationServerService(
                    verifyClientAuthenticationStub = acceptingClientAuth,
                    parsePushedAuthorizationRequestStub =
                        stubParsePushedAuthorizationRequest {
                            Err(IdkError.fromString(code = "invalid_request", message = "missing response_type"))
                        },
                )
            val command = HandlePushedAuthorizationRequestCommandImpl(ctx.execution, service, rejectingDpopVerify)

            val result =
                command.execute(
                    HandlePushedAuthorizationRequestArgs(
                        requestBody = mapOf("client_id" to listOf("client-1")),
                        requestHeaders = emptyMap(),
                    ),
                )

            assertTrue(result.isErr)
            assertEquals("invalid_request", result.error.code)
        }
}
