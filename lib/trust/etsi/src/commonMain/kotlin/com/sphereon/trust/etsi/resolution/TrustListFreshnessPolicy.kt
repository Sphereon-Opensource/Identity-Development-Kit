/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.trust.etsi.resolution

import com.sphereon.trust.core.TrustDiagnosticReasonCodes
import kotlin.time.Instant

/** Fail-closed checks applied to a parsed ETSI trust list before it is trusted or cached. */
internal object TrustListFreshnessPolicy {
    fun failureReason(
        sequenceNumber: Int,
        nextUpdate: Instant,
        previousSequenceNumber: Int?,
        now: Instant,
    ): String? =
        when {
            nextUpdate <= now -> TrustDiagnosticReasonCodes.TRUST_LIST_NEXT_UPDATE_EXPIRED
            previousSequenceNumber != null && sequenceNumber < previousSequenceNumber ->
                TrustDiagnosticReasonCodes.TRUST_LIST_SEQUENCE_ROLLBACK
            else -> null
        }
}
