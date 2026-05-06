/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.mdoc.data

import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.crypto.core.generic.VerifyResult
import com.sphereon.crypto.core.generic.VerifyResults
import com.sphereon.crypto.core.generic.VerifyResultsType
import com.sphereon.di.session.SessionScope
import com.sphereon.mdoc.MdocConst
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * 9.3.1 Inspection procedure for issuer data authentication
 *
 * 1. Validate the certificate included in the MSO header according to 9.3.3.
 * 2. Verify the digital signature of the IssuerAuth structure (see 9.1.2.4) using the working_public_
 * key, working_public_key_parameters, and working_public_key_algorithm from the certificate
 * validation procedure of step 1.
 *
 * === Step 3 and 4 are related to MSO. We have a separate validator that uses this validator for those ===
 * 3. Calculate the digest value for every IssuerSignedItem returned in the DeviceResponse structure
 * according to 9.1.2.5 and verify that these calculated digests equal the corresponding digest values
 * in the MSO.
 * 4. Verify that the DocTypeAlias in the MSO matches the relevant DocTypeAlias in the Documents structure.
 * ========================================================================================================
 *
 * 5. Validate the elements in the ValidityInfo structure, i.e. verify that:
 * — the 'signed' date is within the validity period of the certificate in the MSO header,
 * — the current timestamp shall be equal or later than the ‘validFrom’ element,
 * — the 'validUntil' element shall be equal or later than the current timestamp.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<MdocValidations>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocValidationsImpl", exact = true)
class MdocValidationsImpl(
    val issuerAuthValidation: IssuerAuthValidation,
) : MdocValidations {
    override suspend fun fromDocument(
        document: Document,
        trustedCerts: Array<String>?,
        verificationTime: LocalDateTimeKMP?,
        keyInfo: KeyInfoType<CoseKeyType>?,
        allowNotYetValidDocuments: Boolean,
        allowExpiredDocuments: Boolean,
        dateTimeUtils: DateTimeUtils,
        timeZoneId: String?,
        clockSkewAllowedInSec: Int,
    ) = withParams(
        issuerAuth = null,
        document = document,
        mdocVerificationTypes = MdocVerification.DOCUMENT,
        keyInfo = keyInfo,
        trustedCerts = trustedCerts,
        verificationTime = verificationTime,
        allowNotYetValidDocuments = allowNotYetValidDocuments,
        allowExpiredDocuments = allowExpiredDocuments,
        dateTimeUtils = dateTimeUtils,
        timeZoneId = timeZoneId,
        clockSkewAllowedInSec = clockSkewAllowedInSec,
    )

    override suspend fun fromIssuerAuth(
        issuerAuth: CoseSign1<MobileSecurityObject>,
        keyInfo: KeyInfoType<CoseKeyType>?,
        trustedCerts: Array<String>?,
        verificationTime: LocalDateTimeKMP?,
        allowNotYetValidDocuments: Boolean,
        allowExpiredDocuments: Boolean,
        dateTimeUtils: DateTimeUtils,
        timeZoneId: String?,
        clockSkewAllowedInSec: Int,
    ) = withParams(
        issuerAuth = issuerAuth,
        document = null,
        mdocVerificationTypes = MdocVerification.ISSUER_AUTH,
        keyInfo = keyInfo,
        allowNotYetValidDocuments = allowNotYetValidDocuments,
        allowExpiredDocuments = allowExpiredDocuments,
        trustedCerts = trustedCerts,
        verificationTime = verificationTime,
        dateTimeUtils = dateTimeUtils,
        timeZoneId = timeZoneId,
        clockSkewAllowedInSec = clockSkewAllowedInSec,
    )

    override suspend fun withParams(
        issuerAuth: CoseSign1<MobileSecurityObject>?,
        document: Document?,
        mdocVerificationTypes: MdocVerificationTypes,
        keyInfo: KeyInfoType<CoseKeyType>?,
        trustedCerts: Array<String>?,
        verificationTime: LocalDateTimeKMP?,
        allowNotYetValidDocuments: Boolean?,
        allowExpiredDocuments: Boolean?,
        dateTimeUtils: DateTimeUtils,
        timeZoneId: String?,
        clockSkewAllowedInSec: Int,
    ): VerifyResultsType<CoseKeyType> {
        val verifiedAt = verificationTime ?: LocalDateTimeKMP.Companion.now()
        if (issuerAuth === null && document == null) {
            return VerifyResults(
                error = true,
                keyInfo = null,
                verifications =
                    arrayOf(
                        VerifyResult(
                            name = MdocConst.MDOC_LITERAL,
                            critical = true,
                            error = true,
                            message = "Either an mdoc or an issuerAuth object needs to be provided for verification",
                        ),
                    ),
            )
        } else if (issuerAuth !== null && document !== null && document.issuerSigned.issuerAuth !== issuerAuth) {
            // Both are provided, although mdoc was enough. Make sure the objects are actually the same
            return VerifyResults(
                error = true,
                keyInfo = null,
                verifications =
                    arrayOf(
                        VerifyResult(
                            name = MdocConst.MDOC_LITERAL,
                            critical = true,
                            error = true,
                            message =
                                "Both an mdoc and issuer auth object were supplied for verification, " +
                                    "but the issuerAuth of the mdoc is different from the provided mdoc. " +
                                    "To prevent this only supply the mdoc",
                        ),
                    ),
            )
        }
        val verificationTypes: MdocVerificationTypes = mdocVerificationTypes.ifEmpty { MdocVerification.ALL }
        val auth = document?.issuerSigned?.issuerAuth ?: issuerAuth ?: throw AssertionError()

        val verifications =
            verificationTypes.map {
                when (it) {
                    MdocVerification.CERTIFICATE_CHAIN -> {
                        issuerAuthValidation.verifyCertificateChain(auth, trustedCerts)
                    }

                    MdocVerification.ISSUER_AUTH_SIGNATURE -> {
                        issuerAuthValidation.verifySign1(auth, keyInfo)
                    }

                    MdocVerification.VALIDITY -> {
                        issuerAuthValidation.verifyValidityInfo(
                            auth,
                            verifiedAt,
                            allowNotYetValidDocuments,
                            allowExpiredDocuments,
                            dateTimeUtils,
                            timeZoneId,
                            clockSkewAllowedInSec,
                        )
                    }

                    MdocVerification.DOC_TYPE -> {
                        issuerAuthValidation.verifyDocType(document)
                    }

                    MdocVerification.DIGEST_VALUES -> {
                        // Pass the document so verifyDigests can iterate disclosed items. When only
                        // the raw issuerAuth is supplied (the `fromIssuerAuth` entry point), the
                        // document is null and the step skips with a non-error result.
                        issuerAuthValidation.verifyDigests(auth, document)
                    }
                }
            }

        return VerifyResults(
            // Only critical errors set the overall error
            error = verifications.find { it.error && it.critical }?.error ?: false,
            keyInfo =
                if (keyInfo === null) {
                    null
                } else {
                    KeyInfo.Companion.fromDTO(keyInfo)
                },
            verifications = verifications.map { verification -> VerifyResult.Companion.fromDTO(verification) }.toTypedArray(),
        )
    }

    @ContributesTo(SessionScope::class)
    interface Graph {
        val mdocValidations: MdocValidations
    }
}
