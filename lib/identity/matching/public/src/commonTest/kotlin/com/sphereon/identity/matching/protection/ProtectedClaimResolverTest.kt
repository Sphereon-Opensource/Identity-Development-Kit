/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

@file:OptIn(ExperimentalUuidApi::class, ExperimentalTime::class)

package com.sphereon.identity.matching.protection

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.party.model.IdentifierProtectionMode
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.data.store.party.model.ProtectedIdentifierValue
import com.sphereon.data.store.party.result.IdentityIdentifierResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Claim-value resolution for protected identity identifiers:
 *  - plaintext rows surface as-is,
 *  - reversible envelopes are revealed via the protector,
 *  - non-reversible blinded rows yield null (claim value omitted, verified flag
 *    derivable from the row is kept by the caller).
 */
class ProtectedClaimResolverTest {
    private val tenant = "t1"
    private val identityId = Uuid.random()
    private val t0: Instant = Instant.fromEpochSeconds(1_700_000_000)
    private val resolver = ProtectedClaimResolver(CiphertextPrefixProtector())

    @Test
    fun plaintextEnvelopeSurfacesReadableValue() =
        runTest {
            val row =
                identifierRow(
                    value = "alice@example.com",
                    envelope =
                        ProtectedIdentifierValue(
                            mode = IdentifierProtectionMode.PLAINTEXT,
                            plaintext = "alice@example.com",
                        ),
                )
            assertEquals("alice@example.com", resolver.claimValue(row))
        }

    @Test
    fun missingEnvelopeSurfacesValueAsIs() =
        runTest {
            val row = identifierRow(value = "alice@example.com", envelope = null)
            assertEquals("alice@example.com", resolver.claimValue(row))
        }

    @Test
    fun reversibleEnvelopeIsRevealed() =
        runTest {
            val row =
                identifierRow(
                    value = "hmac:$tenant:alice@example.com", // lookup column = blind index
                    envelope =
                        ProtectedIdentifierValue(
                            mode = IdentifierProtectionMode.SEARCHABLE_BLIND_INDEX,
                            valueCiphertext = "ct:alice@example.com",
                            valueHmac = "hmac:$tenant:alice@example.com",
                        ),
                )
            assertEquals("alice@example.com", resolver.claimValue(row))
        }

    @Test
    fun nonReversibleEnvelopeOmitsClaimValue() =
        runTest {
            val row =
                identifierRow(
                    value = "hmac:salted:alice@example.com",
                    envelope =
                        ProtectedIdentifierValue(
                            mode = IdentifierProtectionMode.SALTED_BLINDED,
                            valueHmac = "hmac:salted:alice@example.com",
                        ),
                )
            // The blind index must NEVER leak as a claim value.
            assertNull(resolver.claimValue(row))
        }

    private fun identifierRow(
        value: String,
        envelope: ProtectedIdentifierValue?,
    ): IdentityIdentifierResult =
        IdentityIdentifierResult(
            identityIdentifierId = Uuid.random(),
            identityId = identityId,
            tenantId = tenant,
            identifierType = IdentifierType("email"),
            value = value,
            protectedValue = envelope,
            isPrimary = true,
            isVerified = true,
            verifiedAt = t0,
            validFrom = t0,
            createdAt = t0,
            updatedAt = t0,
        )
}

/**
 * Deterministic [IdentifierProtector] for this test: ciphertext is the readable
 * value behind a `ct:` prefix, reveal strips it; blind index = `hmac:<tenant>:<value>`.
 * Only [reveal] is exercised by the resolver; protect/blindIndex are provided
 * for interface completeness with the same derivation.
 */
private class CiphertextPrefixProtector : IdentifierProtector {
    override suspend fun protect(
        tenantId: String,
        identityId: String?,
        type: IdentifierType,
        plaintext: String,
        policy: IdentifierProtectionPolicy,
        salt: ByteArray?,
    ): IdkResult<ProtectedIdentifierValue, IdkError> {
        val normalized = normalizeIdentifier(plaintext, policy.normalization)
        return when (policy.mode) {
            IdentifierProtectionMode.PLAINTEXT -> {
                Ok(
                    ProtectedIdentifierValue(mode = IdentifierProtectionMode.PLAINTEXT, plaintext = normalized),
                )
            }

            IdentifierProtectionMode.SEARCHABLE_ENCRYPTED,
            IdentifierProtectionMode.SEARCHABLE_BLIND_INDEX -> {
                Ok(
                    ProtectedIdentifierValue(
                        mode = policy.mode,
                        valueCiphertext = "ct:$normalized",
                        valueHmac = "hmac:$tenantId:$normalized",
                    ),
                )
            }

            IdentifierProtectionMode.SALTED_BLINDED -> {
                if (salt == null) {
                    Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "SALTED_BLINDED protection requires a non-null salt"))
                } else {
                    Ok(ProtectedIdentifierValue(mode = policy.mode, valueHmac = "hmac:salted:$normalized"))
                }
            }
        }
    }

    override suspend fun blindIndex(
        tenantId: String,
        type: IdentifierType,
        plaintext: String,
        policy: IdentifierProtectionPolicy,
        salt: ByteArray?,
    ): IdkResult<String, IdkError> = Ok("hmac:$tenantId:" + normalizeIdentifier(plaintext, policy.normalization))

    override suspend fun reveal(
        protected: ProtectedIdentifierValue,
        tenantId: String,
        identityId: String?,
        type: IdentifierType,
    ): IdkResult<String, IdkError> =
        protected.valueCiphertext?.removePrefix("ct:")?.let { Ok(it) }
            ?: Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "no ciphertext"))
}
