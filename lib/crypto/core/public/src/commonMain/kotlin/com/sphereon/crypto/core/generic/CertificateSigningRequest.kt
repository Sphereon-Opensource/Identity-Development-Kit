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

package com.sphereon.crypto.core.generic

import at.asitplus.signum.indispensable.asn1.encodeToPEM
import at.asitplus.signum.indispensable.pki.Pkcs10CertificationRequest
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.serialization.Serializable

@OptIn(ExperimentalObjCName::class)
@ObjCName("CertificateSigningRequest", exact = true)
@Serializable
data class CertificateSigningRequest  (
    val commonName: String,
    val organization: String? = null,
    val organizationalUnit: String? = null,
    val locality: String? = null,
    val state: String? = null,
    val country: String? = null,
    val email: String? = null,
    val serialNumber: Int,
    val der: ByteArray,
) {

    init {
        require(der.isNotEmpty()) { "CSR der value must not be empty" }
        require(serialNumber > 0) { "CSR serialNumber must be greater than 0, got: $serialNumber" }
        require(commonName.isNotBlank()) { "CSR commonName must not be blank" }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CertificateSigningRequest

        if (commonName != other.commonName) return false
        if (organization != other.organization) return false
        if (organizationalUnit != other.organizationalUnit) return false
        if (locality != other.locality) return false
        if (state != other.state) return false
        if (country != other.country) return false
        if (email != other.email) return false
        if (serialNumber != other.serialNumber) return false
        if (!der.contentEquals(other.der)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = commonName.hashCode()
        result = 31 * result + organization.hashCode()
        result = 31 * result + organizationalUnit.hashCode()
        result = 31 * result + locality.hashCode()
        result = 31 * result + state.hashCode()
        result = 31 * result + country.hashCode()
        result = 31 * result + email.hashCode()
        result = 31 * result + serialNumber.hashCode()
        result = 31 * result + der.contentHashCode()
        return result
    }

    fun toPem(): String {
        val pem = Pkcs10CertificationRequest.decodeFromDer(der).encodeToPEM().getOrThrow()
        return pem
    }

    fun toX509DistinguishedNameElements() = X509DistinguishedNameElements(commonName, country, state, locality, organization, organizationalUnit, email)
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("X509DistinguishedNameElements", exact = true)
@Serializable
data class X509DistinguishedNameElements(
    val commonName: String,
    val country: String? = null,
    val state: String? = null,
    val locality: String? = null,
    val organizationName: String? = null,
    val organizationUnit: String? = null,
    val email: String? = null
) {
    init {
        require(commonName.isNotBlank()) { "X509DistinguishedNameElements commonName must not be blank" }
    }
}
