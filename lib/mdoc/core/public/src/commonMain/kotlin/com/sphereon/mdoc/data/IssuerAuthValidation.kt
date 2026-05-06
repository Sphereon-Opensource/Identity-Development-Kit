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

package com.sphereon.mdoc.data

import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.core.compat.getDateTime
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.cose.COSE_Sign1
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.VerifyResultType
import com.sphereon.crypto.core.generic.VerifySignatureResultType
import com.sphereon.crypto.core.x509.X509VerificationResultType
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Defines the verification steps for issuer authentication (MSO).
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("IssuerAuthValidation", exact = true)
interface IssuerAuthValidation {
    /**
     * Step 1. Validate the X.509 certificate chain in the MSO header.
     */
    suspend fun verifyCertificateChain(
        issuerAuth: COSE_Sign1<MobileSecurityObject>,
        trustedCerts: Array<String>? = null,
    ): X509VerificationResultType

    /**
     * Step 2. Verify the COSE_Sign1 signature over the MSO.
     */
    suspend fun verifySign1(
        issuerAuth: COSE_Sign1<MobileSecurityObject>,
        keyInfo: KeyInfoType<CoseKeyType>?,
    ): VerifySignatureResultType<CoseKeyType>

    /**
     * Step 3. Verify the digests of IssuerSignedItem entries against the MSO.
     *
     * Per ISO 18013-5 §9.1.2.5, each disclosed IssuerSignedItem in [document]'s nameSpaces is
     * hashed (using the digest algorithm declared in `mso.digestAlgorithm`) and compared to the
     * corresponding entry in `mso.valueDigests[namespace][digestID]`. Any mismatch, missing
     * digest entry, or unsupported digest algorithm fails the step.
     *
     * When [document] is `null`, only the IssuerAuth structure was supplied (no disclosed
     * items), so digest verification is skipped — there is nothing to hash.
     */
    fun verifyDigests(
        issuerAuth: COSE_Sign1<MobileSecurityObject>,
        document: Document? = null,
    ): VerifyResultType

    /**
     * Step 4. Check that the document type in the MSO matches the Document.
     */
    fun verifyDocType(document: Document?): VerifyResultType

    /**
     * Step 5. Verify the MSO ValidityInfo (signed date, validFrom, validUntil).
     */
    suspend fun verifyValidityInfo(
        issuerAuth: COSE_Sign1<MobileSecurityObject>,
        verificationTime: LocalDateTimeKMP = LocalDateTimeKMP.now(),
        allowExpiredDocuments: Boolean? = false,
        allowNotYetValidDocuments: Boolean? = false,
        dateTimeUtils: DateTimeUtils = getDateTime(),
        timeZoneId: String? = null,
        clockSkewAllowedInSec: Int = 120,
    ): VerifyResultType
}
