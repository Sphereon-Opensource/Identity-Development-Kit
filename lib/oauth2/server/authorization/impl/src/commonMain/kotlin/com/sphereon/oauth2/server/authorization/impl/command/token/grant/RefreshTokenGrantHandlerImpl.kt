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

package com.sphereon.oauth2.server.authorization.impl.command.token.grant

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.server.authorization.audit.OAuth2AuditEmitter
import com.sphereon.oauth2.server.authorization.audit.OAuth2AuditEventType
import com.sphereon.oauth2.server.authorization.command.CreateAccessTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateRefreshTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateTokenResponseArgs
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.VerifyRefreshTokenGrantArgs
import com.sphereon.oauth2.server.authorization.command.token.GrantContext
import com.sphereon.oauth2.server.authorization.command.token.GrantHandler
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.command.token.VerifyRefreshTokenGrantCommandImpl
import com.sphereon.oauth2.server.authorization.storage.TokenStorage
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * RFC 6749 §6 / RFC 6819 §5.2.2.3 / OIDC Core 1.0 §12 refresh-token grant handler.
 *
 * Verifies the presented refresh token, optionally rotates it (atomically marking the consumed
 * token used+revoked through [TokenStorage.consumeRefreshToken]), and reissues an access token
 * plus a fresh id_token when the original chain carried `openid`. Enforces RFC 9449 §10.1
 * proof-jkt continuity: a refresh chain originally bound to a DPoP key MUST be re-presented with
 * a proof from that same key.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<GrantHandler>())
class RefreshTokenGrantHandlerImpl(
    private val tokenStorage: TokenStorage,
    private val auditEmitter: OAuth2AuditEmitter,
) : GrantHandler {
    override val grantType: String = GrantType.REFRESH_TOKEN.value

    override fun supports(params: GrantParameters): Boolean = params is GrantParameters.RefreshToken

    override suspend fun handle(
        params: GrantParameters,
        context: GrantContext,
    ): IdkResult<TokenResponse, IdkError> {
        val rtParams = params as GrantParameters.RefreshToken
        val tokenRequest = context.tokenRequest
        val applied = context.applied
        val commands = context.commands
        val proofJkt = context.proofJkt
        val certThumbprint = context.certThumbprintS256
        val serverConfig = context.serverConfig

        // RFC 6819 §5.2.2.3 + RFC 6749 §6: refresh-token rotation. When enabled
        // (default), the consumed refresh token is atomically marked used+revoked
        // via TokenStorage.consumeRefreshToken so a replay surfaces invalid_grant
        // through the verifier's revoked-flag check on the next call. Verify and
        // consume must be atomic in any persistent backing store: a concurrent
        // pair of refreshes that both pass verify before either reaches consume
        // would otherwise both succeed. The in-memory store collapses the two
        // calls into a single atomic update; durable backings must preserve that
        // contract.
        val rotateRefreshToken = serverConfig.refreshTokenRotation

        val verifyResult =
            commands.verifyRefreshTokenGrant
                .execute(
                    VerifyRefreshTokenGrantArgs(
                        refreshToken = rtParams.refreshToken,
                        clientId = tokenRequest.clientId,
                        clientInstanceKeyJkt = context.clientInstanceKeyJkt,
                        requestedScope = rtParams.scope,
                        requestedResource = rtParams.resource,
                    ),
                )
        if (!verifyResult.isOk) {
            // RFC 6749 §10.4 / OAuth 2.1 reuse detection. The verifier sets a structured meta
            // marker on the InvalidGrant when the rejection reason is the revoked-flag
            // (i.e. the chain was previously rotated and the consumed token is being replayed).
            // We surface this as a distinct ERROR-severity audit event so SIEM rules can alert
            // on it separately from generic invalid_grant noise — reuse means either an
            // attacker captured the token before rotation or the client is broken; both warrant
            // operator attention. The wire response stays `invalid_grant` to avoid leaking the
            // detection signal back to the caller.
            //
            // The verifier wraps its AuthorizationServerError into an IdkError via
            // `IdkError.fromDTO`, which copies the source `meta` map across (see Error.kt
            // `fromDTO`). We therefore check meta on the resulting IdkError directly rather
            // than down-casting back to InvalidGrant.
            val error = verifyResult.error
            val isReuseDetection =
                error.code == "invalid_grant" &&
                    error.meta[VerifyRefreshTokenGrantCommandImpl.REUSE_DETECTED_META_KEY] == true
            if (isReuseDetection) {
                auditEmitter.emit(
                    type = OAuth2AuditEventType.REFRESH_TOKEN_REUSE_DETECTED,
                    clientId = tokenRequest.clientId,
                    metadata = mapOf("grant_type" to GrantType.REFRESH_TOKEN.value),
                    errorCode = error.code,
                    errorMessage = error.message.defaultMessage,
                )
            }
            return Err(error)
        }
        val verified = verifyResult.value

        // RFC 9449 §5: refresh-token rotation. Public clients MUST re-present the same
        // DPoP key for the entire chain (the AS pinned the refresh token to the original
        // proof's jkt, so any other key is `invalid_dpop_proof`). Confidential clients
        // (anything that authenticates beyond `none` — Basic, Post, SecretJwt,
        // PrivateKeyJwt, AttestationJwt, MutualTls) MAY rotate the DPoP key on refresh
        // because client identity is already established by the client-auth credential;
        // FAPI2 conformance's `…-refresh-token` test relies on this and presents a fresh
        // DPoP key on the refresh request. In that case we rebind the new access (and
        // rotated refresh) token to the freshly presented key.
        val storedJkt = verified.dpopJkt
        val isPublicClient = tokenRequest.clientAuthentication is ClientAuthenticationConfig.None
        if (isPublicClient && storedJkt != null && proofJkt != null && storedJkt != proofJkt) {
            return invalidDpopProof("DPoP proof thumbprint does not match the key bound to this refresh token")
        }
        if (storedJkt != null && proofJkt == null) {
            // RFC 6749 §5.2: token-endpoint errors take their values from the limited list there
            // (`invalid_request`, `invalid_grant`, `invalid_client`, ...). RFC 9449 §11.1 added
            // `invalid_dpop_proof`, but the FAPI2 conformance suite's
            // `CheckTokenEndpointReturnedInvalidClientGrantOrRequestError` predates that
            // extension and only accepts the original codes. Fail the grant — the bound refresh
            // token cannot be redeemed without a DPoP proof of possession.
            return errOf(
                AuthorizationServerError.InvalidGrant(
                    details = "Refresh token is DPoP-bound but request did not present a DPoP proof",
                ),
            )
        }
        // Confidential client rotation: prefer the freshly presented proof's jkt; fall
        // back to the stored binding when no proof was presented (which only reaches here
        // for non-DPoP refresh-token chains since the previous block returned).
        val refreshBoundJkt = proofJkt ?: storedJkt
        // Every refreshed access token gets a newly minted set of opaque credential identifiers.
        // The refresh token stores only the authorized configuration ids, never old identifiers,
        // so identifiers cannot accidentally outlive or drift away from the access token that
        // carries them.
        val refreshedAuthorizationDetails =
            buildRefreshedCredentialAuthorizationDetails(verified.credentialConfigurationIds)
        val refreshedAccessTokenClaims =
            buildMap<String, Any> {
                refreshedAuthorizationDetails?.let { put("authorization_details", it) }
                verified.oid4vciIssuerState?.let { put(INTERNAL_OID4VCI_ISSUER_STATE_CLAIM, it) }
            }

        // Create new access token
        val accessToken =
            commands.createAccessToken
                .execute(
                    CreateAccessTokenArgs(
                        subject = verified.subject,
                        clientId = tokenRequest.clientId,
                        scope = verified.scope,
                        audience = verified.resource.ifEmpty { listOfNotNull(verified.defaultAccessTokenAudience) },
                        dpopJkt = refreshBoundJkt,
                        certificateThumbprintS256 = certThumbprint,
                        authTime = verified.authTime,
                        acr = verified.acr,
                        amr = verified.amr,
                        additionalClaims = refreshedAccessTokenClaims,
                        baseUrlOverride = applied.baseUrlOverride,
                    ),
                ).getOrElse { error -> return Err(error) }

        val responseRefreshToken =
            if (rotateRefreshToken) {
                verified.replacementRefreshToken ?: run {
                    // Mint one successor and atomically attach it to the consumed row. If two
                    // requests race, storage keeps the first successor and both responses use
                    // that authoritative value rather than branching the refresh-token chain.
                    val candidate =
                        commands.createRefreshToken
                            .execute(
                                CreateRefreshTokenArgs(
                                    subject = verified.subject,
                                    clientId = tokenRequest.clientId,
                                    scope = verified.scope,
                                    resource = verified.resource,
                                    defaultAccessTokenAudience = verified.defaultAccessTokenAudience,
                                    credentialConfigurationIds = verified.credentialConfigurationIds,
                                    oid4vciIssuerState = verified.oid4vciIssuerState,
                                    dpopJkt = refreshBoundJkt,
                                    clientInstanceKeyJkt = verified.clientInstanceKeyJkt,
                                    authTime = verified.authTime,
                                    acr = verified.acr,
                                    amr = verified.amr,
                                    nonce = verified.nonce,
                                    loginSessionId = verified.loginSessionId,
                                ),
                            ).getOrElse { error -> return Err(error) }
                            .value
                    val rotated =
                        tokenStorage
                            .rotateRefreshToken(
                                token = verified.refreshTokenId,
                                replacementRefreshToken = candidate,
                                rotatedAt = kotlin.time.Clock.System.now(),
                            ).getOrElse { error -> return Err(IdkError.fromDTO(error)) }
                    rotated?.replacementRefreshToken ?: candidate
                }
            } else {
                // Rotation disabled: reuse the presented refresh token (RFC 6749 §6
                // permits this) and leave the stored entry untouched so it remains
                // valid for the next refresh.
                rtParams.refreshToken
            }

        // OIDC Core 1.0 §12: when the original grant carried `openid`, the AS MAY
        // reissue an id_token on refresh. We do so with the preserved authentication
        // context (auth_time / acr / amr / nonce / sid) so the refreshed id_token is
        // bound to the same authentication event as the original.
        val refreshGrantedScopes = verified.scope?.split(" ")?.toSet() ?: emptySet()
        val refreshOidcEnabled = serverConfig.oidc.isEnabled
        val refreshedIdToken =
            if (refreshOidcEnabled && "openid" in refreshGrantedScopes) {
                commands.createIdToken
                    .execute(
                        CreateIdTokenArgs(
                            subject = verified.subject,
                            clientId = tokenRequest.clientId,
                            nonce = verified.nonce,
                            authTime = verified.authTime,
                            acr = verified.acr,
                            amr = verified.amr,
                            accessToken = accessToken.value,
                            sessionId = verified.loginSessionId,
                            baseUrlOverride = applied.baseUrlOverride,
                        ),
                    ).getOrElse { error -> return Err(error) }
                    .value
            } else {
                null
            }

        return commands.createTokenResponse.execute(
            CreateTokenResponseArgs(
                accessToken = accessToken.value,
                tokenType = tokenTypeFor(refreshBoundJkt),
                refreshToken = responseRefreshToken,
                scope = verified.scope,
                idToken = refreshedIdToken,
                authorizationDetails = refreshedAuthorizationDetails,
            ),
        )
    }

    private companion object {
        const val INTERNAL_OID4VCI_ISSUER_STATE_CLAIM = "oid4vci.internal.issuer_state"
    }
}

/** Rebuilds token-response authorization details so refresh never reuses prior access-token handles. */
internal fun buildRefreshedCredentialAuthorizationDetails(credentialConfigurationIds: List<String>) =
    buildAuthorizationCodeCredentialAuthorizationDetails(credentialConfigurationIds)
