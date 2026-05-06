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

package com.sphereon.oauth2.server.authorization.impl.command.authorization

import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.OAuth2ResponseMode
import com.sphereon.oauth2.server.authorization.command.AuthorizationRequestData
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry

/**
 * Result of the client + grant-type + redirect-URI + response-mode resolution pass. The
 * [Trusted] variant returns the full trusted context; [RejectPreRedirect] carries the
 * [AuthorizationServerError] that both the verifier and the HTTP adapter should surface to
 * the caller as a pre-redirect (JSON) response — the redirect URI cannot be trusted at this
 * point, so emitting an HTTP redirect to the client-supplied URI would be unsafe.
 */
public sealed class RedirectResolution {
    data class Trusted(
        val client: ClientRegistration,
        val redirectUri: String,
        val state: String?,
        val responseMode: OAuth2ResponseMode,
    ) : RedirectResolution()

    data class RejectPreRedirect(
        val error: AuthorizationServerError
    ) : RedirectResolution()
}

/**
 * Run the pre-redirect-eligible validation pass: client lookup, grant-type allowance, redirect
 * URI validation (with single-registered-URI resolution per RFC 6749 §3.1.2.3 / OIDC §3.1.2.1),
 * and response-mode parsing. Consumed by both [VerifyAuthorizationRequestCommandImpl] (which
 * continues with response-type, PKCE, scope, etc.) and the authorization endpoint handler (which
 * uses the trusted context to route subsequent errors as redirects).
 *
 * Failures here are **always pre-redirect**: the redirect URI is not yet trustworthy. Downstream
 * checks (response-type against metadata, PKCE policy, scope, response_mode semantics) happen
 * after this resolver succeeds and yield post-redirect errors.
 */
public suspend fun resolveTrustedRedirect(
    parsed: AuthorizationRequestData,
    clientRegistry: ClientRegistry,
    serversConfigProvider: OAuth2ServersConfigProvider,
): RedirectResolution {
    // ── Client lookup (registry → permissive public-client fallback) ───────
    val clientLookup = clientRegistry.getClient(parsed.clientId)
    if (!clientLookup.isOk) {
        return RedirectResolution.RejectPreRedirect(
            AuthorizationServerError.ServerError(
                details = "Failed to retrieve client registration: ${clientLookup.error}",
                exception = null,
            ),
        )
    }
    val client =
        clientLookup.value
            ?: resolvePublicClientFallback(parsed.clientId, serversConfigProvider)
            ?: return RedirectResolution.RejectPreRedirect(
                AuthorizationServerError.UnauthorizedClient(clientId = parsed.clientId),
            )

    // ── Grant-type allowance ───────────────────────────────────────────────
    if (GrantType.AUTHORIZATION_CODE !in client.grantTypes) {
        return RedirectResolution.RejectPreRedirect(
            AuthorizationServerError.UnauthorizedClient(clientId = parsed.clientId),
        )
    }

    // ── Redirect URI (RFC 6749 §3.1.2.3 / OIDC §3.1.2.1) ───────────────────
    val requestedRedirect = parsed.redirectUri?.takeIf { it.isNotBlank() }
    val resolvedRedirectUri: String =
        when {
            requestedRedirect != null -> {
                if (client.redirectUris.isEmpty()) {
                    // Permissive fallback (empty redirectUris on a synthesised public client)
                    // accepts any explicit URI; normal registered clients always have a URI list.
                    requestedRedirect
                } else if (matchesRegisteredRedirectUri(requestedRedirect, client.redirectUris)) {
                    requestedRedirect
                } else {
                    return RedirectResolution.RejectPreRedirect(
                        AuthorizationServerError.InvalidRequest(
                            details = "redirect_uri does not match any registered redirect URI for this client",
                        ),
                    )
                }
            }

            // Omitted redirect_uri: OK iff exactly one registered URI.
            client.redirectUris.size == 1 -> {
                client.redirectUris.first()
            }

            client.redirectUris.isNotEmpty() -> {
                return RedirectResolution.RejectPreRedirect(
                    AuthorizationServerError.InvalidRequest(
                        details = "redirect_uri is required when client has multiple registered redirect URIs",
                    ),
                )
            }

            else -> {
                return RedirectResolution.RejectPreRedirect(
                    AuthorizationServerError.InvalidRequest(details = "redirect_uri is required"),
                )
            }
        }

    // ── response_mode (wire-string → enum + OIDC Core §3.1.2.1 default) ────
    // The bare JARM `jwt` mode resolves to `query.jwt` for response_type=code and `fragment.jwt`
    // otherwise, per OIDF JARM §2.1.
    val responseMode =
        if (parsed.responseMode.isNullOrBlank()) {
            OAuth2ResponseMode.defaultFor(parsed.responseType)
        } else {
            val parsedMode =
                OAuth2ResponseMode.parse(parsed.responseMode)
                    ?: return RedirectResolution.RejectPreRedirect(
                        AuthorizationServerError.InvalidRequest(details = "Unsupported response_mode: ${parsed.responseMode}"),
                    )
            OAuth2ResponseMode.resolveJarmCarrier(parsedMode, parsed.responseType)
        }

    return RedirectResolution.Trusted(
        client = client,
        redirectUri = resolvedRedirectUri,
        state = parsed.state,
        responseMode = responseMode,
    )
}

/**
 * Match a requested redirect URI against the client's registered URIs per RFC 6749 §3.1.2.2.
 *
 * Two acceptance modes, in order:
 *  1. **Strict simple-string match** — the requested URI is byte-equal to a registered URI.
 *     Required by FAPI2-SP §5.3.2.2 / OIDC Core §3.1.2.1 for clients that pre-register the
 *     complete URI (query string included).
 *  2. **Scheme + authority + path match with dynamic query** — the requested URI's
 *     scheme, host, port, and path equal those of a registered URI **whose query is empty**,
 *     and additional query parameters on the requested URI are accepted. RFC 6749 §3.1.2.2
 *     last paragraph: "the authorization server MAY ignore additional query components in the
 *     redirection URI." Required by clients that pass per-request state through query
 *     parameters (e.g., the OIDF conformance suite's dummy-parameter probe at PAR).
 *
 * Fragment components (`#…`) on a redirect URI are forbidden by RFC 6749 §3.1.2 — entries with
 * a fragment are rejected at the source by the client registry, so this matcher does not need
 * to special-case them.
 */
internal fun matchesRegisteredRedirectUri(
    requested: String,
    registered: List<String>,
): Boolean {
    if (requested in registered) return true
    val requestedParts = splitUriIntoOriginPathQuery(requested) ?: return false
    return registered.any { reg ->
        val regParts = splitUriIntoOriginPathQuery(reg) ?: return@any false
        regParts.query.isEmpty() &&
            regParts.scheme.equals(requestedParts.scheme, ignoreCase = true) &&
            regParts.authority.equals(requestedParts.authority, ignoreCase = true) &&
            regParts.path == requestedParts.path
    }
}

private data class UriParts(
    val scheme: String,
    val authority: String,
    val path: String,
    val query: String,
)

private fun splitUriIntoOriginPathQuery(uri: String): UriParts? {
    val schemeIdx = uri.indexOf("://")
    if (schemeIdx <= 0) return null
    val scheme = uri.substring(0, schemeIdx)
    val rest = uri.substring(schemeIdx + 3)
    val pathStart = rest.indexOf('/').let { if (it < 0) rest.length else it }
    val authority = rest.substring(0, pathStart)
    val pathAndQuery = rest.substring(pathStart)
    val queryIdx = pathAndQuery.indexOf('?')
    val path = if (queryIdx < 0) pathAndQuery else pathAndQuery.substring(0, queryIdx)
    val query = if (queryIdx < 0) "" else pathAndQuery.substring(queryIdx + 1)
    return UriParts(scheme = scheme, authority = authority, path = path, query = query)
}
