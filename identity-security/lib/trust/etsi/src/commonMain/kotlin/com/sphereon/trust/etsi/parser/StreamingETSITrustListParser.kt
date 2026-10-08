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

package com.sphereon.trust.etsi.parser

import com.sphereon.di.session.SessionScope
import com.sphereon.trust.etsi.lote.model.MultiLangString
import com.sphereon.trust.etsi.lote.model.MultiLangURI
import com.sphereon.trust.etsi.lote.serialization.LoTEJson
import com.sphereon.trust.etsi.model.AdditionalServiceInformation
import com.sphereon.trust.etsi.model.ETSIAdditionalInformation
import com.sphereon.trust.etsi.model.ETSILoTE
import com.sphereon.trust.etsi.model.ETSIOperatorAddress
import com.sphereon.trust.etsi.model.ETSIOtherLoTEPointer
import com.sphereon.trust.etsi.model.ETSIServiceDigitalIdentity
import com.sphereon.trust.etsi.model.ETSIServiceInformation
import com.sphereon.trust.etsi.model.ETSITrustedEntity
import com.sphereon.trust.etsi.model.ETSITrustedEntityInformation
import com.sphereon.trust.etsi.model.ETSITrustedEntityService
import com.sphereon.trust.etsi.model.LoTE612Converter.toETSILoTE
import com.sphereon.trust.etsi.model.PostalAddress
import com.sphereon.trust.etsi.model.ServiceSupplyPoint
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.xmlStreaming
import kotlin.time.Instant

/**
 * Cross-platform ETSI Trust List Parser using xmlutil streaming API.
 *
 * This implementation works across JVM, JS (Node.js), and Native platforms without requiring DOM.
 * Produces models using ETSI TS 119 602 LoTE terminology.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ETSITrustListParser>())
class StreamingETSITrustListParser : ETSITrustListParser {
    override fun parseFromBytes(xmlData: ByteArray): ETSILoTE = parseFromString(xmlData.decodeToString())

    override fun parseFromString(xmlString: String): ETSILoTE =
        try {
            val reader = xmlStreaming.newReader(xmlString)
            parseWithReader(reader)
        } catch (e: Exception) {
            throw ETSIParseException("Failed to parse ETSI Trust List XML: ${e.message}", e)
        }

    override fun validate(xmlData: ByteArray): Boolean =
        try {
            val reader = xmlStreaming.newReader(xmlData.decodeToString())
            while (reader.hasNext()) {
                reader.next()
            }
            true
        } catch (e: Exception) {
            false
        }

    override fun parseFromJson(jsonString: String): ETSILoTE =
        try {
            val lote = LoTEJson.parse(jsonString)
            lote.toETSILoTE()
        } catch (e: Exception) {
            throw ETSIParseException("Failed to parse ETSI Trust List JSON: ${e.message}", e)
        }

    private fun parseWithReader(reader: XmlReader): ETSILoTE {
        reader.skipToElement("TrustServiceStatusList")

        var schemeInfo: SchemeInfoParsed? = null
        val trustedEntities = mutableListOf<ETSITrustedEntity>()

        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "SchemeInformation" -> schemeInfo = parseSchemeInformation(reader)
                    "TrustServiceProviderList" -> trustedEntities.addAll(parseTrustServiceProviderList(reader))
                }
            }
        }

        val info = schemeInfo ?: throw ETSIParseException("SchemeInformation not found")

        return ETSILoTE(
            versionIdentifier = info.versionIdentifier,
            sequenceNumber = info.sequenceNumber,
            type = info.type,
            schemeOperatorName = info.schemeOperatorName,
            schemeOperatorAddress = info.schemeOperatorAddress,
            schemeName = info.schemeName,
            schemeInformationURI = info.schemeInformationURI,
            statusDeterminationApproach = info.statusDeterminationApproach,
            schemeTerritory = info.schemeTerritory,
            listIssueDateTime = info.listIssueDateTime,
            nextUpdate = info.nextUpdate,
            distributionPoints = info.distributionPoints,
            schemeTypeCommunityRules = info.schemeTypeCommunityRules,
            policyOrLegalNotice = info.policyOrLegalNotice,
            historicalInformationPeriod = info.historicalInformationPeriod,
            trustedEntities = trustedEntities,
            pointersToOtherLoTE = info.pointersToOtherLoTE,
        )
    }

    private data class SchemeInfoParsed(
        val versionIdentifier: Int,
        val sequenceNumber: Int,
        val type: String,
        val schemeOperatorName: List<MultiLangString>,
        val schemeOperatorAddress: ETSIOperatorAddress?,
        val schemeName: List<MultiLangString>,
        val schemeInformationURI: List<MultiLangURI>,
        val statusDeterminationApproach: String,
        val schemeTerritory: String,
        val listIssueDateTime: Instant,
        val nextUpdate: Instant,
        val distributionPoints: List<String>,
        val schemeTypeCommunityRules: List<MultiLangURI>,
        val policyOrLegalNotice: List<MultiLangString>,
        val historicalInformationPeriod: Int?,
        val pointersToOtherLoTE: List<ETSIOtherLoTEPointer>,
    )

    private fun parseSchemeInformation(reader: XmlReader): SchemeInfoParsed {
        var tslVersionIdentifier: Int? = null
        var tslSequenceNumber: Int? = null
        var tslType: String? = null
        var schemeTerritory: String? = null
        var listIssueDateTime: Instant? = null
        var nextUpdate: Instant? = null
        var schemeOperatorName = listOf<MultiLangString>()
        var schemeOperatorAddress: ETSIOperatorAddress? = null
        var schemeName = listOf<MultiLangString>()
        var schemeInformationURI = listOf<MultiLangURI>()
        var statusDeterminationApproach = ""
        var schemeTypeCommunityRules = listOf<MultiLangURI>()
        var policyOrLegalNotice = listOf<MultiLangString>()
        var historicalInformationPeriod: Int? = null
        var distributionPoints = listOf<String>()
        var pointersToOtherLoTE = listOf<ETSIOtherLoTEPointer>()

        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "TSLVersionIdentifier" -> tslVersionIdentifier = reader.readSimpleElement().toIntOrNull() ?: 5
                    "TSLSequenceNumber" -> tslSequenceNumber = reader.readSimpleElement().toIntOrNull()
                    "TSLType" -> tslType = reader.readSimpleElement()
                    "SchemeTerritory" -> schemeTerritory = reader.readSimpleElement()
                    "ListIssueDateTime" -> listIssueDateTime = Instant.parse(reader.readSimpleElement())
                    "NextUpdate" -> nextUpdate = parseNextUpdate(reader)
                    "SchemeOperatorName" -> schemeOperatorName = parseMultiLangStrings(reader)
                    "SchemeName" -> schemeName = parseMultiLangStrings(reader)
                    "SchemeInformationURI" -> schemeInformationURI = parseMultiLangURIs(reader)
                    "StatusDeterminationApproach" -> statusDeterminationApproach = reader.readSimpleElement()
                    "SchemeTypeCommunityRules" -> schemeTypeCommunityRules = parseMultiLangURIs(reader)
                    "PolicyOrLegalNotice" -> policyOrLegalNotice = parseMultiLangStrings(reader)
                    "HistoricalInformationPeriod" -> historicalInformationPeriod = reader.readSimpleElement().toIntOrNull()
                    "SchemeOperatorAddress" -> schemeOperatorAddress = parseTSPAddress(reader)
                    "DistributionPoints" -> distributionPoints = parseDistributionPoints(reader)
                    "PointersToOtherTSL" -> pointersToOtherLoTE = parsePointersToOtherTSL(reader)
                    else -> reader.skipElement()
                }
            }
        }

        return SchemeInfoParsed(
            versionIdentifier = tslVersionIdentifier ?: 5,
            sequenceNumber = tslSequenceNumber ?: throw ETSIParseException("TSLSequenceNumber is required"),
            type = tslType ?: throw ETSIParseException("TSLType is required"),
            schemeOperatorName = schemeOperatorName,
            schemeOperatorAddress = schemeOperatorAddress,
            schemeName = schemeName,
            schemeInformationURI = schemeInformationURI,
            statusDeterminationApproach = statusDeterminationApproach,
            schemeTerritory = schemeTerritory ?: throw ETSIParseException("SchemeTerritory is required"),
            listIssueDateTime = listIssueDateTime ?: throw ETSIParseException("ListIssueDateTime is required"),
            nextUpdate = nextUpdate ?: throw ETSIParseException("NextUpdate is required"),
            distributionPoints = distributionPoints,
            schemeTypeCommunityRules = schemeTypeCommunityRules,
            policyOrLegalNotice = policyOrLegalNotice,
            historicalInformationPeriod = historicalInformationPeriod,
            pointersToOtherLoTE = pointersToOtherLoTE,
        )
    }

    private fun parseNextUpdate(reader: XmlReader): Instant {
        var dateTime: Instant? = null
        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT && reader.localName == "dateTime") {
                dateTime = Instant.parse(reader.readSimpleElement())
            }
        }
        return dateTime ?: throw ETSIParseException("NextUpdate dateTime is required")
    }

    private fun parseMultiLangStrings(reader: XmlReader): List<MultiLangString> {
        val result = mutableListOf<MultiLangString>()
        val parentElementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.eventType == EventType.END_ELEMENT) {
                if ((reader.depth < depth) || (reader.depth == depth && reader.localName == parentElementName)) {
                    break
                }
            }

            if (reader.eventType == EventType.START_ELEMENT) {
                val lang =
                    reader.getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang")
                        ?: reader.getAttributeValue(null, "xml:lang")
                        ?: "en"
                val text = reader.readSimpleElement().trim()
                result.add(MultiLangString(lang, text))
            }
        }

        return result
    }

    private fun parseMultiLangURIs(reader: XmlReader): List<MultiLangURI> {
        val result = mutableListOf<MultiLangURI>()
        val parentElementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.eventType == EventType.END_ELEMENT) {
                if ((reader.depth < depth) || (reader.depth == depth && reader.localName == parentElementName)) {
                    break
                }
            }

            if (reader.eventType == EventType.START_ELEMENT) {
                val lang =
                    reader.getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang")
                        ?: reader.getAttributeValue(null, "xml:lang")
                        ?: "en"
                val text = reader.readSimpleElement().trim()
                result.add(MultiLangURI(lang, text))
            }
        }

        return result
    }

    private fun parseDistributionPoints(reader: XmlReader): List<String> {
        val result = mutableListOf<String>()
        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT && reader.localName == "URI") {
                result.add(reader.readSimpleElement().trim())
            }
        }
        return result
    }

    private fun parsePointersToOtherTSL(reader: XmlReader): List<ETSIOtherLoTEPointer> {
        val result = mutableListOf<ETSIOtherLoTEPointer>()
        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT && reader.localName == "OtherTSLPointer") {
                result.add(parseOtherTSLPointer(reader))
            }
        }
        return result
    }

    private fun parseOtherTSLPointer(reader: XmlReader): ETSIOtherLoTEPointer {
        var tslLocation: String? = null
        var schemeTerritory: String? = null
        var schemeOperatorName = listOf<MultiLangString>()
        val serviceDigitalIdentities = mutableListOf<ETSIServiceDigitalIdentity>()
        var additionalInfo: ETSIAdditionalInformation? = null

        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "TSLLocation" -> {
                        tslLocation = reader.readSimpleElement()
                    }

                    "SchemeTerritory" -> {
                        schemeTerritory = reader.readSimpleElement()
                    }

                    "SchemeOperatorName" -> {
                        schemeOperatorName = parseMultiLangStrings(reader)
                    }

                    "ServiceDigitalIdentities" -> {
                        serviceDigitalIdentities.addAll(parseServiceDigitalIdentities(reader))
                    }

                    "AdditionalInformation" -> {
                        val parsed = parseAdditionalInformation(reader)
                        additionalInfo = parsed.additionalInformation
                        if (schemeTerritory == null && parsed.schemeTerritory != null) {
                            schemeTerritory = parsed.schemeTerritory
                        }
                    }

                    else -> {
                        reader.skipElement()
                    }
                }
            }
        }

        return ETSIOtherLoTEPointer(
            schemeOperatorName = schemeOperatorName,
            schemeTerritory = schemeTerritory ?: throw ETSIParseException("SchemeTerritory is required"),
            location = tslLocation ?: throw ETSIParseException("TSLLocation is required"),
            serviceDigitalIdentities = serviceDigitalIdentities,
            additionalInformation = additionalInfo,
        )
    }

    private fun parseServiceDigitalIdentities(reader: XmlReader): List<ETSIServiceDigitalIdentity> {
        val result = mutableListOf<ETSIServiceDigitalIdentity>()
        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT && reader.localName == "ServiceDigitalIdentity") {
                result.add(parseServiceDigitalIdentity(reader))
            }
        }
        return result
    }

    private fun parseServiceDigitalIdentity(reader: XmlReader): ETSIServiceDigitalIdentity {
        val x509Certificates = mutableListOf<String>()
        var subjectName: String? = null
        val x509SKIs = mutableListOf<String>()

        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT && reader.localName == "DigitalId") {
                val digitalId = parseDigitalId(reader)
                digitalId["x509Certificate"]?.let { x509Certificates.add(it) }
                if (subjectName == null) subjectName = digitalId["subjectName"]
                digitalId["x509SKI"]?.let { x509SKIs.add(it) }
            }
        }

        return ETSIServiceDigitalIdentity(
            x509Certificates = x509Certificates,
            subjectName = subjectName,
            x509SKIs = x509SKIs,
        )
    }

    private fun parseDigitalId(reader: XmlReader): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    // Base64 fields: strip whitespace that XML formatting introduces
                    "X509Certificate" -> result["x509Certificate"] = reader.readSimpleElement().filterNot { it.isWhitespace() }

                    "X509SubjectName" -> result["subjectName"] = reader.readSimpleElement()

                    "X509SKI" -> result["x509SKI"] = reader.readSimpleElement().filterNot { it.isWhitespace() }
                }
            }
        }
        return result
    }

    private fun parseAdditionalInformation(reader: XmlReader): AdditionalInformationParsed {
        val textualInformation = mutableListOf<MultiLangString>()
        val otherInformation = mutableListOf<String>()
        var mimeType: String? = null
        var schemeTerritory: String? = null

        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "TextualInformation" -> {
                        val lang =
                            reader.getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang")
                                ?: reader.getAttributeValue(null, "xml:lang")
                                ?: "en"
                        textualInformation.add(MultiLangString(lang, reader.readSimpleElement().trim()))
                    }

                    "OtherInformation" -> {
                        val content = parseOtherInformationElement(reader)
                        if (content.containsKey("mimeType")) {
                            mimeType = content["mimeType"]
                        }
                        if (content.containsKey("schemeTerritory")) {
                            schemeTerritory = content["schemeTerritory"]
                        }
                        val value = content["mimeType"] ?: content["schemeTerritory"] ?: content["tslType"] ?: content["skipped"] ?: content["text"] ?: ""
                        if (value.isNotBlank()) {
                            otherInformation.add(value)
                        }
                    }
                }
            }
        }

        val additionalInfo =
            if (textualInformation.isEmpty() && otherInformation.isEmpty() && mimeType == null) {
                null
            } else {
                ETSIAdditionalInformation(
                    textualInformation = textualInformation,
                    otherInformation = otherInformation,
                    mimeType = mimeType,
                )
            }

        return AdditionalInformationParsed(additionalInfo, schemeTerritory)
    }

    private data class AdditionalInformationParsed(
        val additionalInformation: ETSIAdditionalInformation?,
        val schemeTerritory: String?,
    )

    private fun parseOtherInformationElement(reader: XmlReader): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val textBuilder = StringBuilder()

        val depth = reader.depth
        while (reader.hasNext()) {
            when (reader.next()) {
                EventType.END_ELEMENT -> {
                    if (reader.depth <= depth) {
                        if (result.isEmpty() && textBuilder.isNotEmpty()) {
                            result["text"] = textBuilder.toString().trim()
                        }
                        break
                    }
                }

                EventType.START_ELEMENT -> {
                    val elName = reader.localName
                    when (elName) {
                        "MimeType" -> {
                            result["mimeType"] = reader.readSimpleElement().trim()
                        }

                        "SchemeTerritory" -> {
                            result["schemeTerritory"] = reader.readSimpleElement().trim()
                        }

                        "TSLType" -> {
                            result["tslType"] = reader.readSimpleElement().trim()
                        }

                        else -> {
                            result["skipped"] = elName
                            reader.skipElement()
                        }
                    }
                }

                EventType.TEXT, EventType.CDSECT -> {
                    textBuilder.append(reader.text)
                }

                else -> {}
            }
        }

        return result
    }

    private fun parseTrustServiceProviderList(reader: XmlReader): List<ETSITrustedEntity> {
        val result = mutableListOf<ETSITrustedEntity>()
        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT && reader.localName == "TrustServiceProvider") {
                result.add(parseTrustServiceProvider(reader))
            }
        }
        return result
    }

    private fun parseTrustServiceProvider(reader: XmlReader): ETSITrustedEntity {
        var entityInfo: ETSITrustedEntityInformation? = null
        val entityServices = mutableListOf<ETSITrustedEntityService>()

        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "TSPInformation" -> entityInfo = parseTSPInformation(reader)
                    "TSPServices" -> entityServices.addAll(parseTSPServices(reader))
                }
            }
        }

        return ETSITrustedEntity(
            trustedEntityInformation = entityInfo ?: throw ETSIParseException("TSPInformation is required"),
            trustedEntityServices = entityServices,
        )
    }

    private fun parseTSPInformation(reader: XmlReader): ETSITrustedEntityInformation {
        var name = listOf<MultiLangString>()
        var tradeName = listOf<MultiLangString>()
        var address: ETSIOperatorAddress? = null
        var informationURI = listOf<MultiLangURI>()
        var identifier: String? = null

        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "TSPName" -> name = parseMultiLangStrings(reader)
                    "TSPTradeName" -> tradeName = parseMultiLangStrings(reader)
                    "TSPAddress" -> address = parseTSPAddress(reader)
                    "TSPInformationURI" -> informationURI = parseMultiLangURIs(reader)
                    "TSPIdentifier" -> identifier = reader.readSimpleElement()
                    else -> reader.skipElement()
                }
            }
        }

        return ETSITrustedEntityInformation(
            name = name,
            tradeName = tradeName,
            address =
                address ?: ETSIOperatorAddress(
                    postalAddresses =
                        listOf(
                            PostalAddress(streetAddress = "", locality = "", postalCode = null, countryName = ""),
                        ),
                ),
            informationURI = informationURI,
            identifier = identifier,
        )
    }

    private fun parseTSPAddress(reader: XmlReader): ETSIOperatorAddress {
        val postalAddresses = mutableListOf<PostalAddress>()
        val electronicAddresses = mutableListOf<String>()

        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "PostalAddresses" -> postalAddresses.addAll(parsePostalAddresses(reader))
                    "ElectronicAddress" -> electronicAddresses.addAll(parseElectronicAddresses(reader))
                }
            }
        }

        if (postalAddresses.isEmpty()) {
            postalAddresses.add(
                PostalAddress(streetAddress = "", locality = "", postalCode = null, countryName = ""),
            )
        }

        return ETSIOperatorAddress(
            postalAddresses = postalAddresses,
            electronicAddresses = electronicAddresses,
        )
    }

    private fun parsePostalAddresses(reader: XmlReader): List<PostalAddress> {
        val result = mutableListOf<PostalAddress>()
        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT && reader.localName == "PostalAddress") {
                result.add(parsePostalAddress(reader))
            }
        }
        return result
    }

    private fun parsePostalAddress(reader: XmlReader): PostalAddress {
        var streetAddress = ""
        var locality = ""
        var stateOrProvince: String? = null
        var postalCode: String? = null
        var countryName = ""

        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "StreetAddress" -> streetAddress = reader.readSimpleElement()
                    "Locality" -> locality = reader.readSimpleElement()
                    "StateOrProvince" -> stateOrProvince = reader.readSimpleElement()
                    "PostalCode" -> postalCode = reader.readSimpleElement()
                    "CountryName" -> countryName = reader.readSimpleElement()
                }
            }
        }

        return PostalAddress(
            streetAddress = streetAddress,
            locality = locality,
            stateOrProvince = stateOrProvince,
            postalCode = postalCode,
            countryName = countryName,
        )
    }

    private fun parseElectronicAddresses(reader: XmlReader): List<String> {
        val result = mutableListOf<String>()
        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT && reader.localName == "URI") {
                result.add(reader.readSimpleElement().trim())
            }
        }
        return result
    }

    private fun parseTSPServices(reader: XmlReader): List<ETSITrustedEntityService> {
        val result = mutableListOf<ETSITrustedEntityService>()
        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT && reader.localName == "TSPService") {
                result.add(parseTSPService(reader))
            }
        }
        return result
    }

    private fun parseTSPService(reader: XmlReader): ETSITrustedEntityService {
        var serviceInformation: ETSIServiceInformation? = null
        val serviceHistory = mutableListOf<ETSIServiceInformation>()

        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "ServiceInformation" -> serviceInformation = parseServiceInformation(reader)
                    "ServiceHistory" -> serviceHistory.addAll(parseServiceHistory(reader))
                }
            }
        }

        return ETSITrustedEntityService(
            serviceInformation = serviceInformation ?: throw ETSIParseException("ServiceInformation is required"),
            serviceHistory = serviceHistory,
        )
    }

    private fun parseServiceInformation(reader: XmlReader): ETSIServiceInformation {
        var serviceTypeIdentifier: String? = null
        var serviceName = listOf<MultiLangString>()
        var digitalIdentity: ETSIServiceDigitalIdentity? = null
        var serviceStatus: String? = null
        var statusStartingTime: Instant? = null
        val serviceSupplyPoints = mutableListOf<ServiceSupplyPoint>()
        var schemeServiceDefinitionURI = listOf<MultiLangURI>()
        var tspServiceDefinitionURI = listOf<MultiLangURI>()
        val additionalServiceInfo = mutableListOf<AdditionalServiceInformation>()
        var expiredCertsRevocationInfo: Instant? = null

        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "ServiceTypeIdentifier" -> {
                        serviceTypeIdentifier = reader.readSimpleElement()
                    }

                    "ServiceName" -> {
                        serviceName = parseMultiLangStrings(reader)
                    }

                    "ServiceDigitalIdentity" -> {
                        digitalIdentity = parseServiceDigitalIdentity(reader)
                    }

                    "ServiceStatus" -> {
                        serviceStatus = reader.readSimpleElement()
                    }

                    "StatusStartingTime" -> {
                        statusStartingTime = Instant.parse(reader.readSimpleElement())
                    }

                    "ServiceSupplyPoints" -> {
                        serviceSupplyPoints.addAll(parseServiceSupplyPoints(reader))
                    }

                    "SchemeServiceDefinitionURI" -> {
                        schemeServiceDefinitionURI = parseMultiLangURIs(reader)
                    }

                    "TSPServiceDefinitionURI" -> {
                        tspServiceDefinitionURI = parseMultiLangURIs(reader)
                    }

                    "ServiceInformationExtensions" -> {
                        val extensions = parseServiceInformationExtensions(reader)
                        @Suppress("UNCHECKED_CAST")
                        additionalServiceInfo.addAll(extensions["additionalServiceInfo"] as List<AdditionalServiceInformation>)
                        expiredCertsRevocationInfo = extensions["expiredCertsRevocationInfo"] as? Instant
                    }

                    else -> {
                        reader.skipElement()
                    }
                }
            }
        }

        return ETSIServiceInformation(
            serviceTypeIdentifier = serviceTypeIdentifier ?: throw ETSIParseException("ServiceTypeIdentifier is required"),
            serviceName = serviceName,
            serviceDigitalIdentity = digitalIdentity ?: ETSIServiceDigitalIdentity(),
            serviceStatus = serviceStatus ?: throw ETSIParseException("ServiceStatus is required"),
            statusStartingTime = statusStartingTime ?: throw ETSIParseException("StatusStartingTime is required"),
            serviceSupplyPoints = serviceSupplyPoints,
            schemeServiceDefinitionURI = schemeServiceDefinitionURI,
            tspServiceDefinitionURI = tspServiceDefinitionURI,
            additionalServiceInformation = additionalServiceInfo,
            expiredCertsRevocationInfo = expiredCertsRevocationInfo,
        )
    }

    private fun parseServiceSupplyPoints(reader: XmlReader): List<ServiceSupplyPoint> {
        val result = mutableListOf<ServiceSupplyPoint>()
        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT && reader.localName == "ServiceSupplyPoint") {
                val type = reader.getAttributeValue(null, "type")
                val uri = reader.readSimpleElement().trim()
                if (uri.isNotEmpty()) {
                    result.add(ServiceSupplyPoint(uri = uri, type = type))
                }
            }
        }
        return result
    }

    private fun parseServiceInformationExtensions(reader: XmlReader): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val additionalServiceInfo = mutableListOf<AdditionalServiceInformation>()
        var expiredCertsRevocationInfo: Instant? = null

        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT && reader.localName == "Extension") {
                val extension = parseExtension(reader)
                extension["additionalServiceInfo"]?.let {
                    additionalServiceInfo.add(it as AdditionalServiceInformation)
                }
                extension["expiredCertsRevocationInfo"]?.let {
                    expiredCertsRevocationInfo = it as Instant
                }
            }
        }

        result["additionalServiceInfo"] = additionalServiceInfo
        if (expiredCertsRevocationInfo != null) {
            result["expiredCertsRevocationInfo"] = expiredCertsRevocationInfo!!
        }

        return result
    }

    private fun parseExtension(reader: XmlReader): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "AdditionalServiceInformation" -> {
                        result["additionalServiceInfo"] = parseAdditionalServiceInformationElement(reader)
                    }

                    "ExpiredCertsRevocationInfo" -> {
                        val dateTimeStr = reader.readSimpleElement().trim()
                        result["expiredCertsRevocationInfo"] = Instant.parse(dateTimeStr)
                    }
                }
            }
        }
        return result
    }

    private fun parseAdditionalServiceInformationElement(reader: XmlReader): AdditionalServiceInformation {
        val uriList = mutableListOf<MultiLangURI>()
        var informationValue: String? = null
        val otherInfoList = mutableListOf<MultiLangString>()

        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "URI" -> {
                        val lang =
                            reader.getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang")
                                ?: reader.getAttributeValue(null, "xml:lang")
                                ?: "en"
                        uriList.add(MultiLangURI(lang, reader.readSimpleElement().trim()))
                    }

                    "InformationValue" -> {
                        informationValue = reader.readSimpleElement()
                    }

                    "OtherInformation" -> {
                        val lang =
                            reader.getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang")
                                ?: reader.getAttributeValue(null, "xml:lang")
                                ?: "en"
                        otherInfoList.add(MultiLangString(lang, reader.readSimpleElement().trim()))
                    }
                }
            }
        }

        return AdditionalServiceInformation(
            uri = uriList,
            informationValue = informationValue,
            otherInformation = otherInfoList,
        )
    }

    private fun parseServiceHistory(reader: XmlReader): List<ETSIServiceInformation> {
        val result = mutableListOf<ETSIServiceInformation>()
        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT && reader.localName == "ServiceHistory") {
                val historyDepth = reader.depth
                while (reader.hasNext()) {
                    reader.next()
                    if (reader.eventType == EventType.END_ELEMENT && reader.depth == historyDepth) break
                    if (reader.eventType == EventType.START_ELEMENT && reader.localName == "ServiceInformation") {
                        result.add(parseServiceInformation(reader))
                    }
                }
            }
        }
        return result
    }

    /**
     * Platform-independent check for whether we've exited the parent element.
     */
    private fun XmlReader.hasExitedElement(
        startDepth: Int,
        startElementName: String,
    ): Boolean {
        if (eventType != EventType.END_ELEMENT) return false
        return (depth < startDepth) || (depth == startDepth && localName == startElementName)
    }

    private fun XmlReader.skipToElement(elementName: String) {
        while (hasNext()) {
            next()
            if (eventType == EventType.START_ELEMENT && localName == elementName) {
                return
            }
        }
        throw ETSIParseException("Element $elementName not found")
    }

    private fun XmlReader.readSimpleElement(): String {
        val text = StringBuilder()
        val depth = this.depth
        while (hasNext()) {
            when (next()) {
                EventType.TEXT, EventType.CDSECT -> {
                    text.append(this.text)
                }

                EventType.END_ELEMENT -> {
                    if (this.depth <= depth) return text.toString()
                }

                else -> {}
            }
        }
        return text.toString()
    }

    private fun XmlReader.skipElement() {
        val startDepth = this.depth
        while (hasNext()) {
            next()
            if (eventType == EventType.END_ELEMENT && this.depth <= startDepth) {
                return
            }
        }
    }
}
