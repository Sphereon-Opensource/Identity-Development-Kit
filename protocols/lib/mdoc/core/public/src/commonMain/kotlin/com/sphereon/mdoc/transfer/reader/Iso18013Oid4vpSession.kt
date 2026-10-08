/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.mdoc.transfer.reader

import com.sphereon.cbor.CborArray
import com.sphereon.cbor.CborItem
import com.sphereon.cbor.dsl.cborArray
import com.sphereon.cbor.dsl.encode
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import kotlin.experimental.ExperimentalObjCName
import kotlin.js.JsStatic
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * ISO/IEC TS 18013-7 Annex B OID4VP handover.
 *
 * This is deliberately a different type from [com.sphereon.mdoc.transfer.reader.OID4VPHandover],
 * which represents the OpenID4VP 1.0 final handover used by the regular OID4VP/DCQL profile.
 * Annex B hashes the client and response URI together with the mdoc-generated nonce and
 * carries the authorization-request nonce as the third element:
 *
 * ```
 * OID4VPHandover = [ clientIdHash, responseUriHash, nonce ]
 * clientIdHash = SHA-256(CBOR([clientId, mdocGeneratedNonce]))
 * responseUriHash = SHA-256(CBOR([responseUri, mdocGeneratedNonce]))
 * ```
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Iso18013Oid4vpHandover", exact = true)
data class Iso18013Oid4vpHandover(
    val clientIdHash: ByteArray,
    val responseUriHash: ByteArray,
    val nonce: String,
) : Handover<Iso18013Oid4vpHandover, CborArray<CborItem<*>>>() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Iso18013Oid4vpHandover) return false
        return clientIdHash.contentEquals(other.clientIdHash) &&
            responseUriHash.contentEquals(other.responseUriHash) &&
            nonce == other.nonce
    }

    override fun hashCode(): Int {
        var result = clientIdHash.contentHashCode()
        result = 31 * result + responseUriHash.contentHashCode()
        result = 31 * result + nonce.hashCode()
        return result
    }

    companion object {
        /**
         * Reconstruct the Annex B handover from the authorization-request values.
         * The mdoc-generated nonce must be generated once per presentation and reused
         * for both hashes; it must not be replaced by the authorization-request nonce.
         */
        @JsStatic
        @JvmStatic
        fun fromInputs(
            clientId: String,
            responseUri: String,
            mdocGeneratedNonce: String,
            nonce: String,
        ): Iso18013Oid4vpHandover {
            require(clientId.isNotEmpty()) { "clientId must not be empty" }
            require(responseUri.isNotEmpty()) { "responseUri must not be empty" }
            require(mdocGeneratedNonce.isNotEmpty()) { "mdocGeneratedNonce must not be empty" }
            require(nonce.isNotEmpty()) { "nonce must not be empty" }

            return Iso18013Oid4vpHandover(
                clientIdHash = sha256OfPair(clientId, mdocGeneratedNonce),
                responseUriHash = sha256OfPair(responseUri, mdocGeneratedNonce),
                nonce = nonce,
            )
        }

        private fun sha256OfPair(
            first: String,
            second: String,
        ): ByteArray =
            hash(
                cborArray {
                    add(first)
                    add(second)
                }.encode(),
                DigestAlg.SHA256,
            )
    }
}
