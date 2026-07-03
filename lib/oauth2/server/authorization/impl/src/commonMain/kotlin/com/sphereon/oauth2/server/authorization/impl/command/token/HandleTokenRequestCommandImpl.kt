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

package com.sphereon.oauth2.server.authorization.impl.command.token

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.command.VerifyDpopProofCommand
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.model.TokenResponse
import com.sphereon.oauth2.common.model.VerifyDpopProofOptions
import com.sphereon.oauth2.server.authorization.command.ClientAuthenticationEndpoint
import com.sphereon.oauth2.server.authorization.command.ParseTokenRequestArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.token.GrantContext
import com.sphereon.oauth2.server.authorization.command.token.GrantHandler
import com.sphereon.oauth2.server.authorization.command.token.HandleTokenRequestArgs
import com.sphereon.oauth2.server.authorization.command.token.HandleTokenRequestCommand
import com.sphereon.oauth2.server.authorization.dpop.DpopNonceManager
import com.sphereon.oauth2.server.authorization.dpop.DpopProofJtiCache
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.command.token.grant.errOf
import com.sphereon.oauth2.server.authorization.impl.command.token.grant.invalidDpopProof
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * Implementation of [HandleTokenRequestCommand]: orchestrates `POST /token` (RFC 6749 §3.2).
 *
 * Steps: parse the request through [com.sphereon.oauth2.server.authorization.command.ParseTokenRequestCommand],
 * verify the DPoP proof (RFC 9449) when present and enforce the AS nonce policy, optionally derive
 * the mTLS cert thumbprint for cert-bound tokens (RFC 8705 §3), verify client authentication, then
 * dispatch to the [GrantHandler] whose [GrantHandler.supports] matches the parsed grant. Each
 * grant-specific handler owns the access / refresh / id-token minting and any grant-specific
 * post-issuance bookkeeping (refresh-token rotation, device-code consumption).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleTokenRequestCommand>())
class HandleTokenRequestCommandImpl(
    execution: SessionExecution,
    private val authorizationServerService: AuthorizationServerService,
    private val serversConfigProvider: OAuth2ServersConfigProvider,
    private val clientRegistry: ClientRegistry,
    private val verifyDpopProofCommand: VerifyDpopProofCommand,
    private val dpopProofJtiCache: DpopProofJtiCache,
    private val dpopNonceManager: DpopNonceManager,
    private val grantHandlers: Set<GrantHandler>,
) : TypedServiceCommandAdapter<HandleTokenRequestArgs, TokenResponse, IdkError>(
        commandId = HandleTokenRequestCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<HandleTokenRequestArgs>(),
        outputTypeToken = typeToken<TokenResponse>(),
    ),
    HandleTokenRequestCommand {
    override val commandId: String get() = HandleTokenRequestCommand.COMMAND_ID

    private val commands get() = authorizationServerService.commands

    override suspend fun supports(args: Any): Boolean = args is HandleTokenRequestArgs

    override suspend fun doExecute(
        args: HandleTokenRequestArgs,
        applyDuring: (HandleTokenRequestArgs) -> HandleTokenRequestArgs,
    ): IdkResult<TokenResponse, IdkError> {
        val applied = applyDuring(args)

        // Parse the token request
        val tokenRequest =
            commands.parseTokenRequest
                .execute(
                    ParseTokenRequestArgs(
                        requestBody = applied.requestBody,
                        requestHeaders = applied.requestHeaders,
                        httpUrl = applied.httpUrl,
                        clientCertificateDer = applied.clientCertificateDer,
                    ),
                ).getOrElse { error -> return Err(error) }

        // RFC 9449 §5: verify any DPoP proof on the token request and bind the resulting JWK
        // thumbprint as `cnf.jkt` on the issued access token. The thumbprint from the verified
        // proof always wins over the optional `dpop_jkt` query parameter the wallet sent at
        // /authorize (which is just an upfront commitment per RFC 9449 §10).
        val proofJktResult = verifyDpopProofIfPresent(applied.httpUrl, tokenRequest.dpopProof, tokenRequest.httpMethod)
        val proofJkt = proofJktResult.getOrElse { error -> return Err(error) }

        // Per OID4VCI 1.0 §6.1: when the AS advertises
        // `pre-authorized_grant_anonymous_access_supported=true`, the wallet MAY
        // include `client_id` in the pre-authorized-code token request as a bare
        // identifier — it is NOT subject to RFC 6749 client authentication and
        // need not be registered. The parser sees `client_id` without secret/cert
        // and classifies it as `ClientAuthenticationConfig.None(clientId)`, which
        // would otherwise trigger a registry lookup and reject unregistered
        // wallets with `invalid_client`. Downgrade to Anonymous here so the
        // verify command treats the request as unauthenticated, matching spec.
        val effectiveAuth =
            if (tokenRequest.grantType == com.sphereon.oauth2.common.model.GrantType.PRE_AUTHORIZED_CODE &&
                serversConfigProvider.serverConfig.grantTypesEnabled.contains(
                    com.sphereon.oauth2.common.model.GrantType.PRE_AUTHORIZED_CODE.value,
                ) &&
                tokenRequest.clientAuthentication is com.sphereon.oauth2.common.model.ClientAuthenticationConfig.None
            ) {
                com.sphereon.oauth2.common.model.ClientAuthenticationConfig.Anonymous
            } else {
                tokenRequest.clientAuthentication
            }

        // Verify client authentication
        val verifiedAuth =
            commands.verifyClientAuthentication
                .execute(
                    VerifyClientAuthenticationArgs(
                        clientAuthentication = effectiveAuth,
                        clientId = tokenRequest.clientId,
                        tokenEndpointUrl = applied.httpUrl,
                        endpoint = ClientAuthenticationEndpoint.TOKEN,
                    ),
                ).getOrElse { error -> return Err(error) }

        val certThumbprint = computeCertThumbprintIfBound(applied.clientCertificateDer, tokenRequest.clientId)

        val context =
            GrantContext(
                tokenRequest = tokenRequest,
                resolvedClientId = verifiedAuth.clientId,
                proofJkt = proofJkt,
                certThumbprintS256 = certThumbprint,
                applied = applied,
                commands = commands,
                serverConfig = serversConfigProvider.serverConfig,
                walletInstanceAttestation = verifiedAuth.walletInstanceAttestation,
            )

        val handler =
            grantHandlers.firstOrNull { it.supports(tokenRequest.grantParameters) }
                ?: return errOf(
                    AuthorizationServerError.UnsupportedGrantType(grantType = tokenRequest.grantType.value),
                )

        return handler.handle(tokenRequest.grantParameters, context)
    }

    /**
     * Verifies the DPoP proof when present, applies the RFC 9449 §8 nonce policy, and threads
     * jti-replay protection. Returns the JWK thumbprint when a proof verifies successfully, `null`
     * when no proof was presented and none is required.
     */
    private suspend fun verifyDpopProofIfPresent(
        httpUrl: String,
        dpopProofToken: String?,
        httpMethod: String,
    ): IdkResult<String?, IdkError> {
        val log = log.logManager.withTag("DPoPNonce")
        val nonceRequired = serversConfigProvider.serverConfig.dpopNonceRequired
        if (dpopProofToken == null) {
            // RFC 9449 §8: the `use_dpop_nonce` signal applies only to *presented* proofs that
            // are missing or carry a stale `nonce` claim. When no proof is presented at all the
            // grant handlers own the rejection — each handler knows whether its own
            // sender-constrained material requires a proof and emits the spec-correct error:
            //   - Auth-code grant: `invalid_grant` when the code committed a `dpop_jkt`
            //     (RFC 9449 §10.1) — under FAPI2 / HAIP every auth code commits one because the
            //     front channel mandates DPoP, so this fully covers `EnsureHolderOfKeyRequired`.
            //   - Refresh-token grant: `invalid_grant` when the refresh token has `cnf.jkt`
            //     (RFC 9449 §5) — `…-refresh-token`'s `CheckTokenEndpointReturnedInvalidClient
            //     GrantOrRequestError` rejects `invalid_dpop_proof` here, so the per-grant
            //     `invalid_grant` is the only spec-conforming response.
            // A blanket `invalid_dpop_proof` short-circuit at this layer fights both checks: it
            // pre-empts the grant handler before it can emit the right code, and the FAPI2 suite
            // condition above is more restrictive than RFC 9449 §11.1's `invalid_dpop_proof`.
            return Ok(null)
        }

        val verified =
            verifyDpopProofCommand
                .execute(
                    VerifyDpopProofOptions(
                        dpopProof = dpopProofToken,
                        httpMethod = httpMethod,
                        httpUrl = httpUrl,
                    ),
                ).getOrElse { error -> return invalidDpopProof(error.message.defaultMessage) }

        // RFC 9449 §8: when the AS requires a nonce, the proof's `nonce` claim MUST be
        // present and currently valid. Missing or stale nonces yield `use_dpop_nonce`
        // with a fresh nonce surfaced through the response's `DPoP-Nonce` header.
        if (nonceRequired) {
            val proofNonce = verified.payload.nonce
            if (proofNonce == null) {
                val fresh = dpopNonceManager.rotate()
                log.warn("Rejecting token request with use_dpop_nonce: DPoP proof carried no `nonce` claim. Issued fresh DPoP-Nonce='$fresh'.")
                return errOf(AuthorizationServerError.UseDpopNonce(dpopNonce = fresh))
            }
            if (!dpopNonceManager.isValid(proofNonce)) {
                val fresh = dpopNonceManager.rotate()
                log.warn(
                    "Rejecting token request with use_dpop_nonce: DPoP proof `nonce` claim '$proofNonce' is unknown or expired " +
                        "(not in the active rolling window). Issued fresh DPoP-Nonce='$fresh'.",
                )
                return errOf(AuthorizationServerError.UseDpopNonce(dpopNonce = fresh))
            }
        }

        val jti = verified.payload.jti
        if (dpopProofJtiCache.hasBeenUsed(jti)) {
            return invalidDpopProof("DPoP proof jti '$jti' has already been used")
        }
        val iat = Instant.fromEpochSeconds(verified.payload.iat)
        dpopProofJtiCache.markAsUsed(jti, iat + DPOP_JTI_REPLAY_WINDOW)
        return Ok(verified.jwkThumbprint)
    }

    /**
     * RFC 8705 §3: bind the issued access token to the TLS client certificate when the
     * request arrived over mTLS AND either the client opted in
     * (`tlsClientCertificateBoundAccessTokens`) or the server-wide default is on. Per §3
     * this is independent of the client-auth method: a public client doing PKCE can still
     * get a cert-bound token if it presented a cert at the token endpoint.
     */
    private suspend fun computeCertThumbprintIfBound(
        clientCertificateDer: ByteArray?,
        clientId: String,
    ): String? =
        clientCertificateDer?.let { certDer ->
            val serverConfig = serversConfigProvider.serverConfig
            val clientOptIn =
                if (clientId.isNotEmpty()) {
                    clientRegistry
                        .getClient(clientId)
                        .getOrElse { null }
                        ?.tlsClientCertificateBoundAccessTokens ?: false
                } else {
                    false
                }
            val serverOptIn = serverConfig.tlsClientCertificateBoundAccessTokens
            if (clientOptIn || serverOptIn) {
                hash(certDer, DigestAlg.SHA256).encodeToBase64Url()
            } else {
                null
            }
        }

    private companion object {
        // RFC 9449 §11.1 / ClientVerifyDpopProofCommandImpl: proof iat tolerance is 60s, so the
        // replay-protection window stays valid for a buffered 120s.
        private val DPOP_JTI_REPLAY_WINDOW = 120.seconds
    }
}
