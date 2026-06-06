/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.credential.issuance.pipeline

import com.sphereon.attribute.flow.AttributeBag
import com.sphereon.attribute.pipeline.LookupKeySet
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * Assembles the claims input for one credential from the pipeline's accumulated attributes and
 * lookup keys.
 *
 * Its job is narrow: take the bag plus lookups, apply the binding's optional semantic →
 * credential claim-name mapping, and produce the [AssembledClaims] that the issuer core's
 * `HandleCredentialRequestCommandImpl` feeds into its existing `IssuanceContext`. It does NOT
 * resolve selective disclosure / mandatory claims (the OCA-backed credential-design service
 * does) and it does NOT sign (the `CredentialFormatHandler` does).
 */
interface CredentialClaimsAssembler {
    suspend fun assemble(
        bag: AttributeBag,
        lookups: LookupKeySet,
        binding: CredentialClaimsBinding,
    ): IdkResult<AssembledClaims, IdkError>
}
