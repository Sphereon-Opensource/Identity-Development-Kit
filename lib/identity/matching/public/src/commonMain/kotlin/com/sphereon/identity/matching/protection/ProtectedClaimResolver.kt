/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.identity.matching.protection

import com.sphereon.data.store.party.model.IdentifierProtectionMode
import com.sphereon.data.store.party.result.IdentityIdentifierResult
import kotlin.uuid.ExperimentalUuidApi

/**
 * Resolves the readable claim value of a (possibly protected) correlation
 * identifier for claim-emitting surfaces (userinfo, attribute pipelines,
 * credential claim mapping):
 *
 *  - no protection envelope, or PLAINTEXT mode → the readable value as-is
 *  - reversible (ciphertext present) → [IdentifierProtector.reveal]
 *  - non-reversible blinded value without ciphertext, or a failed reveal →
 *    `null` (the claim VALUE is omitted; verified flags derived from the row,
 *    e.g. `email_verified`, are kept by the caller)
 *
 * For blinded rows the identifier's `value` column carries the blind index,
 * which must never leak as a claim — hence the strict null on the
 * non-reversible branch.
 */
class ProtectedClaimResolver(
    private val protector: IdentifierProtector,
) {
    suspend fun claimValue(identifier: IdentityIdentifierResult): String? {
        val envelope =
            identifier.protectedValue
                ?: return identifier.value
        return when {
            envelope.mode == IdentifierProtectionMode.PLAINTEXT -> {
                envelope.plaintext ?: identifier.value
            }

            envelope.valueCiphertext != null -> {
                protector
                    .reveal(
                        protected = envelope,
                        tenantId = identifier.tenantId,
                        identityId = identifier.identityId.toString(),
                        type = identifier.identifierType,
                    ).getOrNull()
            }

            else -> {
                null
            }
        }
    }
}
