@file:OptIn(ExperimentalTime::class)

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

package com.sphereon.oauth2.server.authorization.impl.http.command.token

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.jwt.validation.AccessTokenValidationOptions
import com.sphereon.oauth2.jwt.validation.IdpConfig
import com.sphereon.oauth2.jwt.validation.IdpRegistry
import com.sphereon.oauth2.jwt.validation.IdpType
import com.sphereon.oauth2.jwt.validation.JwtValidationService
import com.sphereon.oauth2.server.authorization.command.token.ProvisionSigningKeyHttpEndpointCommand
import com.sphereon.oauth2.server.authorization.impl.http.oauth2ErrorResponse
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStoreError
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Internal endpoint that PROVISIONS the tenant AS signing key material into THIS process's KMS.
 *
 * In single-port deployments the AS routes KMS to the per-tenant `tenant-kms` over gRPC, so the
 * platform — whose own KMS is different — cannot generate the per-tenant key where the AS will read
 * it. The platform therefore calls this endpoint on the tenant's own AS host (`{tenant}.{base}`);
 * the AS resolves the tenant from that host and generates the key in its own KMS under the same
 * tenant partition the AS later signs with. It also writes the tenant-AS-local [SigningKeyStore]
 * reference, because split platform/satellite deployments keep the platform DB and tenant workload
 * DB separate; a reference written by platform bootstrap is not visible to this AS.
 *
 * Authentication is a short-lived, platform-issued bearer bound to THIS tenant. The platform mints a
 * service token whose `aud` is [ProvisionSigningKeyHttpEndpointCommand.audienceFor] of the target
 * tenant and whose `tenant_id` claim is that tenant; this endpoint validates the bearer's signature
 * against the platform issuer's JWKS and asserts BOTH the per-tenant `aud` AND that `tenant_id` equals
 * the token-bound session tenant. A token minted for tenant A is therefore rejected when replayed in
 * tenant B — no cross-tenant replay. The path stays anonymous to the strict-auth gate; this command
 * performs the validation itself. Key generation is idempotent: an already-present key is left in
 * place. The SigningKeyStore reference is also idempotent: an existing kid is left as-is.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ProvisionSigningKeyHttpEndpointCommand>())
class ProvisionSigningKeyHttpEndpointCommandImpl(
    execution: SessionExecution,
    private val keyManagerService: KeyManagerService,
    private val jwtValidationService: JwtValidationService,
    private val idpRegistry: IdpRegistry,
    private val signingKeyStore: SigningKeyStore,
) : HttpEndpointCommandAdapter(
        id = ProvisionSigningKeyHttpEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = ProvisionSigningKeyHttpEndpointCommand.ENDPOINT,
    ),
    ProvisionSigningKeyHttpEndpointCommand {
    private val json =
        Json {
            prettyPrint = false
            ignoreUnknownKeys = true
        }

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        // TenantResolutionPlugin treats this internal endpoint as token-bound: the validated
        // provisioning bearer supplies the session tenant, and this command validates that the same
        // tenant appears in both the token audience and tenant_id claim.
        val sessionTenant = execution.tenantId
        if (sessionTenant.isBlank() || sessionTenant == "anonymous") {
            return Ok(oauth2ErrorResponse(401, "invalid_token", "Tenant could not be resolved from the provisioning token", json))
        }

        val authHeader = request.headers["authorization"] ?: request.headers["Authorization"] ?: ""
        if (!authHeader.startsWith("Bearer ", ignoreCase = true)) {
            return Ok(oauth2ErrorResponse(401, "invalid_token", "Bearer authentication required", json))
        }
        val token = authHeader.substring("Bearer ".length).trim()
        if (token.isEmpty()) {
            return Ok(oauth2ErrorResponse(401, "invalid_token", "Empty bearer token", json))
        }

        // Trust anchor: the platform AS issuer + its JWKS, read from this service's strict-auth config
        // (the same `server.rest.auth.platform-*` keys the satellites trust). Without it we cannot
        // validate a platform-issued token, so fail closed.
        val platformIssuer = execution.conf.app.getPropertyAsString(KEY_PLATFORM_ISSUER)
        if (platformIssuer.isNullOrBlank()) {
            return Ok(oauth2ErrorResponse(500, "server_error", "Platform issuer trust is not configured on this AS", json))
        }
        val platformJwksUri =
            execution.conf.app.getPropertyAsString(KEY_PLATFORM_JWKS_URI)
                ?: "${platformIssuer.trimEnd('/')}/.well-known/jwks.json"
        idpRegistry.registerIdp(
            IdpConfig(id = PROVISION_IDP_ID, type = IdpType.CUSTOM, issuer = platformIssuer, jwksUri = platformJwksUri),
        )

        // Validate signature/iss/exp AND the per-tenant audience in one pass: expectedAudience pins the
        // token to THIS tenant, so a token minted for another tenant fails here.
        val validation =
            jwtValidationService.validateAccessToken(
                token,
                AccessTokenValidationOptions(
                    idpId = PROVISION_IDP_ID,
                    expectedAudience = ProvisionSigningKeyHttpEndpointCommand.audienceFor(sessionTenant),
                ),
            )
        if (!validation.isOk) {
            return Ok(oauth2ErrorResponse(401, "invalid_token", "Provisioning token validation failed", json))
        }
        // Defense in depth: the `tenant_id` claim must also equal the token-bound session tenant. (The
        // audience check above already binds the tenant; this rejects any token whose claims disagree.)
        // `claims` is the full validated payload (custom claims included), so read it directly.
        val tokenTenant =
            validation.value.claims["tenant_id"]
                ?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }
        if (tokenTenant != sessionTenant) {
            return Ok(oauth2ErrorResponse(403, "invalid_token", "Provisioning token tenant does not match the resolved tenant", json))
        }

        val body =
            request.body
                ?: return Ok(oauth2ErrorResponse(400, "invalid_request", "Missing request body", json))
        val req =
            try {
                json.decodeFromString<ProvisionSigningKeyRequest>(body)
            } catch (expected: Exception) {
                return Ok(oauth2ErrorResponse(400, "invalid_request", "Invalid JSON: ${expected.message}", json))
            }

        val algorithm =
            try {
                SignatureAlgorithm.fromJose(JwaAlgorithm.fromValue(req.algorithm))
            } catch (expected: Exception) {
                return Ok(
                    oauth2ErrorResponse(400, "invalid_request", "Unsupported JOSE algorithm '${req.algorithm}'", json),
                )
            }

        // Idempotent: only generate when the key is absent. The generate call routes to the AS's
        // configured KMS (the per-tenant tenant-kms over gRPC), so the material lands where the AS
        // signs.
        val existing =
            keyManagerService.getKeyResult(
                KeyInfo<KeyType>(kid = req.kid, alias = req.kid, providerId = req.providerId, signatureAlgorithm = algorithm),
            )
        val created =
            if (existing.isOk && existing.value.key != null) {
                false
            } else {
                val generated =
                    keyManagerService.generateKeyResult(
                        providerId = req.providerId,
                        alias = req.kid,
                        use = JwkUse.sig,
                        alg = algorithm,
                        keyVisibility = KeyVisibility.PRIVATE,
                    )
                if (!generated.isOk) {
                    val msg = if (generated.isErr) generated.error.message.defaultMessage else "<unknown>"
                    return Ok(oauth2ErrorResponse(500, "server_error", "Failed to provision signing key '${req.kid}': $msg", json))
                }
                true
            }

        val referenceResult = registerSigningKeyReference(sessionTenant, req, algorithm)
        if (referenceResult.isErr) {
            return Ok(
                oauth2ErrorResponse(
                    500,
                    "server_error",
                    "Failed to register signing key reference '${req.kid}': ${referenceResult.error}",
                    json,
                ),
            )
        }

        return Ok(provisionedResponse(req.kid, created = created))
    }

    private suspend fun registerSigningKeyReference(
        tenantId: String,
        req: ProvisionSigningKeyRequest,
        algorithm: SignatureAlgorithm,
    ): IdkResult<Unit, SigningKeyStoreError> {
        val existing = signingKeyStore.findByKid(tenantId, req.kid)
        if (existing.isErr) return Err(existing.error)
        if (existing.value != null) return Ok(Unit)

        val now = Clock.System.now()
        val registerResult =
            signingKeyStore.register(
                OAuth2SigningKey(
                    tenantId = tenantId,
                    keyInfo =
                        KeyInfo<KeyType>(
                            kid = req.kid,
                            alias = req.kid,
                            providerId = req.providerId,
                            signatureAlgorithm = algorithm,
                        ),
                    state = OAuth2SigningKeyState.ACTIVE,
                    priority = 1,
                    createdAt = now,
                    notBefore = now,
                ),
            )
        return if (registerResult.isErr && registerResult.error is SigningKeyStoreError.DuplicateKid) {
            Ok(Unit)
        } else {
            registerResult
        }
    }

    private fun provisionedResponse(
        kid: String,
        created: Boolean,
    ): GenericHttpResponse =
        GenericHttpResponse(
            statusCode = 200,
            headers = mapOf("Content-Type" to "application/json"),
            body = json.encodeToString(mapOf("kid" to kid, "created" to created.toString())),
        )
}

/**
 * Wire shape for the AS signing-key provisioning request body. `algorithm` is the JOSE name (e.g.
 * `ES256`); the tenant is resolved from the validated provisioning token, not the body.
 */
@Serializable
internal data class ProvisionSigningKeyRequest(
    val kid: String,
    val providerId: String,
    val algorithm: String,
)

/** Strict-auth config keys identifying the platform AS this service trusts for east-west tokens. */
private const val KEY_PLATFORM_ISSUER = "server.rest.auth.platform-issuer"
private const val KEY_PLATFORM_JWKS_URI = "server.rest.auth.platform-jwks-uri"

/** Registry id for the platform issuer entry used to validate the provisioning bearer. */
private const val PROVISION_IDP_ID = "platform-provisioning"
