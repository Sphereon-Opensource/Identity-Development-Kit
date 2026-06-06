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
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOpts
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.crypto.resolution.managed.ManagedOptsKid
import com.sphereon.crypto.resolution.managed.MultiManagedIdentifierService
import com.sphereon.crypto.resolution.tryManagedIdentifierToJwk
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.GetJwksArgs
import com.sphereon.oauth2.server.authorization.command.GetJwksCommand
import com.sphereon.oauth2.server.authorization.command.JwksResult
import com.sphereon.oauth2.server.authorization.signing.AsServerSigningIdentifierResolver
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Default tenant identifier the JWKS publication uses when no per-request tenant has been
 * threaded through the session. Mirrors the constant in `DefaultAsServerSigningIdentifierResolver`; lifted
 * here as a private const rather than a shared one because the two consumers are in different
 * source sets (commonMain vs jvmMain).
 */
private const val DEFAULT_SIGNING_KEY_TENANT = "default"

/**
 * Implementation of GetJwksCommand.
 *
 * Returns every publishable signing key for the default tenant — i.e. the highest-priority
 * `ACTIVE` key plus any `LEGACY` keys that still verify in-flight tokens issued before the
 * most recent rotation. RPs cache the JWKS and look up by `kid`, so as long as the tenant's
 * key history is in the store, every issued token's verification key is reachable.
 *
 * Replaces the earlier single-`serverIdentifier`-by-alias model. Multi-key publication is
 * required for safe rotation: an RP that fetched the previous JWKS still has the LEGACY key
 * for verifying tokens issued before the rotation completed.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("GetJwksCommandImpl", exact = true)
class GetJwksCommandImpl(
    execution: SessionExecution,
    private val signingKeyStore: SigningKeyStore,
    private val multiManagedIdentifierService: MultiManagedIdentifierService,
    private val signingIdentifierResolver: AsServerSigningIdentifierResolver,
) : TypedServiceCommandAdapter<GetJwksArgs, JwksResult, IdkError>(
        commandId = GetJwksCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<GetJwksArgs>(),
        outputTypeToken = typeToken<JwksResult>(),
    ),
    GetJwksCommand {
    override val commandId: String get() = GetJwksCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is GetJwksArgs

    override suspend fun doExecute(
        args: GetJwksArgs,
        applyDuring: (GetJwksArgs) -> GetJwksArgs,
    ): IdkResult<JwksResult, IdkError> {
        applyDuring(args)
        return executeInternal()
    }

    private suspend fun executeInternal(): IdkResult<JwksResult, IdkError> {
        val tenantId = execution.tenantId.takeIf { it.isNotBlank() } ?: DEFAULT_SIGNING_KEY_TENANT
        // Ensure the store is seeded for hosted-AS deployments before publishing. The resolver
        // self-seeds the default signing key on first use (a no-op when this process does not host
        // an AS, or once a key already exists), so a JWKS request that lands before any token has
        // been signed still publishes the active key instead of an empty set.
        signingIdentifierResolver.resolveSigningIdentifier()
        val publishableResult = signingKeyStore.listPublishable(tenantId)
        if (!publishableResult.isOk) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to list publishable signing keys: ${publishableResult.error}"))
        }
        val publishable = publishableResult.value
        if (publishable.isEmpty()) {
            // Empty JWKS is the documented response shape when no key is registered; RPs
            // treat it as "no public verification key available". Keeps the endpoint healthy
            // during pre-bootstrap or post-emergency-revoke states.
            return Ok(JwksResult(keys = emptyList()))
        }

        val publishedJwks = mutableListOf<Jwk>()
        for (key in publishable) {
            val jwk = key.resolveAsPublicJwk() ?: continue
            publishedJwks.add(jwk)
        }
        return Ok(JwksResult(keys = publishedJwks))
    }

    /**
     * Resolve a single [OAuth2SigningKey] into the public-only JWK that JWKS consumers need.
     * Pins the advertised `kid` to the entry's [OAuth2SigningKey.kid] so RPs that look up by
     * `kid` from the JWS header find the right entry even when the underlying key material's
     * computed `kid` differs.
     *
     * Returns null when the resolution fails for this individual key — a per-key failure
     * should not blank out the whole JWKS, since callers downstream expect at least the
     * still-resolvable keys to publish. The failed key surfaces as a missing-kid in any RP
     * verification attempt, which is the correct signal: the operator sees a kid mismatch
     * in their RP logs and can investigate.
     */
    private suspend fun OAuth2SigningKey.resolveAsPublicJwk(): Jwk? {
        // Prefer addressing by alias (matches the sign-path identifier construction in
        // DefaultAsServerSigningIdentifierResolver); fall back to kid when no alias is configured.
        val identifier: ManagedIdentifierOpts =
            keyInfo.alias?.let { ManagedOptsAlias(identifier = it) }
                ?: ManagedOptsKid(identifier = keyInfo.kid ?: kid)
        val resolveResult = multiManagedIdentifierService.resolve(identifier)
        if (!resolveResult.isOk) return null
        val jwkResult = tryManagedIdentifierToJwk(resolveResult.value).getOrNull() ?: return null
        val publicJwk = jwkResult.identifier.toPublicKey() as? Jwk ?: return null
        return if (publicJwk.kid == kid) publicJwk else publicJwk.copy(kid = kid)
    }
}
