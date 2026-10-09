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
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.di.context.IdentityConstants
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.impl.command.TokenPathStage
import com.sphereon.oauth2.server.authorization.impl.command.TokenPathStageTimings
import com.sphereon.oauth2.server.authorization.impl.command.discovery.keyAlgorithmToJwsAlg
import com.sphereon.oauth2.server.authorization.signing.AsServerSigningIdentifierResolver
import com.sphereon.oauth2.server.authorization.signing.AsSigningRequirement
import com.sphereon.oauth2.server.authorization.signing.AsSigningSelection
import com.sphereon.oauth2.server.authorization.signing.CapturedAsServerConfig
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default [AsServerSigningIdentifierResolver].
 *
 * Reads a revision-accepted collection of `ACTIVE` keys for the tenant from [SigningKeyStore].
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
    private val signingKeyStore: SigningKeyStore,
    private val activeSigningKeySnapshotCache: ActiveSigningKeySnapshotCache,
) : AsServerSigningIdentifierResolver {
    private val log = Log.app().withTag("DefaultAsServerSigningIdentifierResolver")

    override suspend fun selectSigning(
        captured: CapturedAsServerConfig,
        requirement: AsSigningRequirement,
        requestedAlgorithm: String?,
    ): AsSigningSelection {
        val timings = TokenPathStageTimings(operation = "signing-identifier-resolution")
        var outcome = "failed"
        return try {
            doResolve(captured, requirement, requestedAlgorithm, timings).also { selection ->
                outcome = if (selection.identifier == null) "not-applicable" else "success"
            }
        } finally {
            timings.report(log, outcome)
        }
    }

    private suspend fun doResolve(
        captured: CapturedAsServerConfig,
        requirement: AsSigningRequirement,
        requestedAlgorithm: String?,
        timings: TokenPathStageTimings,
    ): AsSigningSelection {
        val requested = requestedAlgorithm?.trim()
        require(requested == null || (requested.isNotEmpty() && !requested.equals("none", ignoreCase = true))) {
            "A non-empty asymmetric JWS signing algorithm is required"
        }
        if (!captured.explicitlyConfigured) {
            if (requirement == AsSigningRequirement.REQUIRED) {
                throw IllegalStateException("A configured hosted authorization server is required for signing")
            }
            return AsSigningSelection(null, emptySet())
        }
        check(captured.serverKey != null && captured.server != null) { "Incomplete captured authorization server" }
        if (requirement == AsSigningRequirement.NOT_REQUIRED) return AsSigningSelection(null, emptySet())

        val tenantId =
            timings.record(TokenPathStage.SIGNING_TENANT_DERIVATION) {
                resolveSigningKeyTenant(runCatching { execution.tenantId }.getOrNull())
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
                    .resolveAll(
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
            }

        val algorithms = active.mapTo(linkedSetOf()) { keyAlgorithmToJwsAlg(it.algorithm) }
        val chosen =
            if (requested == null) active.firstOrNull()
            else active.firstOrNull { keyAlgorithmToJwsAlg(it.algorithm).equals(requested, ignoreCase = true) }
        if (chosen == null && requirement == AsSigningRequirement.REQUIRED) {
            throw OAuth2SigningKeyUnavailableException(
                tenantId = tenantId,
                message = if (requested == null) "No ACTIVE OAuth2 signing key for tenant '$tenantId'"
                else "No ACTIVE OAuth2 signing key for JWS algorithm '$requested'",
            )
        }
        return AsSigningSelection(chosen?.let { ManagedOptsKeyInfo(identifier = it.keyInfo) }, algorithms)
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
internal fun resolveSigningKeyTenant(tenantId: String?): String {
    require(!tenantId.isNullOrBlank() && tenantId != IdentityConstants.ANONYMOUS_TENANT_ID) {
        "AS signing-key resolution requires a real (domain/path-resolved) tenant; the session carries " +
            "'${tenantId ?: "<none>"}'. Public/anonymous requests must resolve their tenant from the host/path " +
            "before reaching a signing path; the AS never falls back to a 'default' tenant."
    }
    return tenantId
}
