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

package com.sphereon.oauth2.server.authorization.impl.command.logout

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.query.percentEncodeQueryComponent
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.logout.CreateLogoutTokenArgs
import com.sphereon.oauth2.server.authorization.command.logout.CreateLogoutTokenCommand
import com.sphereon.oauth2.server.authorization.command.logout.HandleEndSessionRequestArgs
import com.sphereon.oauth2.server.authorization.command.logout.HandleEndSessionRequestCommand
import com.sphereon.oauth2.server.authorization.command.logout.LogoutOutcome
import com.sphereon.oauth2.server.authorization.command.logout.SendBackChannelLogoutArgs
import com.sphereon.oauth2.server.authorization.command.logout.SendBackChannelLogoutCommand
import com.sphereon.oauth2.server.authorization.impl.command.authorization.decodeIdTokenHintClaims
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.provider.FrontChannelLogoutIframe
import com.sphereon.oauth2.server.authorization.provider.LogoutPageContext
import com.sphereon.oauth2.server.authorization.provider.LogoutPageRenderer
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSession
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSessionStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * IDK [HandleEndSessionRequestCommand] implementation. Folds the four-step
 * RP-Initiated Logout 1.0 §2 flow into one orchestrator:
 *
 *  1. Resolve the active [OidcLoginSession] from the cookie-derived session id
 *     (preferred when both are present, since the cookie is the AS's
 *     authoritative session identifier; the `id_token_hint` `sid` claim is
 *     consulted as a fallback when the cookie is absent).
 *  2. Validate `post_logout_redirect_uri` against the resolved client's
 *     [ClientRegistration.postLogoutRedirectUris]. An unregistered URI is
 *     ignored (RP-Initiated §2: the AS MUST validate; mismatched values cause
 *     the AS to render its own logged-out page instead of redirecting).
 *  3. Fan out Front-Channel and Back-Channel logout to every RP that has both
 *     a participating session entry AND the relevant logout URI registered.
 *     Front-Channel iframes are passed to [LogoutPageRenderer]; Back-Channel
 *     deliveries fire `logout_token` POSTs through [SendBackChannelLogoutCommand].
 *  4. Revoke the [OidcLoginSession]; produce the [LogoutOutcome] (redirect or
 *     rendered page). Cookie clearing happens in the HTTP layer.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleEndSessionRequestCommand>())
class HandleEndSessionRequestCommandImpl(
    execution: SessionExecution,
    private val loginSessionStore: OidcLoginSessionStore,
    private val clientRegistry: ClientRegistry,
    private val logoutPageRenderer: LogoutPageRenderer,
    private val createLogoutTokenCommand: CreateLogoutTokenCommand,
    private val sendBackChannelLogoutCommand: SendBackChannelLogoutCommand,
) : TypedServiceCommandAdapter<HandleEndSessionRequestArgs, LogoutOutcome, IdkError>(
        commandId = HandleEndSessionRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleEndSessionRequestArgs>(),
        outputTypeToken = typeToken<LogoutOutcome>(),
    ),
    HandleEndSessionRequestCommand {
    override val commandId: String get() = HandleEndSessionRequestCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is HandleEndSessionRequestArgs

    override suspend fun doExecute(
        args: HandleEndSessionRequestArgs,
        applyDuring: (HandleEndSessionRequestArgs) -> HandleEndSessionRequestArgs,
    ): IdkResult<LogoutOutcome, IdkError> {
        val applied = applyDuring(args)

        // 1. Resolve the active session. Cookie is preferred over id_token_hint.sid because
        // the cookie reflects the live state on the AS; the id_token_hint may be stale.
        val hintClaims =
            applied.idTokenHint?.let { decodeIdTokenHintClaims(it, expectedIssuer = applied.baseUrl) }
        val resolvedSessionId = applied.currentLoginSessionId ?: hintClaims?.sid
        val session: OidcLoginSession? = resolvedSessionId?.let { id -> loginSessionStore.findById(id).getOrNull() }

        // 2. Resolve the client (for redirect validation + selecting which RPs to notify).
        // The id_token_hint.aud takes precedence; client_id is a fallback per RP-Initiated §2.
        val claimedClientId = hintClaims?.aud ?: applied.clientId
        val claimedClient: ClientRegistration? = claimedClientId?.let { id -> clientRegistry.getClient(id).getOrNull() }

        // 3. Validate post_logout_redirect_uri exact-match against the resolved client.
        // RP-Initiated §2: an unregistered value is treated as absent (no redirect, page only).
        val requestedRedirectUri = applied.postLogoutRedirectUri
        val validatedRedirectUri =
            when {
                requestedRedirectUri == null -> {
                    null
                }

                claimedClient == null -> {
                    execution.log.warn(
                        "RP-Initiated Logout: post_logout_redirect_uri provided but no client " +
                            "could be resolved (id_token_hint=${applied.idTokenHint != null}, " +
                            "client_id=${applied.clientId}); rendering logged-out page instead",
                    )
                    null
                }

                requestedRedirectUri in claimedClient.postLogoutRedirectUris -> {
                    requestedRedirectUri
                }

                else -> {
                    execution.log.warn(
                        "RP-Initiated Logout: post_logout_redirect_uri '$requestedRedirectUri' " +
                            "is not registered for client '${claimedClient.clientId}'; rendering logged-out page instead",
                    )
                    null
                }
            }

        // 4. Build the FC iframes + drive BC fan-out for participating RPs.
        val iframes = mutableListOf<FrontChannelLogoutIframe>()
        if (session != null) {
            for ((clientId, sid) in session.rpSessions) {
                val rp = clientRegistry.getClient(clientId).getOrNull() ?: continue
                buildFrontChannelIframe(rp, applied.baseUrl, sid)?.let { iframes.add(it) }

                val bcUri = rp.backchannelLogoutUri
                if (!bcUri.isNullOrBlank()) {
                    sendBackChannelLogout(
                        issuer = applied.baseUrl,
                        sub = session.sub,
                        sid = if (rp.backchannelLogoutSessionRequired) sid else null,
                        clientId = rp.clientId,
                        backchannelLogoutUri = bcUri,
                    )
                }
            }
        }

        // 5. Revoke the session.
        if (resolvedSessionId != null) {
            val revoked = loginSessionStore.revoke(resolvedSessionId)
            if (revoked.isErr) {
                execution.log.warn(
                    "RP-Initiated Logout: failed to revoke OidcLoginSession '$resolvedSessionId': " +
                        revoked.error.message.defaultMessage,
                )
            }
        }

        // 6. Compose the final outcome. When there are FC iframes we MUST render the page so
        // the iframes get a chance to load; the meta-refresh / JS fallback then redirects to
        // the validated post_logout_redirect_uri (if any). When there are no iframes AND a
        // valid redirect URI, we 302 directly. Otherwise we render a confirmation page.
        val state = applied.state
        val redirectLocation =
            validatedRedirectUri?.let { uri ->
                if (state != null) {
                    val sep = if (uri.contains('?')) '&' else '?'
                    "$uri${sep}state=${percentEncodeQueryComponent(state)}"
                } else {
                    uri
                }
            }

        return if (iframes.isEmpty() && redirectLocation != null) {
            Ok(LogoutOutcome.Redirect(location = redirectLocation))
        } else {
            val ctx =
                LogoutPageContext(
                    iframes = iframes,
                    postLogoutLocation = redirectLocation,
                    locale = "en",
                )
            val page = logoutPageRenderer.render(ctx)
            if (page.isOk) {
                Ok(
                    LogoutOutcome.RenderPage(
                        html = page.value.html,
                        contentType = page.value.contentType,
                    ),
                )
            } else {
                // Renderer failure is a deployment bug; surface a minimal fallback so the
                // client at least gets a 200 instead of a 500 that loops the conformance suite.
                execution.log.warn(
                    "LogoutPageRenderer failed: ${page.error.message.defaultMessage}; falling back to minimal HTML",
                )
                Ok(
                    LogoutOutcome.RenderPage(
                        html = MINIMAL_FALLBACK_HTML,
                    ),
                )
            }
        }
    }

    private fun buildFrontChannelIframe(
        client: ClientRegistration,
        issuer: String,
        sid: String,
    ): FrontChannelLogoutIframe? {
        val baseUri = client.frontchannelLogoutUri?.takeIf { it.isNotBlank() } ?: return null
        val url =
            if (client.frontchannelLogoutSessionRequired) {
                val sep = if (baseUri.contains('?')) '&' else '?'
                "$baseUri${sep}iss=${percentEncodeQueryComponent(issuer)}&sid=${percentEncodeQueryComponent(sid)}"
            } else {
                baseUri
            }
        return FrontChannelLogoutIframe(clientId = client.clientId, iframeUrl = url)
    }

    private suspend fun sendBackChannelLogout(
        issuer: String,
        sub: String,
        sid: String?,
        clientId: String,
        backchannelLogoutUri: String,
    ) {
        val tokenResult =
            createLogoutTokenCommand.execute(
                CreateLogoutTokenArgs(
                    issuer = issuer,
                    clientId = clientId,
                    sub = sub,
                    sid = sid,
                ),
            )
        if (tokenResult.isErr) {
            execution.log.warn(
                "Back-Channel Logout: failed to mint logout_token for $clientId: " +
                    tokenResult.error.message.defaultMessage,
            )
            return
        }
        sendBackChannelLogoutCommand.execute(
            SendBackChannelLogoutArgs(
                backchannelLogoutUri = backchannelLogoutUri,
                logoutToken = tokenResult.value.value,
                clientId = clientId,
            ),
        )
    }

    private companion object {
        const val MINIMAL_FALLBACK_HTML: String =
            "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Signed out</title></head>" +
                "<body><p>You have been signed out.</p></body></html>"
    }
}
