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

package com.sphereon.trust.etsi.model

import com.sphereon.trust.etsi.lote.model.ListAndSchemeInformation
import com.sphereon.trust.etsi.lote.model.LoTE
import com.sphereon.trust.etsi.lote.model.LoTEPostalAddress
import com.sphereon.trust.etsi.lote.model.LoTEQualifier
import com.sphereon.trust.etsi.lote.model.LoTEServiceDigitalIdentity
import com.sphereon.trust.etsi.lote.model.LoTEServiceInformation
import com.sphereon.trust.etsi.lote.model.MultiLangString
import com.sphereon.trust.etsi.lote.model.MultiLangURI
import com.sphereon.trust.etsi.lote.model.PkiObject
import com.sphereon.trust.etsi.lote.model.PolicyOrLegalNoticeEntry
import com.sphereon.trust.etsi.lote.model.TrustedEntity
import com.sphereon.trust.etsi.lote.model.TrustedEntityInformation
import com.sphereon.trust.etsi.lote.model.TrustedEntityService
import com.sphereon.trust.etsi.lote.model.OperatorAddress as LoTEOperatorAddress
import com.sphereon.trust.etsi.lote.model.OtherLoTEPointer as LoTE602OtherPointer

/**
 * Bidirectional converter between ETSI TS 119 602 LoTE and ETSI 612 ETSILoTE models.
 *
 * Per ETSI 602 Table A.1 mapping.
 */
object LoTE612Converter {
    /**
     * Converts a 602 LoTE to an ETSILoTE (612 model with 602 naming).
     */
    fun LoTE.toETSILoTE(): ETSILoTE {
        val info = listAndSchemeInformation
        return ETSILoTE(
            versionIdentifier = info.versionIdentifier,
            sequenceNumber = info.sequenceNumber,
            type = info.type ?: "",
            schemeOperatorName = info.schemeOperatorName,
            schemeOperatorAddress = info.schemeOperatorAddress?.toETSI(),
            schemeName = info.schemeName,
            schemeInformationURI = info.schemeInformationURI,
            statusDeterminationApproach = info.statusDeterminationApproach ?: "",
            schemeTerritory = info.schemeTerritory ?: "",
            listIssueDateTime = info.listIssueDateTime,
            nextUpdate = info.nextUpdate,
            distributionPoints = info.distributionPoints,
            schemeTypeCommunityRules = info.schemeTypeCommunityRules,
            policyOrLegalNotice =
                info.policyOrLegalNotice.map {
                    MultiLangString(it.lang ?: "en", it.notice ?: it.noticeURI ?: "")
                },
            historicalInformationPeriod = info.historicalInformationPeriod,
            trustedEntities = trustedEntitiesList.map { it.toETSITrustedEntity() },
            pointersToOtherLoTE = info.pointersToOtherLoTE.map { it.toETSIOtherLoTEPointer() },
        )
    }

    /**
     * Converts an ETSILoTE (612 model) to a 602 LoTE.
     */
    fun ETSILoTE.toLoTE(): LoTE =
        LoTE(
            listAndSchemeInformation =
                ListAndSchemeInformation(
                    versionIdentifier = versionIdentifier,
                    sequenceNumber = sequenceNumber,
                    type = type.ifEmpty { null },
                    schemeOperatorName = schemeOperatorName,
                    schemeOperatorAddress = schemeOperatorAddress?.toLoTE(),
                    schemeName = schemeName,
                    schemeInformationURI = schemeInformationURI,
                    statusDeterminationApproach = statusDeterminationApproach.ifEmpty { null },
                    schemeTypeCommunityRules = schemeTypeCommunityRules,
                    schemeTerritory = schemeTerritory.ifEmpty { null },
                    policyOrLegalNotice =
                        policyOrLegalNotice.map {
                            PolicyOrLegalNoticeEntry(lang = it.lang, notice = it.value)
                        },
                    historicalInformationPeriod = historicalInformationPeriod,
                    pointersToOtherLoTE = pointersToOtherLoTE.map { it.toLoTEOtherPointer() },
                    listIssueDateTime = listIssueDateTime,
                    nextUpdate = nextUpdate,
                    distributionPoints = distributionPoints,
                ),
            trustedEntitiesList = trustedEntities.map { it.toTrustedEntity() },
        )

    private fun TrustedEntity.toETSITrustedEntity(): ETSITrustedEntity =
        ETSITrustedEntity(
            trustedEntityInformation = trustedEntityInformation.toETSI(),
            trustedEntityServices = trustedEntityServices.map { it.toETSI() },
        )

    private fun TrustedEntityInformation.toETSI(): ETSITrustedEntityInformation =
        ETSITrustedEntityInformation(
            name = name,
            tradeName = tradeName,
            address = address?.toETSI() ?: ETSIOperatorAddress(postalAddresses = emptyList()),
            informationURI = informationURI,
        )

    private fun LoTEOperatorAddress.toETSI(): ETSIOperatorAddress =
        ETSIOperatorAddress(
            postalAddresses =
                postalAddresses.map {
                    PostalAddress(
                        streetAddress = it.streetAddress,
                        locality = it.locality ?: "",
                        stateOrProvince = it.stateOrProvince,
                        postalCode = it.postalCode,
                        countryName = it.countryName,
                    )
                },
            electronicAddresses = electronicAddress.map { it.uriValue },
        )

    private fun TrustedEntityService.toETSI(): ETSITrustedEntityService =
        ETSITrustedEntityService(
            serviceInformation = serviceInformation.toETSI(),
            serviceHistory = serviceHistory.map { it.toETSI() },
        )

    private fun LoTEServiceInformation.toETSI(): ETSIServiceInformation =
        ETSIServiceInformation(
            serviceTypeIdentifier = serviceTypeIdentifier,
            serviceName = serviceName,
            serviceDigitalIdentity = serviceDigitalIdentity.toETSI(),
            serviceStatus = serviceStatus ?: "",
            statusStartingTime = statusStartingTime ?: kotlin.time.Instant.fromEpochMilliseconds(0),
            serviceSupplyPoints = serviceSupplyPoints.map { ServiceSupplyPoint(uri = it) },
            schemeServiceDefinitionURI = schemeServiceDefinitionURI,
            tspServiceDefinitionURI = teServiceDefinitionURI,
        )

    private fun LoTEServiceDigitalIdentity.toETSI(): ETSIServiceDigitalIdentity =
        ETSIServiceDigitalIdentity(
            x509Certificates = x509Certificates.map { it.value },
            subjectName = x509SubjectNames.firstOrNull(),
            x509SKIs = x509SKIs,
        )

    private fun LoTE602OtherPointer.toETSIOtherLoTEPointer(): ETSIOtherLoTEPointer {
        val firstQualifier = qualifiers.firstOrNull()
        return ETSIOtherLoTEPointer(
            schemeOperatorName = firstQualifier?.schemeOperatorName ?: emptyList(),
            schemeTerritory = firstQualifier?.schemeTerritory ?: "",
            location = location,
            serviceDigitalIdentities = serviceDigitalIdentities.map { it.toETSI() },
            additionalInformation = buildAdditionalInfoFromQualifiers(qualifiers),
        )
    }

    private fun buildAdditionalInfoFromQualifiers(qualifiers: List<LoTEQualifier>): ETSIAdditionalInformation? {
        if (qualifiers.isEmpty()) {
            return null
        }
        val otherInfo = mutableListOf<String>()
        var mimeType: String? = null
        for (q in qualifiers) {
            q.loTEType?.let { otherInfo.add(it) }
            if (mimeType == null) {
                mimeType = q.mimeType
            }
            q.schemeTypeCommunityRules.forEach { otherInfo.add(it.uriValue) }
        }
        if (otherInfo.isEmpty() && mimeType == null) {
            return null
        }
        return ETSIAdditionalInformation(
            otherInformation = otherInfo,
            mimeType = mimeType,
        )
    }

    // Reverse conversions

    private fun ETSITrustedEntity.toTrustedEntity(): TrustedEntity =
        TrustedEntity(
            trustedEntityInformation = trustedEntityInformation.toLoTE(),
            trustedEntityServices = trustedEntityServices.map { it.toLoTE() },
        )

    private fun ETSITrustedEntityInformation.toLoTE(): TrustedEntityInformation =
        TrustedEntityInformation(
            name = name,
            tradeName = tradeName,
            address = address.toLoTE(),
            informationURI = informationURI,
        )

    private fun ETSIOperatorAddress.toLoTE(): LoTEOperatorAddress =
        LoTEOperatorAddress(
            postalAddresses =
                postalAddresses.map {
                    LoTEPostalAddress(
                        streetAddress = it.streetAddress,
                        locality = it.locality,
                        stateOrProvince = it.stateOrProvince,
                        postalCode = it.postalCode,
                        countryName = it.countryName,
                    )
                },
            electronicAddress = electronicAddresses.map { MultiLangURI("en", it) },
        )

    private fun ETSITrustedEntityService.toLoTE(): TrustedEntityService =
        TrustedEntityService(
            serviceInformation = serviceInformation.toLoTE(),
            serviceHistory = serviceHistory.map { it.toLoTE() },
        )

    private fun ETSIServiceInformation.toLoTE(): LoTEServiceInformation =
        LoTEServiceInformation(
            serviceTypeIdentifier = serviceTypeIdentifier,
            serviceName = serviceName,
            serviceDigitalIdentity = serviceDigitalIdentity.toLoTE(),
            serviceStatus = serviceStatus,
            statusStartingTime = statusStartingTime,
            serviceSupplyPoints = serviceSupplyPoints.map { it.uri },
            schemeServiceDefinitionURI = schemeServiceDefinitionURI,
            teServiceDefinitionURI = tspServiceDefinitionURI,
        )

    private fun ETSIServiceDigitalIdentity.toLoTE(): LoTEServiceDigitalIdentity =
        LoTEServiceDigitalIdentity(
            x509Certificates = x509Certificates.map { PkiObject(it) },
            x509SubjectNames =
                if (subjectName != null) {
                    listOf(subjectName)
                } else {
                    emptyList()
                },
            x509SKIs = x509SKIs,
        )

    private fun ETSIOtherLoTEPointer.toLoTEOtherPointer(): LoTE602OtherPointer {
        val qualifier =
            LoTEQualifier(
                schemeOperatorName = schemeOperatorName,
                schemeTerritory = schemeTerritory.ifEmpty { null },
                loTEType = additionalInformation?.otherInformation?.firstOrNull(),
                mimeType = additionalInformation?.mimeType,
            )
        return LoTE602OtherPointer(
            serviceDigitalIdentities = serviceDigitalIdentities.map { it.toLoTE() },
            location = location,
            qualifiers = listOf(qualifier),
        )
    }
}
