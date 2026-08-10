package com.sphereon.conf.theme.core.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * Pins the [ElementValue] wire format against the literal JSON the OpenAPI contract declares.
 *
 * Every variant is asserted in both directions against an exact string. Encode-only would not be
 * enough on its own: a round trip reads the same `@SerialName` it wrote, so renaming `"asset"` to
 * `"assets"` would still round trip cleanly while breaking the console's generated client. The
 * literal decode direction pins the discriminator the console actually sends.
 */
class ElementValueTest {
    private val json = Json { classDiscriminator = "kind" }

    private val assetJson = """{"kind":"asset","asset":{"uri":"https://cdn.example/logo.svg"}}"""
    private val textJson = """{"kind":"text","text":"Secure access"}"""
    private val choiceJson = """{"kind":"choice","choice":"panel"}"""
    private val toggleJson = """{"kind":"toggle","enabled":true}"""

    @Test
    fun assetSerializesToItsContractShape() {
        val value = AssetElementValue(ThemeAssetReference(uri = "https://cdn.example/logo.svg"))
        assertEquals(assetJson, json.encodeToString<ElementValue>(value))
        assertEquals(value, json.decodeFromString<ElementValue>(assetJson))
    }

    @Test
    fun textSerializesToItsContractShape() {
        val value = TextElementValue("Secure access")
        assertEquals(textJson, json.encodeToString<ElementValue>(value))
        assertEquals(value, json.decodeFromString<ElementValue>(textJson))
    }

    @Test
    fun choiceSerializesToItsContractShape() {
        val value = ChoiceElementValue(choice = "panel")
        assertEquals(choiceJson, json.encodeToString<ElementValue>(value))
        assertEquals(value, json.decodeFromString<ElementValue>(choiceJson))
    }

    @Test
    fun toggleSerializesToItsContractShape() {
        val value = ToggleElementValue(enabled = true)
        assertEquals(toggleJson, json.encodeToString<ElementValue>(value))
        assertEquals(value, json.decodeFromString<ElementValue>(toggleJson))
    }

    @Test
    fun eachDiscriminatorSelectsItsOwnVariant() {
        assertIs<AssetElementValue>(json.decodeFromString<ElementValue>(assetJson))
        assertIs<TextElementValue>(json.decodeFromString<ElementValue>(textJson))
        assertIs<ChoiceElementValue>(json.decodeFromString<ElementValue>(choiceJson))
        assertIs<ToggleElementValue>(json.decodeFromString<ElementValue>(toggleJson))
    }
}
