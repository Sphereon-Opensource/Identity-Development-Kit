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
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.TokenFormat
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.server.authorization.signing.AsServerSigningIdentifierResolver
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Default [AsServerSigningIdentifierResolver].
 *
 * Reads the highest-priority `ACTIVE` key for the resolved tenant from the [SigningKeyStore],
 * self-seeding a default ES256 key when the store is empty (the standalone config-driven AS has
 * no tenant-registration bootstrap; EDK supplies one via `KmsBackedAsBootstrapDelegate`). The
 * store is an AppScope singleton, so the first session that resolves seeds it app-wide and every
 * later session just reads.
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
    private val keyManagerService: KeyManagerService,
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
        val active =
            signingKeyStore.getActive(tenantId).let { if (it.isOk) it.value else null }
                ?: seedDefaultSigningKey(tenantId, signingKeyStore, keyManagerService)
                ?: return null

        val identifier = active.keyInfo.alias ?: active.keyInfo.kid ?: active.kid
        return ManagedOptsAlias(identifier = identifier)
    }
}

/**
 * Tenant identifier the AS uses when no per-request tenant has been threaded into the session.
 * Single-tenant deployments stay on this default; multi-tenant deployments override the
 * resolver binding with a tenant-aware variant that resolves via the session's tenant context.
 */
private const val DEFAULT_SIGNING_KEY_TENANT = "default"

/**
 * Resolve the tenant for this session's signing key; fall back to [DEFAULT_SIGNING_KEY_TENANT] for
 * single-tenant deployments where the session carries a blank or anonymous tenantId.
 */
private fun resolveSigningKeyTenant(execution: SessionExecution): String =
    runCatching { execution.tenantId }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }
        ?: DEFAULT_SIGNING_KEY_TENANT

/**
 * Self-seed a default AS signing key when the [SigningKeyStore] is empty.
 *
 * Generates an ES256 key pair in the configured KMS under a deterministic alias and registers it as
 * the ACTIVE key, returning the now-active key (or null if generation/registration could not
 * complete). Idempotent: a concurrent session that already seeded short-circuits the re-check, and
 * a register race on the same kid resolves to whichever ACTIVE key won.
 *
 * Note: the software KMS persists keys into a certificate-based PKCS12 keystore, so the provider
 * must be configured with `autoCreateCertificate: true` for a bare signing key to be storable —
 * otherwise generation fails with "Either certChain or keyInfo.x5c must be present".
 */
private suspend fun seedDefaultSigningKey(
    tenantId: String,
    signingKeyStore: SigningKeyStore,
    keyManagerService: KeyManagerService,
): OAuth2SigningKey? {
    // A concurrent session may have seeded the AppScope store between our getActive and here.
    signingKeyStore.getActive(tenantId).let { if (it.isOk) it.value else null }?.let { return it }

    val kid = "oauth2-as-$tenantId"
    val algorithm = SignatureAlgorithm.ECDSA_SHA256 // JWA ES256
    val generateResult = keyManagerService.generateKeyResult(alias = kid, use = JwkUse.sig, alg = algorithm)
    if (!generateResult.isOk) return null
    val keyPair = generateResult.value.keyPair ?: return null

    val now = Clock.System.now()
    signingKeyStore.register(
        OAuth2SigningKey(
            tenantId = tenantId,
            keyInfo =
                KeyInfo<KeyType>(
                    kid = kid,
                    alias = keyPair.alias,
                    providerId = keyPair.providerId,
                    signatureAlgorithm = algorithm,
                ),
            state = OAuth2SigningKeyState.ACTIVE,
            priority = 1,
            createdAt = now,
            notBefore = now,
        ),
    )
    // Re-read so a register race (DuplicateKid) resolves to whichever key won.
    return signingKeyStore.getActive(tenantId).let { if (it.isOk) it.value else null }
}
