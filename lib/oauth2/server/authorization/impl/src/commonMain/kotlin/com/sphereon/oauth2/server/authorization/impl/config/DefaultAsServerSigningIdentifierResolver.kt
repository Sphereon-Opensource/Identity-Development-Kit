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
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.TokenFormat
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.server.authorization.signing.AsServerSigningIdentifierResolver
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Default [AsServerSigningIdentifierResolver].
 *
 * Reads the highest-priority `ACTIVE` key for the resolved tenant from the durable [SigningKeyStore].
 * It does NOT generate or self-seed a key: a durable AS signing key is PROVISIONED explicitly at
 * tenant registration (`KmsBackedAsBootstrapDelegate`, gated by the explicit `signing-key.auto-generate`
 * flag) into the durable store. If no `ACTIVE` key exists for the resolved tenant this fails closed
 * (the deployment is unprovisioned/misconfigured) rather than minting an opaque token or auto-generating.
 *
 * Bound `@SingleIn(SessionScope::class)` and memoized: the lookup happens once per AS session, so
 * a rotation that lands mid-session does not affect tokens minted within that session — new
 * sessions pick up the new active key. This matches mature IdPs' realm-key-cache semantics
 * (Keycloak's `DefaultKeyManager` caches per-realm with explicit eviction on rotation).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<AsServerSigningIdentifierResolver>())
class DefaultAsServerSigningIdentifierResolver(
    private val execution: SessionExecution,
    private val configProvider: OAuth2ServersConfigProvider,
    private val signingKeyStore: SigningKeyStore,
) : AsServerSigningIdentifierResolver {
    private val mutex = Mutex()
    private var resolved = false
    private var cached: ManagedIdentifierOptsOrResult? = null

    override suspend fun resolveSigningIdentifier(): ManagedIdentifierOptsOrResult? =
        mutex.withLock {
            if (!resolved) {
                cached = doResolve()
                resolved = true
            }
            cached
        }

    private suspend fun doResolve(): ManagedIdentifierOptsOrResult? {
        // Only act where this process is actually configured to host an AS. The
        // oauth2-server-authorization impl is bundled into AS-integrating services (OID4VCI issuer,
        // OID4VP verifier, monolith) that declare no `oauth2.servers.*` config, so `serverConfig` is
        // a synthesized bare default. Returning null there avoids resolving — and lazily seeding — an
        // AS signing key those services never sign with. The discriminator is the presence of explicit
        // server config, NOT a configured `issuer`/`issuerTemplate`: a genuinely hosted AS resolves
        // its issuer per-request from the request base URL (see HybridFrontChannelMint.baseUrlOverride),
        // so a hosted AS legitimately runs with `issuer == null`.
        if (!configProvider.getConfig().explicitlyConfigured) {
            return null
        }

        // Opaque-token / no-OIDC deployments do not need a signing key. Return null so any
        // accidental sign path call surfaces a clear "no signing identifier configured" error
        // rather than wandering into the KMS with a default alias that does not exist.
        val config = configProvider.serverConfig
        if (config.tokenFormat != TokenFormat.JWT && !config.oidc.isEnabled) {
            return null
        }

        val tenantId = resolveSigningKeyTenant(execution)
        // No lazy/at-sign-time KMS key generation. A durable AS signing key is PROVISIONED
        // explicitly at tenant registration (KmsBackedAsBootstrapDelegate, gated by the explicit
        // `signing-key.auto-generate` flag) into the durable, DB-backed SigningKeyStore. If no
        // ACTIVE key is present the deployment is unprovisioned/misconfigured (or — before the
        // resolution fix — the session resolved the wrong tenant): fail closed instead of
        // self-seeding a key or letting the mint silently fall back to an opaque token.
        val activeResult = signingKeyStore.getActive(tenantId)
        if (activeResult.isErr) {
            throw IllegalStateException(
                "OAuth2 signing-key store lookup failed for tenant '$tenantId': ${activeResult.error}",
            )
        }
        val active =
            activeResult.value
                ?: throw OAuth2SigningKeyUnavailableException(
                    tenantId = tenantId,
                    message = "No ACTIVE OAuth2 signing key provisioned for tenant '$tenantId'. Provision it at tenant " +
                        "registration ('signing-key.auto-generate') or via SigningKeyStore.register; the AS does " +
                        "NOT self-seed signing keys.",
                )

        return ManagedOptsKeyInfo(identifier = active.keyInfo)
    }
}

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
