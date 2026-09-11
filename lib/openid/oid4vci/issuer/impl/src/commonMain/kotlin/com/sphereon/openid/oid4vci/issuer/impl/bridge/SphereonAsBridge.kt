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

import com.sphereon.oauth2.server.authorization.model.FederationTokenMetadata

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
import com.sphereon.oauth2.common.config.MutableOAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.server.resource.command.VerifyJwtArgs
import com.sphereon.oauth2.server.resource.command.VerifyJwtCommand
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationServerDeployment
import com.sphereon.oauth2.server.authorization.command.GetUserInfoArgs
import com.sphereon.oauth2.server.authorization.command.IntrospectTokenArgs
import com.sphereon.oauth2.server.authorization.command.VerifyPreAuthCodeArgs
import com.sphereon.oauth2.server.authorization.service.AuthorizationServerService
import com.sphereon.oauth2.server.authorization.service.InternalClientRoleResolver
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeData
import com.sphereon.oauth2.server.authorization.storage.PreAuthorizedCodeStorage
import com.sphereon.oauth2.server.authorization.command.token.validatePreAuthorizedCodeExpiry
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
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedWalletInstanceAttestationEvidence
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedWalletUnitStatusReference
import dev.whyoleg.cryptography.random.CryptographyRandom
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

private const val ISSUER_INTERNAL_CLIENT_ROLE = "issuer"

/**
 * AS bridge for the embedded Sphereon OAuth2 AS (same-process).
 *
 * - Pre-authorized code registration via [PreAuthorizedCodeStorage] and
 *   consumption through the authorization-server verifier
 * - Token validation via [AuthorizationServerService.introspectToken]
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciAuthorizationServerBridge>())
class SphereonAsBridge(
    private val preAuthorizedCodeStorage: PreAuthorizedCodeStorage,
    private val authorizationServerService: AuthorizationServerService,
    private val verifyDpopProofCommand: VerifyDpopProofCommand,
    private val internalClientRoleResolver: InternalClientRoleResolver,
    private val execution: SessionExecution,
    private val asInstanceIdProvider: MutableOAuth2ServerInstanceIdProvider = object : MutableOAuth2ServerInstanceIdProvider {
        private var current: String? = null
        override fun currentAsInstanceId(): String? = current
        override fun setCurrentAsInstanceId(asInstanceId: String) { current = asInstanceId }
        override fun clearCurrentAsInstanceId() { current = null }
    },
    private val verifyJwtCommand: VerifyJwtCommand? = null,
    private val clock: Clock = Clock.System,
) : Oid4vciAuthorizationServerBridge {
    override suspend fun registerPreAuthorizedCode(args: RegisterPreAuthCodeArgs): IdkResult<RegisteredPreAuthCode, IdkError> {
        if (args.authorizationServer.deployment == Oid4vciAuthorizationServerDeployment.EXTERNAL) {
            return Err(
                IdkError.fromString(
                    code = "unsupported_operation",
                    message = "External authorization servers do not support issuer-side pre-authorized-code registration",
                ),
            )
        }
        val expiry = validatePreAuthorizedCodeExpiry(args.expiresAtEpochSeconds, clock.now()).getOrElse { return Err(it) }
        val runtimeKey = args.authorizationServer.runtimeServerKey?.takeIf { it.isNotBlank() }
            ?: return Err(IdkError.INVALID_STATE(message = "Hosted authorization-server snapshot has no runtime key"))
        val previous = asInstanceIdProvider.currentAsInstanceId()
        asInstanceIdProvider.setCurrentAsInstanceId(runtimeKey)
        try {
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

        val now = clock.now()
        val data =
            PreAuthorizedCodeData(
                sessionId = args.sessionId,
                credentialConfigurationIds = args.credentialConfigurationIds,
                txCodeRequired = args.txCodeRequired,
                txCodeHash = txCodeHash,
                issuerIdentifier = args.issuerIdentifier,
                useCredentialIdentifiers = args.useCredentialIdentifiers,
                createdAt = now,
                expiresAt = expiry,
            )

        preAuthorizedCodeStorage.storePreAuthorizedCode(code, data).getOrElse {
            return Err(IdkError.UNKNOWN_ERROR(message = "Failed to store pre-authorized code: ${it.details}"))
        }

        return Ok(RegisteredPreAuthCode(code = code, txCode = txCode))
        } finally {
            if (previous == null) asInstanceIdProvider.clearCurrentAsInstanceId()
            else asInstanceIdProvider.setCurrentAsInstanceId(previous)
        }
    }

    /**
     * Consume a pre-authorized code. This is a thin storage facade — tx_code validation
     * is handled by [VerifyPreAuthorizedCodeGrantCommand], not duplicated here.
     */
    override suspend fun consumePreAuthorizedCode(args: ConsumePreAuthCodeArgs): IdkResult<ConsumedPreAuthCode, IdkError> {
        val verified =
            authorizationServerService
                .verifyPreAuthorizedCodeGrant(
                    VerifyPreAuthCodeArgs(
                        preAuthorizedCode = args.code,
                        txCode = args.txCode,
                        clientId = args.clientId,
                    ),
                ).getOrElse { return Err(it) }

        return Ok(
            ConsumedPreAuthCode(
                sessionId = verified.sessionId,
                subject = verified.subject,
                credentialConfigurationIds = verified.credentialConfigurationIds,
            ),
        )
    }

    override suspend fun createAuthorizationContext(args: CreateAuthContextArgs): IdkResult<AuthorizationContextRef, IdkError> =
        Ok(AuthorizationContextRef(issuerState = args.issuerState, sessionId = args.issuerState))

    override suspend fun validateAccessToken(args: ValidateAccessTokenArgs): IdkResult<ValidatedTokenContext, IdkError> {
        if (args.authorizationServer.deployment == Oid4vciAuthorizationServerDeployment.EXTERNAL) {
            return validateExternalAccessToken(args)
        }
        val runtimeKey = args.authorizationServer.runtimeServerKey?.takeIf { it.isNotBlank() }
            ?: return Err(IdkError.INVALID_STATE(message = "Hosted authorization-server snapshot has no runtime key"))
        val previous = asInstanceIdProvider.currentAsInstanceId()
        asInstanceIdProvider.setCurrentAsInstanceId(runtimeKey)
        try {
        val introspectingClientId =
            internalClientRoleResolver
                .resolveClientId(ISSUER_INTERNAL_CLIENT_ROLE)
                .orEmpty()

        // Introspect the access token via the AS service
        val introspection =
            authorizationServerService
                .introspectToken(
                    IntrospectTokenArgs(
                        token = args.accessToken,
                        tokenTypeHint = "access_token",
                        clientId = introspectingClientId,
                    ),
                ).getOrElse { return Err(it) }

        if (!introspection.active) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Access token is not active"))
        }

        val subject =
            introspection.sub
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Token introspection missing sub claim"))

        val clientId = introspection.clientId ?: ""
        val tokenId =
            introspection.jti?.takeIf { it.isNotBlank() && it.length <= MAX_TOKEN_ID_LENGTH }

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
        val credentialIdentifierMappings =
            authDetailsArray?.flatMap { detail ->
                val detailObj = detail.jsonObject
                val configId = detailObj["credential_configuration_id"]?.jsonPrimitive?.contentOrNull
                if (configId == null) emptyList()
                else detailObj["credential_identifiers"]?.jsonArray?.map { it.jsonPrimitive.content to configId }.orEmpty()
            }?.toMap().orEmpty()

        val additionalClaims = introspection.additionalClaims

        // acr and auth_time are standard OIDC claims carried in the token.
        val acr = additionalClaims["acr"]?.jsonPrimitive?.content
        val authTime =
            additionalClaims["auth_time"]?.jsonPrimitive?.longOrNull?.let { epochSeconds ->
                Instant.fromEpochSeconds(epochSeconds)
            }

        val federation = FederationTokenMetadata.read(additionalClaims)
        val upstreamSubject = federation?.upstreamSubject
        val upstreamIssuer = federation?.upstreamIssuer

        val userinfoClaims =
            if (FederationTokenMetadata.isFederated(additionalClaims)) {
                resolveUserinfoClaims(federation?.userinfo.orEmpty(), upstreamIssuer)
            } else {
                resolveLocalUserinfoClaims(args.accessToken)
            }

        return Ok(
            ValidatedTokenContext(
                authorizationServerId = args.authorizationServer.id,
                authorizationServerIssuer = args.authorizationServer.issuer,
                tokenId = tokenId,
                expiresAtEpochSeconds = introspection.exp,
                subject = subject,
                clientId = clientId,
                scope = introspection.scope,
                credentialConfigurationIds = credentialConfigurationIds,
                credentialIdentifiers = credentialIdentifiers.ifEmpty { null },
                credentialIdentifierMappings = credentialIdentifierMappings,
                issuerState = additionalClaims[INTERNAL_OID4VCI_ISSUER_STATE_CLAIM]?.jsonPrimitive?.contentOrNull,
                cnfJkt = cnfJkt,
                userinfoClaims = userinfoClaims,
                acr = acr,
                authTime = authTime,
                upstreamSubject = upstreamSubject,
                upstreamIssuer = upstreamIssuer,
                walletInstanceAttestation = parseWalletInstanceAttestation(additionalClaims),
            ),
        )
        } finally {
            if (previous == null) asInstanceIdProvider.clearCurrentAsInstanceId()
            else asInstanceIdProvider.setCurrentAsInstanceId(previous)
        }

    }

    private suspend fun validateExternalAccessToken(args: ValidateAccessTokenArgs): IdkResult<ValidatedTokenContext, IdkError> {
        val jwksUri = args.authorizationServer.jwksUri?.takeIf { it.isNotBlank() }
            ?: return Err(IdkError.INVALID_STATE(message = "External authorization-server snapshot has no JWKS URI"))
        val verifier = verifyJwtCommand
            ?: return Err(IdkError.INVALID_STATE(message = "External JWT verification is unavailable"))
        val verified = verifier.execute(
            VerifyJwtArgs(
                jwt = args.accessToken,
                authorizationServer = args.authorizationServer.issuer,
                expectedAudience = args.expectedAudience,
                jwksUri = jwksUri,
            ),
        ).getOrElse { return Err(it) }
        val cnfJkt = verified.dpopJkt
        if (cnfJkt != null) {
            val proof = args.dpopProof
                ?: return Err(IdkError.UNAUTHORIZED_ERROR(message = "DPoP proof required for DPoP-bound access token (RFC 9449 §7.1)"))
            val httpUrl = args.httpUrl
                ?: return Err(IdkError.UNKNOWN_ERROR(message = "Resource endpoint did not propagate request URL for DPoP verification"))
            val httpMethod = args.httpMethod
                ?: return Err(IdkError.UNKNOWN_ERROR(message = "Resource endpoint did not propagate request method for DPoP verification"))
            verifyDpopProofCommand.execute(
                VerifyDpopProofOptions(
                    dpopProof = proof,
                    httpMethod = httpMethod,
                    httpUrl = httpUrl,
                    accessToken = args.accessToken,
                    expectedJwkThumbprint = cnfJkt,
                ),
            ).getOrElse { return Err(IdkError.UNAUTHORIZED_ERROR(message = "Invalid DPoP proof: ${it.message.defaultMessage}")) }
        }
        val authorizationDetails = verified.additionalClaims["authorization_details"]?.runCatching { jsonArray }?.getOrNull()
        val configurationIds = authorizationDetails?.mapNotNull {
            it.jsonObject["credential_configuration_id"]?.jsonPrimitive?.contentOrNull
        }.orEmpty()
        val identifiers = authorizationDetails?.flatMap {
            it.jsonObject["credential_identifiers"]?.jsonArray?.mapNotNull { value -> value.jsonPrimitive.contentOrNull }.orEmpty()
        }.orEmpty()
        val mappings = authorizationDetails?.flatMap {
            val objectValue = it.jsonObject
            val configId = objectValue["credential_configuration_id"]?.jsonPrimitive?.contentOrNull
            if (configId == null) emptyList()
            else objectValue["credential_identifiers"]?.jsonArray?.mapNotNull { value -> value.jsonPrimitive.contentOrNull?.let { id -> id to configId } }.orEmpty()
        }?.toMap().orEmpty()
        return Ok(
            ValidatedTokenContext(
                authorizationServerId = args.authorizationServer.id,
                authorizationServerIssuer = verified.iss,
                subject = verified.sub,
                clientId = verified.clientId.orEmpty(),
                scope = verified.scope,
                credentialConfigurationIds = configurationIds,
                credentialIdentifiers = identifiers.ifEmpty { null },
                credentialIdentifierMappings = mappings,
                issuerState = verified.additionalClaims[INTERNAL_OID4VCI_ISSUER_STATE_CLAIM]?.jsonPrimitive?.contentOrNull,
                cnfJkt = cnfJkt,
                tokenId = verified.jti?.takeIf { it.isNotBlank() && it.length <= MAX_TOKEN_ID_LENGTH },
                expiresAtEpochSeconds = verified.exp.epochSeconds,
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
        return FederationTokenMetadata.filterUserinfo(additionalClaims).takeIf { it.isNotEmpty() }
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

    private fun parseWalletInstanceAttestation(additionalClaims: Map<String, JsonElement>): ValidatedWalletInstanceAttestationEvidence? {
        val clientStatus =
            additionalClaims["client_status"] as? JsonObject
                ?: return null
        val evidence =
            additionalClaims["wallet_instance_attestation"] as? JsonObject
                ?: return null
        val statusListUri = clientStatus.stringClaim("status_list_uri") ?: return null
        val index = clientStatus.stringClaim("index") ?: return null
        val evidenceId = evidence.stringClaim("evidence_id") ?: return null
        val profile = evidence.stringClaim("profile") ?: return null
        val format = evidence.stringClaim("format") ?: return null
        val expiresAt = evidence.longClaim("expires_at") ?: return null
        return ValidatedWalletInstanceAttestationEvidence(
            evidenceId = evidenceId,
            profile = profile,
            format = format,
            expiresAtEpochSeconds = expiresAt,
            clientStatus =
                ValidatedWalletUnitStatusReference(
                    statusListUri = statusListUri,
                    index = index,
                    status = clientStatus.stringClaim("status"),
                    revoked = clientStatus.booleanClaim("revoked") ?: false,
                    maintenanceExpiresAtEpochSeconds = clientStatus.longClaim("maintenance_expires_at"),
                ),
            walletInstanceId = evidence.stringClaim("wallet_instance_id"),
            walletProvider = evidence.stringClaim("wallet_provider"),
            walletSolution = evidence.stringClaim("wallet_solution"),
            walletUnitId = evidence.stringClaim("wallet_unit_id"),
            walletAccountId = evidence.stringClaim("wallet_account_id"),
        )
    }

    private fun JsonObject.stringClaim(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.longClaim(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull

    private fun JsonObject.booleanClaim(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    private companion object {
        const val MAX_TOKEN_ID_LENGTH = 128
        const val DEFAULT_TX_CODE_LENGTH = 6
        const val MAX_TX_CODE_LENGTH = 32
        const val TX_CODE_NUMERIC = "0123456789"
        const val TX_CODE_ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        const val SURFACE_LOCAL_USERINFO_KEY = "oid4vci.issuer.surface-local-userinfo-to-issuance"
        const val INTERNAL_OID4VCI_ISSUER_STATE_CLAIM = "oid4vci.internal.issuer_state"
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
