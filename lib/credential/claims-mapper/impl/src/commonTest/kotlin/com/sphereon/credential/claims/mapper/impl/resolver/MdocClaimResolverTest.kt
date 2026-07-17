/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.credential.claims.mapper.impl.resolver

import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.TDate
import com.sphereon.cbor.toCborItem
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.IssuerSigned
import com.sphereon.mdoc.data.device.IssuerSignedCborCodecImpl
import com.sphereon.mdoc.data.device.IssuerSignedItem
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.mdoc.data.device.RandomValue
import com.sphereon.mdoc.data.mso.DeviceKeyInfo
import com.sphereon.mdoc.data.mso.DigestAlgorithm
import com.sphereon.mdoc.data.mso.DigestID
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodecImpl
import com.sphereon.mdoc.data.mso.MsoVersion
import com.sphereon.mdoc.data.mso.ValidityInfo
import com.sphereon.openid.oid4vp.common.CredentialFormat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MdocClaimResolverTest {
    private val issuerSignedCodec = IssuerSignedCborCodecImpl()
    private val resolver = MdocClaimResolver(issuerSignedCodec)

    @Test
    fun extractsSelectedIssuerSignedDataElementsFromRealMdocCbor() = runTest {
        val credential = issuerSignedCodec.encode(testIssuerSigned()).getOrThrow().encodeToBase64Url()

        val result = resolver.extractClaims(
            credential = credential,
            format = CredentialFormat.MSO_MDOC,
            claimPaths = listOf(
                listOf(NAMESPACE, "family_name"),
                listOf(NAMESPACE, "age_over_18"),
                listOf(NAMESPACE, "not_present"),
            ),
        )

        assertTrue(result.isOk)
        assertEquals(JsonPrimitive("Lindgren"), result.value["$NAMESPACE.family_name"])
        assertEquals(JsonPrimitive(true), result.value["$NAMESPACE.age_over_18"])
        assertFalse(result.value.containsKey("$NAMESPACE.not_present"))
    }

    @Test
    fun rejectsNonMdocInputWithoutReturningRawContent() = runTest {
        val result = resolver.extractAllClaims("not-an-mdoc", CredentialFormat.MSO_MDOC)

        assertTrue(result.isErr)
        assertEquals("EXTRACTION_FAILED", result.error.code)
    }

    private fun testIssuerSigned(): IssuerSigned =
        IssuerSigned(
            nameSpaces = mapOf(
                NameSpace(NAMESPACE) to arrayOf(
                    issuerSignedItem(1u, "family_name", "Lindgren"),
                    issuerSignedItem(2u, "age_over_18", true),
                ),
            ),
            issuerAuth = testIssuerAuth(),
            original = null,
        )

    private fun testIssuerAuth(): CoseSign1<MobileSecurityObject> {
        val now = TDate("2026-07-14T00:00:00Z")
        val mso = MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo = DeviceKeyInfo(
                deviceKey = CoseKeyJson.Builder()
                    .withKty(CoseKeyTypeEnum.EC2)
                    .withCrv(CoseCurve.P_256)
                    .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
                    .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
                    .build()
                    .toCbor(),
                keyAuthorizations = null,
                keyInfo = null,
                original = null,
            ),
            docType = DocType(NAMESPACE),
            validityInfo = ValidityInfo(now, now, now, null),
            original = null,
        )
        return CoseSign1(
            protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
            unprotectedHeader = CoseHeaderCbor(),
            payload = CborByteString(MobileSecurityObjectCborCodecImpl().encodeTag24(mso).getOrThrow()),
            signature = CborByteString(ByteArray(64) { it.toByte() }),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun issuerSignedItem(
        digestId: UInt,
        elementId: String,
        value: Any,
    ): CborEncodedItem<IssuerSignedItem<Any>> {
        val item = IssuerSignedItem(
            digestID = DigestID(digestId),
            random = RandomValue(ByteArray(24) { 0x42 }),
            elementIdentifier = DataElementIdentifier(elementId),
            elementValue = value,
        )
        val encoded =
            CborMap(
                mutableMapOf(
                    IssuerSignedItem.DIGEST_ID to CborUInt(digestId.toLong()),
                    IssuerSignedItem.RANDOM to item.random.toCborItem(),
                    IssuerSignedItem.ELEMENT_IDENTIFIER to CborString(elementId),
                    IssuerSignedItem.ELEMENT_VALUE to value.toCborItem(),
                ),
            ).encodeCbor()
        return CborEncodedItem(encoded, item)
    }

    private companion object {
        const val NAMESPACE = "eu.europa.ec.eudi.pid.1"
    }
}
