/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.sphereon.crypto.core.cose

import com.sphereon.cbor.AbstractCborInt
import com.sphereon.cbor.CDDL
import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborNull
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborUInt
import com.sphereon.cbor.NumberLabel
import com.sphereon.cbor.toNumberLabel

internal fun encodeCoseKey(value: CoseKey): ByteArray = value.original ?: Cbor.encode(encodeCoseKeyStructure(value))

internal fun encodeCoseKeyStructure(value: CoseKey): CborMap<NumberLabel, CborItem<*>> {
    val entries = mutableMapOf<NumberLabel, CborItem<*>>()
    value.additional?.value?.let { entries.putAll(it) }
    entries[CoseKey.KTY] = value.kty
    value.kid?.let { entries[CoseKey.KID] = it }
    value.alg?.let { entries[CoseKey.ALG] = it }
    value.key_ops?.let { entries[CoseKey.KEY_OPS] = it }
    value.baseIV?.let { entries[CoseKey.BASE_IV] = it }
    value.x5chain?.let { entries[CoseKey.X5_CHAIN] = it }

    when (CoseKeyTypeEnum.fromValue(value.kty.value)) {
        CoseKeyTypeEnum.RSA -> {
            value.n?.let { entries[CoseKey.N] = it }
            value.rsaE?.let { entries[CoseKey.E] = it }
            value.d?.let { entries[CoseKey.D_RSA] = it }
            value.rsaP?.let { entries[CoseKey.P] = it }
            value.rsaQ?.let { entries[CoseKey.Q] = it }
            value.rsaDP?.let { entries[CoseKey.DP] = it }
            value.rsaDQ?.let { entries[CoseKey.DQ] = it }
            value.rsaQInv?.let { entries[CoseKey.QINV] = it }
        }

        else -> {
            value.crv?.let { entries[CoseKey.CRV] = it }
            value.x?.let { entries[CoseKey.X] = it }
            value.y?.let { entries[CoseKey.Y] = it }
            value.d?.let { entries[CoseKey.D] = it }
        }
    }
    return CborMap(entries)
}

internal fun decodeCoseKey(m: CborMap<NumberLabel, CborItem<*>>): CoseKey {
    val kty = CoseKey.KTY.required<CborUInt>(m)
    val keyType = CoseKeyTypeEnum.fromValue(kty.value)
    val additional =
        mutableMapOf(
            *m.value.entries
                .filter { !CoseKey.labels.contains(it.key) }
                .map { it.key to it.value }
                .toTypedArray(),
        )

    return if (keyType == CoseKeyTypeEnum.RSA) {
        CoseKey(
            generateKid = false,
            kty = kty,
            kid = CoseKey.KID.optional(m),
            alg = CoseKey.ALG.optional(m),
            key_ops = CoseKey.KEY_OPS.optional(m),
            baseIV = CoseKey.BASE_IV.optional(m),
            x5chain = CoseKey.X5_CHAIN.optional(m),
            n = CoseKey.N.optional(m),
            rsaE = CoseKey.E.optional(m),
            d = CoseKey.D_RSA.optional(m),
            rsaP = CoseKey.P.optional(m),
            rsaQ = CoseKey.Q.optional(m),
            rsaDP = CoseKey.DP.optional(m),
            rsaDQ = CoseKey.DQ.optional(m),
            rsaQInv = CoseKey.QINV.optional(m),
            additional =
                if (additional.isEmpty()) {
                    null
                } else {
                    CborMap(additional)
                },
        )
    } else {
        CoseKey(
            generateKid = false,
            kty = kty,
            kid = CoseKey.KID.optional(m),
            alg = CoseKey.ALG.optional(m),
            key_ops = CoseKey.KEY_OPS.optional(m),
            baseIV = CoseKey.BASE_IV.optional(m),
            crv = CoseKey.CRV.optional(m),
            x5chain = CoseKey.X5_CHAIN.optional(m),
            x = CoseKey.X.optional(m),
            y = CoseKey.Y.optional(m),
            d = CoseKey.D.optional(m),
            additional =
                if (additional.isEmpty()) {
                    null
                } else {
                    CborMap(additional)
                },
        )
    }
}

internal fun encodeCoseHeader(value: CoseHeaderCbor): ByteArray = Cbor.encode(encodeCoseHeaderStructure(value))

internal fun encodeCoseHeaderStructure(value: CoseHeaderCbor): CborMap<NumberLabel, CborItem<*>> =
    CborMap(
        buildMap {
            value.alg
                ?.value
                ?.toInt()
                ?.toNumberLabel()
                ?.let { put(CoseHeaderCbor.ALG, it) }
            value.crit?.let { put(CoseHeaderCbor.CRIT, it) }
            value.contentType?.let { put(CoseHeaderCbor.CONTENT_TYPE, it) }
            value.typ?.let { put(CoseHeaderCbor.TYP, it) }
            value.kid?.let { put(CoseHeaderCbor.KID, it) }
            value.iv?.let { put(CoseHeaderCbor.IV, it) }
            value.partialIv?.let { put(CoseHeaderCbor.PARTIAL_IV, it) }
            value.x5chain?.let { chain ->
                put(
                    CoseHeaderCbor.X5CHAIN,
                    if (chain.value.size == 1) {
                        chain.value[0]
                    } else {
                        chain
                    }
                )
            }
        }.toMutableMap(),
    )

internal fun decodeCoseHeader(m: CborMap<NumberLabel, CborItem<*>>): CoseHeaderCbor {
    val algValue =
        CoseHeaderCbor.ALG
            .optional<AbstractCborInt<Long>>(m)
            ?.let {
                if (it.cddl == CDDL.nint) {
                    -it.value.toInt()
                } else {
                    it.value.toInt()
                }
            }

    var x5Chain = CoseHeaderCbor.X5CHAIN.optional<CborItem<*>>(m)
    if (x5Chain is CborByteString) {
        x5Chain = CborArray(mutableListOf(x5Chain))
    }
    @Suppress("UNCHECKED_CAST")
    return CoseHeaderCbor(
        alg = CoseAlgorithm.fromValue(algValue),
        crit = CoseHeaderCbor.CRIT.optional(m),
        contentType = CoseHeaderCbor.CONTENT_TYPE.optional(m),
        kid = CoseHeaderCbor.KID.optional(m),
        iv = CoseHeaderCbor.IV.optional(m),
        partialIv = CoseHeaderCbor.PARTIAL_IV.optional(m),
        x5chain = x5Chain as? CborArray<CborByteString>,
        typ = CoseHeaderCbor.TYP.optional(m),
    )
}

internal fun encodeCoseSign1(value: CoseSign1<*>): ByteArray = Cbor.encode(encodeCoseSign1Structure(value))

internal fun encodeCoseSign1Structure(value: CoseSign1<*>): CborArray<CborItem<*>> =
    CborArray(
        mutableListOf(
            CborByteString(encodeCoseHeader(value.protectedHeader)),
            value.unprotectedHeader?.let(::encodeCoseHeaderStructure) ?: encodeCoseHeaderStructure(CoseHeaderCbor()),
            value.payload ?: CborNull(),
            value.signature,
        ),
    )

internal fun encodeCoseMac0(value: CoseMac0Cbor): ByteArray = Cbor.encode(encodeCoseMac0Structure(value))

internal fun encodeCoseMac0Structure(value: CoseMac0Cbor): CborArray<CborItem<*>> =
    CborArray(
        mutableListOf(
            CborByteString(encodeCoseHeader(value.protectedHeader)),
            value.unprotectedHeader?.let(::encodeCoseHeaderStructure) ?: CborNull(),
            value.payload ?: CborNull(),
            value.tag,
        ),
    )

internal fun encodeCoseSignatureStructure(value: CoseSignatureStructureCbor): ByteArray =
    Cbor.encode(
        CborArray(
            mutableListOf<CborItem<*>>(value.structure, value.bodyProtected).apply {
                value.signProtected?.let { add(it) }
                add(value.externalAad)
                add(value.payload)
            },
        ),
    )

@Suppress("MagicNumber")
internal fun decodeCoseSignatureStructure(a: CborArray<CborItem<*>>): CoseSignatureStructureCbor =
    if (a.value.size == 4) {
        CoseSignatureStructureCbor(
            structure = a.required(0),
            bodyProtected = a.required(1),
            externalAad = a.required(2),
            payload = a.required(3),
        )
    } else {
        CoseSignatureStructureCbor(
            structure = a.required(0),
            bodyProtected = a.required(1),
            signProtected = a.optional(2),
            externalAad = a.required(3),
            payload = a.required(4),
        )
    }

internal fun encodeCoseMacStructure(value: CoseMacStructureCbor): ByteArray = Cbor.encode(CborArray(mutableListOf(CborString(value.context.value), value.protected, value.externalAad, value.payload)))

@Suppress("MagicNumber")
internal fun decodeCoseMacStructure(a: CborArray<CborItem<*>>): CoseMacStructureCbor =
    CoseMacStructureCbor(
        context = a.required(0),
        protected = a.required(1),
        externalAad = a.required(2),
        payload = a.required(3),
    )

internal fun encodeToBeSigned(value: ToBeSignedCbor): ByteArray = Cbor.encode(CborByteString(value.value))
