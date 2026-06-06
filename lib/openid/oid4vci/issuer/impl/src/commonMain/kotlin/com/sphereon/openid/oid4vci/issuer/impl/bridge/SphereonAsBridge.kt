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

@file:OptIn(ExperimentalTime::class)

package com.sphereon.openid.oid4vci.issuer.impl.bridge

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.command.VerifyDpopProofCommand
import com.sphereon.oauth2.common.model.VerifyDpopProofOptions
import com.sphereon.oauth2.server.authorization.command.GetUserInfoArgs
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenArgs
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeData
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeStorage
import com.sphereon.openid.oid4vci.issuer.bridge.AugmentAsMetadataArgs
import com.sphereon.openid.oid4vci.issuer.bridge.AuthorizationContextRef
import com.sphereon.openid.oid4vci.issuer.bridge.ConsumePreAuthCodeArgs
import com.sphereon.openid.oid4vci.issuer.bridge.ConsumedPreAuthCode
import com.sphereon.openid.oid4vci.issuer.bridge.CreateAuthContextArgs
import com.sphereon.openid.oid4vci.issuer.bridge.Oid4vciAuthorizationServerBridge
import com.sphereon.openid.oid4vci.issuer.bridge.RegisterPreAuthCodeArgs
import com.sphereon.openid.oid4vci.issuer.bridge.RegisteredPreAuthCode
import com.sphereon.openid.oid4vci.issuer.bridge.ValidateAccessTokenArgs
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import dev.whyoleg.cryptography.random.CryptographyRandom
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * AS bridge for the embedded Sphereon OAuth2 AS (same-process).
 *
 * - Pre-authorized code management via [PreAuthorizedCodeStorage]
 * - Token validation via [AuthorizationServerService.introspectToken]
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciAuthorizationServerBridge>())
class SphereonAsBridge(
    private val preAuthorizedCodeStorage: PreAuthorizedCodeStorage,
    private val authorizationServerService: AuthorizationServerService,
    private val verifyDpopProofCommand: VerifyDpopProofCommand,
    private val execution: SessionExecution,
) : Oid4vciAuthorizationServerBridge {
    override suspend fun registerPreAuthorizedCode(args: RegisterPreAuthCodeArgs): IdkResult<RegisteredPreAuthCode, IdkError> {
        val code = CryptographyRandom.nextBytes(32).encodeToBase64Url()
        val txCode =
            if (args.txCodeRequired) {
                generateTxCode(args.txCodeLength ?: DEFAULT_TX_CODE_LENGTH, args.txCodeInputMode)
            } else {
                null
            }
        val txCodeHash =
            txCode?.let {
                hash(it.encodeToByteArray(), DigestAlg.SHA256).encodeToBase64Url()
            }

        val now = Clock.System.now()
        val data =
            PreAuthorizedCodeData(
                sessionId = args.sessionId,
                credentialConfigurationIds = args.credentialConfigurationIds,
                txCodeRequired = args.txCodeRequired,
                txCodeHash = txCodeHash,
                issuerIdentifier = args.issuerIdentifier,
                useCredentialIdentifiers = args.useCredentialIdentifiers,
                createdAt = now,
                expiresAt = now + 10.minutes,
            )

        preAuthorizedCodeStorage.storePreAuthorizedCode(code, data).getOrElse {
            return Err(IdkError.UNKNOWN_ERROR(message = "Failed to store pre-authorized code: ${it.details}"))
        }

        return Ok(RegisteredPreAuthCode(code = code, txCode = txCode))
    }

    /**
     * Consume a pre-authorized code. This is a thin storage facade — tx_code validation
     * is handled by [VerifyPreAuthorizedCodeGrantCommand], not duplicated here.
     */
    override suspend fun consumePreAuthorizedCode(args: ConsumePreAuthCodeArgs): IdkResult<ConsumedPreAuthCode, IdkError> {
        val data =
            preAuthorizedCodeStorage.consumePreAuthorizedCode(args.code).getOrElse {
                return Err(IdkError.UNKNOWN_ERROR(message = "Failed to consume pre-authorized code: ${it.details}"))
            }

        if (data == null) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid or already used pre-authorized code"))
        }

        return Ok(
            ConsumedPreAuthCode(
                sessionId = data.sessionId,
                subject = data.subject,
                credentialConfigurationIds = data.credentialConfigurationIds,
            ),
        )
    }

    override suspend fun createAuthorizationContext(args: CreateAuthContextArgs): IdkResult<AuthorizationContextRef, IdkError> =
        Ok(AuthorizationContextRef(issuerState = args.issuerState, sessionId = args.issuerState))

    override suspend fun validateAccessToken(args: ValidateAccessTokenArgs): IdkResult<ValidatedTokenContext, IdkError> {
        // Introspect the access token via the AS service
        val introspection =
            authorizationServerService
                .introspectToken(
                    IntrospectTokenArgs(
                        token = args.accessToken,
                        tokenTypeHint = "access_token",
                        clientId = "",
                    ),
                ).getOrElse { return Err(it) }

        if (!introspection.active) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Access token is not active"))
        }

        val subject =
            introspection.sub
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Token introspection missing sub claim"))

        val clientId = introspection.clientId ?: ""

        // RFC 9449 §7.1: when the access token carries `cnf.jkt`, the resource server MUST
        // require a DPoP proof in the request and verify that:
        //   - the proof's signature validates with the embedded JWK,
        //   - `htm` / `htu` match the resource request,
        //   - `iat` is fresh,
        //   - `ath` = base64url(SHA-256(access token)),
        //   - JWK thumbprint of the proof matches `cnf.jkt`.
        // The verifier handles all of these in one pass given the right options.
        val cnfJkt = introspection.cnf?.jkt
        if (cnfJkt != null) {
            val proof =
                args.dpopProof
                    ?: return Err(IdkError.UNAUTHORIZED_ERROR(message = "DPoP proof required for DPoP-bound access token (RFC 9449 §7.1)"))
            val httpUrl = args.httpUrl
            val httpMethod = args.httpMethod
            if (httpUrl == null || httpMethod == null) {
                return Err(IdkError.UNKNOWN_ERROR(message = "Resource endpoint did not propagate request URL/method for DPoP htu/htm verification"))
            }
            verifyDpopProofCommand
                .execute(
                    VerifyDpopProofOptions(
                        dpopProof = proof,
                        httpMethod = httpMethod,
                        httpUrl = httpUrl,
                        accessToken = args.accessToken,
                        expectedJwkThumbprint = cnfJkt,
                    ),
                ).getOrElse { error ->
                    return Err(IdkError.UNAUTHORIZED_ERROR(message = "Invalid DPoP proof: ${error.message.defaultMessage}"))
                }
        }

        // Extract credential_configuration_ids from authorization_details (RFC 9396 array of objects)
        val authDetailsArray =
            introspection.additionalClaims["authorization_details"]?.let { ad ->
                try {
                    ad.jsonArray
                } catch (_: Exception) {
                    // Ignored: authorization_details is not a JSON array
                    null
                }
            }

        val credentialConfigurationIds =
            authDetailsArray?.mapNotNull { detail ->
                detail.jsonObject["credential_configuration_id"]?.jsonPrimitive?.content
            } ?: emptyList()

        // Extract credential_identifiers from authorization_details
        val credentialIdentifiers =
            authDetailsArray?.flatMap { detail ->
                val detailObj = detail.jsonObject
                detailObj["credential_identifiers"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
            } ?: emptyList()

        val additionalClaims = introspection.additionalClaims

        // acr and auth_time are standard OIDC claims carried in the token.
        val acr = additionalClaims["acr"]?.jsonPrimitive?.content
        val authTime =
            additionalClaims["auth_time"]?.jsonPrimitive?.longOrNull?.let { epochSeconds ->
                Instant.fromEpochSeconds(epochSeconds)
            }

        // upstream_sub / upstream_iss are populated by the upstream federation flow; null for local auth.
        val upstreamSubject = additionalClaims["upstream_sub"]?.jsonPrimitive?.content
        val upstreamIssuer = additionalClaims["upstream_iss"]?.jsonPrimitive?.content

        val userinfoClaims =
            resolveUserinfoClaims(additionalClaims, upstreamIssuer)
                ?: resolveLocalUserinfoClaims(args.accessToken)

        return Ok(
            ValidatedTokenContext(
                subject = subject,
                clientId = clientId,
                scope = introspection.scope,
                credentialConfigurationIds = credentialConfigurationIds,
                credentialIdentifiers = credentialIdentifiers.ifEmpty { null },
                cnfJkt = cnfJkt,
                userinfoClaims = userinfoClaims,
                acr = acr,
                authTime = authTime,
                upstreamSubject = upstreamSubject,
                upstreamIssuer = upstreamIssuer,
            ),
        )
    }

    /**
     * Surface userinfo claims from the token's `additionalClaims` when the tenant has opted in via
     * `tenant.idp.[<upstreamIssuer>].surface-userinfo-to-issuance=true`.
     *
     * The idpId key uses bracket-quoting because the upstream issuer is a URL: without it,
     * `PropertyKeyNormalizer` mangles dots, colons, and slashes so the property can never resolve.
     *
     * Returns the filtered claim map (with protocol-reserved keys stripped) or `null` when
     * the tenant has not opted in, the upstream issuer is unknown, or the filtered map is empty.
     */
    private fun resolveUserinfoClaims(
        additionalClaims: Map<String, JsonElement>,
        upstreamIssuer: String?,
    ): Map<String, JsonElement>? {
        upstreamIssuer ?: return null
        val configService = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService
        val surfaceUserinfo =
            configService
                .getPropertyAsString("tenant.idp.[$upstreamIssuer].surface-userinfo-to-issuance")
                ?.toBoolean()
                ?: false
        if (!surfaceUserinfo) {
            return null
        }
        // The token's additionalClaims carry whatever the AS stored at token minting time.
        // Surface the full map minus the protocol claims already modeled as dedicated fields
        // so callers don't need to double-read.
        return additionalClaims.filterKeys { it !in PROTOCOL_CLAIM_KEYS }.takeIf { it.isNotEmpty() }
    }

    /**
     * Surface the authenticated user's claims for a LOCAL (non-federated) access token by reading
     * the AS's own UserInfo for the token, when the tenant has opted in via
     * `oid4vci.issuer.surface-local-userinfo-to-issuance=true`.
     *
     * For a local config-backed or database-backed AS there is no `upstream_iss`, so
     * [resolveUserinfoClaims] returns null and the issuance pipeline's `AuthSessionClaimSource`
     * would see no userinfo claims. The user's profile claims are deliberately NOT embedded in the
     * access token (RFC 9068), but the AS resolves them by subject at the UserInfo endpoint. This
     * reads exactly that UserInfo (OIDC §5.3.2 — requires the `openid` scope on the token) and
     * surfaces the claims minus the `sub` field (already modeled as the dedicated subject).
     *
     * Off by default so existing deployments are byte-identical: the call is only made when the
     * opt-in flag is set, and any failure (e.g. the token lacks `openid` scope) yields `null`
     * rather than failing token validation.
     */
    private suspend fun resolveLocalUserinfoClaims(accessToken: String): Map<String, JsonElement>? {
        val configService = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService
        val enabled =
            configService
                .getPropertyAsString(SURFACE_LOCAL_USERINFO_KEY)
                ?.toBoolean()
                ?: false
        if (!enabled) return null

        val userInfo =
            authorizationServerService
                .getUserInfo(GetUserInfoArgs(accessToken = accessToken))
                .getOrElse { return null }
        return userInfo.claims
            .filterKeys { it != "sub" && it !in PROTOCOL_CLAIM_KEYS }
            .takeIf { it.isNotEmpty() }
    }

    override suspend fun augmentAsMetadata(args: AugmentAsMetadataArgs): IdkResult<JsonObject, IdkError> =
        Ok(
            buildJsonObject {
                args.baseMetadata.forEach { (k, v) -> put(k, v) }
                put("pre-authorized_grant_anonymous_access_supported", JsonPrimitive(true))
            },
        )

    private fun generateTxCode(
        length: Int = DEFAULT_TX_CODE_LENGTH,
        inputMode: String? = null,
    ): String {
        val count = length.coerceIn(1, MAX_TX_CODE_LENGTH)
        val alphabet = if (inputMode == "text") TX_CODE_ALPHANUMERIC else TX_CODE_NUMERIC
        val bytes = CryptographyRandom.nextBytes(count)
        return bytes.joinToString("") { alphabet[(it.toInt() and 0xFF).mod(alphabet.length)].toString() }
    }

    private companion object {
        const val DEFAULT_TX_CODE_LENGTH = 6
        const val MAX_TX_CODE_LENGTH = 32
        const val TX_CODE_NUMERIC = "0123456789"
        const val TX_CODE_ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        const val SURFACE_LOCAL_USERINFO_KEY = "oid4vci.issuer.surface-local-userinfo-to-issuance"
        val PROTOCOL_CLAIM_KEYS =
            setOf(
                "authorization_details",
                "acr",
                "auth_time",
                "upstream_sub",
                "upstream_iss",
            )
    }
}
