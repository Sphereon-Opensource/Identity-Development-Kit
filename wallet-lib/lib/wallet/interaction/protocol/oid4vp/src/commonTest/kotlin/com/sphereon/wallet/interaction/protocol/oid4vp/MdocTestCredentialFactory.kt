/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.protocol.oid4vp

import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.TDate
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.cose.CoseAlgorithm
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.IssuerSigned
import com.sphereon.mdoc.data.device.IssuerSignedCborCodecImpl
import com.sphereon.mdoc.data.mso.DeviceKeyInfo
import com.sphereon.mdoc.data.mso.DigestAlgorithm
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodecImpl
import com.sphereon.mdoc.data.mso.MsoVersion
import com.sphereon.mdoc.data.mso.ValidityInfo

internal fun mdocCredential(
    validFrom: String,
    validUntil: String,
): String {
    val msoCodec = MobileSecurityObjectCborCodecImpl()
    val issuerSignedCodec = IssuerSignedCborCodecImpl()
    val mso =
        MobileSecurityObject(
            version = MsoVersion("1.0"),
            digestAlgorithm = DigestAlgorithm("SHA-256"),
            valueDigests = emptyMap(),
            deviceKeyInfo =
                DeviceKeyInfo(
                    deviceKey =
                        CoseKeyJson
                            .Builder()
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
            docType = DocType("eu.europa.ec.eudi.pid.1"),
            validityInfo =
                ValidityInfo(
                    signed = TDate(validFrom),
                    validFrom = TDate(validFrom),
                    validUntil = TDate(validUntil),
                ),
            original = null,
        )
    val issuerSigned =
        IssuerSigned(
            nameSpaces = emptyMap(),
            issuerAuth =
                CoseSign1<MobileSecurityObject>(
                    protectedHeader = CoseHeaderCbor(alg = CoseAlgorithm.ES256),
                    unprotectedHeader = CoseHeaderCbor(),
                    payload = CborByteString(msoCodec.encodeTag24(mso).getOrThrow()),
                    signature = CborByteString(ByteArray(64) { it.toByte() }),
                ),
            original = null,
        )
    return issuerSignedCodec.encode(issuerSigned).getOrThrow().encodeToBase64Url()
}
