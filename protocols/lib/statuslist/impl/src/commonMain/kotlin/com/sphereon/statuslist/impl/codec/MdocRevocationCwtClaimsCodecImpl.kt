/*
 * Copyright (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.statuslist.impl.codec

import com.sphereon.cbor.Cbor
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborNInt
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborMap
import com.sphereon.cbor.CborString
import com.sphereon.cbor.CborTagged
import com.sphereon.cbor.CborUInt
import com.sphereon.statuslist.MdocRevocationCwtClaims
import com.sphereon.statuslist.MdocRevocationCwtClaimsCodec
import com.sphereon.statuslist.MdocStatusListPayload

/**
 * Codec for the CBOR claim map inside an ISO mdoc revocation CWT.
 *
 * The outer CWT claim labels are integer labels. The inner Token Status List payload is delegated
 * to [MdocStatusListCodec], which deliberately uses the mdoc text-key/binary-list representation
 * rather than the generic status-list integer-key representation.
 */
object MdocRevocationCwtClaimsCodecImpl : MdocRevocationCwtClaimsCodec {
    private const val ISS = 1L
    private const val SUB = 2L
    private const val EXP = 4L
    private const val IAT = 6L
    private const val IDENTIFIER_LIST = 65530L
    private const val STATUS_LIST = 65533L
    private const val TTL = 65534L

    override fun encode(claims: MdocRevocationCwtClaims): ByteArray {
        val values = mutableMapOf<CborItem<*>, CborItem<*>>()
        claims.issuer?.let { values[CborUInt(ISS)] = CborString(it) }
        claims.subject?.let { values[CborUInt(SUB)] = CborString(it) }
        claims.issuedAtEpochSeconds?.let { values[CborUInt(IAT)] = CborUInt(it) }
        values[CborUInt(EXP)] = CborUInt(claims.expiresAtEpochSeconds)
        claims.ttlSeconds?.let { values[CborUInt(TTL)] = CborUInt(it) }

        val claimLabel =
            when (claims.payload) {
                is MdocStatusListPayload.IdentifierList -> IDENTIFIER_LIST
                is MdocStatusListPayload.Token -> STATUS_LIST
            }
        values[CborUInt(claimLabel)] = Cbor.tryDecode(MdocStatusListCodec.encode(claims.payload)).getOrThrow()
        return Cbor.encode(CborMap(values))
    }

    override fun decode(encoded: ByteArray): MdocRevocationCwtClaims {
        // The shared COSE signer historically emits a CBOR data-item (tag 24) around
        // application payloads. ISO mdoc CWT implementations exist in both forms, so accept
        // the wrapper at this boundary while keeping the claim map itself strictly typed.
        val decoded = Cbor.tryDecode(encoded).getOrThrow()
        val claimsItem =
            when (decoded) {
                is CborEncodedItem<*> -> Cbor.tryDecode(decoded.value.taggedItem.value).getOrThrow()
                is CborTagged<*> -> if (decoded.tagNumber == 24) {
                    (decoded.taggedItem as? CborByteString)?.value?.let { Cbor.tryDecode(it).getOrThrow() }
                        ?: error("mdoc revocation CWT tag 24 must contain a byte string")
                } else decoded
                else -> decoded
            }
        val map = claimsItem as? CborMap<*, *>
            ?: error("mdoc revocation CWT claims must be a CBOR map")
        require(map.value.keys.all { it is CborUInt || it is CborNInt }) { "mdoc revocation CWT claim labels must be integers" }

        fun item(label: Long): CborItem<*>? =
            map.value.entries.firstOrNull { (key, _) ->
                when (key) {
                    is CborUInt -> key.value == label
                    else -> false
                }
            }?.value

        fun text(label: Long): String? =
            (item(label) as? CborString)?.value ?:
                if (item(label) == null) null else error("mdoc revocation CWT claim $label must be text")

        fun epoch(label: Long, required: Boolean): Long? {
            val value = item(label)
            if (value == null) {
                if (required) throw IllegalArgumentException("mdoc revocation CWT claim $label is required")
                return null
            }
            return (value as? CborUInt)?.value ?: error("mdoc revocation CWT claim $label must be unsigned")
        }

        val identifierList = item(IDENTIFIER_LIST)
        val statusList = item(STATUS_LIST)
        require((identifierList == null) xor (statusList == null)) {
            "mdoc revocation CWT must contain exactly one Identifier List or Token Status List claim"
        }
        val payload = MdocStatusListCodec.decode(Cbor.encode(identifierList ?: statusList!!))
        if (identifierList != null) {
            require(payload is MdocStatusListPayload.IdentifierList) {
                "mdoc Identifier List claim does not contain an Identifier List payload"
            }
        } else {
            require(payload is MdocStatusListPayload.Token) {
                "mdoc Token Status List claim does not contain a Token Status List payload"
            }
        }

        return MdocRevocationCwtClaims(
            issuer = text(ISS),
            subject = text(SUB),
            issuedAtEpochSeconds = epoch(IAT, required = false),
            expiresAtEpochSeconds = epoch(EXP, required = true)!!,
            ttlSeconds = epoch(TTL, required = false),
            payload = payload,
        )
    }
}
