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

package com.sphereon.trust.etsi.lote.serialization

import com.sphereon.trust.etsi.lote.model.*
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonElement
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.XmlWriter
import nl.adaptivity.xmlutil.newWriter
import nl.adaptivity.xmlutil.xmlStreaming

/**
 * XML serialization/deserialization for ETSI TS 119 602 LoTE.
 *
 * Uses xmlutil streaming API for cross-platform parsing.
 * The 602 XML namespace is "http://uri.etsi.org/019602/v1#".
 */
object LoTEXml {
    const val NAMESPACE_602 = "http://uri.etsi.org/019602/v1#"

    /**
     * Parses a LoTE from an XML string.
     */
    fun parse(xmlString: String): LoTE {
        val reader = xmlStreaming.newReader(xmlString)
        return parseWithReader(reader)
    }

    /**
     * Parses a LoTE from XML bytes.
     */
    fun parse(xmlData: ByteArray): LoTE = parse(xmlData.decodeToString())

    /**
     * Encodes a LoTE to an XML string following the ETSI TS 119 602 XML binding.
     */
    fun encode(lote: LoTE): String {
        val sb = StringBuilder()
        val writer = xmlStreaming.newWriter(sb, repairNamespaces = false, xmlDeclMode = XmlDeclMode.None)
        writer.startDocument("1.0", "UTF-8", null)
        writer.setPrefix("", NAMESPACE_602)
        writer.startTag(NAMESPACE_602, "ListOfTrustedEntities", "")
        writeListAndSchemeInformation(writer, lote.listAndSchemeInformation)
        if (lote.trustedEntitiesList.isNotEmpty()) {
            writer.startTag(NAMESPACE_602, "TrustedEntitiesList", "")
            for (entity in lote.trustedEntitiesList) {
                writeTrustedEntity(writer, entity)
            }
            writer.endTag(NAMESPACE_602, "TrustedEntitiesList", "")
        }
        writer.endTag(NAMESPACE_602, "ListOfTrustedEntities", "")
        writer.endDocument()
        writer.close()
        return sb.toString()
    }

    // --- Writer helpers ---

    private fun writeListAndSchemeInformation(writer: XmlWriter, info: ListAndSchemeInformation) {
        writer.startTag(NAMESPACE_602, "ListAndSchemeInformation", "")

        writer.writeSimpleElement(NAMESPACE_602, "LoTEVersionIdentifier", info.versionIdentifier.toString())
        writer.writeSimpleElement(NAMESPACE_602, "LoTESequenceNumber", info.sequenceNumber.toString())
        info.type?.let { writer.writeSimpleElement(NAMESPACE_602, "LoTEType", it) }
        writer.writeMultiLangStrings(NAMESPACE_602, "SchemeOperatorName", "Name", info.schemeOperatorName)
        info.schemeOperatorAddress?.let { writeOperatorAddress(writer, "SchemeOperatorAddress", it) }
        writer.writeMultiLangStrings(NAMESPACE_602, "SchemeName", "Name", info.schemeName)
        writer.writeMultiLangURIs(NAMESPACE_602, "SchemeInformationURI", "URI", info.schemeInformationURI)
        info.statusDeterminationApproach?.let { writer.writeSimpleElement(NAMESPACE_602, "StatusDeterminationApproach", it) }
        writer.writeMultiLangURIs(NAMESPACE_602, "SchemeTypeCommunityRules", "URI", info.schemeTypeCommunityRules)
        info.schemeTerritory?.let { writer.writeSimpleElement(NAMESPACE_602, "SchemeTerritory", it) }
        if (info.policyOrLegalNotice.isNotEmpty()) {
            writePolicyOrLegalNotice(writer, info.policyOrLegalNotice)
        }
        info.historicalInformationPeriod?.let { writer.writeSimpleElement(NAMESPACE_602, "HistoricalInformationPeriod", it.toString()) }
        if (info.pointersToOtherLoTE.isNotEmpty()) {
            writer.startTag(NAMESPACE_602, "PointersToOtherLoTE", "")
            for (pointer in info.pointersToOtherLoTE) {
                writeOtherLoTEPointer(writer, pointer)
            }
            writer.endTag(NAMESPACE_602, "PointersToOtherLoTE", "")
        }
        writer.writeSimpleElement(NAMESPACE_602, "ListIssueDateTime", info.listIssueDateTime.toString())
        writer.startTag(NAMESPACE_602, "NextUpdate", "")
        writer.writeSimpleElement(NAMESPACE_602, "dateTime", info.nextUpdate.toString())
        writer.endTag(NAMESPACE_602, "NextUpdate", "")
        if (info.distributionPoints.isNotEmpty()) {
            writer.startTag(NAMESPACE_602, "DistributionPoints", "")
            for (uri in info.distributionPoints) {
                writer.writeSimpleElement(NAMESPACE_602, "URI", uri)
            }
            writer.endTag(NAMESPACE_602, "DistributionPoints", "")
        }

        writer.endTag(NAMESPACE_602, "ListAndSchemeInformation", "")
    }

    private fun writePolicyOrLegalNotice(writer: XmlWriter, entries: List<PolicyOrLegalNoticeEntry>) {
        writer.startTag(NAMESPACE_602, "PolicyOrLegalNotice", "")
        for (entry in entries) {
            if (entry.noticeURI != null) {
                writer.startTag(NAMESPACE_602, "URI", "")
                entry.lang?.let { writer.attribute("http://www.w3.org/XML/1998/namespace", "lang", "xml", it) }
                writer.text(entry.noticeURI)
                writer.endTag(NAMESPACE_602, "URI", "")
            } else if (entry.notice != null) {
                writer.startTag(NAMESPACE_602, "Notice", "")
                entry.lang?.let { writer.attribute("http://www.w3.org/XML/1998/namespace", "lang", "xml", it) }
                writer.text(entry.notice)
                writer.endTag(NAMESPACE_602, "Notice", "")
            }
        }
        writer.endTag(NAMESPACE_602, "PolicyOrLegalNotice", "")
    }

    private fun writeOperatorAddress(writer: XmlWriter, elementName: String, address: OperatorAddress) {
        writer.startTag(NAMESPACE_602, elementName, "")
        if (address.postalAddresses.isNotEmpty()) {
            writer.startTag(NAMESPACE_602, "PostalAddresses", "")
            for (postal in address.postalAddresses) {
                writePostalAddress(writer, postal)
            }
            writer.endTag(NAMESPACE_602, "PostalAddresses", "")
        }
        if (address.electronicAddress.isNotEmpty()) {
            writer.writeMultiLangURIs(NAMESPACE_602, "ElectronicAddress", "URI", address.electronicAddress)
        }
        writer.endTag(NAMESPACE_602, elementName, "")
    }

    private fun writePostalAddress(writer: XmlWriter, postal: LoTEPostalAddress) {
        writer.startTag(NAMESPACE_602, "PostalAddress", "")
        postal.lang?.let { writer.attribute("http://www.w3.org/XML/1998/namespace", "lang", "xml", it) }
        writer.writeSimpleElement(NAMESPACE_602, "StreetAddress", postal.streetAddress)
        postal.locality?.let { writer.writeSimpleElement(NAMESPACE_602, "Locality", it) }
        postal.stateOrProvince?.let { writer.writeSimpleElement(NAMESPACE_602, "StateOrProvince", it) }
        postal.postalCode?.let { writer.writeSimpleElement(NAMESPACE_602, "PostalCode", it) }
        writer.writeSimpleElement(NAMESPACE_602, "CountryName", postal.countryName)
        writer.endTag(NAMESPACE_602, "PostalAddress", "")
    }

    private fun writeOtherLoTEPointer(writer: XmlWriter, pointer: OtherLoTEPointer) {
        writer.startTag(NAMESPACE_602, "OtherLoTEPointer", "")
        writer.writeSimpleElement(NAMESPACE_602, "LoTELocation", pointer.location)
        if (pointer.serviceDigitalIdentities.isNotEmpty()) {
            writer.startTag(NAMESPACE_602, "ServiceDigitalIdentities", "")
            for (identity in pointer.serviceDigitalIdentities) {
                writeServiceDigitalIdentity(writer, identity)
            }
            writer.endTag(NAMESPACE_602, "ServiceDigitalIdentities", "")
        }
        if (pointer.qualifiers.isNotEmpty()) {
            writer.startTag(NAMESPACE_602, "LoTEQualifiers", "")
            for (qualifier in pointer.qualifiers) {
                writeLoTEQualifier(writer, qualifier)
            }
            writer.endTag(NAMESPACE_602, "LoTEQualifiers", "")
        }
        writer.endTag(NAMESPACE_602, "OtherLoTEPointer", "")
    }

    private fun writeLoTEQualifier(writer: XmlWriter, qualifier: LoTEQualifier) {
        writer.startTag(NAMESPACE_602, "LoTEQualifier", "")
        qualifier.loTEType?.let { writer.writeSimpleElement(NAMESPACE_602, "LoTEType", it) }
        writer.writeMultiLangStrings(NAMESPACE_602, "SchemeOperatorName", "Name", qualifier.schemeOperatorName)
        writer.writeMultiLangURIs(NAMESPACE_602, "SchemeTypeCommunityRules", "URI", qualifier.schemeTypeCommunityRules)
        qualifier.schemeTerritory?.let { writer.writeSimpleElement(NAMESPACE_602, "SchemeTerritory", it) }
        qualifier.mimeType?.let { writer.writeSimpleElement(NAMESPACE_602, "MimeType", it) }
        writer.endTag(NAMESPACE_602, "LoTEQualifier", "")
    }

    private fun writeTrustedEntity(writer: XmlWriter, entity: TrustedEntity) {
        writer.startTag(NAMESPACE_602, "TrustedEntity", "")
        writeTrustedEntityInformation(writer, entity.trustedEntityInformation)
        if (entity.trustedEntityServices.isNotEmpty()) {
            writer.startTag(NAMESPACE_602, "TrustedEntityServices", "")
            for (service in entity.trustedEntityServices) {
                writeTrustedEntityService(writer, service)
            }
            writer.endTag(NAMESPACE_602, "TrustedEntityServices", "")
        }
        writer.endTag(NAMESPACE_602, "TrustedEntity", "")
    }

    private fun writeTrustedEntityInformation(writer: XmlWriter, info: TrustedEntityInformation) {
        writer.startTag(NAMESPACE_602, "TrustedEntityInformation", "")
        writer.writeMultiLangStrings(NAMESPACE_602, "TEName", "Name", info.name)
        writer.writeMultiLangStrings(NAMESPACE_602, "TETradeName", "Name", info.tradeName)
        info.address?.let { writeOperatorAddress(writer, "TEAddress", it) }
        writer.writeMultiLangURIs(NAMESPACE_602, "TEInformationURI", "URI", info.informationURI)
        writer.endTag(NAMESPACE_602, "TrustedEntityInformation", "")
    }

    private fun writeTrustedEntityService(writer: XmlWriter, service: TrustedEntityService) {
        writer.startTag(NAMESPACE_602, "TrustedEntityService", "")
        writeServiceInformation(writer, service.serviceInformation, "ServiceInformation")
        if (service.serviceHistory.isNotEmpty()) {
            writer.startTag(NAMESPACE_602, "ServiceHistory", "")
            for (history in service.serviceHistory) {
                writeServiceInformation(writer, history, "ServiceHistoryInstance")
            }
            writer.endTag(NAMESPACE_602, "ServiceHistory", "")
        }
        writer.endTag(NAMESPACE_602, "TrustedEntityService", "")
    }

    private fun writeServiceInformation(writer: XmlWriter, info: LoTEServiceInformation, elementName: String) {
        writer.startTag(NAMESPACE_602, elementName, "")
        writer.writeSimpleElement(NAMESPACE_602, "ServiceTypeIdentifier", info.serviceTypeIdentifier)
        writer.writeMultiLangStrings(NAMESPACE_602, "ServiceName", "Name", info.serviceName)
        writeServiceDigitalIdentity(writer, info.serviceDigitalIdentity)
        info.serviceStatus?.let { writer.writeSimpleElement(NAMESPACE_602, "ServiceStatus", it) }
        info.statusStartingTime?.let { writer.writeSimpleElement(NAMESPACE_602, "StatusStartingTime", it.toString()) }
        if (info.serviceSupplyPoints.isNotEmpty()) {
            writer.startTag(NAMESPACE_602, "ServiceSupplyPoints", "")
            for (uri in info.serviceSupplyPoints) {
                writer.writeSimpleElement(NAMESPACE_602, "URI", uri)
            }
            writer.endTag(NAMESPACE_602, "ServiceSupplyPoints", "")
        }
        writer.writeMultiLangURIs(NAMESPACE_602, "SchemeServiceDefinitionURI", "URI", info.schemeServiceDefinitionURI)
        writer.writeMultiLangURIs(NAMESPACE_602, "TEServiceDefinitionURI", "URI", info.teServiceDefinitionURI)
        writer.endTag(NAMESPACE_602, elementName, "")
    }

    private fun writeServiceDigitalIdentity(writer: XmlWriter, identity: LoTEServiceDigitalIdentity) {
        if (identity.x509Certificates.isEmpty() && identity.x509SubjectNames.isEmpty() && identity.x509SKIs.isEmpty()) {
            return
        }
        writer.startTag(NAMESPACE_602, "ServiceDigitalIdentity", "")
        for (cert in identity.x509Certificates) {
            writer.writeSimpleElement(NAMESPACE_602, "X509Certificate", cert.value)
        }
        for (name in identity.x509SubjectNames) {
            writer.writeSimpleElement(NAMESPACE_602, "X509SubjectName", name)
        }
        for (ski in identity.x509SKIs) {
            writer.writeSimpleElement(NAMESPACE_602, "X509SKI", ski)
        }
        writer.endTag(NAMESPACE_602, "ServiceDigitalIdentity", "")
    }

    // --- XmlWriter extension helpers ---

    private fun XmlWriter.writeSimpleElement(ns: String, localName: String, value: String) {
        startTag(ns, localName, "")
        text(value)
        endTag(ns, localName, "")
    }

    private fun XmlWriter.writeMultiLangStrings(ns: String, containerName: String, elementName: String, items: List<MultiLangString>) {
        if (items.isEmpty()) return
        startTag(ns, containerName, "")
        for (item in items) {
            startTag(ns, elementName, "")
            attribute("http://www.w3.org/XML/1998/namespace", "lang", "xml", item.lang)
            text(item.value)
            endTag(ns, elementName, "")
        }
        endTag(ns, containerName, "")
    }

    private fun XmlWriter.writeMultiLangURIs(ns: String, containerName: String, elementName: String, items: List<MultiLangURI>) {
        if (items.isEmpty()) return
        startTag(ns, containerName, "")
        for (item in items) {
            startTag(ns, elementName, "")
            attribute("http://www.w3.org/XML/1998/namespace", "lang", "xml", item.lang)
            text(item.uriValue)
            endTag(ns, elementName, "")
        }
        endTag(ns, containerName, "")
    }

    // --- Parser methods ---

    private fun parseWithReader(reader: XmlReader): LoTE {
        reader.skipToElement("ListOfTrustedEntities")

        var listAndSchemeInfo: ListAndSchemeInformation? = null
        val trustedEntities = mutableListOf<TrustedEntity>()

        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "ListAndSchemeInformation" -> listAndSchemeInfo = parseListAndSchemeInformation(reader)
                    "TrustedEntitiesList" -> trustedEntities.addAll(parseTrustedEntitiesList(reader))
                    "TrustedEntity" -> trustedEntities.add(parseTrustedEntity(reader))
                }
            }
        }

        return LoTE(
            listAndSchemeInformation = listAndSchemeInfo
                ?: throw IllegalArgumentException("ListAndSchemeInformation not found"),
            trustedEntitiesList = trustedEntities
        )
    }

    private fun parseListAndSchemeInformation(reader: XmlReader): ListAndSchemeInformation {
        var versionIdentifier = 1
        var sequenceNumber = 0
        var type: String? = null
        var schemeOperatorName = listOf<MultiLangString>()
        var schemeOperatorAddress: OperatorAddress? = null
        var schemeName = listOf<MultiLangString>()
        var schemeInformationURI = listOf<MultiLangURI>()
        var statusDeterminationApproach: String? = null
        var schemeTypeCommunityRules = listOf<MultiLangURI>()
        var schemeTerritory: String? = null
        var policyOrLegalNotice = listOf<PolicyOrLegalNoticeEntry>()
        var historicalInformationPeriod: Int? = null
        var pointersToOtherLoTE = listOf<OtherLoTEPointer>()
        var listIssueDateTime: Instant? = null
        var nextUpdate: Instant? = null
        var distributionPoints = listOf<String>()

        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "LoTEVersionIdentifier" -> versionIdentifier = reader.readSimpleElement().toIntOrNull() ?: 1
                    "LoTESequenceNumber" -> sequenceNumber = reader.readSimpleElement().toIntOrNull() ?: 0
                    "LoTEType" -> type = reader.readSimpleElement()
                    "SchemeOperatorName" -> schemeOperatorName = parseMultiLangStrings(reader)
                    "SchemeOperatorAddress" -> schemeOperatorAddress = parseOperatorAddress(reader)
                    "SchemeName" -> schemeName = parseMultiLangStrings(reader)
                    "SchemeInformationURI" -> schemeInformationURI = parseMultiLangURIs(reader)
                    "StatusDeterminationApproach" -> statusDeterminationApproach = reader.readSimpleElement()
                    "SchemeTypeCommunityRules" -> schemeTypeCommunityRules = parseMultiLangURIs(reader)
                    "SchemeTerritory" -> schemeTerritory = reader.readSimpleElement()
                    "PolicyOrLegalNotice" -> policyOrLegalNotice = parsePolicyOrLegalNotice(reader)
                    "HistoricalInformationPeriod" -> historicalInformationPeriod = reader.readSimpleElement().toIntOrNull()
                    "PointersToOtherLoTE" -> pointersToOtherLoTE = parsePointersToOtherLoTE(reader)
                    "ListIssueDateTime" -> listIssueDateTime = Instant.parse(reader.readSimpleElement())
                    "NextUpdate" -> nextUpdate = parseNextUpdate(reader)
                    "DistributionPoints" -> distributionPoints = parseDistributionPoints(reader)
                    else -> reader.skipElement()
                }
            }
        }

        return ListAndSchemeInformation(
            versionIdentifier = versionIdentifier,
            sequenceNumber = sequenceNumber,
            type = type,
            schemeOperatorName = schemeOperatorName,
            schemeOperatorAddress = schemeOperatorAddress,
            schemeName = schemeName,
            schemeInformationURI = schemeInformationURI,
            statusDeterminationApproach = statusDeterminationApproach,
            schemeTypeCommunityRules = schemeTypeCommunityRules,
            schemeTerritory = schemeTerritory,
            policyOrLegalNotice = policyOrLegalNotice,
            historicalInformationPeriod = historicalInformationPeriod,
            pointersToOtherLoTE = pointersToOtherLoTE,
            listIssueDateTime = listIssueDateTime
                ?: throw IllegalArgumentException("ListIssueDateTime is required"),
            nextUpdate = nextUpdate
                ?: throw IllegalArgumentException("NextUpdate is required"),
            distributionPoints = distributionPoints
        )
    }

    private fun parseNextUpdate(reader: XmlReader): Instant {
        var dateTime: Instant? = null
        val elementName = reader.localName
        val depth = reader.depth
        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "dateTime" -> dateTime = Instant.parse(reader.readSimpleElement())
                    else -> {
                        // Try to parse the element text directly as a date
                        val text = reader.readSimpleElement().trim()
                        if (text.isNotEmpty()) {
                            dateTime = Instant.parse(text)
                        }
                    }
                }
            }
        }
        // If no child element, the NextUpdate itself may contain the text
        return dateTime ?: throw IllegalArgumentException("NextUpdate dateTime is required")
    }

    private fun parseMultiLangStrings(reader: XmlReader): List<MultiLangString> {
        val result = mutableListOf<MultiLangString>()
        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                val lang = reader.getLangAttribute() ?: "en"
                val text = reader.readSimpleElement().trim()
                result.add(MultiLangString(lang, text))
            }
        }

        return result
    }

    private fun parseMultiLangURIs(reader: XmlReader): List<MultiLangURI> {
        val result = mutableListOf<MultiLangURI>()
        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                val lang = reader.getLangAttribute() ?: "en"
                val text = reader.readSimpleElement().trim()
                result.add(MultiLangURI(lang, text))
            }
        }

        return result
    }

    private fun parsePolicyOrLegalNotice(reader: XmlReader): List<PolicyOrLegalNoticeEntry> {
        val result = mutableListOf<PolicyOrLegalNoticeEntry>()
        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                val lang = reader.getLangAttribute()
                val text = reader.readSimpleElement().trim()
                result.add(PolicyOrLegalNoticeEntry(lang = lang, notice = text))
            }
        }

        return result
    }

    private fun parseOperatorAddress(reader: XmlReader): OperatorAddress {
        val postalAddresses = mutableListOf<LoTEPostalAddress>()
        val electronicAddress = mutableListOf<MultiLangURI>()

        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "PostalAddresses" -> postalAddresses.addAll(parsePostalAddresses(reader))
                    "PostalAddress" -> postalAddresses.add(parsePostalAddress(reader))
                    "ElectronicAddress" -> electronicAddress.addAll(parseMultiLangURIs(reader))
                }
            }
        }

        return OperatorAddress(postalAddresses = postalAddresses, electronicAddress = electronicAddress)
    }

    private fun parsePostalAddresses(reader: XmlReader): List<LoTEPostalAddress> {
        val result = mutableListOf<LoTEPostalAddress>()
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

    private fun parsePostalAddress(reader: XmlReader): LoTEPostalAddress {
        val lang = reader.getLangAttribute()
        var streetAddress = ""
        var locality: String? = null
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

        return LoTEPostalAddress(
            lang = lang,
            streetAddress = streetAddress,
            locality = locality,
            stateOrProvince = stateOrProvince,
            postalCode = postalCode,
            countryName = countryName
        )
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

    private fun parsePointersToOtherLoTE(reader: XmlReader): List<OtherLoTEPointer> {
        val result = mutableListOf<OtherLoTEPointer>()
        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT && reader.localName == "OtherLoTEPointer") {
                result.add(parseOtherLoTEPointer(reader))
            }
        }

        return result
    }

    private fun parseOtherLoTEPointer(reader: XmlReader): OtherLoTEPointer {
        var location: String? = null
        val serviceDigitalIdentities = mutableListOf<LoTEServiceDigitalIdentity>()
        val qualifiers = mutableListOf<LoTEQualifier>()

        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "LoTELocation" -> location = reader.readSimpleElement()
                    "ServiceDigitalIdentities" -> serviceDigitalIdentities.addAll(parseServiceDigitalIdentities(reader))
                    "LoTEQualifiers" -> qualifiers.addAll(parseLoTEQualifiers(reader))
                    else -> reader.skipElement()
                }
            }
        }

        return OtherLoTEPointer(
            serviceDigitalIdentities = serviceDigitalIdentities,
            location = location ?: throw IllegalArgumentException("LoTELocation is required"),
            qualifiers = qualifiers
        )
    }

    private fun parseServiceDigitalIdentities(reader: XmlReader): List<LoTEServiceDigitalIdentity> {
        val result = mutableListOf<LoTEServiceDigitalIdentity>()
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

    private fun parseServiceDigitalIdentity(reader: XmlReader): LoTEServiceDigitalIdentity {
        val x509Certificates = mutableListOf<PkiObject>()
        val x509SubjectNames = mutableListOf<String>()
        val x509SKIs = mutableListOf<String>()

        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "X509Certificate" -> x509Certificates.add(PkiObject(reader.readSimpleElement().trim()))
                    "X509SubjectName" -> x509SubjectNames.add(reader.readSimpleElement().trim())
                    "X509SKI" -> x509SKIs.add(reader.readSimpleElement().trim())
                    else -> reader.skipElement()
                }
            }
        }

        return LoTEServiceDigitalIdentity(
            x509Certificates = x509Certificates,
            x509SubjectNames = x509SubjectNames,
            x509SKIs = x509SKIs
        )
    }

    private fun parseLoTEQualifiers(reader: XmlReader): List<LoTEQualifier> {
        val result = mutableListOf<LoTEQualifier>()
        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT && reader.localName == "LoTEQualifier") {
                result.add(parseLoTEQualifier(reader))
            }
        }

        return result
    }

    private fun parseLoTEQualifier(reader: XmlReader): LoTEQualifier {
        var loTEType: String? = null
        var schemeOperatorName = listOf<MultiLangString>()
        var schemeTypeCommunityRules = listOf<MultiLangURI>()
        var schemeTerritory: String? = null
        var mimeType: String? = null

        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "LoTEType" -> loTEType = reader.readSimpleElement()
                    "SchemeOperatorName" -> schemeOperatorName = parseMultiLangStrings(reader)
                    "SchemeTypeCommunityRules" -> schemeTypeCommunityRules = parseMultiLangURIs(reader)
                    "SchemeTerritory" -> schemeTerritory = reader.readSimpleElement()
                    "MimeType" -> mimeType = reader.readSimpleElement()
                    else -> reader.skipElement()
                }
            }
        }

        return LoTEQualifier(
            loTEType = loTEType,
            schemeOperatorName = schemeOperatorName,
            schemeTypeCommunityRules = schemeTypeCommunityRules,
            schemeTerritory = schemeTerritory,
            mimeType = mimeType
        )
    }

    private fun parseTrustedEntitiesList(reader: XmlReader): List<TrustedEntity> {
        val result = mutableListOf<TrustedEntity>()
        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT && reader.localName == "TrustedEntity") {
                result.add(parseTrustedEntity(reader))
            }
        }

        return result
    }

    private fun parseTrustedEntity(reader: XmlReader): TrustedEntity {
        var trustedEntityInformation: TrustedEntityInformation? = null
        val trustedEntityServices = mutableListOf<TrustedEntityService>()

        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "TrustedEntityInformation" -> trustedEntityInformation = parseTrustedEntityInformation(reader)
                    "TrustedEntityServices" -> trustedEntityServices.addAll(parseTrustedEntityServices(reader))
                    "TrustedEntityService" -> trustedEntityServices.add(parseTrustedEntityService(reader))
                }
            }
        }

        return TrustedEntity(
            trustedEntityInformation = trustedEntityInformation
                ?: throw IllegalArgumentException("TrustedEntityInformation is required"),
            trustedEntityServices = trustedEntityServices
        )
    }

    private fun parseTrustedEntityInformation(reader: XmlReader): TrustedEntityInformation {
        var name = listOf<MultiLangString>()
        var tradeName = listOf<MultiLangString>()
        var address: OperatorAddress? = null
        var informationURI = listOf<MultiLangURI>()

        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "TEName" -> name = parseMultiLangStrings(reader)
                    "TETradeName" -> tradeName = parseMultiLangStrings(reader)
                    "TEAddress" -> address = parseOperatorAddress(reader)
                    "TEInformationURI" -> informationURI = parseMultiLangURIs(reader)
                    else -> reader.skipElement()
                }
            }
        }

        return TrustedEntityInformation(
            name = name,
            tradeName = tradeName,
            address = address,
            informationURI = informationURI
        )
    }

    private fun parseTrustedEntityServices(reader: XmlReader): List<TrustedEntityService> {
        val result = mutableListOf<TrustedEntityService>()
        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT && reader.localName == "TrustedEntityService") {
                result.add(parseTrustedEntityService(reader))
            }
        }

        return result
    }

    private fun parseTrustedEntityService(reader: XmlReader): TrustedEntityService {
        var serviceInformation: LoTEServiceInformation? = null
        val serviceHistory = mutableListOf<ServiceHistoryInstance>()

        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "ServiceInformation" -> serviceInformation = parseServiceInformation(reader)
                    "ServiceHistory" -> serviceHistory.addAll(parseServiceHistoryList(reader))
                }
            }
        }

        return TrustedEntityService(
            serviceInformation = serviceInformation
                ?: throw IllegalArgumentException("ServiceInformation is required"),
            serviceHistory = serviceHistory
        )
    }

    private fun parseServiceInformation(reader: XmlReader): LoTEServiceInformation {
        var serviceTypeIdentifier: String? = null
        var serviceName = listOf<MultiLangString>()
        var serviceDigitalIdentity = LoTEServiceDigitalIdentity()
        var serviceStatus: String? = null
        var statusStartingTime: Instant? = null
        val serviceSupplyPoints = mutableListOf<String>()
        var schemeServiceDefinitionURI = listOf<MultiLangURI>()
        var teServiceDefinitionURI = listOf<MultiLangURI>()

        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break

            if (reader.eventType == EventType.START_ELEMENT) {
                when (reader.localName) {
                    "ServiceTypeIdentifier" -> serviceTypeIdentifier = reader.readSimpleElement()
                    "ServiceName" -> serviceName = parseMultiLangStrings(reader)
                    "ServiceDigitalIdentity" -> serviceDigitalIdentity = parseServiceDigitalIdentity(reader)
                    "ServiceStatus" -> serviceStatus = reader.readSimpleElement()
                    "StatusStartingTime" -> statusStartingTime = Instant.parse(reader.readSimpleElement())
                    "ServiceSupplyPoints" -> serviceSupplyPoints.addAll(parseDistributionPoints(reader))
                    "SchemeServiceDefinitionURI" -> schemeServiceDefinitionURI = parseMultiLangURIs(reader)
                    "TEServiceDefinitionURI" -> teServiceDefinitionURI = parseMultiLangURIs(reader)
                    else -> reader.skipElement()
                }
            }
        }

        return LoTEServiceInformation(
            serviceTypeIdentifier = serviceTypeIdentifier
                ?: throw IllegalArgumentException("ServiceTypeIdentifier is required"),
            serviceName = serviceName,
            serviceDigitalIdentity = serviceDigitalIdentity,
            serviceStatus = serviceStatus,
            statusStartingTime = statusStartingTime,
            serviceSupplyPoints = serviceSupplyPoints,
            schemeServiceDefinitionURI = schemeServiceDefinitionURI,
            teServiceDefinitionURI = teServiceDefinitionURI
        )
    }

    private fun parseServiceHistoryList(reader: XmlReader): List<ServiceHistoryInstance> {
        val result = mutableListOf<ServiceHistoryInstance>()
        val elementName = reader.localName
        val depth = reader.depth

        while (reader.hasNext()) {
            reader.next()
            if (reader.hasExitedElement(depth, elementName)) break
            if (reader.eventType == EventType.START_ELEMENT && reader.localName == "ServiceHistoryInstance") {
                result.add(parseServiceInformation(reader))
            }
        }

        return result
    }

    // --- Helper extensions ---

    private fun XmlReader.hasExitedElement(startDepth: Int, startElementName: String): Boolean {
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
        throw IllegalArgumentException("Element $elementName not found")
    }

    private fun XmlReader.readSimpleElement(): String {
        val text = StringBuilder()
        val depth = this.depth
        while (hasNext()) {
            when (next()) {
                EventType.TEXT, EventType.CDSECT -> text.append(this.text)
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

    private fun XmlReader.getLangAttribute(): String? {
        return getAttributeValue("http://www.w3.org/XML/1998/namespace", "lang")
            ?: getAttributeValue(null, "xml:lang")
    }
}
