package com.sphereon.conf.theme.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class DesignElementTest {
    private val json = Json { classDiscriminator = "kind" }
    @Test
    fun choiceRejectsADefaultOutsideItsAllowedValues() {
        assertFailsWith<IllegalArgumentException> {
            ChoiceDesignElement(elementId = "headerStyle", allowedValues = listOf("panel", "hairline"), default = "banner")
        }
    }

    @Test
    fun choiceRejectsAnEmptyAllowedValueList() {
        assertFailsWith<IllegalArgumentException> {
            ChoiceDesignElement(elementId = "headerStyle", allowedValues = emptyList(), default = "panel")
        }
    }

    @Test
    fun choiceCoercesAnUnknownTokenValueToNothing() {
        val element = ChoiceDesignElement(elementId = "headerStyle", allowedValues = listOf("panel", "hairline"), default = "panel")
        assertEquals(ChoiceElementValue("hairline"), element.valueFromToken("hairline"))
        assertNull(element.valueFromToken("banner"))
        assertEquals(ChoiceElementValue("panel"), element.defaultValue())
    }

    @Test
    fun toggleAcceptsOnlyStrictBooleanText() {
        val element = ToggleDesignElement(elementId = "showTagline", default = true)
        assertEquals(ToggleElementValue(false), element.valueFromToken("false"))
        assertNull(element.valueFromToken("yes"))
        assertEquals(ToggleElementValue(true), element.defaultValue())
    }

    @Test
    fun assetCoercesATokenUriIntoAnAssetReference() {
        val element = AssetDesignElement(elementId = "logo", fallbackTokenKey = "branding.logoUrl")
        assertEquals(
            AssetElementValue(ThemeAssetReference(uri = "https://cdn.example/logo.svg")),
            element.valueFromToken("https://cdn.example/logo.svg"),
        )
        assertNull(element.valueFromToken("   "))
    }

    @Test
    fun textPassesThroughAndHasNoDefaultUnlessGiven() {
        val element = TextDesignElement(elementId = "tagline")
        assertEquals(TextElementValue("Secure access"), element.valueFromToken("Secure access"))
        assertNull(element.defaultValue())
    }

    // Each variant is pinned against the literal JSON the OpenAPI contract declares, in both
    // directions. The literal decode direction is what pins the `@SerialName` discriminator:
    // an encode-then-decode round trip reads back the same serial name it just wrote, so it
    // would stay green even if the discriminator were renamed out from under the console.

    @Test
    fun assetElementSerializesToItsContractShape() {
        val element = AssetDesignElement(elementId = "logo", fallbackTokenKey = "branding.logoUrl")
        val expected = """{"kind":"asset","elementId":"logo","fallbackTokenKey":"branding.logoUrl"}"""
        assertEquals(expected, json.encodeToString<DesignElement>(element))
        assertEquals(element, json.decodeFromString<DesignElement>(expected))
    }

    @Test
    fun textElementSerializesToItsContractShape() {
        val element = TextDesignElement(elementId = "tagline", maxLength = 120, default = "Secure access")
        val expected = """{"kind":"text","elementId":"tagline","maxLength":120,"default":"Secure access"}"""
        assertEquals(expected, json.encodeToString<DesignElement>(element))
        assertEquals(element, json.decodeFromString<DesignElement>(expected))
    }

    @Test
    fun choiceElementSerializesToItsContractShape() {
        val element = ChoiceDesignElement(
            elementId = "headerStyle",
            allowedValues = listOf("panel", "hairline"),
            default = "panel",
        )
        val expected =
            """{"kind":"choice","elementId":"headerStyle","allowedValues":["panel","hairline"],"default":"panel"}"""
        assertEquals(expected, json.encodeToString<DesignElement>(element))
        assertEquals(element, json.decodeFromString<DesignElement>(expected))
    }

    @Test
    fun toggleElementSerializesToItsContractShape() {
        val element = ToggleDesignElement(elementId = "showTagline", default = true)
        val expected = """{"kind":"toggle","elementId":"showTagline","default":true}"""
        assertEquals(expected, json.encodeToString<DesignElement>(element))
        assertEquals(element, json.decodeFromString<DesignElement>(expected))
    }

    @Test
    fun eachDiscriminatorSelectsItsOwnVariant() {
        assertIs<AssetDesignElement>(json.decodeFromString<DesignElement>("""{"kind":"asset","elementId":"logo"}"""))
        assertIs<TextDesignElement>(json.decodeFromString<DesignElement>("""{"kind":"text","elementId":"tagline"}"""))
        assertIs<ChoiceDesignElement>(
            json.decodeFromString<DesignElement>(
                """{"kind":"choice","elementId":"headerStyle","allowedValues":["panel"],"default":"panel"}""",
            ),
        )
        assertIs<ToggleDesignElement>(
            json.decodeFromString<DesignElement>("""{"kind":"toggle","elementId":"showTagline","default":true}"""),
        )
    }

    @Test
    fun choiceInvariantsSurviveDeserialization() {
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<DesignElement>(
                """{"kind":"choice","elementId":"headerStyle","allowedValues":["panel"],"default":"banner"}""",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            json.decodeFromString<DesignElement>(
                """{"kind":"choice","elementId":"headerStyle","allowedValues":[],"default":"panel"}""",
            )
        }
    }
}
