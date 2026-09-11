/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.mdoc.transfer

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.mdoc.engagement.DeviceEngagement

/**
 * Validates the origin assertion carried by a website DeviceEngagement.
 *
 * The expected domain must be obtained from a trusted user-agent/referrer
 * context. The ReaderEngagement endpoint is deliberately not accepted as an
 * origin source because it is attacker-controlled input at this boundary.
 */
object OriginInfoValidator {
    private const val TRUSTED_CATEGORY = 1u
    private const val OTHER_TYPE = 0u
    private const val DOMAIN_TYPE = 1u

    fun validate(
        engagement: DeviceEngagement,
        expectedDomain: String,
    ): IdkResult<Unit, IdkError> =
        validate(
            originInfos = (engagement as? DeviceEngagement.V1_1)?.originInfos,
            expectedDomain = expectedDomain,
        )

    fun validate(
        originInfos: Array<OriginInfo>?,
        expectedDomain: String,
    ): IdkResult<Unit, IdkError> =
        try {
            val normalizedExpected = normalizeDomain(expectedDomain, "expected domain")
            require(originInfos != null) { "DeviceEngagement OriginInfos are required" }
            val seenTypes = mutableSetOf<UInt>()

            originInfos.forEachIndexed { index, originInfo ->
                require(originInfo.cat.category == TRUSTED_CATEGORY) {
                    "OriginInfo[$index].cat is not the defined origin category"
                }
                require(seenTypes.add(originInfo.type.infoType)) {
                    "OriginInfo contains duplicate type ${originInfo.type.infoType}"
                }

                when (originInfo.type.infoType) {
                    DOMAIN_TYPE -> {
                        val details = requireNotNull(originInfo.details) { "Domain OriginInfo.details is required" }
                        require(details.size == 1 && details.containsKey(OriginInfoDetails.DOMAIN)) {
                            "Domain OriginInfo.details must contain only the domain key"
                        }
                        val domain = details[OriginInfoDetails.DOMAIN]
                        require(domain is String) { "Domain OriginInfo.domain must be a text string" }
                        val normalizedOrigin = normalizeDomain(domain, "OriginInfo.domain")
                        require(normalizedOrigin == normalizedExpected) {
                            "OriginInfo.domain does not match the requested domain"
                        }
                    }

                    OTHER_TYPE -> Unit
                    else -> {
                        // RFU origin types are not interpreted, but their
                        // structural uniqueness and category are still checked.
                    }
                }
            }

            require(originInfos.any { it.type.infoType == DOMAIN_TYPE }) {
                "A domain OriginInfo is required for website retrieval"
            }
            Ok(Unit)
        } catch (expected: Exception) {
            Err(
                IdkError.fromString(
                    code = "MDOC_ORIGIN_INFO_INVALID",
                    message = "Invalid mdoc origin information: ${expected.message}",
                    exception = expected,
                ),
            )
        }

    private fun normalizeDomain(
        value: String,
        field: String,
    ): String {
        val normalized = value.trim().removeSuffix(".").lowercase()
        require(normalized.isNotEmpty()) { "$field must not be empty" }
        require(normalized.none { it.isWhitespace() || it == '/' || it == ':' || it == '?' || it == '#' }) {
            "$field must contain a host name, not a URI"
        }
        return normalized
    }
}
