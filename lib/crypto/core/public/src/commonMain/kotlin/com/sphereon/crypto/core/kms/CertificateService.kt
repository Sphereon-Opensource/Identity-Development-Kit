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

package com.sphereon.crypto.core.kms

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.plus
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.generic.CertificateSigningRequest
import com.sphereon.crypto.core.generic.X509DistinguishedNameElements
import com.sphereon.crypto.core.x509.Certificate
import kotlinx.datetime.atTime
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

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
    ): CertificateSigningRequest

    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    suspend fun createCertificate(
        issuerKeyInfo: KeyInfoType<KeyType>,
        issuer: X509DistinguishedNameElements,
        subjectKeyInfo: ResolvedKeyInfoType<KeyType>,
        subject: X509DistinguishedNameElements,
        serialNumber: Int,
        notBefore: LocalDateTimeKMP = LocalDateTimeKMP.now(),
        notAfter: LocalDateTimeKMP = LocalDateTimeKMP.fromString(
            Clock.System.now()
                .toLocalDateTime(TimeZone.UTC)
                .date.plus(365, DateTimeUnit.DAY)
                .atTime(0, 0)
                .toString()
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
        notBefore: LocalDateTimeKMP = LocalDateTimeKMP.now(),
        notAfter: LocalDateTimeKMP = LocalDateTimeKMP.fromString(
            Clock.System.now()
                .toLocalDateTime(TimeZone.UTC)
                .date.plus(365, DateTimeUnit.DAY)
                .atTime(0, 0)
                .toString()
        ),
    ): CertificateResult
}


data class CertificateResult(val certificate: Certificate, val keyInfo: ResolvedKeyInfoType<out KeyType>)

data class CertificateOptions(
    val subjectKeyInfo: ResolvedKeyInfoType<KeyType>,
    val subject: X509DistinguishedNameElements,
    val issuerKeyInfo: KeyInfoType<KeyType> = subjectKeyInfo,
    val issuer: X509DistinguishedNameElements = subject,
    val serialNumber: Int = 1,
    val notBefore: LocalDateTimeKMP = LocalDateTimeKMP.now(),
    val notAfter: LocalDateTimeKMP = LocalDateTimeKMP.fromString(
        Clock.System.now()
            .toLocalDateTime(TimeZone.UTC)
            .date.plus(365, DateTimeUnit.DAY)
            .atTime(0, 0)
            .toString()
    ),
)
