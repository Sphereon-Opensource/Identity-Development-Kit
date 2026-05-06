/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.command.oidc

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierResult
import com.sphereon.crypto.resolution.managed.MultiManagedIdentifierService
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.validation.jwsAlgToDigest
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.command.discovery.keyAlgorithmToJwsAlg
import com.sphereon.oauth2.server.authorization.impl.command.putClaims
import com.sphereon.oauth2.server.authorization.provider.SessionParticipationRecorder
import com.sphereon.oauth2.server.authorization.storage.OidcLoginSessionIdProvider
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock

/**
 * Implementation of CreateIdTokenCommand
 *
 * Builds and signs an OpenID Connect ID Token JWT.
 *
 * Computes at_hash: SHA-256 of access token ASCII bytes, take left 128 bits, base64url encode.
 * Computes c_hash: same algorithm but for authorization code.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateIdTokenCommandImpl", exact = true)
class CreateIdTokenCommandImpl(
    execution: SessionExecution,
    private val jwtService: JwtService,
    private val configProvider: OAuth2ServersConfigProvider,
    @Named("oauth2.serverIdentifier") private val serverIdentifier: ManagedIdentifierOptsOrResult?,
    private val identifierService: MultiManagedIdentifierService,
    private val sessionParticipationRecorders: Set<SessionParticipationRecorder>,
    private val loginSessionIdProvider: OidcLoginSessionIdProvider,
) : TypedServiceCommandAdapter<CreateIdTokenArgs, StringResult, IdkError>(
        commandId = CreateIdTokenCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateIdTokenArgs>(),
        outputTypeToken = typeToken<StringResult>(),
    ),
    CreateIdTokenCommand {
    override val commandId: String get() = CreateIdTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateIdTokenArgs

    override suspend fun doExecute(
        args: CreateIdTokenArgs,
        applyDuring: (CreateIdTokenArgs) -> CreateIdTokenArgs,
    ): IdkResult<StringResult, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied).map { StringResult(it) }.mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(args: CreateIdTokenArgs): IdkResult<String, AuthorizationServerError> {
        if (serverIdentifier == null) {
            return Err(
                AuthorizationServerError.ServerError(
                    details = "Cannot create ID token: server signing key not configured",
                    exception = null,
                ),
            )
        }

        val issuerUrl =
            configProvider.serverConfig.issuer
                ?: args.baseUrlOverride
                ?: return Err(
                    AuthorizationServerError.ServerError(
                        details =
                            "OAuth2 server has no issuer configured and no request-time baseUrl override; " +
                                "set oauth2.servers.<id>.issuer or ensure the request carries Host + X-Forwarded-Proto headers",
                    ),
                )

        val now = Clock.System.now()
        val config = configProvider.serverConfig
        val expiresAt = now.epochSeconds + config.idTokenLifetimeSeconds

        // Resolve the signing key once so the JWS `alg` we report through `at_hash`/`c_hash`
        // matches the alg `PrepareJwsCommandImpl` will write into the JOSE header from
        // `keyInfo.signatureAlgorithm`. Resolution failure here is non-fatal — the JWS path
        // exercises the same resolver moments later and will surface the underlying error
        // through `jwtService.createJwsCompact`.
        val resolvedKeyResult = identifierService.resolve(serverIdentifier)
        val resolvedKey: ManagedIdentifierResult<*>? =
            if (resolvedKeyResult.isOk) resolvedKeyResult.value else null

        // OIDC Back-Channel Logout 1.0 §4.1: emit `sid` so RPs can correlate logout_token.sid
        // back to a local session. We prefer the cookie-derived OIDC login session id (the
        // value the end-session orchestrator looks up when the RP later passes id_token_hint),
        // falling back to args.sessionId (the pending-authorization session id) when the
        // cookie isn't on the request (e.g. federated grants that don't write the AS-side
        // cookie). The same value is later handed to [sessionParticipationRecorder] so the
        // RP-binding map keys match the `sid` the RP saw in its id_token.
        val sidClaim: String? = loginSessionIdProvider.currentLoginSessionId() ?: args.sessionId

        val payload =
            buildJsonObject {
                put("iss", issuerUrl)
                put("sub", args.subject)
                put("aud", args.clientId)
                put("exp", expiresAt)
                put("iat", now.epochSeconds)
                sidClaim?.let { put("sid", it) }

                args.nonce?.let { put("nonce", it) }
                args.authTime?.let { put("auth_time", it) }
                args.acr?.let { put("acr", it) }
                args.amr?.let { amrList ->
                    put("amr", buildJsonArray { amrList.forEach { add(JsonPrimitive(it)) } })
                }

                // at_hash / c_hash per OIDC Core §3.1.3.6: hash algorithm matches the ID token
                // signing alg (RS/ES/PS/HS 256/384/512 → SHA-256/-384/-512). The signing alg
                // is derived from the resolved KMS key so the digest matches the JWS header
                // `alg` `PrepareJwsCommandImpl` will write — config-pinned overrides win over
                // derivation so an operator can advertise a narrower set than the key supports.
                val idTokenSigningAlg = resolveIdTokenAlg(config, resolvedKey)
                args.accessToken?.let { token ->
                    computeTokenHash(token, idTokenSigningAlg)?.let { put("at_hash", it) }
                }
                args.authorizationCode?.let { code ->
                    computeTokenHash(code, idTokenSigningAlg)?.let { put("c_hash", it) }
                }

                // OIDC Core §5.4: "The Claims requested by the profile, email, address, and
                // phone scope values are returned from the UserInfo Endpoint … when a
                // response_type value is used that results in an Access Token being issued.
                // However, when no Access Token is issued (which is the case for the
                // response_type value id_token), the resulting Claims are returned in the
                // ID Token." So in code/hybrid flows we keep scope-derived user claims
                // out of the id_token by default (they go to /userinfo); the OIDC Basic
                // conformance suite's `EnsureIdTokenDoesNotContainNonRequestedClaims`
                // warns when they leak in. Pure id_token flows always embed them.
                //
                // The deployment opt-in `embedUserinfoClaimsInIdToken` overrides this
                // separation — when true, the id_token always carries every projected
                // user claim, useful for RPs that consume only the id_token and never
                // call /userinfo (e.g. Auth.js v5 default behaviour). Deviates from
                // §5.4 — operators turn it off for OIDC Basic OP conformance runs.
                //
                // `additionalClaims` is reserved for explicit `claims` request-parameter
                // entries (OIDC Core §5.5) and stays unconditional — by definition the RP
                // asked for those in the id_token.
                if (args.accessToken == null || config.embedUserinfoClaimsInIdToken) {
                    putClaims(args.userClaims)
                }
                putClaims(args.additionalClaims)
            }

        val header =
            buildJsonObject {
                put("typ", "JWT")
            }

        return try {
            val jwsArgs =
                CreateJwsArgs(
                    issuer = serverIdentifier,
                    payload = payload.toString(),
                    opts =
                        CreateJwsOpts(
                            protectedHeader = header,
                            noIssPayloadUpdate = true,
                        ),
                )

            jwtService
                .createJwsCompact(jwsArgs)
                .map { it.jwt }
                .mapError { error ->
                    AuthorizationServerError.ServerError(
                        details = "Failed to sign ID token: ${error.message.defaultMessage}",
                        exception = error.exception,
                    )
                }.also { result ->
                    // Record RP participation post-signing so OIDC Back-Channel Logout
                    // §2.4 / Front-Channel Logout 1.0 §3 can target the right recipients.
                    // Best-effort: a recorder failure here must not fail token issuance —
                    // it degrades logout precision (fall back to notifying every RP), never
                    // blocks auth.
                    //
                    // Use the same `sid` value the id_token carried so the
                    // [SessionParticipationRecorder] can map RPs into the cookie-keyed
                    // login session record the end-session orchestrator looks up.
                    val recordedSid = sidClaim
                    if (result.isOk && recordedSid != null) {
                        for (recorder in sessionParticipationRecorders) {
                            val recorded =
                                recorder.recordRpParticipation(
                                    sessionId = recordedSid,
                                    clientId = args.clientId,
                                )
                            if (!recorded.isOk) {
                                execution.log.warn(
                                    "SessionParticipationRecorder ${recorder::class.simpleName} failed for " +
                                        "session=$recordedSid client=${args.clientId}: " +
                                        "${recorded.error.message.defaultMessage}, back-channel logout precision " +
                                        "for this recorder degrades to all-registered-RPs fallback",
                                )
                            }
                        }
                    }
                }
        } catch (expected: Exception) {
            Err(
                AuthorizationServerError.ServerError(
                    details = "ID token creation failed: ${expected.message}",
                    exception = expected,
                ),
            )
        }
    }

    /**
     * Compute `at_hash` / `c_hash` per OpenID Connect Core §3.1.3.6: take the JWS digest matching
     * [jwsAlg] (256/384/512), hash the ASCII bytes of [input], take the left half of the digest,
     * base64url encode without padding.
     *
     * Returns `null` (omits the hash claim) if [jwsAlg] is unknown or the digest routine throws —
     * a missing hash claim is preferable to emitting a wrong value that would fail RP validation.
     */
    private fun computeTokenHash(
        input: String,
        jwsAlg: String,
    ): String? {
        val digestAlg = jwsAlgToDigest(jwsAlg)
        if (digestAlg == null) {
            execution.log.warn("No digest mapping for JWS alg '$jwsAlg'; omitting at_hash/c_hash")
            return null
        }
        return try {
            val bytes = input.encodeToByteArray()
            val hashBytes = hash(bytes, digestAlg)
            val leftHalf = hashBytes.copyOfRange(0, hashBytes.size / 2)
            leftHalf.encodeToBase64Url()
        } catch (expected: Exception) {
            execution.log.debug("Failed to compute token hash with alg $jwsAlg: ${expected.message}")
            null
        }
    }

    /**
     * Pick the JWS `alg` to digest under for `at_hash`/`c_hash`. Resolution order:
     *  1. Operator-pinned `idTokenSigningAlgValuesSupported` (first entry) — lets a deployment
     *     advertise a narrower / different alg than the key supports if the metadata path was
     *     overridden.
     *  2. The resolved KMS key's `signatureAlgorithm` mapped through [keyAlgorithmToJwsAlg] —
     *     this is what `PrepareJwsCommandImpl` will write into the JOSE `alg` header at sign
     *     time, so the digest matches the actual signature.
     *  3. RS256 — OIDC Core §10.1 mandates RP support for this alg; safer than ES256 as a
     *     defensive default when the key resolver returned nothing.
     */
    private fun resolveIdTokenAlg(
        config: OAuth2ServerInstanceConfig,
        resolvedKey: ManagedIdentifierResult<*>?,
    ): String {
        config.idTokenSigningAlgValuesSupported?.firstOrNull()?.let { return it }
        val keyAlg =
            resolvedKey?.keyInfo?.signatureAlgorithm
                ?: resolvedKey?.keyInfo?.key?.getSignatureAlgorithm()
        if (keyAlg != null) {
            try {
                return keyAlgorithmToJwsAlg(keyAlg)
            } catch (expected: IllegalStateException) {
                execution.log.warn(
                    "Resolved id-token signing key alg '$keyAlg' has no JWS mapping; falling back to RS256: ${expected.message}",
                )
            }
        }
        return DEFAULT_ID_TOKEN_SIGNING_ALG
    }

    private companion object {
        /**
         * OIDC Core §10.1 mandates RP support for `RS256`; using it as the defensive fallback
         * keeps `at_hash`/`c_hash` digests interoperable when the key resolver can't report an
         * alg. Replaces the historical hardcoded `ES256` which mismatched real RSA-backed keys.
         */
        const val DEFAULT_ID_TOKEN_SIGNING_ALG = "RS256"
    }
}
