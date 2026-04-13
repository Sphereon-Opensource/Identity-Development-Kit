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

import com.sphereon.cbor.localDateToDateStringISO
import com.sphereon.cbor.tDateToEpochSeconds
import com.sphereon.core.compat.DateTimeUtils
import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.core.compat.toLocalDateTimeKMP
import com.sphereon.crypto.core.CoseCryptoService
import com.sphereon.crypto.core.CryptoConst
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.cose.COSE_Sign1
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.VerifyResult
import com.sphereon.crypto.core.generic.VerifyResultType
import com.sphereon.crypto.core.generic.VerifySignatureResultType
import com.sphereon.crypto.core.x509.X509VerificationProfile
import com.sphereon.crypto.core.x509.X509VerificationRequest
import com.sphereon.crypto.core.x509.X509VerificationResult
import com.sphereon.crypto.core.x509.X509VerificationResultType
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.di.session.SessionScope
import com.sphereon.mdoc.MdocConst
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodec
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

private const val CERT_VALIDITY_OFFSET_SECONDS = 600

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
@ContributesBinding(SessionScope::class, binding = binding<IssuerAuthValidation>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("IssuerAuthValidationImpl", exact = true)
class IssuerAuthValidationImpl(
    val x509VerifyService: X509VerifyService,
    val coseCryptoService: CoseCryptoService,
    val mobileSecurityObjectCborCodec: MobileSecurityObjectCborCodec,
) : IssuerAuthValidation {
    /**
     * * 1. Validate the certificate included in the MSO header according to 9.3.3.
     */
    override suspend fun verifyCertificateChain(
        issuerAuth: COSE_Sign1<MobileSecurityObject>,
        trustedCerts: Array<String>?,
    ): X509VerificationResultType {
        val x5chain =
            issuerAuth.protectedHeader.x5chain ?: issuerAuth.unprotectedHeader?.x5chain
        if (x5chain === null || x5chain.value.isEmpty()) {
            return X509VerificationResult(
                name = CryptoConst.X509_LITERAL,
                error = true,
                critical = true,
                certificateChain = emptyArray(),
                message = "No X.509 Chain present in the issuerAuth headers",
            )
        }
        val request =
            X509VerificationRequest(
                chainDER = x5chain.value.map { it.value }.toTypedArray(),
                trustedCerts = trustedCerts ?: x509VerifyService.getTrustedCerts(),
                verificationProfile = X509VerificationProfile.ISO_18013_5,
            )
        return x509VerifyService.verifyCertificateChain(request)
    }

    /**
     * 2. Verify the digital signature of the IssuerAuth structure (see 9.1.2.4) using the working_public_
     * key, working_public_key_parameters, and working_public_key_algorithm from the certificate
     * validation procedure of step 1.
     */
    override suspend fun verifySign1(
        issuerAuth: COSE_Sign1<MobileSecurityObject>,
        keyInfo: KeyInfoType<CoseKeyType>?,
    ): VerifySignatureResultType<CoseKeyType> {
        if (keyInfo?.key?.d !== null) {
            throw AssertionError("Do not use private keys to verify!")
        }
        return coseCryptoService.verify1(issuerAuth, keyInfo, true)
    }

    /**
     *  3. Calculate the digest value for every IssuerSignedItem returned in the DeviceResponse structure
     *  * according to 9.1.2.5 and verify that these calculated digests equal the corresponding digest values
     *  * in the MSO.
     *
     *  This is a READER method. FIXME: Implement
     */
    override fun verifyDigests(
        issuerAuth: COSE_Sign1<MobileSecurityObject>,
//        deviceResponse: DeviceResponse
    ): VerifyResultType =
        VerifyResult(
            error = false,
            critical = true,
            message = "Device signed verification validation not implemented yet",
            name = MdocConst.MDOC_LITERAL,
        )

    /**
     * 4. Verify that the DocTypeAlias in the MSO matches the relevant DocTypeAlias in the Documents structure.
     *
     *  This is a READER method.
     */
    override fun verifyDocType(document: Document?): VerifyResultType {
        val msoDocType = document?.let(::decodeMso)?.docType
        val docTypesMatch = document !== null && document.docType == msoDocType
        return VerifyResult(
            error = !docTypesMatch,
            critical = !docTypesMatch,
            message = "Doc type verification was ${if (docTypesMatch) {
                "successful"
            } else {
                "not successful. MSO $msoDocType, document: ${document?.docType}"
            }}",
            name = MdocConst.MDOC_LITERAL,
        )
    }

    /**
     * 5. Validate the elements in the ValidityInfo structure, i.e. verify that:
     * — the 'signed' date is within the validity period of the certificate in the MSO header, <-- FIXME, we need an additional x509 function
     * — the current timestamp shall be equal or later than the ‘validFrom’ element,
     * — the 'validUntil' element shall be equal or later than the current timestamp.
     */
    override suspend fun verifyValidityInfo(
        issuerAuth: COSE_Sign1<MobileSecurityObject>,
        verificationTime: LocalDateTimeKMP,
        allowExpiredDocuments: Boolean?,
        allowNotYetValidDocuments: Boolean?,
        dateTimeUtils: DateTimeUtils,
        timeZoneId: String?,
        clockSkewAllowedInSec: Int,
    ): VerifyResultType {
        val mso = decodeMso(issuerAuth)

        val now =
            dateTimeUtils.epochSeconds().toLong() // toLong as the date time utils uses ints for these (valid till 2038)
        val signed =
            mso.validityInfo.signed
                .tDateToEpochSeconds(dateTimeUtils, timeZoneId)
                .toLong()
        val validFrom =
            mso.validityInfo.validFrom
                .tDateToEpochSeconds(dateTimeUtils, timeZoneId)
                .toLong()
        val validUntil =
            mso.validityInfo.validUntil
                .tDateToEpochSeconds(dateTimeUtils, timeZoneId)
                .toLong()
        val verificationAt = verificationTime.toInstant(dateTimeUtils, timeZoneId).toString()
        val validFromStr =
            validFrom.toLocalDateTimeKMP(dateTimeUtils).toInstant(dateTimeUtils, timeZoneId).toString()
        val validUntilStr =
            validUntil.toLocalDateTimeKMP(dateTimeUtils).toInstant(dateTimeUtils, timeZoneId).toString()

        // FIXME: Offset used as we cannot inspect cert valid from - to yet. Needs a function on the x509Service.
        val fixmeOffset = CERT_VALIDITY_OFFSET_SECONDS
        val certValidFrom = validFrom - fixmeOffset
        val certValidUntil = validUntil + fixmeOffset

        // the 'signed' date is within the validity period of the certificate in the MSO header
        // Let's not do a clock skew on dates that typically are far away
        if ((signed < certValidFrom) || (signed > certValidUntil)) {
            return VerifyResult(
                error = true,
                critical = true,
                message = "The signature date is not within the certificate validity range of ${
                    certValidFrom.toLocalDateTimeKMP(
                        dateTimeUtils,
                    ).localDateToDateStringISO(dateTimeUtils, timeZoneId)
                } and ${
                    certValidUntil.toLocalDateTimeKMP(dateTimeUtils).localDateToDateStringISO(dateTimeUtils, timeZoneId)
                }",
                name = MdocConst.MDOC_LITERAL,
            )
        }

        // the current timestamp shall be equal or later than the 'validFrom' element,
        if ((now + clockSkewAllowedInSec) < validFrom) {
            return VerifyResult(
                error = allowNotYetValidDocuments != true,
                critical = allowNotYetValidDocuments != true,
                message = "The document is not yet valid. Current date/time: $verificationAt and valid From $validFromStr",
                name = MdocConst.MDOC_LITERAL,
            )
        }

        // the 'validUntil' element shall be equal or later than the current timestamp.

        if (validUntil < (now - clockSkewAllowedInSec)) {
            val datesMatch = verificationAt == validUntilStr
            return VerifyResult(
                error = allowExpiredDocuments != true,
                critical = allowExpiredDocuments != true,
                message =
                    "The document is not valid anymore. Current date/time: $verificationAt " +
                        "${if (datesMatch) {
                            "($now)"
                        } else {
                            ""
                        }} and valid Until $validUntilStr " +
                        "${if (datesMatch) {
                            "($validUntil)"
                        } else {
                            ""
                        }}",
                name = MdocConst.MDOC_LITERAL,
            )
        }

        return VerifyResult(
            name = MdocConst.MDOC_LITERAL,
            error = false,
            critical = false,
            message = "Signature is signed during Certificate validity and valid",
        )
    }

    private fun decodeMso(issuerAuth: COSE_Sign1<MobileSecurityObject>): MobileSecurityObject {
        val payload =
            issuerAuth.payload?.value
                ?: throw IllegalArgumentException("Payload is null for MSO, that is not allowed")
        return mobileSecurityObjectCborCodec.decode(payload).getOrThrow().value
    }

    private fun decodeMso(document: Document) = decodeMso(document.issuerSigned.issuerAuth)
}
