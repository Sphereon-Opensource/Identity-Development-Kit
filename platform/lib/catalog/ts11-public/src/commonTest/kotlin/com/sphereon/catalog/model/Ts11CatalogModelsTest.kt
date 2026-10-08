package com.sphereon.catalog.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Ts11CatalogModelsTest {
    @Test
    fun `framework is typed and wire vocabulary is stable`() {
        val authority = TrustAuthority(TrustFrameworkType.etsi_tl, "https://tl.example.test", true)
        assertEquals("{\"frameworkType\":\"etsi_tl\",\"value\":\"https://tl.example.test\",\"isLOTE\":true}", Json.encodeToString(authority))
    }

    @Test
    fun `schema metadata owns deep defensive snapshots`() {
        val authorities = mutableListOf(TrustAuthority(TrustFrameworkType.aki, "key-1"))
        val formats = mutableListOf("dc+sd-jwt")
        val uris = mutableListOf(SchemaUriRef("dc+sd-jwt", "urn:eudi:pid:1"))
        val schema = SchemaMeta(
            version = "1.0.0",
            rulebookURI = "https://example.test/rulebook",
            trustedAuthorities = authorities,
            attestationLoS = "iso_18045_high",
            bindingType = "key",
            supportedFormats = formats,
            schemaURIs = uris,
        )

        authorities.clear(); formats.clear(); uris.clear()
        assertEquals(1, schema.trustedAuthorities.size)
        assertEquals(listOf("dc+sd-jwt"), schema.supportedFormats)
        assertEquals(1, schema.schemaURIs.size)
        runCatching { (schema.trustedAuthorities as MutableList<*>).clear() }
        assertEquals(1, schema.trustedAuthorities.size)
    }

    @Test
    fun `scalar vocabulary rejects invalid values`() {
        assertFailsWith<IllegalArgumentException> { AttestationTypeKey(AttestationTypeKeyKind.VCT, " ") }
        assertFailsWith<IllegalArgumentException> { TrustAuthority(TrustFrameworkType.aki, "") }
        assertFailsWith<IllegalArgumentException> { SchemaUriRef("unknown", "urn:test") }
    }
}
