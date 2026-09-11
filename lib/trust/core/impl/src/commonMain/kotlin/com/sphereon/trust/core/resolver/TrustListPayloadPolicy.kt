/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.resolver

import com.sphereon.trust.core.TrustDiagnosticReasonCodes

internal object TrustListPayloadPolicy {
    fun enforce(
        data: ByteArray,
        maxBodyBytes: Long,
    ) {
        if (maxBodyBytes <= 0L) {
            throw TrustListResolutionException(
                "Trust-list response body limit is invalid",
                reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_BODY_TOO_LARGE,
            )
        }
        if (data.size.toLong() > maxBodyBytes) {
            throw TrustListResolutionException(
                "Trust-list response exceeds configured body limit of $maxBodyBytes bytes",
                reasonCode = TrustDiagnosticReasonCodes.TRUST_LIST_BODY_TOO_LARGE,
            )
        }
    }
}
