/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.crypto.dataintegrity.resolution

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import com.sphereon.crypto.resolution.MultiIdentifierResolutionService
import com.sphereon.crypto.resolution.managed.ManagedIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierDidOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwksUrlOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOIDFEntityIdOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierX5cOpts
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedOptsAlias
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Adapter over the canonical multi-identifier resolver. It deliberately
 * preserves relationship metadata only when the source returned it; key
 * material without authenticated controller/purpose information is not
 * promoted into an authorized verification method by the cryptosuites.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<VerificationMethodResolver>())
class IdentifierVerificationMethodResolver(
    private val identifierService: MultiIdentifierResolutionService,
) : VerificationMethodResolver {
    override suspend fun resolve(reference: String): IdkResult<VerificationMethodResolution, IdkErrorType> {
        if (reference.isBlank()) return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "verification method reference must not be blank"))
        // A DID URL contains an authenticated controller/relationship model in
        // its DID document. All other identifier methods require explicit
        // trusted configuration; spelling alone is never a resolver hint.
        if (!reference.startsWith("did:", ignoreCase = true)) {
            return missingPolicy(reference)
        }
        return resolveWithOptions(reference, ExternalIdentifierDidOpts(reference), trusted = null)
    }

    override suspend fun resolve(
        reference: String,
        policy: VerificationMethodResolutionPolicy,
    ): IdkResult<VerificationMethodResolution, IdkErrorType> {
        if (reference.isBlank()) return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "verification method reference must not be blank"))
        if (reference.startsWith("did:", ignoreCase = true)) {
            // DID relationship metadata is authoritative. Do not allow a
            // caller-provided policy to replace it with token/proof metadata.
            return resolveWithOptions(reference, ExternalIdentifierDidOpts(reference), trusted = null)
        }
        val trusted = policy.entry(reference) ?: return missingPolicy(reference)
        return resolveWithOptions(reference, trusted.identifierOpts, trusted)
    }

    private suspend fun resolveWithOptions(
        reference: String,
        options: IdentifierOptsOrResult,
        trusted: TrustedVerificationMethod?,
    ): IdkResult<VerificationMethodResolution, IdkErrorType> {
        if (options.isResolved) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "verification method '$reference' must use unresolved canonical identifier options"))
        }
        if (trusted != null) {
            val validation = validateTrustedOptions(reference, trusted.identifierOpts)
            if (validation != null) return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = validation))
        }
        val resolved = try {
            identifierService.resolve(options)
        } catch (expected: Exception) {
            return Err(IdkError.fromString(message = "verification method resolution failed: ${expected.message}", code = "VERIFICATION_METHOD_RESOLUTION_ERROR", exception = expected))
        }
        if (resolved.isErr) return resolved.error.asErrorResult()
        return when (val value = resolved.value) {
            is ExternalIdentifierResult -> fromExternal(reference, value, trusted)
            is ManagedIdentifierResult<*> -> fromManaged(reference, value, trusted)
            else -> Err(IdkError.NOT_FOUND_ERROR(resource = reference, message = "identifier resolver returned no verification key"))
        }
    }

    private fun fromExternal(
        reference: String,
        result: ExternalIdentifierResult,
        trusted: TrustedVerificationMethod?,
    ): IdkResult<VerificationMethodResolution, IdkErrorType> {
        if (result is ExternalIdentifierResult.X5c && result.verificationResult.error) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "X.509 verification failed for verification method '$reference': ${result.verificationResult.message ?: "unknown certificate error"}",
                ),
            )
        }
        val key = result.keyInfo.key as? JwkType
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "resolved verification method '$reference' is not a JWK"))
        if (result is ExternalIdentifierResult.Did) {
            val vm = result.didDocument?.verificationMethod?.firstOrNull { it.id == reference }
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = reference, message = "verification method is absent from resolved DID document"))
            val document = result.didDocument
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = reference, message = "resolved DID has no document metadata"))
            return Ok(
                VerificationMethodResolution(
                    reference = reference,
                    key = key,
                    controller = vm.controller,
                    authorizedProofPurposes = buildSet {
                        if (document.assertionMethod.orEmpty().authorizes(reference)) add(ProofPurpose.ASSERTION_METHOD)
                        if (document.authentication.orEmpty().authorizes(reference)) add(ProofPurpose.AUTHENTICATION)
                        if (document.keyAgreement.orEmpty().authorizes(reference)) add(ProofPurpose.KEY_AGREEMENT)
                        if (document.capabilityInvocation.orEmpty().authorizes(reference)) add(ProofPurpose.CAPABILITY_INVOCATION)
                        if (document.capabilityDelegation.orEmpty().authorizes(reference)) add(ProofPurpose.CAPABILITY_DELEGATION)
                    },
                ),
            )
        }
        if (trusted == null) return missingPolicy(reference)
        if (!matchesResolvedExternalMethod(trusted.identifierOpts, result)) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "identifier resolver returned a different method than trusted configuration for '$reference'"))
        }
        if (result is ExternalIdentifierResult.JwksUrl) {
            val expectedKid = trusted.identifierOpts.lookup.kid
            if (expectedKid != null && (result.selectedKid != expectedKid || result.keyInfo.kid != expectedKid)) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "JWKS resolver did not return the explicitly configured kid '$expectedKid' for '$reference'"))
            }
        }
        return Ok(VerificationMethodResolution(reference, key, trusted.controller, trusted.authorizedProofPurposes))
    }

    private fun fromManaged(
        reference: String,
        result: ManagedIdentifierResult<*>,
        trusted: TrustedVerificationMethod?,
    ): IdkResult<VerificationMethodResolution, IdkErrorType> {
        val key = result.keyInfo.key as? JwkType
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "managed verification method '$reference' is not a JWK"))
        if (trusted == null) return missingPolicy(reference)
        val managedMethodMatches = trusted.identifierOpts is ManagedIdentifierOptsOrResult &&
            (trusted.identifierOpts.method == result.method ||
                (trusted.identifierOpts is ManagedOptsAlias && result.method == IdentifierMethodDefaults.KEY))
        if (!managedMethodMatches) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "managed resolver returned a different method than trusted configuration for '$reference'"))
        }
        return Ok(VerificationMethodResolution(reference, key, trusted.controller, trusted.authorizedProofPurposes))
    }

    private fun validateTrustedOptions(reference: String, options: IdentifierOptsOrResult): String? {
        when (options) {
            is ExternalIdentifierJwksUrlOpts -> {
                val base = options.identifier
                val kid = options.lookup.kid
                val expected = if (kid.isNullOrBlank()) base else "$base#$kid"
                if (expected != reference) return "trusted JWKS options do not canonically identify '$reference'"
            }
            // X.509 and managed-key options are verifier-owned backing sources for an exact
            // public verification-method reference. Their internal representation is not a
            // public identifier and must never constrain, or leak into, proof.verificationMethod.
            is ExternalIdentifierX5cOpts -> Unit
            is ExternalIdentifierOIDFEntityIdOpts -> if (options.identifier != reference) return "trusted OIDF entity options do not canonically identify '$reference'"
            is ManagedOptsAlias -> Unit
        }
        return null
    }

    private fun matchesResolvedExternalMethod(
        options: IdentifierOptsOrResult,
        result: ExternalIdentifierResult,
    ): Boolean = when (options.method) {
        IdentifierMethodDefaults.JWKS_URL -> result is ExternalIdentifierResult.JwksUrl
        IdentifierMethodDefaults.X5C -> result is ExternalIdentifierResult.X5c
        IdentifierMethodDefaults.ENTITY_ID -> result is ExternalIdentifierResult.OIDFEntityId
        IdentifierMethodDefaults.DID -> result is ExternalIdentifierResult.Did
        else -> true
    }

    private fun missingPolicy(reference: String): IdkResult<VerificationMethodResolution, IdkErrorType> =
        Err(
            IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "no trusted verification-method policy is configured for '$reference'; non-DID identifiers are never inferred from their spelling",
            ),
        )

    private fun List<String>.authorizes(reference: String): Boolean = any { relationship ->
        relationship == reference || (relationship.startsWith("#") && reference.endsWith(relationship))
    }
}
