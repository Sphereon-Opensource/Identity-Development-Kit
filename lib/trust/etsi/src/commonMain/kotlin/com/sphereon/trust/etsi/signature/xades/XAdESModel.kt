/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.trust.etsi.signature.xades

import kotlinx.datetime.Instant

/**
 * XAdES QualifyingProperties model — data classes representing the
 * XAdES (XML Advanced Electronic Signatures) properties found in
 * ETSI trust list signatures.
 *
 * Supports XAdES-B (Baseline) profile. Higher profiles (T, LT, LTA)
 * can be extended by adding to UnsignedProperties.
 */

/** Namespace URI for XAdES 1.3.2 */
const val XADES_NS = "http://uri.etsi.org/01903/v1.3.2#"

/** Namespace URI for XAdES 1.4.1 */
const val XADES_141_NS = "http://uri.etsi.org/01903/v1.4.1#"

/** XML Signature namespace */
const val XMLDSIG_NS = "http://www.w3.org/2000/09/xmldsig#"

/** Reference type for XAdES SignedProperties */
const val SIGNED_PROPERTIES_TYPE = "http://uri.etsi.org/01903#SignedProperties"

data class QualifyingProperties(
    val target: String?,
    val signedProperties: SignedProperties?,
    val unsignedProperties: UnsignedProperties? = null
)

data class SignedProperties(
    val id: String?,
    val signedSignatureProperties: SignedSignatureProperties?,
    val signedDataObjectProperties: SignedDataObjectProperties? = null
)

data class SignedSignatureProperties(
    val signingTime: Instant?,
    val signingCertificateV2: List<CertDigest>?,
    /** Legacy v1 signing certificate (for older trust lists) */
    val signingCertificate: List<CertDigest>? = null
)

data class CertDigest(
    val digestAlgorithm: String,
    val digestValue: ByteArray,
    val issuerSerialV2: ByteArray? = null,
    /** v1 fields */
    val issuerName: String? = null,
    val serialNumber: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as CertDigest
        if (digestAlgorithm != other.digestAlgorithm) return false
        if (!digestValue.contentEquals(other.digestValue)) return false
        if (issuerSerialV2 != null) {
            if (other.issuerSerialV2 == null) return false
            if (!issuerSerialV2.contentEquals(other.issuerSerialV2)) return false
        } else if (other.issuerSerialV2 != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = digestAlgorithm.hashCode()
        result = 31 * result + digestValue.contentHashCode()
        result = 31 * result + (issuerSerialV2?.contentHashCode() ?: 0)
        return result
    }
}

data class SignedDataObjectProperties(
    val dataObjectFormats: List<DataObjectFormat> = emptyList()
)

data class DataObjectFormat(
    val objectReference: String?,
    val mimeType: String?
)

data class UnsignedProperties(
    val unsignedSignatureProperties: UnsignedSignatureProperties? = null
)

data class UnsignedSignatureProperties(
    val signatureTimestamps: List<SignatureTimestamp> = emptyList()
)

data class SignatureTimestamp(
    val encapsulatedTimestamp: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false
        other as SignatureTimestamp
        return encapsulatedTimestamp.contentEquals(other.encapsulatedTimestamp)
    }

    override fun hashCode(): Int = encapsulatedTimestamp.contentHashCode()
}
