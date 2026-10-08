/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.mdoc.transfer

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.engagement.DeviceEngagementSecurity
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OriginInfoValidatorTest {
    private fun engagement(originInfos: Array<OriginInfo>): DeviceEngagement =
        DeviceEngagement.V1_1(
            security =
                DeviceEngagementSecurity(
                    cipherSuite = 1u,
                    eDeviceKeyBytes = CborEncodedItem(createTestKeyBytes(), createTestKey()),
                ),
            originInfos = originInfos,
            original = null,
        )

    private fun createTestKey(): CoseKey =
        CoseKeyJson
            .Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
            .build()
            .toCbor()

    private fun createTestKeyBytes(): ByteArray = byteArrayOf(0x01)

    private fun domainOrigin(domain: Any?): OriginInfo =
        OriginInfo(
            cat = OriginInfoCategory(1u),
            type = OriginInfoType(1u),
            details = OriginInfoDetails(mapOf(OriginInfoDetails.DOMAIN to domain)),
            original = null,
        )

    @Test
    fun matchingDomainIsAcceptedCaseInsensitively() {
        val result = OriginInfoValidator.validate(engagement(arrayOf(domainOrigin("Reader.Example.com"))), "reader.example.com.")

        assertTrue(result.isOk, result.toString())
    }

    @Test
    fun mismatchingDomainIsRejected() {
        val result = OriginInfoValidator.validate(engagement(arrayOf(domainOrigin("attacker.example.com"))), "reader.example.com")

        assertFalse(result.isOk)
    }

    @Test
    fun emptyOrUriDomainIsRejected() {
        assertFalse(OriginInfoValidator.validate(engagement(arrayOf(domainOrigin(""))), "reader.example.com").isOk)
        assertFalse(OriginInfoValidator.validate(engagement(arrayOf(domainOrigin("https://reader.example.com"))), "reader.example.com").isOk)
    }

    @Test
    fun duplicateOriginTypesAreRejected() {
        val result =
            OriginInfoValidator.validate(
                engagement(arrayOf(domainOrigin("reader.example.com"), domainOrigin("reader.example.com"))),
                "reader.example.com",
            )

        assertFalse(result.isOk)
    }

    @Test
    fun legacyV10EngagementCannotAssertWebsiteOrigin() {
        val result =
            OriginInfoValidator.validate(
                DeviceEngagement.V1_0(
                    security =
                        DeviceEngagementSecurity(
                            cipherSuite = 1u,
                            eDeviceKeyBytes = CborEncodedItem(createTestKeyBytes(), createTestKey()),
                        ),
                    original = null,
                ),
                "reader.example.com",
            )

        assertFalse(result.isOk)
    }
}
