/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.crypto.dataintegrity.resolution

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.crypto.resolution.IdentifierOptsOrResult

/**
 * Identifier-neutral verification-method resolution for VC Data Integrity.
 *
 * Implementations may use DID, HTTPS/JWKS, X.509, managed KMS, or another
 * registered identifier method. A cryptosuite must not infer controller or
 * proof-purpose authorization from the spelling of an identifier.
 */
interface VerificationMethodResolver {
    suspend fun resolve(reference: String): IdkResult<VerificationMethodResolution, IdkErrorType>

    /**
     * Resolve a non-DID verification method using an explicitly trusted,
     * exact-reference policy entry.
     *
     * HTTPS/JWKS, X.509, OIDF and managed identifiers do not carry an
     * authenticated controller/proof-purpose relationship in their spelling.
     * Callers therefore have to supply the canonical identifier options and
     * trusted relationship metadata out of their verifier configuration. The
     * proof itself must never be used to construct this policy.
     *
     * The default keeps source compatibility for resolver implementations that
     * only support their own one-argument resolution path. Implementations
     * which resolve identifier-backed methods must override it.
     */
    suspend fun resolve(
        reference: String,
        policy: VerificationMethodResolutionPolicy,
    ): IdkResult<VerificationMethodResolution, IdkErrorType> = resolve(reference)
}

/**
 * Trusted configuration for one verification method reference.
 *
 * [reference] is the exact value that may occur in a Data Integrity proof;
 * prefix, host and fragment substitutions are not permitted. [identifierOpts]
 * is the canonical, unresolved [IdentifierOptsOrResult] selected by the
 * application (for example a JWKS URL with an explicit kid, an X.509 chain,
 * or a managed alias). It is intentionally not inferred from [reference].
 */
data class TrustedVerificationMethod(
    val reference: String,
    val identifierOpts: IdentifierOptsOrResult,
    val controller: String,
    val authorizedProofPurposes: Set<ProofPurpose>,
) {
    init {
        require(reference.isNotBlank()) { "verification method reference must not be blank" }
        require(controller.isNotBlank()) { "verification method controller must not be blank" }
        require(authorizedProofPurposes.isNotEmpty()) { "at least one proof purpose must be authorized" }
        require(!identifierOpts.isResolved) {
            "trusted verification method configuration must contain unresolved identifier options"
        }
    }
}

/**
 * Exact-reference trusted verification-method policy.
 *
 * This type is deliberately immutable and rejects duplicate references. A
 * verifier should construct it from its trusted issuer/holder configuration,
 * never from proof or token input.
 */
class VerificationMethodResolutionPolicy private constructor(
    private val entries: Map<String, TrustedVerificationMethod>,
) {
    fun entry(reference: String): TrustedVerificationMethod? = entries[reference]

    companion object {
        fun of(entries: Iterable<TrustedVerificationMethod>): VerificationMethodResolutionPolicy {
            val configured = entries.toList()
            require(configured.map { it.reference }.distinct().size == configured.size) {
                "trusted verification method references must be unique"
            }
            return VerificationMethodResolutionPolicy(configured.associateBy { it.reference })
        }

        fun empty(): VerificationMethodResolutionPolicy = VerificationMethodResolutionPolicy(emptyMap())
    }
}

/** Key material and authenticated relationship metadata for a verification method. */
data class VerificationMethodResolution(
    val reference: String,
    val key: JwkType,
    /** Authenticated controller, when supplied by the resolution source. */
    val controller: String?,
    /** Proof purposes explicitly authorized by the resolution source. */
    val authorizedProofPurposes: Set<ProofPurpose>,
)
