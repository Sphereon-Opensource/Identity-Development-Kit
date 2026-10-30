/* Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 * See https://www.apache.org/licenses/LICENSE-2.0
 */
package com.sphereon.authenticsource

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

class AuthenticSourceWireCodecTest {
    private val codec = DefaultAuthenticSourceWireCodec()
    private val match = "http://uri.etsi.org/19478/VerificationResult/Match"
    private val minimal = """{"provider":{"legalName":"Example"},"attributeVerificationResults":[{"attributeIdentifier":"urn:example:attribute","attributeVerificationResult":"$match"}]}"""
    private fun decoded(input: String): WireVerifyResponse {
        val result = codec.decodeResponse(input)
        assertTrue(result.isOk, "expected valid annex response")
        return result.value
    }
    private fun rejected(input: String) = assertTrue(codec.decodeResponse(input).isErr)

    @Test fun minimalResponsePreservesOptionalValueAbsence() {
        val value = decoded(minimal)
        assertEquals("Example", value.provider.legalName)
        assertNull(value.authenticSource)
        val item = requireNotNull(value.attributeVerificationResults).single()
        assertEquals("urn:example:attribute", item.attributeIdentifier)
        assertEquals(match, item.attributeVerificationResult)
        assertNull(item.attributeValueJson)
        assertEquals(minimal, value.originalJson)
    }
    @Test fun fullProviderSourceAndObjectValuesSurvive() {
        val attributeObject = """{ "nested" : [1.00, "\u0041"] }"""
        val mandateObject = """{ "authorized" : true, "number" : 1e2 }"""
        val input = """{"provider":{"legalName":"Provider","identifiers":[{"type":"urn:example:type","identifier":"123"}],"establishedByLaw":{"legislationIdentifier":"urn:example:law","legalBasis":"Act"},"currentAddress":"Address"},"authenticSource":{"legalName":"Source"},"attributeVerificationResults":[{"attributeIdentifier":"urn:example:a","attributeVerificationResult":"$match","attributeValue":$attributeObject}],"mandateResult":$mandateObject,"extension":{"x":1}}"""
        val value = decoded(input)
        assertEquals(WireProvider("Provider", listOf(WireProviderIdentifier("urn:example:type", "123")), WireLaw("urn:example:law", "Act"), "Address"), value.provider)
        assertEquals("Source", value.authenticSource!!.legalName)
        assertEquals(attributeObject, requireNotNull(value.attributeVerificationResults).single().attributeValueJson)
        assertEquals(mandateObject, value.mandateResultJson)
        assertEquals(input, value.originalJson)
    }
    @Test fun fragmentResultHasNoInventedLocation() {
        val fragmentObject = """{ "x" : 1.0, "text" : "\u0041" }"""
        val input = """{"provider":{"legalName":"P"},"fragmentVerificationResults":[{"attributeIdentifier":"urn:example:a","fragmentVerificationResult":"$match","fragmentValue":$fragmentObject}]}"""
        val value = decoded(input)
        val item = requireNotNull(value.fragmentVerificationResults).single()
        assertEquals("urn:example:a", item.attributeIdentifier)
        assertEquals(match, item.fragmentVerificationResult)
        assertEquals(fragmentObject, item.fragmentValueJson)
        assertEquals(input, value.originalJson)
        assertNull(value.attributeVerificationResults)
    }
    @Test fun encodeRequestUsesActualWireNamesAndObjectValues() {
        val result = codec.encodeRequest(WireVerifyRequest(listOf(WireAttribute("urn:example:a", """{"nested":[1,true]}""")), listOf(WireFragment("urn:example:a", "/x", """{"x":1}""")), """{"id":"m"}"""))
        assertTrue(result.isOk)
        val expected = """{"attributes":[{"attributeIdentifier":"urn:example:a","attributeValue":{"nested":[1,true]}}],"attributeFragments":[{"attributeIdentifier":"urn:example:a","location":"/x","value":{"x":1}}],"mandate":{"id":"m"}}"""
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(result.value))
        assertEquals(setOf("attributes", "attributeFragments", "mandate"), Json.parseToJsonElement(result.value).jsonObject.keys)
    }
    @Test fun rejectsMissingProvider() = rejected("""{"attributeVerificationResults":[]}""")
    @Test fun rejectsMissingProviderName() = rejected("""{"provider":{}}""")
    @Test fun rejectsWrongProviderType() = rejected("""{"provider":[]}""")
    @Test fun rejectsUnknownProviderMember() = rejected("""{"provider":{"legalName":"P","unexpected":1}}""")
    @Test fun rejectsProviderIdentifierMissingType() = rejected("""{"provider":{"legalName":"P","identifiers":[{"identifier":"x"}]}}""")
    @Test fun rejectsWrongResultsType() = rejected("""{"provider":{"legalName":"P"},"attributeVerificationResults":{}}""")
    @Test fun rejectsMissingResultUri() = rejected("""{"provider":{"legalName":"P"},"attributeVerificationResults":[{"attributeIdentifier":"urn:example:a"}]}""")
    @Test fun rejectsExplicitNullOptionalObject() = rejected("""{"provider":{"legalName":"P"},"attributeVerificationResults":[{"attributeIdentifier":"urn:example:a","attributeVerificationResult":"$match","attributeValue":null}]}""")
    @Test fun rejectsArrayResultValue() = rejected("""{"provider":{"legalName":"P"},"attributeVerificationResults":[{"attributeIdentifier":"urn:example:a","attributeVerificationResult":"$match","attributeValue":[]}]}""")
    @Test fun rejectsBlankAttributeIdentifier() = rejected("""{"provider":{"legalName":"P"},"attributeVerificationResults":[{"attributeIdentifier":" ","attributeVerificationResult":"$match"}]}""")
    @Test fun rejectsInvalidAttributeUri() = rejected("""{"provider":{"legalName":"P"},"attributeVerificationResults":[{"attributeIdentifier":"not a uri","attributeVerificationResult":"$match"}]}""")
    @Test fun rejectsDuplicateTopLevelMembers() = rejected("""{"provider":{"legalName":"P"},"provider":{"legalName":"Q"}}""")
    @Test fun rejectsEscapedDuplicateNestedObjectMembers() = rejected("""{"provider":{"legalName":"P"},"mandateResult":{"a":1,"\u0061":2}}""")
    @Test fun rejectsMalformedJson() = rejected("""{"provider":{"legalName":"P"}""")
    @Test fun rejectsArrayRequestValue() {
        assertTrue(codec.encodeRequest(WireVerifyRequest(listOf(WireAttribute("urn:example:a", "[]")))).isErr)
    }
    @Test fun rejectsDuplicateRequestObjectMembers() {
        assertTrue(codec.encodeRequest(WireVerifyRequest(listOf(WireAttribute("urn:example:a", """{"a":1,"a":2}""")))).isErr)
    }
}

