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

package com.sphereon.data.store.credential.design.impl

import com.sphereon.data.store.credential.design.model.AppliedDesignLayer
import com.sphereon.data.store.credential.design.model.AssetReference
import com.sphereon.data.store.credential.design.model.ClaimCardinality
import com.sphereon.data.store.credential.design.model.ClaimLabel
import com.sphereon.data.store.credential.design.model.ClaimPathSegment
import com.sphereon.data.store.credential.design.model.ClaimPresentation
import com.sphereon.data.store.credential.design.model.ClaimValueKind
import com.sphereon.data.store.credential.design.model.ClaimWidgetHint
import com.sphereon.data.store.credential.design.model.CredentialDesignModuleConfig
import com.sphereon.data.store.credential.design.model.CredentialDesignRecord
import com.sphereon.data.store.credential.design.model.DesignBinding
import com.sphereon.data.store.credential.design.model.DesignHostingMode
import com.sphereon.data.store.credential.design.model.DesignSourceType
import com.sphereon.data.store.credential.design.model.FieldRenderHint
import com.sphereon.data.store.credential.design.model.LocalizedCredentialDisplay
import com.sphereon.data.store.credential.design.model.RenderVariantKind
import com.sphereon.data.store.credential.design.model.RenderVariantRecord
import com.sphereon.data.store.credential.design.model.ResolvedCredentialDesign
import com.sphereon.data.store.credential.design.model.SdPolicy
import com.sphereon.data.store.credential.design.model.VctHostingMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.uuid.Uuid

class CanonicalModelSerializationTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = false
        }

    @Test
    fun claimPathSegmentPropertyRoundTrip() {
        val segment: ClaimPathSegment = ClaimPathSegment.Property("name")
        val encoded = json.encodeToString(ClaimPathSegment.serializer(), segment)
        val decoded = json.decodeFromString(ClaimPathSegment.serializer(), encoded)
        assertEquals(segment, decoded)
    }

    @Test
    fun claimPathSegmentIndexRoundTrip() {
        val segment: ClaimPathSegment = ClaimPathSegment.Index(42)
        val encoded = json.encodeToString(ClaimPathSegment.serializer(), segment)
        val decoded = json.decodeFromString(ClaimPathSegment.serializer(), encoded)
        assertEquals(segment, decoded)
    }

    @Test
    fun claimPathSegmentAnyArrayRoundTrip() {
        val segment: ClaimPathSegment = ClaimPathSegment.AnyArrayElement
        val encoded = json.encodeToString(ClaimPathSegment.serializer(), segment)
        val decoded = json.decodeFromString(ClaimPathSegment.serializer(), encoded)
        assertEquals(segment, decoded)
    }

    @Test
    fun designBindingRoundTrip() {
        val binding = DesignBinding(vct = "urn:example:pid", issuerUri = "https://issuer.example.com")
        val encoded = json.encodeToString(binding)
        val decoded = json.decodeFromString<DesignBinding>(encoded)
        assertEquals(binding, decoded)
    }

    @Test
    fun designBindingHostedVctRestShapeRoundTrip() {
        val binding =
            DesignBinding(
                credentialConfigurationId = "EmployeeBadge",
                vct = "EmployeeBadge",
                vctHostingMode = VctHostingMode.HOSTED,
            )

        val encoded = json.encodeToString(binding)
        val encodedObject = json.parseToJsonElement(encoded).jsonObject
        assertEquals("EmployeeBadge", encodedObject["vct"]?.jsonPrimitive?.content)
        assertEquals("HOSTED", encodedObject["vctHostingMode"]?.jsonPrimitive?.content)

        val decoded = json.decodeFromString<DesignBinding>(encoded)
        assertEquals(binding, decoded)
    }

    @Test
    fun claimPresentationRoundTrip() {
        val claim =
            ClaimPresentation(
                path = listOf(ClaimPathSegment.Property("given_name")),
                labels =
                    listOf(
                        ClaimLabel(locale = "en", label = "Given Name", description = "First name"),
                        ClaimLabel(locale = "nl", label = "Voornaam", entryValues = mapOf("M" to "Man", "V" to "Vrouw")),
                    ),
                mandatory = true,
                sdPolicy = SdPolicy.ALWAYS,
                order = 1,
                entryCodes = listOf("M", "V"),
                unit = "cm",
            )
        val encoded = json.encodeToString(claim)
        val decoded = json.decodeFromString<ClaimPresentation>(encoded)
        assertEquals(claim, decoded)
    }

    @Test
    fun credentialDesignRecordRoundTrip() {
        val now = Clock.System.now()
        val record =
            CredentialDesignRecord(
                id = Uuid.random(),
                tenantId = "test-tenant",
                alias = "PID Credential",
                hostingMode = DesignHostingMode.LOCAL,
                bindings = listOf(DesignBinding(vct = "urn:eu:pid")),
                displays = listOf(LocalizedCredentialDisplay(locale = "en", name = "Person ID")),
                claims =
                    listOf(
                        ClaimPresentation(
                            path = listOf(ClaimPathSegment.Property("family_name")),
                            labels = listOf(ClaimLabel(locale = "en", label = "Family Name")),
                        ),
                    ),
                createdAt = now,
                updatedAt = now,
            )
        val encoded = json.encodeToString(record)
        val decoded = json.decodeFromString<CredentialDesignRecord>(encoded)
        assertEquals(record.id, decoded.id)
        assertEquals(record.tenantId, decoded.tenantId)
        assertEquals(record.bindings, decoded.bindings)
        assertEquals(record.displays, decoded.displays)
        assertEquals(record.claims.size, decoded.claims.size)
    }

    @Test
    fun renderVariantRecordRoundTrip() {
        val variant =
            RenderVariantRecord(
                id = Uuid.random(),
                tenantId = "test",
                kind = RenderVariantKind.SIMPLE_CARD,
                backgroundColor = "#1A2B3C",
                textColor = "#FFFFFF",
                logo = AssetReference(uri = "https://example.com/logo.png", altText = "Logo"),
            )
        val encoded = json.encodeToString(variant)
        val decoded = json.decodeFromString<RenderVariantRecord>(encoded)
        assertEquals(variant, decoded)
    }

    @Test
    fun fieldRenderHintWithCardinalityRoundTrip() {
        val hint =
            FieldRenderHint(
                path = listOf(ClaimPathSegment.Property("phone_numbers")),
                valueKind = ClaimValueKind.ARRAY,
                widgetHint = ClaimWidgetHint.LIST,
                cardinality = ClaimCardinality(min = 0, max = 3),
                sensitive = true,
                standard = "E.164",
            )
        val encoded = json.encodeToString(hint)
        val decoded = json.decodeFromString<FieldRenderHint>(encoded)
        assertEquals(hint, decoded)
    }

    @Test
    fun resolvedCredentialDesignRoundTrip() {
        val now = Clock.System.now()
        val resolved =
            ResolvedCredentialDesign(
                design =
                    CredentialDesignRecord(
                        id = Uuid.random(),
                        tenantId = "t1",
                        hostingMode = DesignHostingMode.LOCAL,
                        bindings = listOf(DesignBinding(vct = "test")),
                        displays = listOf(LocalizedCredentialDisplay(locale = "en", name = "Test")),
                        createdAt = now,
                        updatedAt = now,
                    ),
                renderVariants = emptyList(),
                appliedLayers =
                    listOf(
                        AppliedDesignLayer(sourceType = DesignSourceType.LOCAL_OVERRIDE, priority = 0),
                    ),
                lockedFields = mapOf("display:en" to DesignSourceType.EIDAS_REGISTRY),
                resolvedAt = now,
            )
        val encoded = json.encodeToString(resolved)
        val decoded = json.decodeFromString<ResolvedCredentialDesign>(encoded)
        assertEquals(resolved.design.id, decoded.design.id)
        assertEquals(resolved.lockedFields, decoded.lockedFields)
    }

    @Test
    fun designSourceTypeEnumRoundTrip() {
        for (sourceType in DesignSourceType.entries) {
            val encoded = json.encodeToString(sourceType)
            val decoded = json.decodeFromString<DesignSourceType>(encoded)
            assertEquals(sourceType, decoded)
        }
    }

    @Test
    fun moduleConfigDefaultsRoundTrip() {
        val config = CredentialDesignModuleConfig()
        val encoded = json.encodeToString(config)
        val decoded = json.decodeFromString<CredentialDesignModuleConfig>(encoded)
        assertEquals(config, decoded)
        assertEquals(16, decoded.validation.maxBindingsPerDesign)
        assertEquals(256, decoded.validation.maxClaimsPerDesign)
        assertEquals(86400L, decoded.refresh.defaultTtlSeconds)
    }
}
