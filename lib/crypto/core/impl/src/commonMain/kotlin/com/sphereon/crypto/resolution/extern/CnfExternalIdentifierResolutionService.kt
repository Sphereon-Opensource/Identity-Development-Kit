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
 *
 */

package com.sphereon.crypto.resolution.extern

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.resolution.AdditionalIdentifierLookup
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Stub implementation of [VerificationMethodKeyResolver] that always returns an error.
 *
 * This is used as a fallback when no DID resolver is available. It will be replaced
 * by a real implementation (from lib-did-resolver-impl) when that module is on the classpath.
 *
 * The high order value (9999) ensures this binding is only used when no other implementation
 * is available.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class NoOpVerificationMethodKeyResolverImpl : VerificationMethodKeyResolver {
    override suspend fun resolveVerificationMethodKey(
        did: String,
        verificationMethodId: String?,
    ): IdkResult<JwkType, IdkErrorType> =
        IdkError
            .COMMAND_ARG_NOT_SUPPORTED_ERROR(
                message =
                    "DID resolution is not available. Cannot resolve DID-based kid: $did. " +
                        "Add lib-did-resolver-impl to the classpath to enable DID resolution.",
            ).asErrorResult()
}

/**
 * Service for resolving RFC 7800 CNF (Confirmation) claims to obtain holder binding keys.
 *
 * This service handles ExternalIdentifierCnfOpts which represents a CNF claim from SD-JWT or other
 * holder binding scenarios. It resolves the key material based on what's present in the CNF:
 *
 * Resolution priority:
 * 1. If kid starts with "did:" → resolve using DID resolver
 * 2. If jwk is present → use directly via JWK resolver
 * 3. If jku is present → fetch from JWK Set URL
 * 4. If only non-DID kid without jwk or jku → error (cannot resolve)
 *
 * @property execution Session execution context
 * @property jwkResolver Service for resolving JWK identifiers
 * @property jwksUrlResolver Service for resolving JWKS URL identifiers
 * @property verificationMethodKeyResolver Optional DID verification method resolver (null if DID resolution not available)
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CnfExternalIdentifierResolutionService>())
@ContributesIntoSet(SessionScope::class, binding = binding<ExternalIdentifierService>())
class CnfExternalIdentifierResolutionServiceImpl(
    execution: SessionExecution,
    private val jwkResolver: JwkExternalIdentifierResolutionService,
    private val jwksUrlResolver: JwksUrlExternalIdentifierResolutionService,
    private val verificationMethodKeyResolver: VerificationMethodKeyResolver,
) : ExternalIdentifierServiceAdapter<ExternalIdentifierResult.Cnf>(
        supportedIdentifierMethods = listOf(IdentifierMethodDefaults.CNF),
        execution = execution,
        commandId = COMMAND_ID,
    ),
    CnfExternalIdentifierResolutionService {
    override suspend fun doExecute(
        args: ExternalIdentifierOptsOrResult,
        applyDuring: (ExternalIdentifierOptsOrResult) -> ExternalIdentifierOptsOrResult,
    ): IdkResult<ExternalIdentifierResult.Cnf, IdkErrorType> {
        log.debug("Resolving CNF identifier...")

        // Note: supports() validation is already performed by parent CommandAdapter.execute()
        val opts = asSupportedOpts(args).value
        val kid = opts.kid
        val jwk = opts.jwk
        val jku = opts.jku

        // Validate CNF has at least one resolvable element
        if (kid == null && jwk == null && jku == null) {
            return IdkError
                .ILLEGAL_ARGUMENT_ERROR(
                    message = "CNF claim must contain at least one of: kid, jwk, or jku",
                ).asErrorResult()
        }

        // Resolution priority:
        // 1. If kid is a DID → resolve DID
        if (kid != null && kid.startsWith("did:")) {
            return resolveFromDid(opts, kid)
        }

        // 2. If jwk is present → use directly
        if (jwk != null) {
            return resolveFromJwk(opts, jwk, kid)
        }

        // 3. If jku is present → fetch from JWKS URL
        if (jku != null) {
            return resolveFromJku(opts, jku, kid)
        }

        // 4. Only non-DID kid without jwk or jku → cannot resolve
        return IdkError
            .ILLEGAL_ARGUMENT_ERROR(
                message =
                    "CNF contains only a non-DID kid '$kid' without jwk or jku. " +
                        "Non-DID kid values cannot be resolved without additional key material. " +
                        "Either use a DID-based kid, provide a jwk, or provide a jku (JWK Set URL).",
            ).asErrorResult()
    }

    private suspend fun resolveFromDid(
        opts: ExternalIdentifierCnfOpts,
        kid: String,
    ): IdkResult<ExternalIdentifierResult.Cnf, IdkErrorType> {
        log.debug("Resolving CNF from DID: $kid")

        // Extract DID and fragment (verification method ID)
        val didPart = kid.substringBefore('#')
        val verificationMethodId =
            if (kid.contains('#')) {
                kid.substringAfter('#')
            } else {
                null
            }

        return verificationMethodKeyResolver.resolveVerificationMethodKey(didPart, verificationMethodId).fold(
            success = { jwk ->
                val keyInfo = ResolvedKeyInfo.fromKey(jwk)
                ExternalIdentifierResult
                    .Cnf(
                        identifierOpts = opts,
                        jwks = arrayOf(keyInfo),
                        keyInfo = keyInfo,
                        resolvedFrom = CnfResolutionSource.DID,
                    ).asOkResult()
            },
            failure = { error ->
                IdkError
                    .UNKNOWN_ERROR(
                        message = "Failed to resolve DID $kid: ${error.message}",
                    ).asErrorResult()
            },
        )
    }

    private suspend fun resolveFromJwk(
        opts: ExternalIdentifierCnfOpts,
        jwk: JwkType,
        kid: String?,
    ): IdkResult<ExternalIdentifierResult.Cnf, IdkErrorType> {
        log.debug("Resolving CNF from embedded JWK")

        // Use the JWK resolver to properly handle the JWK
        val jwkOpts = ExternalIdentifierJwkOpts(identifier = jwk)
        return jwkResolver.resolve(jwkOpts).fold(
            success = { jwkResult ->
                // Create CNF result from JWK result
                val keyInfo =
                    jwkResult.keyInfo.let { info ->
                        // If kid provided in CNF, use it; otherwise use kid from JWK
                        if (kid != null && info.kid != kid) {
                            ResolvedKeyInfo(key = info.key, kid = kid, alias = info.alias, providerId = info.providerId)
                        } else {
                            info
                        }
                    }
                ExternalIdentifierResult
                    .Cnf(
                        identifierOpts = opts,
                        jwks = arrayOf(keyInfo),
                        keyInfo = keyInfo,
                        resolvedFrom = CnfResolutionSource.JWK,
                    ).asOkResult()
            },
            failure = { error ->
                IdkError
                    .UNKNOWN_ERROR(
                        message = "Failed to resolve JWK from CNF: ${error.message}",
                    ).asErrorResult()
            },
        )
    }

    private suspend fun resolveFromJku(
        opts: ExternalIdentifierCnfOpts,
        jku: String,
        kid: String?,
    ): IdkResult<ExternalIdentifierResult.Cnf, IdkErrorType> {
        log.debug("Resolving CNF from JKU: $jku (kid: $kid)")

        // Use the JWKS URL resolver
        val jwksOpts =
            ExternalIdentifierJwksUrlOpts(
                identifier = jku,
                lookup =
                    if (kid != null) {
                        AdditionalIdentifierLookup(kid = kid)
                    } else {
                        AdditionalIdentifierLookup()
                    },
            )

        return jwksUrlResolver.resolve(jwksOpts).fold(
            success = { jwksResult ->
                ExternalIdentifierResult
                    .Cnf(
                        identifierOpts = opts,
                        jwks = jwksResult.jwks,
                        keyInfo = jwksResult.keyInfo,
                        resolvedFrom = CnfResolutionSource.JKU,
                    ).asOkResult()
            },
            failure = { error ->
                IdkError
                    .UNKNOWN_ERROR(
                        message = "Failed to fetch JWK Set from $jku: ${error.message}",
                    ).asErrorResult()
            },
        )
    }

    override suspend fun supports(args: Any): Boolean {
        val externalArgs = args as? ExternalIdentifierOptsOrResult ?: return false
        return externalArgs is ExternalIdentifierCnfOpts || externalArgs.method == IdentifierMethodDefaults.CNF
    }

    override suspend fun isSupportedIdentifier(identifier: Any): Boolean {
        // CNF identifiers are Map<String, Any?> structures
        return identifier is Map<*, *>
    }

    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult.Cnf, IdkErrorType> = execute(opts)

    override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierCnfOpts, IdkErrorType> =
        if (isSupportedOpts(opts)) {
            (opts as ExternalIdentifierCnfOpts).asOkResult()
        } else {
            IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR().asErrorResult()
        }

    companion object {
        const val COMMAND_ID = "crypto.resolution.cnf"
    }
}
