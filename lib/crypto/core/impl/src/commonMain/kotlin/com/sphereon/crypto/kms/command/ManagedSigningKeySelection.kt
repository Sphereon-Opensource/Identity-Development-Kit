/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.command

import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.toKeyReferenceOrNull
import com.sphereon.crypto.core.toSigningKeyReferenceOrNull

/** Keeps inline material intact and otherwise produces the transport-safe managed selector. */
internal fun KeyInfoType<*>.toSigningCommandKeyInfo(): KeyInfoType<*> {
    val inline = key
    return when {
        inline == null -> toSigningKeyReferenceOrNull() ?: toKeyReferenceOrNull() ?: this
        inline is CoseKey ->
            // The CBOR COSE key has no kotlinx serializer and cannot cross the command
            // transport. Preserve the inline material directly, even when selector metadata is
            // also present; inline key material remains authoritative for this conversion.
            ResolvedKeyInfo(
                kid = kid,
                key = inline.toJson(),
                keyVisibility = keyVisibility,
                signatureAlgorithm = signatureAlgorithm,
                alias = alias,
                x5c = x5c,
                providerId = providerId,
                keyType = keyType,
                keyEncoding = keyEncoding,
            )
        else -> this
    }
}

/**
 * Proves that a compound managed-key selector identifies one canonical provider key.
 *
 * Inline key material remains authoritative and is validated by the provider's existing
 * inline-key policy. Single-coordinate selectors retain their established provider lookup
 * semantics. Only an alias plus kid needs this cross-check: resolve by alias alone, without
 * copying the caller-controlled kid onto the result, then compare the canonical kid before
 * any signing operation is invoked.
 */
