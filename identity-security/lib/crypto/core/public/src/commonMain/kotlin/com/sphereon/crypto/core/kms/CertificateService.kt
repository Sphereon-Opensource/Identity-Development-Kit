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

package com.sphereon.crypto.core.kms

import at.asitplus.awesn1.crypto.pki.Pkcs10CsrAttribute
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.CertificateSigningRequest
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.jvm.JvmOverloads
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock

/**
 * Interface for a session that generates Certificate Signing Requests (CSRs).
 */
interface CertificateService {
    /**
     * Generates a Certificate Signing Request (CSR) for the given key pair and parameters.
     *
     * @param keyInfo The managed key information for which the CSR is generated.
     * @param params The parameters for generating the CSR.
     * @return The generated Certificate Signing Request.
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    suspend fun generateCSR(
        subjectKeyInfo: ResolvedKeyInfoType<*>,
        distinguishedNameElements: X509DistinguishedNameElements,
        serialNumber: Int = 1,
        attributes: List<Pkcs10CsrAttribute> = emptyList(),
    ): CertificateSigningRequest

    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    suspend fun createCertificate(
        issuerKeyInfo: KeyInfoType<KeyType>,
        issuer: X509DistinguishedNameElements,
        subjectKeyInfo: ResolvedKeyInfoType<KeyType>,
        subject: X509DistinguishedNameElements,
        serialNumber: Int,
        extensions: List<X509CertificateExtensionSpec> = emptyList(),
        notBefore: LocalDateTimeKMP = LocalDateTimeKMP.now(),
        notAfter: LocalDateTimeKMP =
            LocalDateTimeKMP.fromString(
                Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.UTC)
                    .date
                    .plus(365, DateTimeUnit.DAY)
                    .atTime(0, 0)
                    .toString(),
            ),
    ): CertificateResult

    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    suspend fun createCertificateFromCSR(
        issuerKeyInfo: KeyInfoType<KeyType>,
        issuer: X509DistinguishedNameElements,
        subjectKeyInfo: ResolvedKeyInfoType<KeyType>,
        csr: CertificateSigningRequest,
        serialNumber: Int = csr.serialNumber,
        extensions: List<X509CertificateExtensionSpec> = emptyList(),
        notBefore: LocalDateTimeKMP = LocalDateTimeKMP.now(),
        notAfter: LocalDateTimeKMP =
            LocalDateTimeKMP.fromString(
                Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.UTC)
                    .date
                    .plus(365, DateTimeUnit.DAY)
                    .atTime(0, 0)
                    .toString(),
            ),
    ): CertificateResult

    @ContributesTo(SessionScope::class)
    interface Graph {
        val certificateService: CertificateService
    }
}

@JsExportCompat
data class CertificateResult(
    val certificate: Certificate,
    val keyInfo: ResolvedKeyInfoType<out KeyType>,
)

@JsExportCompat
data class X509CertificateExtensionSpec(
    val oid: String,
    val critical: Boolean = false,
    val valueDer: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is X509CertificateExtensionSpec) return false
        return oid == other.oid &&
            critical == other.critical &&
            valueDer.contentEquals(other.valueDer)
    }

    override fun hashCode(): Int {
        var result = oid.hashCode()
        result = 31 * result + critical.hashCode()
        result = 31 * result + valueDer.contentHashCode()
        return result
    }
}

@JsExportCompat
data class
CertificateOptions
    @JvmOverloads
    constructor(
        val subjectKeyInfo: ResolvedKeyInfoType<KeyType>,
        val subject: X509DistinguishedNameElements,
        val issuerKeyInfo: KeyInfoType<KeyType> = subjectKeyInfo,
        val issuer: X509DistinguishedNameElements = subject,
        val serialNumber: Int = 1,
        val extensions: List<X509CertificateExtensionSpec> = emptyList(),
        val notBefore: LocalDateTimeKMP = LocalDateTimeKMP.now(),
        val notAfter: LocalDateTimeKMP =
            LocalDateTimeKMP.fromString(
                Clock.System
                    .now()
                    .toLocalDateTime(TimeZone.UTC)
                    .date
                    .plus(365, DateTimeUnit.DAY)
                    .atTime(0, 0)
                    .toString(),
            ),
    )
