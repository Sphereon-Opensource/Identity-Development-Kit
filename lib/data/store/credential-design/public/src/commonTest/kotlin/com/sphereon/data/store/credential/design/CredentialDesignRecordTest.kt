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

package com.sphereon.data.store.credential.design

import com.sphereon.data.store.credential.design.model.CredentialDesignRecord
import com.sphereon.data.store.credential.design.model.DesignHostingMode
import com.sphereon.data.store.credential.design.model.SemanticAttributeSetRef
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.uuid.Uuid

class CredentialDesignRecordTest {
    private val now = Clock.System.now()
    private val id = Uuid.random()
    private val tenantId = "tenant-1"

    private fun minimalRecord(
        semanticAttributeSetRef: SemanticAttributeSetRef? = null,
        attributeProfileId: Uuid? = null,
        attributeProfileVersion: Long? = null,
    ): CredentialDesignRecord =
        CredentialDesignRecord(
            id = id,
            tenantId = tenantId,
            hostingMode = DesignHostingMode.LOCAL,
            bindings = emptyList(),
            displays = emptyList(),
            createdAt = now,
            updatedAt = now,
            semanticAttributeSetRef = semanticAttributeSetRef,
            attributeProfileId = attributeProfileId,
            attributeProfileVersion = attributeProfileVersion,
        )

    @Test
    fun semanticAttributeSetRefDefaultsToNull() {
        val record = minimalRecord()
        assertNull(record.semanticAttributeSetRef)
    }

    @Test
    fun semanticAttributeSetRefRoundTripWithRef() {
        val ref = SemanticAttributeSetRef(bundleId = "oca-pid-bundle", version = "1.0.0")
        val record = minimalRecord(semanticAttributeSetRef = ref)
        val json = Json
        val decoded =
            json.decodeFromString(
                CredentialDesignRecord.serializer(),
                json.encodeToString(CredentialDesignRecord.serializer(), record),
            )
        assertEquals(record, decoded)
        assertEquals(ref, decoded.semanticAttributeSetRef)
    }

    @Test
    fun semanticAttributeSetRefWireKeyPresentWhenSet() {
        val ref = SemanticAttributeSetRef(bundleId = "oca-pid-bundle")
        val record = minimalRecord(semanticAttributeSetRef = ref)
        val serialized = Json.encodeToString(CredentialDesignRecord.serializer(), record)
        assertContains(serialized, "\"semanticAttributeSetRef\"")
    }

    @Test
    fun semanticAttributeSetRefNullDefaultRoundTrip() {
        val record = minimalRecord()
        val json = Json
        val serialized = json.encodeToString(CredentialDesignRecord.serializer(), record)
        assertFalse(serialized.contains("\"semanticAttributeSetRef\""))
        val decoded = json.decodeFromString(CredentialDesignRecord.serializer(), serialized)
        assertNull(decoded.semanticAttributeSetRef)
    }

    @Test
    fun attributeProfileRefDefaultsToNull() {
        val record = minimalRecord()
        assertNull(record.attributeProfileId)
        assertNull(record.attributeProfileVersion)
    }

    @Test
    fun attributeProfileRefRoundTripWithRef() {
        val profileId = Uuid.random()
        val record = minimalRecord(attributeProfileId = profileId, attributeProfileVersion = 2L)
        val json = Json
        val decoded =
            json.decodeFromString(
                CredentialDesignRecord.serializer(),
                json.encodeToString(CredentialDesignRecord.serializer(), record),
            )
        assertEquals(record, decoded)
        assertEquals(profileId, decoded.attributeProfileId)
        assertEquals(2L, decoded.attributeProfileVersion)
    }

    @Test
    fun attributeProfileRefCoexistsWithLegacySetRef() {
        val profileId = Uuid.random()
        val record =
            minimalRecord(
                semanticAttributeSetRef = SemanticAttributeSetRef(bundleId = "oca-pid-bundle", version = "1.0.0"),
                attributeProfileId = profileId,
                attributeProfileVersion = 1L,
            )
        val json = Json
        val decoded =
            json.decodeFromString(
                CredentialDesignRecord.serializer(),
                json.encodeToString(CredentialDesignRecord.serializer(), record),
            )
        assertEquals(record, decoded)
        assertEquals("oca-pid-bundle", decoded.semanticAttributeSetRef?.bundleId)
        assertEquals(profileId, decoded.attributeProfileId)
    }

    @Test
    fun attributeProfileRefNullDefaultOmittedFromWire() {
        val record = minimalRecord()
        val serialized = Json.encodeToString(CredentialDesignRecord.serializer(), record)
        assertFalse(serialized.contains("\"attributeProfileId\""))
        assertFalse(serialized.contains("\"attributeProfileVersion\""))
    }
}
