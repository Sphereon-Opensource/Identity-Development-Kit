/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.format

/**
 * Applies the configured clock-skew tolerance and rounds the result down to an hour boundary.
 *
 * RFC 9901 section 10.1 recommends deliberately general time claims so credentials issued
 * together cannot be correlated through a precise issuance instant. A whole-hour boundary also
 * keeps expiry claims coarse when a whole-day validity period is added to this value.
 */
internal fun roundedCredentialIssuanceEpochSeconds(
    nowEpochSeconds: Long,
    issuanceClockSkewInSeconds: Long,
): Long {
    val skewAdjusted = nowEpochSeconds - issuanceClockSkewInSeconds
    return skewAdjusted - skewAdjusted.mod(CREDENTIAL_TIME_ROUNDING_SECONDS)
}

internal const val CREDENTIAL_TIME_ROUNDING_SECONDS: Long = 60L * 60L
