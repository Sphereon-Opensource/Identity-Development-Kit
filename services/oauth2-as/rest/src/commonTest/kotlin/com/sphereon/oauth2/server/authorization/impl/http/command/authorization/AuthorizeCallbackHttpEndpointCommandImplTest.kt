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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.oauth2.common.model.OAuth2ResponseMode
import com.sphereon.oauth2.server.authorization.command.AuthorizationResponseData
import com.sphereon.oauth2.server.authorization.command.authorization.HandleAuthorizeCallbackArgs
import com.sphereon.oauth2.server.authorization.command.authorization.HandleAuthorizeCallbackCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.http.command.TestMutableOidcLoginSessionIdProvider
import com.sphereon.oauth2.server.authorization.impl.http.command.TestOAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.impl.http.command.TestSessionExecution
import com.sphereon.oauth2.server.authorization.impl.http.requiredaction.RequiredActionsRedirectHandler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthorizeCallbackHttpEndpointCommandImplTest {
    private class FakeCallbackCommand(
        private val handler: suspend (HandleAuthorizeCallbackArgs) -> IdkResult<AuthorizationResponseData, IdkError>,
    ) : HandleAuthorizeCallbackCommand {
        override val commandId: String get() = HandleAuthorizeCallbackCommand.COMMAND_ID
        override val inputTypeToken get() = typeToken<HandleAuthorizeCallbackArgs>()
        override val outputTypeToken get() = typeToken<AuthorizationResponseData>()
        override val isEnabled: Boolean = true

        override suspend fun supports(args: Any): Boolean = args is HandleAuthorizeCallbackArgs

        override suspend fun execute(args: HandleAuthorizeCallbackArgs): IdkResult<AuthorizationResponseData, IdkError> = handler(args)
    }

    @Test
    fun queryMode_returns302WithLocation() =
        runTest {
            val command =
                AuthorizeCallbackHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    loginSessionIdProvider = TestMutableOidcLoginSessionIdProvider(),
                    configProvider = TestOAuth2ServersConfigProvider(),
                    requiredActionsRedirectHandler = NoopRedirectHandler,
                    handleAuthorizeCallbackCommand =
                        FakeCallbackCommand {
                            Ok(
                                AuthorizationResponseData(
                                    code = "auth-code",
                                    state = "state-1",
                                    redirectUri = "https://client.example/cb?code=auth-code&state=state-1",
                                    responseMode = OAuth2ResponseMode.QUERY,
                                ),
                            )
                        },
                )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/authorize/callback",
                    queryParameters = mapOf("session_id" to "sess-123"),
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(302, result.value.statusCode)
            assertEquals("https://client.example/cb?code=auth-code&state=state-1", result.value.headers["Location"])
        }

    @Test
    fun formPostMode_returns200WithHtml() =
        runTest {
            val command =
                AuthorizeCallbackHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    loginSessionIdProvider = TestMutableOidcLoginSessionIdProvider(),
                    configProvider = TestOAuth2ServersConfigProvider(),
                    requiredActionsRedirectHandler = NoopRedirectHandler,
                    handleAuthorizeCallbackCommand =
                        FakeCallbackCommand {
                            Ok(
                                AuthorizationResponseData(
                                    code = "auth-code",
                                    state = null,
                                    redirectUri = "https://client.example/cb",
                                    responseMode = OAuth2ResponseMode.FORM_POST,
                                    formPostHtml = "<html>auto-post</html>",
                                ),
                            )
                        },
                )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/authorize/callback",
                    queryParameters = mapOf("session_id" to "sess-x"),
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(200, result.value.statusCode)
            assertEquals("text/html;charset=UTF-8", result.value.headers["Content-Type"])
            assertEquals("<html>auto-post</html>", result.value.body)
        }

    @Test
    fun missingSessionId_returns400() =
        runTest {
            val command =
                AuthorizeCallbackHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    loginSessionIdProvider = TestMutableOidcLoginSessionIdProvider(),
                    configProvider = TestOAuth2ServersConfigProvider(),
                    requiredActionsRedirectHandler = NoopRedirectHandler,
                    handleAuthorizeCallbackCommand = FakeCallbackCommand { error("Should not be called") },
                )

            val request = GenericHttpRequest(method = "GET", path = "/authorize/callback")
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(400, result.value.statusCode)
            assertTrue(result.value.body!!.contains("session_id"))
        }

    @Test
    fun serviceError_invalidRequest_returns400() =
        runTest {
            val command =
                AuthorizeCallbackHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    loginSessionIdProvider = TestMutableOidcLoginSessionIdProvider(),
                    configProvider = TestOAuth2ServersConfigProvider(),
                    requiredActionsRedirectHandler = NoopRedirectHandler,
                    handleAuthorizeCallbackCommand =
                        FakeCallbackCommand { Err(IdkError.fromString(code = "invalid_request", message = "session not found")) },
                )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/authorize/callback",
                    queryParameters = mapOf("session_id" to "missing"),
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(400, result.value.statusCode)
        }

    @Test
    fun requiredActionsPending_dispatchesToRedirectHandler_andReturnsItsResponse() =
        runTest {
            // Production wire-shape: the upstream typed error is mapped via
            // IdkError.fromDTO before reaching this endpoint, so the handler dispatches off
            // error.code + error.meta — not the runtime class.
            val redirectHandler =
                RecordingRedirectHandler(
                    response =
                        GenericHttpResponse(
                            statusCode = 302,
                            headers = mapOf("Location" to "https://as.example.org/idv/run/exec-9?session_id=sess-pending"),
                            body = "",
                        ),
                )
            val command =
                AuthorizeCallbackHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    loginSessionIdProvider = TestMutableOidcLoginSessionIdProvider(),
                    configProvider = TestOAuth2ServersConfigProvider(),
                    requiredActionsRedirectHandler = redirectHandler,
                    handleAuthorizeCallbackCommand =
                        FakeCallbackCommand {
                            Err(
                                requiredActionsError(
                                    ids = listOf("must-change-password"),
                                    labels = listOf("Change your password"),
                                    metas = listOf(mapOf("policy_id" to "scheduled")),
                                )
                            )
                        },
                )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/authorize/callback",
                    queryParameters = mapOf("session_id" to "sess-pending"),
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(302, result.value.statusCode)
            assertEquals(
                "https://as.example.org/idv/run/exec-9?session_id=sess-pending",
                result.value.headers["Location"],
            )
            assertEquals("sess-pending", redirectHandler.lastSessionId, "handler must receive the session id")
            assertEquals("interaction_required", redirectHandler.lastError?.code, "handler must receive the wire-shape error")
        }

    @Test
    fun requiredActionsPending_handlerReturnsNull_fallsBackToErrorJson() =
        runTest {
            // Configuration drift: no provider mapped any of the unmet actions, so the strategy
            // returned Err and the handler returned null. The endpoint must surface a valid OIDC
            // error response — NOT a silent 200.
            val command =
                AuthorizeCallbackHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    loginSessionIdProvider = TestMutableOidcLoginSessionIdProvider(),
                    configProvider = TestOAuth2ServersConfigProvider(),
                    requiredActionsRedirectHandler = NoopRedirectHandler,
                    handleAuthorizeCallbackCommand =
                        FakeCallbackCommand {
                            Err(
                                requiredActionsError(
                                    ids = listOf("enroll-mfa"),
                                    labels = listOf("Set up two-factor"),
                                )
                            )
                        },
                )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/authorize/callback",
                    queryParameters = mapOf("session_id" to "sess-fallback"),
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            // Today the endpoint maps any non-`invalid_request` error to 500/server_error;
            // the fallback path is exercised — the body carries the original code so callers
            // can still distinguish it from a genuine 5xx if they look at the JSON.
            assertTrue(
                result.value.statusCode in setOf(400, 500),
                "fallback must surface an OIDC error status, not a silent 200"
            )
        }

    @Test
    fun requiredActionsPending_handlerReceivesReconstructedActionsWithMetadata() =
        runTest {
            // The error payload zips actionIds + labels + metadata index-aligned in the meta
            // map; the handler must see the same shape the evaluator emitted (so the strategy
            // can read e.g. `required_version` for accept-terms).
            val redirectHandler = RecordingRedirectHandler(response = null)
            val command =
                AuthorizeCallbackHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    loginSessionIdProvider = TestMutableOidcLoginSessionIdProvider(),
                    configProvider = TestOAuth2ServersConfigProvider(),
                    requiredActionsRedirectHandler = redirectHandler,
                    handleAuthorizeCallbackCommand =
                        FakeCallbackCommand {
                            Err(
                                requiredActionsError(
                                    ids = listOf("accept-terms", "must-change-password"),
                                    labels = listOf("Accept ToS", "Change password"),
                                    metas =
                                        listOf(
                                            mapOf("required_version" to "2026-04-01"),
                                            mapOf("policy_id" to "rotate-90d"),
                                        ),
                                )
                            )
                        },
                )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/authorize/callback",
                    queryParameters = mapOf("session_id" to "sess-meta"),
                )
            command.execute(request)

            val received = redirectHandler.lastError ?: kotlin.error("handler should have been called")

            @Suppress("UNCHECKED_CAST")
            val ids = received.meta["required_action_ids"] as List<String>

            @Suppress("UNCHECKED_CAST")
            val labels = received.meta["required_action_labels"] as List<String>

            @Suppress("UNCHECKED_CAST")
            val metas = received.meta["required_action_metadata"] as List<Map<String, String>>
            assertEquals(listOf("accept-terms", "must-change-password"), ids)
            assertEquals(listOf("Accept ToS", "Change password"), labels)
            assertEquals("2026-04-01", metas[0]["required_version"])
            assertEquals("rotate-90d", metas[1]["policy_id"])
        }

    @Test
    fun requiredActionsPending_typedSourcePathDispatchesWithoutMetaInspection() =
        runTest {
            // Production wire shape: the gate returns Err(AuthorizationServerError.RequiredActionsPending(...))
            // and CreateAuthorizationCodeCommandImpl maps it via IdkError.fromDTO, which now
            // preserves the typed source on `IdkError.source`. The handler MUST recover the
            // typed payload directly — no need to inspect `meta`. We prove it by constructing
            // an IdkError WITHOUT the meta keys; the handler still resolves the actions because
            // `sourceAs<RequiredActionsPending>()` returns the typed payload.
            val typed =
                AuthorizationServerError.RequiredActionsPending(
                    actionIds = listOf("must-change-password", "accept-terms"),
                    actionLabels = listOf("Change password", "Accept ToS"),
                    actionMetadata =
                        listOf(
                            mapOf("policy_id" to "rotate-90d"),
                            mapOf("required_version" to "2026-04-01"),
                        ),
                )
            val wrapped = IdkError.fromDTO(typed)
            // Sanity: meta is populated by RequiredActionsPending's `meta` override too, so
            // strip it to prove the typed path is what's running. (The handler would short-
            // circuit on `code != REQUIRED_ACTIONS_ERROR_CODE` if it were forced into the
            // wire-shape branch.)
            val errorWithoutMeta =
                IdkError(
                    code = wrapped.code,
                    message = wrapped.message,
                    meta = emptyMap(),
                    source = typed,
                )

            val redirectHandler =
                RecordingRedirectHandler(
                    response =
                        GenericHttpResponse(
                            statusCode = 302,
                            headers = mapOf("Location" to "https://as.example.org/idv/run/exec-typed?session_id=sess-typed"),
                            body = "",
                        ),
                )
            val command =
                AuthorizeCallbackHttpEndpointCommandImpl(
                    execution = TestSessionExecution(),
                    loginSessionIdProvider = TestMutableOidcLoginSessionIdProvider(),
                    configProvider = TestOAuth2ServersConfigProvider(),
                    requiredActionsRedirectHandler = redirectHandler,
                    handleAuthorizeCallbackCommand = FakeCallbackCommand { Err(errorWithoutMeta) },
                )

            val request =
                GenericHttpRequest(
                    method = "GET",
                    path = "/authorize/callback",
                    queryParameters = mapOf("session_id" to "sess-typed"),
                )
            val result = command.execute(request)

            assertTrue(result.isOk)
            assertEquals(302, result.value.statusCode, "typed-source dispatch must succeed even with empty meta")
            assertEquals(
                "https://as.example.org/idv/run/exec-typed?session_id=sess-typed",
                result.value.headers["Location"],
            )
        }

    /**
     * Reproduces the wire-shape that
     * `AuthorizationServerError.RequiredActionsPending` collapses into via `IdkError.fromDTO`
     * upstream of the HTTP layer. Constants in [com.sphereon.oauth2.server.authorization.impl.http.requiredaction.DefaultRequiredActionsRedirectHandler]
     * MUST stay in sync with these strings.
     */
    private fun requiredActionsError(
        ids: List<String>,
        labels: List<String>,
        metas: List<Map<String, String>> = emptyList(),
    ): IdkError =
        IdkError(
            code = "interaction_required",
            message =
                IdkError.Message(
                    i18nKey = "oauth2.as.error.required_actions_pending",
                    defaultMessage = "Required actions pending: ${ids.joinToString(", ")}",
                ),
            meta =
                mapOf(
                    "required_action_ids" to ids,
                    "required_action_labels" to labels,
                    "required_action_metadata" to metas,
                ),
        )
}

/**
 * Default no-op redirect handler used by the legacy tests — always returns null so the
 * endpoint falls through to its existing JSON error path. Tests of new behavior pass a
 * [RecordingRedirectHandler] instead.
 */
private object NoopRedirectHandler : RequiredActionsRedirectHandler {
    override suspend fun handle(
        error: IdkError,
        sessionId: String,
        baseUrl: String,
    ): GenericHttpResponse? = null
}

private class RecordingRedirectHandler(
    private val response: GenericHttpResponse?,
) : RequiredActionsRedirectHandler {
    var lastError: IdkError? = null
    var lastSessionId: String? = null

    override suspend fun handle(
        error: IdkError,
        sessionId: String,
        baseUrl: String,
    ): GenericHttpResponse? {
        lastError = error
        lastSessionId = sessionId
        return response
    }
}
