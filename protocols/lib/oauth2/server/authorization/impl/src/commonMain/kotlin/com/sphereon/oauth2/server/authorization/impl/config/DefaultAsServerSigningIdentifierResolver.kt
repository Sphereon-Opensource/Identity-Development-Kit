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

package com.sphereon.oauth2.server.authorization.impl.config

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.log.Log
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.TokenFormat
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.server.authorization.impl.command.TokenPathStage
import com.sphereon.oauth2.server.authorization.impl.command.TokenPathStageTimings
import com.sphereon.oauth2.server.authorization.impl.command.discovery.keyAlgorithmToJwsAlg
import com.sphereon.oauth2.server.authorization.signing.AsServerSigningIdentifierResolver
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock

/**
 * Default [AsServerSigningIdentifierResolver].
 *
 * Reads the highest-priority `ACTIVE` key for the resolved tenant from the durable [SigningKeyStore].
 * It does NOT generate or self-seed a key: a durable AS signing key is PROVISIONED explicitly at
 * tenant registration (`KmsBackedAsBootstrapDelegate`, gated by the explicit `signing-key.auto-generate`
 * flag) into the durable store. If no `ACTIVE` key exists for the resolved tenant this fails closed
 * (the deployment is unprovisioned/misconfigured) rather than minting an opaque token or auto-generating.
 *
 * The resolver stays session-scoped because tenant/config context is session-owned. The immutable
 * active-key descriptor is cached by [ActiveSigningKeySnapshotCache] at AppScope and every durable
 * register, rotation, or state transition explicitly advances its tenant revision.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AsServerSigningIdentifierResolver>())
class DefaultAsServerSigningIdentifierResolver(
    private val execution: SessionExecution,
    private val configProvider: OAuth2ServersConfigProvider,
    private val signingKeyStore: SigningKeyStore,
    private val activeSigningKeySnapshotCache: ActiveSigningKeySnapshotCache,
) : AsServerSigningIdentifierResolver {
    private val log = Log.app().withTag("DefaultAsServerSigningIdentifierResolver")

    override suspend fun resolveSigningIdentifier(): ManagedIdentifierOptsOrResult? {
        val timings = TokenPathStageTimings(operation = "signing-identifier-resolution")
        var outcome = "failed"
        return try {
            doResolve(timings).also { resolved ->
                outcome = if (resolved == null) "not-applicable" else "success"
            }
        } finally {
            timings.report(log, outcome)
        }
    }

    override suspend fun resolveSigningIdentifier(jwsAlgorithm: String): ManagedIdentifierOptsOrResult? {
        val requested = jwsAlgorithm.trim()
        require(requested.isNotEmpty() && !requested.equals("none", ignoreCase = true)) {
            "A non-empty asymmetric JWS signing algorithm is required"
        }
        val active = activeSigningKeys().firstOrNull { keyAlgorithmToJwsAlg(it.algorithm).equals(requested, ignoreCase = true) }
            ?: throw OAuth2SigningKeyUnavailableException(
                tenantId = resolveSigningKeyTenant(execution),
                message = "No ACTIVE OAuth2 signing key for JWS algorithm '$requested'",
            )
        return ManagedOptsKeyInfo(identifier = active.keyInfo)
    }

    override suspend fun supportedSigningAlgorithms(): Set<String> =
        activeSigningKeys().mapTo(linkedSetOf()) { keyAlgorithmToJwsAlg(it.algorithm) }

    private suspend fun activeSigningKeys(): List<OAuth2SigningKey> {
        val tenantId = resolveSigningKeyTenant(execution)
        val allResult = signingKeyStore.listAll(tenantId)
        if (allResult.isErr) {
            throw IllegalStateException("OAuth2 signing-key store lookup failed for tenant '$tenantId': ${allResult.error}")
        }
        val now = Clock.System.now()
        return allResult.value
            .asSequence()
            .filter { it.tenantId == tenantId && it.state == OAuth2SigningKeyState.ACTIVE && it.notBefore <= now }
            .sortedWith(activePriorityOrder.reversed())
            .toList()
    }

    private suspend fun doResolve(timings: TokenPathStageTimings): ManagedIdentifierOptsOrResult? {
        // Only act where this process is actually configured to host an AS. The
        // oauth2-server-authorization impl is bundled into AS-integrating services (OID4VCI issuer,
        // OID4VP verifier, monolith) that declare no `oauth2.servers.*` config, so `serverConfig` is
        // a synthesized bare default. Returning null there avoids resolving — and lazily seeding — an
        // AS signing key those services never sign with. The discriminator is the presence of explicit
        // server config, NOT a configured `issuer`/`issuerTemplate`: a genuinely hosted AS resolves
        // its issuer per-request from the request base URL (see HybridFrontChannelMint.baseUrlOverride),
        // so a hosted AS legitimately runs with `issuer == null`.
        val signingIdentifierRequired =
            timings.record(TokenPathStage.AS_CONFIG_RESOLUTION) {
                if (!configProvider.getConfig().explicitlyConfigured) {
                    false
                } else {
                    // Opaque-token / no-OIDC deployments do not need a signing key. Return null so any
                    // accidental sign path call surfaces a clear "no signing identifier configured" error
                    // rather than wandering into the KMS with a default alias that does not exist.
                    val config = configProvider.serverConfig
                    config.tokenFormat == TokenFormat.JWT || config.oidc.isEnabled
                }
            }
        if (!signingIdentifierRequired) {
            return null
        }

        val tenantId =
            timings.record(TokenPathStage.SIGNING_TENANT_DERIVATION) {
                resolveSigningKeyTenant(execution)
            }
        // No lazy/at-sign-time KMS key generation. A durable AS signing key is PROVISIONED
        // explicitly at tenant registration (KmsBackedAsBootstrapDelegate, gated by the explicit
        // `signing-key.auto-generate` flag) into the durable, DB-backed SigningKeyStore. If no
        // ACTIVE key is present the deployment is unprovisioned/misconfigured (or — before the
        // resolution fix — the session resolved the wrong tenant): fail closed instead of
        // self-seeding a key or letting the mint silently fall back to an opaque token.
        val active =
            timings.record(TokenPathStage.ACTIVE_SIGNING_KEY_RESOLUTION) {
                activeSigningKeySnapshotCache
                    .resolve(
                        tenantId = tenantId,
                        readRevision = {
                            val revisionResult = signingKeyStore.contentRevision(tenantId)
                            if (revisionResult.isErr) {
                                throw IllegalStateException(
                                    "OAuth2 signing-key revision lookup failed for tenant '$tenantId': ${revisionResult.error}",
                                )
                            }
                            revisionResult.value
                        },
                    ) {
                        val allResult = signingKeyStore.listAll(tenantId)
                        if (allResult.isErr) {
                            throw IllegalStateException(
                                "OAuth2 signing-key store lookup failed for tenant '$tenantId': ${allResult.error}",
                            )
                        }
                        allResult.value
                    }
                    ?: throw OAuth2SigningKeyUnavailableException(
                        tenantId = tenantId,
                        message = "No ACTIVE OAuth2 signing key provisioned for tenant '$tenantId'. Provision it at tenant " +
                            "registration ('signing-key.auto-generate') or via SigningKeyStore.register; the AS does " +
                            "NOT self-seed signing keys.",
                    )
            }

        return ManagedOptsKeyInfo(identifier = active.keyInfo)
    }
}

private val activePriorityOrder: Comparator<OAuth2SigningKey> =
    compareBy<OAuth2SigningKey> { it.priority }.thenBy { it.createdAt }

/**
 * Expected fail-closed state while an authorization server has not been provisioned yet.
 *
 * Keeping this distinct from an arbitrary store or KMS failure lets the token command return the
 * RFC 6749 `temporarily_unavailable` response during first-run bootstrap instead of escaping the
 * command boundary as an endpoint exception and filling the platform log with stack traces.
 */
class OAuth2SigningKeyUnavailableException(
    val tenantId: String,
    message: String,
) : IllegalStateException(message)

/**
 * Resolve the tenant whose signing key this session mints with. There is NO `"default"`/anonymous
 * fallback: tenant resolution (domain/path first, JWT override) must have established a real tenant
 * before any sign path runs. A blank/anonymous tenant here is a bug — a request reached a signing
 * path without a resolved tenant — so fail closed and surface it.
 */
private fun resolveSigningKeyTenant(execution: SessionExecution): String {
    val tenantId = runCatching { execution.tenantId }.getOrNull()
    require(!tenantId.isNullOrBlank() && tenantId != IdentityConstants.ANONYMOUS_TENANT_ID) {
        "AS signing-key resolution requires a real (domain/path-resolved) tenant; the session carries " +
            "'${tenantId ?: "<none>"}'. Public/anonymous requests must resolve their tenant from the host/path " +
            "before reaching a signing path; the AS never falls back to a 'default' tenant."
    }
    return tenantId
}
