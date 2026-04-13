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

package com.sphereon.mdoc.data

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.VerifyResultsType
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import com.sphereon.core.compat.JsExportCompat
import kotlin.js.JsStatic


/**
 * Public API for mdoc validations.
 */
interface MdocValidations {
    suspend fun fromDocument(
        document: Document,
        trustedCerts: Array<String>? = null,
        verificationTime: LocalDateTimeKMP? = LocalDateTimeKMP.now(),
        keyInfo: KeyInfoType<CoseKeyType>? = null,
        allowNotYetValidDocuments: Boolean = true,
        allowExpiredDocuments: Boolean = false,
        dateTimeUtils: DateTimeUtils = DateTimeUtils.DEFAULTS,
        timeZoneId: String? = null,
        clockSkewAllowedInSec: Int = 120,
    ): VerifyResultsType<CoseKeyType>

    suspend fun fromIssuerAuth(
        issuerAuth: CoseSign1<MobileSecurityObject>,
        keyInfo: KeyInfoType<CoseKeyType>? = null,
        trustedCerts: Array<String>? = null,
        verificationTime: LocalDateTimeKMP? = LocalDateTimeKMP.now(),
        allowNotYetValidDocuments: Boolean = true,
        allowExpiredDocuments: Boolean = false,
        dateTimeUtils: DateTimeUtils = DateTimeUtils.DEFAULTS,
        timeZoneId: String? = null,
        clockSkewAllowedInSec: Int = 120
    ): VerifyResultsType<CoseKeyType>

    suspend fun withParams(
        issuerAuth: CoseSign1<MobileSecurityObject>?,
        document: Document? = null,
        mdocVerificationTypes: MdocVerificationTypes = MdocVerification.ALL,
        keyInfo: KeyInfoType<CoseKeyType>? = null,
        trustedCerts: Array<String>? = null,
        verificationTime: LocalDateTimeKMP? = LocalDateTimeKMP.now(),
        allowNotYetValidDocuments: Boolean? = true,
        allowExpiredDocuments: Boolean? = false,
        dateTimeUtils: DateTimeUtils = DateTimeUtils.DEFAULTS,
        timeZoneId: String? = null,
        clockSkewAllowedInSec: Int = 120,
    ): VerifyResultsType<CoseKeyType>
}


@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocVerification", exact = true)
enum class MdocVerification {
    CERTIFICATE_CHAIN,
    ISSUER_AUTH_SIGNATURE,
    DIGEST_VALUES,
    DOC_TYPE,
    VALIDITY;

    companion object {
        @JsStatic
        val ALL: MdocVerificationTypes = entries.toSet()
        @JsStatic
        val ISSUER_AUTH: MdocVerificationTypes = setOf(CERTIFICATE_CHAIN, ISSUER_AUTH_SIGNATURE, VALIDITY)
        @JsStatic
        val DOCUMENT: MdocVerificationTypes = ALL
    }
}

typealias MdocVerificationTypes = Set<MdocVerification>

