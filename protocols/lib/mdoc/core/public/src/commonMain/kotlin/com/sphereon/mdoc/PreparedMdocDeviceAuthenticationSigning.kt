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
 */

package com.sphereon.mdoc

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.cose.CoseAlgorithm
import kotlinx.serialization.Serializable

/**
 * Serializable public snapshot of the exact mdoc DeviceAuthentication COSE signing operation.
 *
 * The byte arrays contain the DeviceAuthentication payload and COSE `Sig_structure`; they may
 * include presentation data and should be kept only in the caller's protected interaction state.
 * No private key, opaque signer handle, or signature is included.
 */
@JsExportCompat
@Serializable
data class PreparedMdocDeviceAuthenticationSigning(
    val version: Int = CURRENT_VERSION,
    val docType: String,
    val keyReference: String,
    val providerId: String? = null,
    val algorithm: CoseAlgorithm,
    val sessionTranscriptCbor: ByteArray,
    val deviceAuthenticationPayload: ByteArray,
    /** Canonical CBOR map bytes for the protected header. */
    val protectedHeaderCbor: ByteArray,
    /** Canonical CBOR map bytes for the unprotected header; null preserves an absent header. */
    val unprotectedHeaderCbor: ByteArray? = null,
    /** Canonical, public-only COSE_Key CBOR; avoids serializer-dependent polymorphic key types. */
    val publicDeviceKeyCbor: ByteArray,
    /** Exact CBOR encoding of COSE `Sig_structure` for this payload and protected header. */
    val sigStructure: ByteArray,
) {
    companion object {
        const val CURRENT_VERSION: Int = 1
    }
}
