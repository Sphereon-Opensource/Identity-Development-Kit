package com.sphereon.trust.etsi.parser

import com.sphereon.trust.etsi.lote.serialization.LoTEJson
import com.sphereon.trust.etsi.lote.model.MultiLangString
import com.sphereon.trust.etsi.model.ETSIOperatorAddress
import com.sphereon.trust.etsi.model.LoTE612Converter.toLoTE
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Offline structural conversion checks; these do not assert a signed or profile-valid list. */
class Native612ConversionTest {
    private val parser = StreamingETSITrustListParser()

    @Test
    fun formattedXmlUriHasItsLexicalWhitespaceRemovedBeforeConversion() {
        val native = parser.parseFromString(xml("\n  urn:example:status-policy\n "))
        assertEquals("urn:example:list", native.type)
        assertEquals("urn:example:status-policy", native.statusDeterminationApproach)
        val encoded = LoTEJson.encode(native.toLoTE())
        assertEquals(native.statusDeterminationApproach, LoTEJson.parse(encoded).listAndSchemeInformation.statusDeterminationApproach)
    }

    @Test
    fun whitespaceOnlyOptionalUriIsOmitted() {
        val converted = parser.parseFromString(xml("\n \t ")).toLoTE()
        assertNull(converted.listAndSchemeInformation.statusDeterminationApproach)
        assertFalse(LoTEJson.encode(converted).contains("StatusDeterminationApproach"))
    }

    @Test
    fun absentOptionalMetadataIsOmittedRatherThanEmittedAsAnEmptyUri() {
        val native = parser.parseFromString(xml(null))
        val converted = native.toLoTE()
        assertNull(converted.listAndSchemeInformation.statusDeterminationApproach)
        assertNull(converted.listAndSchemeInformation.schemeOperatorAddress)
        val encoded = LoTEJson.encode(converted)
        assertFalse(encoded.contains("StatusDeterminationApproach"))
        assertFalse(encoded.contains("SchemeOperatorAddress"))
        assertEquals(converted, LoTEJson.parse(encoded))
    }

    @Test
    fun invalidNonemptyUriCannotBeSilentlyOmitted() {
        val native = parser.parseFromString(xml("not a URI"))
        val failure = assertFailsWith<IllegalArgumentException> { LoTEJson.encode(native.toLoTE()) }
        assertTrue(failure.message.orEmpty().contains("StatusDeterminationApproach"))
    }

    @Test
    fun unrepresentableAddressCardinalityFailsWithoutFabricatedContactData() {
        val native = parser.parseFromString(xml(null)).copy(schemeOperatorAddress = ETSIOperatorAddress(emptyList()))
        val failure = assertFailsWith<IllegalArgumentException> { LoTEJson.encode(native.toLoTE()) }
        assertTrue(failure.message.orEmpty().contains("SchemeOperatorAddress"))
    }

    @Test
    fun pointerMetadataInAdditionalInformationSurvivesConversion() {
        val native = parser.parseFromString(xml(null).replace("</SchemeInformation>", "$pointer</SchemeInformation>"))
        val parsedPointer = native.pointersToOtherLoTE.single()
        assertEquals(listOf(MultiLangString("en", "Referenced operator"), MultiLangString("nl", "Verwezen beheerder")), parsedPointer.schemeOperatorName)
        assertEquals("application/vnd.etsi.tsl+xml", parsedPointer.additionalInformation?.mimeType)
        val encoded = LoTEJson.encode(native.toLoTE())
        val qualifier = LoTEJson.parse(encoded).listAndSchemeInformation.pointersToOtherLoTE.single().qualifiers.single()
        assertEquals(parsedPointer.schemeOperatorName, qualifier.schemeOperatorName)
        assertEquals(parsedPointer.additionalInformation?.mimeType, qualifier.mimeType)
        assertEquals("urn:example:referenced-list", qualifier.loTEType)
    }

    @Test
    fun pointerWithoutRequired602MetadataCannotBeExported() {
        val native = parser.parseFromString(xml(null).replace("</SchemeInformation>", "$pointer</SchemeInformation>"))
        val incomplete = native.copy(pointersToOtherLoTE = native.pointersToOtherLoTE.map {
            it.copy(schemeOperatorName = emptyList(), additionalInformation = it.additionalInformation?.copy(mimeType = null))
        })
        val failure = assertFailsWith<IllegalArgumentException> { LoTEJson.encode(incomplete.toLoTE()) }
        assertTrue(failure.message.orEmpty().contains("LoTEQualifiers"))
    }

    // Native 612 placement: MimeType is on the pointer, operator names inside typed OtherInformation.
    private val pointer = """
        <PointersToOtherTSL><OtherTSLPointer>
          <ServiceDigitalIdentities><ServiceDigitalIdentity><DigitalId><X509SubjectName>CN=Example</X509SubjectName></DigitalId></ServiceDigitalIdentity></ServiceDigitalIdentities>
          <TSLLocation>https://example.org/list.xml</TSLLocation>
          <MimeType>application/vnd.etsi.tsl+xml</MimeType>
          <AdditionalInformation>
            <OtherInformation><TSLType>urn:example:referenced-list</TSLType></OtherInformation>
            <OtherInformation><SchemeOperatorName><Name xml:lang="en">Referenced operator</Name><Name xml:lang="nl">Verwezen beheerder</Name></SchemeOperatorName></OtherInformation>
            <OtherInformation><SchemeTerritory>NL</SchemeTerritory></OtherInformation>
          </AdditionalInformation>
        </OtherTSLPointer></PointersToOtherTSL>
    """.trimIndent()

    private fun xml(status: String?): String = """
        <TrustServiceStatusList xmlns="http://uri.etsi.org/02231/v2#">
          <SchemeInformation>
            <TSLVersionIdentifier>5</TSLVersionIdentifier>
            <TSLSequenceNumber>1</TSLSequenceNumber>
            <TSLType>
              urn:example:list
            </TSLType>
            <SchemeOperatorName><Name xml:lang="en">Example operator</Name></SchemeOperatorName>
            <SchemeTerritory>NL</SchemeTerritory>
            ${status?.let { "<StatusDeterminationApproach>$it</StatusDeterminationApproach>" } ?: ""}
            <ListIssueDateTime>2026-10-01T00:00:00Z</ListIssueDateTime>
            <NextUpdate><dateTime>2026-11-01T00:00:00Z</dateTime></NextUpdate>
          </SchemeInformation>
        </TrustServiceStatusList>
    """.trimIndent()
}
