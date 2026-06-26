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

package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.oauth2.common.command.VerifyDpopProofCommand
import com.sphereon.oauth2.common.model.VerifyDpopProofOptions
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
import com.sphereon.openid.oid4vci.issuer.impl.bridge.SphereonAsBridge
import dev.whyoleg.cryptography.random.CryptographyRandom
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * HTTP-based AS bridge for separate-process deployments.
 *
 * Registers pre-authorized codes with the AS via its internal HTTP API.
 * Used when the OID4VCI issuer and OAuth2 AS run in different containers/processes.
 *
 * Configuration (under `sphereon.oid4vci.issuer.as-bridge`):
 * - `internal-url`: Internal URL of the AS (e.g., http://oauth2-as:8080)
 * - `client-id`: Client ID for authenticating with the AS internal API
 * - `client-secret`: Client secret for authenticating with the AS internal API
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciAuthorizationServerBridge>(), replaces = [SphereonAsBridge::class])
class HttpAsBridge(
    private val execution: SessionExecution,
    private val httpClientFactory: HttpClientFactory,
    private val verifyDpopProofCommand: VerifyDpopProofCommand,
    private val dpopProofJtiCache: com.sphereon.oauth2.server.authorization.dpop.DpopProofJtiCache,
    private val asBaseUrlResolver: Oid4vciAsBridgeBaseUrlResolver,
) : Oid4vciAuthorizationServerBridge {
    private val httpClient: HttpClient by lazy {
        httpClientFactory.createClient(HttpClientOptions())
    }

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val configService: PrincipalConfigService
        get() = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService

    /** Connection target + optional Host override for one east-west AS call at [path]. */
    private data class AsTarget(
        val url: String,
        val hostHeader: String?
    )

    /**
     * Resolve where to send an east-west AS call. When a per-tenant PUBLIC host resolves AND an
     * internal AS address is configured (`$CONFIG_PREFIX.internal-url`), CONNECT to the internal
     * address and carry the public host in the `Host` header — the AS resolves the tenant from that
     * header. This avoids resolving the per-tenant public `{tenant}.{base}` FQDN from inside the
     * network (loopback under localtest.me; a fragile hairpin in a real cluster). Falls back to the
     * public host directly (real-DNS deployments where the AS is only reachable that way), or to the
     * static internal URL when no per-tenant host applies (single-tenant / non-gateway).
     */
    private suspend fun asTarget(path: String): AsTarget {
        val publicBase = asBaseUrlResolver.resolveAsBaseUrl()?.trimEnd('/')
        val internalUrl = configService.getPropertyAsString("$CONFIG_PREFIX.internal-url")?.trimEnd('/')
        return when {
            publicBase != null && internalUrl != null -> {
                AsTarget("$internalUrl$path", publicBase.substringAfter("://"))
            }

            publicBase != null -> {
                AsTarget("$publicBase$path", null)
            }

            else -> {
                AsTarget("${internalUrl ?: "http://localhost:8080"}$path", null)
            }
        }
    }

    private val clientId: String
        get() =
            configService.getPropertyAsString("$CONFIG_PREFIX.client-id")
                ?: "issuer-service"

    private val clientSecret: String
        get() =
            configService.getPropertyAsString("$CONFIG_PREFIX.client-secret")
                ?: ""

    private fun basicAuthHeader(): String {
        val credentials = "$clientId:$clientSecret"
        return "Basic ${credentials.encodeToByteArray().encodeToBase64()}"
    }

    override suspend fun registerPreAuthorizedCode(args: RegisterPreAuthCodeArgs): IdkResult<RegisteredPreAuthCode, IdkError> {
        val code = CryptographyRandom.nextBytes(32).encodeToBase64Url()
        val txCode = if (args.txCodeRequired) generateTxCode() else null
        val txCodeHash =
            txCode?.let {
                hash(it.encodeToByteArray(), DigestAlg.SHA256).encodeToBase64Url()
            }

        val request =
            PreAuthRegistrationRequest(
                code = code,
                sessionId = args.sessionId,
                credentialConfigurationIds = args.credentialConfigurationIds,
                txCodeRequired = args.txCodeRequired,
                txCodeHash = txCodeHash,
                issuerIdentifier = args.issuerIdentifier,
                useCredentialIdentifiers = args.useCredentialIdentifiers,
            )

        try {
            val target = asTarget("/internal/preauth/register")
            val response =
                httpClient.post(target.url) {
                    contentType(ContentType.Application.Json)
                    headers {
                        append(HttpHeaders.Authorization, basicAuthHeader())
                        target.hostHeader?.let { append(HttpHeaders.Host, it) }
                    }
                    setBody(json.encodeToString(request))
                }
            if (response.status.value !in 200..299) {
                val body = response.bodyAsText()
                return Err(IdkError.UNKNOWN_ERROR(message = "AS returned ${response.status.value}: $body"))
            }
        } catch (expected: Exception) {
            return Err(IdkError.UNKNOWN_ERROR(message = "Failed to register pre-auth code with AS: ${expected.message}"))
        }

        return Ok(RegisteredPreAuthCode(code = code, txCode = txCode))
    }

    override suspend fun consumePreAuthorizedCode(args: ConsumePreAuthCodeArgs): IdkResult<ConsumedPreAuthCode, IdkError> =
        Err(IdkError.UNKNOWN_ERROR(message = "Pre-auth code consumption is handled by the AS token endpoint"))

    override suspend fun createAuthorizationContext(args: CreateAuthContextArgs): IdkResult<AuthorizationContextRef, IdkError> =
        Ok(AuthorizationContextRef(issuerState = args.issuerState, sessionId = args.issuerState))

    override suspend fun validateAccessToken(args: ValidateAccessTokenArgs): IdkResult<ValidatedTokenContext, IdkError> {
        try {
            val target = asTarget("/introspect")
            val response =
                httpClient.post(target.url) {
                    contentType(ContentType.Application.FormUrlEncoded)
                    headers {
                        append(HttpHeaders.Authorization, basicAuthHeader())
                        target.hostHeader?.let { append(HttpHeaders.Host, it) }
                    }
                    setBody("token=${args.accessToken}&token_type_hint=access_token&client_id=$clientId")
                }
            val body = response.bodyAsText()
            val introspection = json.decodeFromString<JsonObject>(body)

            val active = introspection["active"]?.jsonPrimitive?.content?.toBoolean() ?: false
            if (!active) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Access token is not active"))
            }

            val subject =
                introspection["sub"]?.jsonPrimitive?.content
                    ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Token introspection missing sub claim"))

            val clientId = introspection["client_id"]?.jsonPrimitive?.content ?: ""
            val scope = introspection["scope"]?.jsonPrimitive?.content

            // RFC 9449 §7.1: when the access token carries `cnf.jkt`, the resource server MUST
            // verify the DPoP proof binds the request to that key. Skipping this is what makes
            // every DPoP negative test (wrong jkt, missing ath, replayed jti, etc.) succeed.
            val cnfJkt =
                introspection["cnf"]
                    ?.jsonObject
                    ?.get("jkt")
                    ?.jsonPrimitive
                    ?.content
            if (cnfJkt != null) {
                val proof =
                    args.dpopProof
                        ?: return Err(IdkError.UNAUTHORIZED_ERROR(message = "DPoP proof required for DPoP-bound access token (RFC 9449 §7.1)"))
                val httpUrl = args.httpUrl
                val httpMethod = args.httpMethod
                if (httpUrl == null || httpMethod == null) {
                    return Err(IdkError.UNKNOWN_ERROR(message = "Resource endpoint did not propagate request URL/method for DPoP htu/htm verification"))
                }
                val verified =
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
                // RFC 9449 §11.1: protect against proof replay by tracking each `jti` for the
                // proof iat tolerance window (60s + 60s skew = 120s). The same jti presented
                // twice within that window is a replay and MUST be rejected — this matches the
                // FAPI2 conformance suite's `…-dpop-negative-tests` "DPoP reuse" probe.
                val jti = verified.payload.jti
                if (dpopProofJtiCache.hasBeenUsed(jti)) {
                    return Err(IdkError.UNAUTHORIZED_ERROR(message = "DPoP proof jti '$jti' has already been used (replay)"))
                }
                val jtiWindowSeconds =
                    configService.getProperty(CONFIG_JTI_REPLAY_WINDOW_SECONDS, Long::class, DEFAULT_JTI_REPLAY_WINDOW_SECONDS)
                        ?: DEFAULT_JTI_REPLAY_WINDOW_SECONDS
                val jtiExpiresAt = Instant.fromEpochSeconds(verified.payload.iat) + jtiWindowSeconds.seconds
                dpopProofJtiCache.markAsUsed(jti, jtiExpiresAt)
            }

            val authDetailsArray =
                introspection["authorization_details"]?.let { ad ->
                    try {
                        ad.jsonArray
                    } catch (_: Exception) {
                        null
                    }
                }

            val credentialConfigurationIds =
                authDetailsArray?.mapNotNull { detail ->
                    detail.jsonObject["credential_configuration_id"]?.jsonPrimitive?.content
                } ?: emptyList()

            val credentialIdentifiers =
                authDetailsArray?.flatMap { detail ->
                    detail.jsonObject["credential_identifiers"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
                } ?: emptyList()

            return Ok(
                ValidatedTokenContext(
                    subject = subject,
                    clientId = clientId,
                    scope = scope,
                    credentialConfigurationIds = credentialConfigurationIds,
                    credentialIdentifiers = credentialIdentifiers.ifEmpty { null },
                    cnfJkt = cnfJkt,
                    userinfoClaims = resolveLocalUserinfoClaims(args.accessToken),
                ),
            )
        } catch (expected: Exception) {
            return Err(IdkError.UNKNOWN_ERROR(message = "Token introspection failed: ${expected.message}"))
        }
    }

    override suspend fun augmentAsMetadata(args: AugmentAsMetadataArgs): IdkResult<JsonObject, IdkError> =
        Ok(
            buildJsonObject {
                args.baseMetadata.forEach { (k, v) -> put(k, v) }
                put("pre-authorized_grant_anonymous_access_supported", JsonPrimitive(true))
            },
        )

    /**
     * Surface the authenticated user's claims to issuance by reading the AS's own UserInfo
     * endpoint (OIDC §5.3.2) for the access token, when the tenant has opted in via
     * `oid4vci.issuer.surface-local-userinfo-to-issuance=true`.
     *
     * For a LOCAL (config-backed / database-backed) AS there is no upstream IdP, so the access
     * token carries no identity claims (RFC 9068) and introspection surfaces none. The AS resolves
     * the user's claims by subject at /userinfo; this reads exactly that and surfaces the claims
     * (minus `sub`, already modeled as the dedicated subject) onto
     * [ValidatedTokenContext.userinfoClaims], which the issuance pipeline's
     * `PipelineCredentialAttributeContributor` pushes into the AUTHORIZATION phase for
     * `AuthSessionClaimSource` to map into credential attributes.
     *
     * Off by default so existing deployments are byte-identical. Any failure (token lacks `openid`
     * scope, /userinfo unreachable, etc.) yields `null` rather than failing token validation. The
     * claims returned are scope-filtered by the AS per the token's granted scopes.
     */
    private suspend fun resolveLocalUserinfoClaims(accessToken: String): Map<String, JsonElement>? {
        val enabled =
            configService.getPropertyAsString(SURFACE_LOCAL_USERINFO_KEY)?.toBoolean() ?: false
        if (!enabled) return null
        return try {
            val target = asTarget("/userinfo")
            val response =
                httpClient.get(target.url) {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $accessToken")
                        target.hostHeader?.let { append(HttpHeaders.Host, it) }
                    }
                }
            if (response.status.value !in 200..299) return null
            json
                .decodeFromString<JsonObject>(response.bodyAsText())
                .filterKeys { it != "sub" }
                .takeIf { it.isNotEmpty() }
        } catch (expected: Exception) {
            null
        }
    }

    private fun generateTxCode(): String {
        val bytes = CryptographyRandom.nextBytes(6)
        return bytes.joinToString("") { (it.toInt() and 0xFF).mod(10).toString() }
    }

    companion object {
        const val CONFIG_PREFIX = "oid4vci.issuer.as-bridge"
        const val SURFACE_LOCAL_USERINFO_KEY = "oid4vci.issuer.surface-local-userinfo-to-issuance"

        /**
         * RFC 9449 §11.1: jti-replay tracking window in seconds. Default covers the verifier's
         * full `iat` tolerance band (`max-age + clock-skew`, both 60s by default) so a captured
         * proof cannot be replayed before its `iat` ages out. Override via config.
         */
        const val CONFIG_JTI_REPLAY_WINDOW_SECONDS: String = "oid4vci.issuer.dpop.jti-replay-window-seconds"
        const val DEFAULT_JTI_REPLAY_WINDOW_SECONDS: Long = 120L
    }
}

@Serializable
data class PreAuthRegistrationRequest(
    val code: String,
    val sessionId: String,
    val credentialConfigurationIds: List<String>,
    val txCodeRequired: Boolean = false,
    val txCodeHash: String? = null,
    val issuerIdentifier: String? = null,
    val useCredentialIdentifiers: Boolean = true,
)
