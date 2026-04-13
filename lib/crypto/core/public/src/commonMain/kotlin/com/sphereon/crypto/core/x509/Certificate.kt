/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.crypto.core.x509

import com.sphereon.cbor.instantToDateStringISO
import com.sphereon.core.api.Base64Serializer
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.encodeTo
import com.sphereon.crypto.core.CoseJoseKeyMappingService
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.interop.toSignatureAlgorithm
import com.sphereon.crypto.core.interop.toSignumX509Certificate
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import kotlinx.serialization.Serializable
import com.sphereon.core.compat.JsExportCompat
import kotlinx.datetime.Instant
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * A X509 certificate. It is encoded in DER format as bytes in the value field.
 *
 * // TODO: Makes sense to have a function to get the pub key
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("Certificate", exact = true)
data class Certificate(
    // Cert in DER format. We choose base64 here is x5c's are base64 and not base64 url like other JWK values.
    @Serializable(with = Base64Serializer::class)
    val der: ByteArray,
    val fingerPrint: String,
    val serialNumber: String? = null,
    val issuerDN: String,
    val subjectDN: String,
    val notBefore: Instant,
    val notAfter: Instant,
    val keyUsage: KeyUsage? = null,
    val subjectAlternativeNames: SubjectAlternativeName? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as Certificate

        if (!der.contentEquals(other.der)) return false
        if (fingerPrint != other.fingerPrint) return false
        if (serialNumber != other.serialNumber) return false
        if (issuerDN != other.issuerDN) return false
        if (subjectDN != other.subjectDN) return false
        if (notBefore != other.notBefore) return false
        if (notAfter != other.notAfter) return false
        if (keyUsage != other.keyUsage) return false
        if (subjectAlternativeNames != other.subjectAlternativeNames) return false

        return true
    }

    override fun hashCode(): Int {
        var result = der.contentHashCode()
        result = 31 * result + fingerPrint.hashCode()
        result = 31 * result + (serialNumber?.hashCode() ?: 0)
        result = 31 * result + issuerDN.hashCode()
        result = 31 * result + subjectDN.hashCode()
        result = 31 * result + notBefore.hashCode()
        result = 31 * result + notAfter.hashCode()
        result = 31 * result + (keyUsage?.hashCode() ?: 0)
        result = 31 * result + (subjectAlternativeNames?.hashCode() ?: 0)
        return result
    }

    fun derToBase64(): String = der.encodeTo(Encoding.BASE64)
    override fun toString(): String {
        return "Certificate(fingerPrint='$fingerPrint', serialNumber=$serialNumber, issuerDN='$issuerDN', subjectDN='$subjectDN', notBefore=${notBefore.instantToDateStringISO()}, notAfter=${notAfter.instantToDateStringISO()}, keyUsage=$keyUsage, san=$subjectAlternativeNames)"
    }

    fun amendJwkKeyInfo(input: ResolvedKeyInfoType<KeyType>): ResolvedKeyInfoType<JwkType> {
        val keyInfo = CoseJoseKeyMappingService.toResolvedJwkKeyInfo(input).copy(keyVisibility = input.keyVisibility ?: KeyVisibility.PUBLIC)
        val x5c = pemAndDerToCertificateChain(pemChain = null, derChain = arrayOf(this.der)).map { it.derToBase64() }.toTypedArray()
        val jwk = Jwk.from(keyInfo.key).copy(x5c = x5c)
        return keyInfo.copy(key = jwk, x5c = x5c)
    }

    fun amendCoseKeyInfo(input: ResolvedKeyInfoType<KeyType>): ResolvedKeyInfoType<CoseKeyType> {
        val amendedKeyInfo = amendJwkKeyInfo(input)
        return CoseJoseKeyMappingService.toResolvedCoseKeyInfo(amendedKeyInfo)
    }

    companion object {
        fun fromDer(der: ByteArray) = certificateFromDer(der)

        fun fromPem(pem: String) = certificateFromPem(pem)

        fun chainFromPem(pem: String) = certificateChainFromPem(pem)

    }
}

fun Certificate.getSignatureAlgorithm(): SignatureAlgorithm {
    val x509 = toSignumX509Certificate()
    return x509.signatureAlgorithm.toSignatureAlgorithm()
}
