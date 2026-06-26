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

package com.sphereon.data.store.credential.design.impl.mapper

import com.sphereon.data.store.credential.design.model.AppliedDesignLayer
import com.sphereon.data.store.credential.design.model.AssetReference
import com.sphereon.data.store.credential.design.model.ClaimLabel
import com.sphereon.data.store.credential.design.model.ClaimPathSegment
import com.sphereon.data.store.credential.design.model.ClaimPresentation
import com.sphereon.data.store.credential.design.model.CredentialDesignRecord
import com.sphereon.data.store.credential.design.model.DesignBinding
import com.sphereon.data.store.credential.design.model.DesignHostingMode
import com.sphereon.data.store.credential.design.model.DesignSourceType
import com.sphereon.data.store.credential.design.model.LocalizedCredentialDisplay
import com.sphereon.data.store.credential.design.model.RenderVariantKind
import com.sphereon.data.store.credential.design.model.RenderVariantRecord
import com.sphereon.data.store.credential.design.model.ResolvedCredentialDesign
import com.sphereon.data.store.credential.design.model.SdPolicy
import com.sphereon.openid.oid4vci.common.model.ClaimDisplay
import com.sphereon.openid.oid4vci.common.model.ClaimMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialClaim
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialDefinition
import com.sphereon.openid.oid4vci.common.model.CredentialMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialMetadataClaim
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlin.uuid.Uuid

class Oid4vciDesignMapperTest {
    private val now = Instant.parse("2025-01-01T00:00:00Z")

    // ---- Helpers ----

    private fun buildDesign(
        bindings: List<DesignBinding> = emptyList(),
        displays: List<LocalizedCredentialDisplay> = emptyList(),
        claims: List<ClaimPresentation> = emptyList(),
        renderVariants: List<RenderVariantRecord> = emptyList(),
    ): ResolvedCredentialDesign {
        val record =
            CredentialDesignRecord(
                id = Uuid.parse("00000000-0000-0000-0000-000000000001"),
                tenantId = "tenant-1",
                hostingMode = DesignHostingMode.LOCAL,
                bindings = bindings,
                displays = displays,
                claims = claims,
                createdAt = now,
                updatedAt = now,
            )
        return ResolvedCredentialDesign(
            design = record,
            renderVariants = renderVariants,
            appliedLayers =
                listOf(
                    AppliedDesignLayer(
                        sourceType = DesignSourceType.LOCAL_OVERRIDE,
                        priority = 0,
                        authoritative = true,
                    ),
                ),
            lockedFields = emptyMap(),
            resolvedAt = now,
        )
    }

    // =========================================================================
    // Direction 1: Design → OID4VCI
    // =========================================================================

    @Test
    fun displaysAreMappedToCredentialMetadataDisplay() {
        val design =
            buildDesign(
                displays =
                    listOf(
                        LocalizedCredentialDisplay(locale = "en-US", name = "Identity Card", description = "A digital ID"),
                        LocalizedCredentialDisplay(locale = "de-DE", name = "Personalausweis", description = null),
                    ),
            )

        val config = Oid4vciDesignMapper.toCredentialConfiguration(design, "dc+sd-jwt")

        val metadataDisplay = config.credentialMetadata?.display
        assertNotNull(metadataDisplay)
        assertEquals(2, metadataDisplay.size)

        assertEquals("en-US", metadataDisplay[0].locale)
        assertEquals("Identity Card", metadataDisplay[0].name)
        assertEquals("A digital ID", metadataDisplay[0].description)

        assertEquals("de-DE", metadataDisplay[1].locale)
        assertEquals("Personalausweis", metadataDisplay[1].name)
        assertNull(metadataDisplay[1].description)
    }

    @Test
    fun claimsAreMappedToCredentialMetadataClaims() {
        val design =
            buildDesign(
                claims =
                    listOf(
                        ClaimPresentation(
                            path = listOf(ClaimPathSegment.Property("given_name")),
                            labels =
                                listOf(
                                    ClaimLabel(locale = "en-US", label = "Given Name"),
                                    ClaimLabel(locale = "de-DE", label = "Vorname"),
                                ),
                            mandatory = true,
                            sdPolicy = SdPolicy.ALWAYS,
                            order = 0,
                        ),
                        ClaimPresentation(
                            path = listOf(ClaimPathSegment.Property("address"), ClaimPathSegment.Property("street")),
                            labels = listOf(ClaimLabel(locale = "en-US", label = "Street")),
                            mandatory = false,
                            sdPolicy = SdPolicy.ALLOWED,
                            order = 1,
                        ),
                    ),
            )

        val config = Oid4vciDesignMapper.toCredentialConfiguration(design, "dc+sd-jwt")

        val metadataClaims = config.credentialMetadata?.claims
        assertNotNull(metadataClaims)
        assertEquals(2, metadataClaims.size)

        // First claim: single property path, mandatory, two display labels
        val first = metadataClaims[0]
        assertEquals(1, first.path.size)
        assertEquals(JsonPrimitive("given_name"), first.path[0])
        assertEquals(true, first.mandatory)
        assertEquals(2, first.display?.size)
        assertEquals("Given Name", first.display!![0].name)
        assertEquals("en-US", first.display!![0].locale)
        assertEquals("Vorname", first.display!![1].name)
        assertEquals("de-DE", first.display!![1].locale)

        // Second claim: nested path, not mandatory
        val second = metadataClaims[1]
        assertEquals(2, second.path.size)
        assertEquals(JsonPrimitive("address"), second.path[0])
        assertEquals(JsonPrimitive("street"), second.path[1])
        assertNull(second.mandatory)
    }

    @Test
    fun sdJwtFormatSetVctFromBinding() {
        val design =
            buildDesign(
                bindings = listOf(DesignBinding(vct = "https://example.com/pid", credentialConfigurationId = "pid_mdl")),
            )

        val config = Oid4vciDesignMapper.toCredentialConfiguration(design, "dc+sd-jwt")

        assertEquals("https://example.com/pid", config.vct)
        assertNull(config.doctype)
        assertNull(config.credentialDefinition)
    }

    @Test
    fun mDocFormatSetsDoctypeFromBinding() {
        val design =
            buildDesign(
                bindings = listOf(DesignBinding(docType = "org.iso.18013.5.1.mDL")),
            )

        val config = Oid4vciDesignMapper.toCredentialConfiguration(design, "mso_mdoc")

        assertEquals("org.iso.18013.5.1.mDL", config.doctype)
        assertNull(config.vct)
        assertNull(config.credentialDefinition)
    }

    @Test
    fun jwtVcJsonFormatSetsCredentialDefinitionTypeFromBinding() {
        val design =
            buildDesign(
                bindings = listOf(DesignBinding(vcType = "EmployeeCredential")),
            )

        val config = Oid4vciDesignMapper.toCredentialConfiguration(design, "jwt_vc_json")

        assertNull(config.vct)
        assertNull(config.doctype)
        assertNotNull(config.credentialDefinition)
        val types = config.credentialDefinition!!.type
        assertNotNull(types)
        assertEquals(2, types.size)
        assertEquals("VerifiableCredential", types[0])
        assertEquals("EmployeeCredential", types[1])
    }

    @Test
    fun ldpVcFormatSetsCredentialDefinitionType() {
        val design =
            buildDesign(
                bindings = listOf(DesignBinding(vcType = "UniversityDegreeCredential")),
            )

        val config = Oid4vciDesignMapper.toCredentialConfiguration(design, "ldp_vc")

        assertNotNull(config.credentialDefinition)
        val types = config.credentialDefinition!!.type
        assertNotNull(types)
        assertTrue(types.contains("VerifiableCredential"))
        assertTrue(types.contains("UniversityDegreeCredential"))
    }

    @Test
    fun renderVariantColorsAreMergedIntoDisplay() {
        val variant =
            RenderVariantRecord(
                id = Uuid.parse("00000000-0000-0000-0000-000000000002"),
                tenantId = "tenant-1",
                kind = RenderVariantKind.SIMPLE_CARD,
                backgroundColor = "#003366",
                textColor = "#FFFFFF",
            )
        val design =
            buildDesign(
                displays =
                    listOf(
                        LocalizedCredentialDisplay(locale = "en-US", name = "Styled Credential"),
                    ),
                renderVariants = listOf(variant),
            )

        val config = Oid4vciDesignMapper.toCredentialConfiguration(design, "dc+sd-jwt")

        val displayEntry = config.credentialMetadata?.display?.firstOrNull()
        assertNotNull(displayEntry)
        assertEquals("#003366", displayEntry.backgroundColor)
        assertEquals("#FFFFFF", displayEntry.textColor)
    }

    @Test
    fun renderVariantLogoIsMergedIntoDisplay() {
        val variant =
            RenderVariantRecord(
                id = Uuid.parse("00000000-0000-0000-0000-000000000003"),
                tenantId = "tenant-1",
                kind = RenderVariantKind.SIMPLE_CARD,
                logo =
                    com.sphereon.data.store.credential.design.model.AssetReference(
                        uri = "https://example.com/logo.png",
                        altText = "Example Corp Logo",
                    ),
            )
        val design =
            buildDesign(
                displays =
                    listOf(
                        LocalizedCredentialDisplay(locale = "en-US", name = "Corp Credential"),
                    ),
                renderVariants = listOf(variant),
            )

        val config = Oid4vciDesignMapper.toCredentialConfiguration(design, "dc+sd-jwt")

        val displayEntry = config.credentialMetadata?.display?.firstOrNull()
        assertNotNull(displayEntry)
        assertNotNull(displayEntry.logo)
        assertEquals("https://example.com/logo.png", displayEntry.logo!!.uri)
        assertEquals("Example Corp Logo", displayEntry.logo!!.altText)
    }

    @Test
    fun emptyDesignProducesNullCredentialMetadata() {
        val design = buildDesign()

        val config = Oid4vciDesignMapper.toCredentialConfiguration(design, "dc+sd-jwt")

        assertNull(config.credentialMetadata)
    }

    @Test
    fun claimPathWithArrayElementProducesJsonNull() {
        val design =
            buildDesign(
                claims =
                    listOf(
                        ClaimPresentation(
                            path =
                                listOf(
                                    ClaimPathSegment.Property("nationalities"),
                                    ClaimPathSegment.AnyArrayElement,
                                ),
                            labels = emptyList(),
                            mandatory = false,
                            order = 0,
                        ),
                    ),
            )

        val config = Oid4vciDesignMapper.toCredentialConfiguration(design, "dc+sd-jwt")

        val claim = config.credentialMetadata?.claims?.firstOrNull()
        assertNotNull(claim)
        assertEquals(2, claim.path.size)
        assertEquals(JsonPrimitive("nationalities"), claim.path[0])
        assertEquals(JsonNull, claim.path[1])
    }

    @Test
    fun claimMandatoryFalseProducesNullMandatoryInMetadata() {
        // mandatory = false should be omitted (null) in OID4VCI output
        val design =
            buildDesign(
                claims =
                    listOf(
                        ClaimPresentation(
                            path = listOf(ClaimPathSegment.Property("email")),
                            labels = emptyList(),
                            mandatory = false,
                            order = 0,
                        ),
                    ),
            )

        val config = Oid4vciDesignMapper.toCredentialConfiguration(design, "dc+sd-jwt")

        val claim = config.credentialMetadata?.claims?.firstOrNull()
        assertNotNull(claim)
        assertNull(claim.mandatory)
    }

    @Test
    fun multipleLocaleDisplaysPreserved() {
        val design =
            buildDesign(
                displays =
                    listOf(
                        LocalizedCredentialDisplay(locale = "en-US", name = "Credential EN"),
                        LocalizedCredentialDisplay(locale = "nl-NL", name = "Credential NL"),
                        LocalizedCredentialDisplay(locale = "fr-FR", name = "Credential FR"),
                    ),
            )

        val config = Oid4vciDesignMapper.toCredentialConfiguration(design, "dc+sd-jwt")

        val metadataDisplay = config.credentialMetadata?.display
        assertNotNull(metadataDisplay)
        assertEquals(3, metadataDisplay.size)
        assertEquals("en-US", metadataDisplay[0].locale)
        assertEquals("nl-NL", metadataDisplay[1].locale)
        assertEquals("fr-FR", metadataDisplay[2].locale)
    }

    @Test
    fun perLocaleVariantBrandingIsSelectedForEachDisplay() {
        // Two displays (en, nl) each backed by a distinct SIMPLE_CARD variant.
        // After the fix each display must carry its own variant's branding.
        val variantEn =
            RenderVariantRecord(
                id = Uuid.parse("00000000-0000-0000-0000-000000000010"),
                tenantId = "tenant-1",
                kind = RenderVariantKind.SIMPLE_CARD,
                localeApplicability = listOf("en"),
                backgroundColor = "#111111",
                logo =
                    AssetReference(
                        uri = "https://example.com/logo-en.png",
                        altText = "EN Logo",
                    ),
            )
        val variantNl =
            RenderVariantRecord(
                id = Uuid.parse("00000000-0000-0000-0000-000000000011"),
                tenantId = "tenant-1",
                kind = RenderVariantKind.SIMPLE_CARD,
                localeApplicability = listOf("nl"),
                backgroundColor = "#222222",
                logo =
                    AssetReference(
                        uri = "https://example.com/logo-nl.png",
                        altText = "NL Logo",
                    ),
            )
        val design =
            buildDesign(
                displays =
                    listOf(
                        LocalizedCredentialDisplay(locale = "en", name = "Identity Card EN"),
                        LocalizedCredentialDisplay(locale = "nl", name = "Identiteitskaart NL"),
                    ),
                renderVariants = listOf(variantEn, variantNl),
            )

        val config = Oid4vciDesignMapper.toCredentialConfiguration(design, "dc+sd-jwt")

        val displays = config.credentialMetadata?.display
        assertNotNull(displays)
        assertEquals(2, displays.size)

        val byLocale = displays.associateBy { it.locale }

        val enDisplay = byLocale["en"]
        assertNotNull(enDisplay)
        assertEquals("#111111", enDisplay.backgroundColor)
        assertEquals("https://example.com/logo-en.png", enDisplay.logo?.uri)

        val nlDisplay = byLocale["nl"]
        assertNotNull(nlDisplay)
        assertEquals("#222222", nlDisplay.backgroundColor)
        assertEquals("https://example.com/logo-nl.png", nlDisplay.logo?.uri)
    }

    @Test
    fun singleVariantAppliesToAllLocales() {
        // Regression: when there is only one variant it must still be applied to every display.
        val variant =
            RenderVariantRecord(
                id = Uuid.parse("00000000-0000-0000-0000-000000000012"),
                tenantId = "tenant-1",
                kind = RenderVariantKind.SIMPLE_CARD,
                localeApplicability = listOf("en"),
                backgroundColor = "#AABBCC",
                textColor = "#000000",
            )
        val design =
            buildDesign(
                displays =
                    listOf(
                        LocalizedCredentialDisplay(locale = "en", name = "Credential EN"),
                        LocalizedCredentialDisplay(locale = "de", name = "Credential DE"),
                        LocalizedCredentialDisplay(locale = "fr", name = "Credential FR"),
                    ),
                renderVariants = listOf(variant),
            )

        val config = Oid4vciDesignMapper.toCredentialConfiguration(design, "dc+sd-jwt")

        val displays = config.credentialMetadata?.display
        assertNotNull(displays)
        assertEquals(3, displays.size)

        // All three must carry the single variant's colors (fallback path)
        for (display in displays) {
            assertEquals("#AABBCC", display.backgroundColor, "Expected variant color for locale ${display.locale}")
            assertEquals("#000000", display.textColor, "Expected variant textColor for locale ${display.locale}")
        }
    }

    // =========================================================================
    // Direction 2: OID4VCI → Design
    // =========================================================================

    @Test
    fun credentialDesignMapsToOid4vciFinalCredentialMetadataWithBrandingClaimsAndLocaleFallback() {
        val variant =
            RenderVariantRecord(
                id = Uuid.parse("00000000-0000-0000-0000-000000000013"),
                tenantId = "tenant-1",
                kind = RenderVariantKind.SIMPLE_CARD,
                localeApplicability = listOf("en-US"),
                backgroundColor = "#003087",
                textColor = "#FFFFFF",
                logo =
                    AssetReference(
                        uri = "/public/assets/design/logo.png",
                        altText = "Government logo",
                    ),
                backgroundImage =
                    AssetReference(
                        uri = "/public/assets/design/background.png",
                        altText = "Credential background",
                    ),
            )
        val design =
            buildDesign(
                displays =
                    listOf(
                        LocalizedCredentialDisplay(
                            locale = "en-US",
                            name = "Personal ID",
                            description = "Government-issued identity document",
                        ),
                        LocalizedCredentialDisplay(locale = "nl-NL", name = "Persoonsbewijs"),
                    ),
                claims =
                    listOf(
                        ClaimPresentation(
                            path = listOf(ClaimPathSegment.Property("given_name")),
                            labels =
                                listOf(
                                    ClaimLabel(locale = "en-US", label = "Given Name"),
                                    ClaimLabel(locale = "nl-NL", label = "Voornaam"),
                                ),
                            mandatory = true,
                        ),
                        ClaimPresentation(
                            path = listOf(ClaimPathSegment.Property("address"), ClaimPathSegment.Property("street")),
                            labels = listOf(ClaimLabel(locale = "en-US", label = "Street")),
                        ),
                    ),
                renderVariants = listOf(variant),
            )

        val config = Oid4vciDesignMapper.toCredentialConfiguration(design, "dc+sd-jwt")

        assertNull(config.display, "OID4VCI final metadata must not use top-level draft display")
        assertNull(config.claims, "OID4VCI final metadata must not use top-level draft claims")

        val metadata = config.credentialMetadata
        assertNotNull(metadata)
        val displays = metadata.display
        assertNotNull(displays)
        assertEquals(2, displays.size)

        val en = displays.first { it.locale == "en-US" }
        assertEquals("Personal ID", en.name)
        assertEquals("Government-issued identity document", en.description)
        assertEquals("/public/assets/design/logo.png", en.logo?.uri)
        assertEquals("Government logo", en.logo?.altText)
        assertEquals("/public/assets/design/background.png", en.backgroundImage?.uri)
        assertEquals("#003087", en.backgroundColor)
        assertEquals("#FFFFFF", en.textColor)

        val nl = displays.first { it.locale == "nl-NL" }
        assertEquals("Persoonsbewijs", nl.name)
        assertEquals("/public/assets/design/logo.png", nl.logo?.uri)
        assertEquals("/public/assets/design/background.png", nl.backgroundImage?.uri)

        val claims = metadata.claims
        assertNotNull(claims)
        assertEquals(2, claims.size)

        val givenName = claims.first { it.path == listOf(JsonPrimitive("given_name")) }
        assertEquals(true, givenName.mandatory)
        assertEquals(2, givenName.display?.size)
        assertEquals("Given Name", givenName.display?.get(0)?.name)
        assertEquals("en-US", givenName.display?.get(0)?.locale)
        assertEquals("Voornaam", givenName.display?.get(1)?.name)
        assertEquals("nl-NL", givenName.display?.get(1)?.locale)

        val street = claims.first { it.path == listOf(JsonPrimitive("address"), JsonPrimitive("street")) }
        assertNull(street.mandatory)
        assertEquals("Street", street.display?.single()?.name)
    }

    @Test
    fun fromCredentialConfigurationMaps11MetadataDisplays() {
        val config =
            CredentialConfigurationSupported(
                format = "dc+sd-jwt",
                credentialMetadata =
                    CredentialMetadata(
                        display =
                            listOf(
                                com.sphereon.openid.oid4vc.common
                                    .DisplayProperties(name = "ID Card", locale = "en-US", description = "A digital ID"),
                                com.sphereon.openid.oid4vc.common
                                    .DisplayProperties(name = "Persoonskaart", locale = "nl-NL"),
                            ),
                    ),
            )

        val result = Oid4vciDesignMapper.fromCredentialConfiguration("pid-config", config)

        assertEquals(2, result.displays.size)
        assertEquals("en-US", result.displays[0].locale)
        assertEquals("ID Card", result.displays[0].name)
        assertEquals("A digital ID", result.displays[0].description)
        assertEquals("nl-NL", result.displays[1].locale)
        assertEquals("Persoonskaart", result.displays[1].name)
    }

    @Test
    fun fromCredentialConfigurationMaps11MetadataClaims() {
        val config =
            CredentialConfigurationSupported(
                format = "dc+sd-jwt",
                credentialMetadata =
                    CredentialMetadata(
                        claims =
                            listOf(
                                CredentialMetadataClaim(
                                    path = listOf(JsonPrimitive("given_name")),
                                    mandatory = true,
                                    display = listOf(ClaimDisplay(name = "Given Name", locale = "en-US")),
                                ),
                                CredentialMetadataClaim(
                                    path = listOf(JsonPrimitive("address"), JsonPrimitive("city")),
                                    mandatory = null,
                                    display = null,
                                ),
                            ),
                    ),
            )

        val result = Oid4vciDesignMapper.fromCredentialConfiguration("pid-config", config)

        assertEquals(2, result.claims.size)

        val first = result.claims[0]
        assertEquals(listOf(ClaimPathSegment.Property("given_name")), first.path)
        assertTrue(first.mandatory)
        assertEquals(1, first.labels.size)
        assertEquals("Given Name", first.labels[0].label)
        assertEquals("en-US", first.labels[0].locale)

        val second = result.claims[1]
        assertEquals(
            listOf(ClaimPathSegment.Property("address"), ClaimPathSegment.Property("city")),
            second.path,
        )
        assertTrue(second.labels.isEmpty())
    }

    @Test
    fun fromCredentialConfigurationFallsBackTo10TopLevelDisplay() {
        val config =
            CredentialConfigurationSupported(
                format = "jwt_vc_json",
                display =
                    listOf(
                        com.sphereon.openid.oid4vc.common
                            .DisplayProperties(name = "Employee Credential", locale = "en-US"),
                    ),
                claims =
                    listOf(
                        CredentialClaim(
                            path = listOf("employee_id"),
                            mandatory = true,
                            display = listOf(ClaimDisplay(name = "Employee ID", locale = "en-US")),
                        ),
                    ),
            )

        val result = Oid4vciDesignMapper.fromCredentialConfiguration("emp-config", config)

        assertEquals(1, result.displays.size)
        assertEquals("Employee Credential", result.displays[0].name)
        assertEquals("en-US", result.displays[0].locale)

        assertEquals(1, result.claims.size)
        assertEquals(listOf(ClaimPathSegment.Property("employee_id")), result.claims[0].path)
        assertTrue(result.claims[0].mandatory)
        assertEquals("Employee ID", result.claims[0].labels[0].label)
    }

    @Test
    fun fromCredentialConfigurationFallsBackToCredentialSubjectClaims() {
        val config =
            CredentialConfigurationSupported(
                format = "jwt_vc_json",
                credentialDefinition =
                    CredentialDefinition(
                        type = listOf("VerifiableCredential", "DegreeCredential"),
                        credentialSubject =
                            mapOf(
                                "degree" to
                                    ClaimMetadata(
                                        mandatory = false,
                                        display = listOf(ClaimDisplay(name = "Degree", locale = "en-US")),
                                    ),
                            ),
                    ),
            )

        val result = Oid4vciDesignMapper.fromCredentialConfiguration("degree-config", config)

        assertEquals(1, result.claims.size)
        assertEquals(listOf(ClaimPathSegment.Property("degree")), result.claims[0].path)
    }

    @Test
    fun fromCredentialConfigurationProvidedFieldsAreBuilt() {
        val config =
            CredentialConfigurationSupported(
                format = "dc+sd-jwt",
                credentialMetadata =
                    CredentialMetadata(
                        display =
                            listOf(
                                com.sphereon.openid.oid4vc.common
                                    .DisplayProperties(name = "Test", locale = "en-US"),
                            ),
                        claims =
                            listOf(
                                CredentialMetadataClaim(
                                    path = listOf(JsonPrimitive("email")),
                                    mandatory = false,
                                ),
                            ),
                    ),
            )

        val result = Oid4vciDesignMapper.fromCredentialConfiguration("test-config", config)

        assertTrue(result.providedFields.any { it.startsWith("display:") })
        assertTrue(result.providedFields.any { it.startsWith("claim:") })
    }
}
