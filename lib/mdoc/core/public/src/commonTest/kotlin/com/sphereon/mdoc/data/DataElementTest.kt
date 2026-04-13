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

package com.sphereon.mdoc.data

import com.sphereon.cbor.CDDL
import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.IntentToRetain
import com.sphereon.mdoc.data.device.NameSpace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for DataElement, Presence enum, and AbstractDataElementDef.
 */
class DataElementTest {

    private val mdlNamespace = NameSpace("org.iso.18013.5.1")

    // Test implementation of AbstractDataElementDef for testing purposes
    private class TestDataElementDef(
        override val nameSpace: NameSpace,
        override val identifier: DataElementIdentifier,
        override val presence: Presence,
        override val details: String = "test details",
        override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
    ) : AbstractDataElementDef()

    @Test
    fun testDataElementCreation() {
        val element = DataElement(
            identifier = DataElementIdentifier("given_name"),
            intentToRetain = IntentToRetain(false)
        )

        assertEquals("given_name", element.identifier.toString())
        assertEquals(IntentToRetain(false), element.intentToRetain)
    }

    @Test
    fun testDataElementToPair() {
        val element = DataElement(
            identifier = DataElementIdentifier("family_name"),
            intentToRetain = IntentToRetain(true)
        )

        val pair = element.toPair()
        assertEquals(DataElementIdentifier("family_name"), pair.first)
        assertEquals(IntentToRetain(true), pair.second)
    }

    @Test
    fun testDataElementFromDefinition() {
        val def = TestDataElementDef(
            nameSpace = mdlNamespace,
            identifier = DataElementIdentifier("birth_date"),
            presence = Presence.MANDATORY
        )

        val element = DataElement.fromDefinition(def)

        assertEquals(def.identifier, element.identifier)
        // Mandatory presence should translate to intentToRetain = true
        assertEquals(IntentToRetain(true), element.intentToRetain)
        assertEquals(def, element.definition)
    }

    @Test
    fun testDataElementFromDefinitionOptional() {
        val def = TestDataElementDef(
            nameSpace = mdlNamespace,
            identifier = DataElementIdentifier("portrait"),
            presence = Presence.OPTIONAL
        )

        val element = DataElement.fromDefinition(def)

        assertEquals(def.identifier, element.identifier)
        // Optional presence should translate to intentToRetain = false
        assertEquals(IntentToRetain(false), element.intentToRetain)
    }

    @Test
    fun testDataElementEquality() {
        val element1 = DataElement(
            identifier = DataElementIdentifier("given_name"),
            intentToRetain = IntentToRetain(false)
        )
        val element2 = DataElement(
            identifier = DataElementIdentifier("given_name"),
            intentToRetain = IntentToRetain(false)
        )

        assertEquals(element1, element2)
    }

    @Test
    fun testDataElementToString() {
        val element = DataElement(
            identifier = DataElementIdentifier("portrait"),
            intentToRetain = IntentToRetain(false)
        )

        val str = element.toString()
        assertNotNull(str)
        assertTrue(str.contains("DataElement"))
        assertTrue(str.contains("portrait"))
    }

    @Test
    fun testPresenceMandatory() {
        assertEquals("MANDATORY", Presence.MANDATORY.value)
        assertTrue(Presence.MANDATORY.mandatory)
    }

    @Test
    fun testPresenceOptional() {
        assertEquals("OPTIONAL", Presence.OPTIONAL.value)
        assertFalse(Presence.OPTIONAL.mandatory)
    }

    @Test
    fun testPresenceEntries() {
        assertEquals(2, Presence.entries.size)
        assertTrue(Presence.entries.contains(Presence.MANDATORY))
        assertTrue(Presence.entries.contains(Presence.OPTIONAL))
    }

    @Test
    fun testAbstractDataElementDefToElement() {
        val def = TestDataElementDef(
            nameSpace = mdlNamespace,
            identifier = DataElementIdentifier("given_name"),
            presence = Presence.MANDATORY
        )

        val element = def.toElement()
        assertEquals(def.identifier, element.identifier)
        assertEquals(IntentToRetain(false), element.intentToRetain)

        val elementWithRetain = def.toElement(IntentToRetain(true))
        assertEquals(IntentToRetain(true), elementWithRetain.intentToRetain)
    }

    @Test
    fun testAbstractDataElementDefCddl() {
        val def = TestDataElementDef(
            nameSpace = mdlNamespace,
            identifier = DataElementIdentifier("given_name"),
            presence = Presence.MANDATORY,
            cddls = arrayOf(CDDL.tstr, CDDL.int)
        )

        assertEquals(CDDL.tstr, def.cddl)
    }

    @Test
    fun testAbstractDataElementDefEquality() {
        val def1 = TestDataElementDef(
            nameSpace = mdlNamespace,
            identifier = DataElementIdentifier("birth_date"),
            presence = Presence.MANDATORY
        )
        val def2 = TestDataElementDef(
            nameSpace = mdlNamespace,
            identifier = DataElementIdentifier("birth_date"),
            presence = Presence.MANDATORY
        )

        assertEquals(def1, def2)
        assertEquals(def1.hashCode(), def2.hashCode())
    }

    @Test
    fun testAbstractDataElementDefEqualitySameInstance() {
        val def = TestDataElementDef(
            nameSpace = mdlNamespace,
            identifier = DataElementIdentifier("birth_date"),
            presence = Presence.MANDATORY
        )

        assertEquals(def, def)
    }

    @Test
    fun testAbstractDataElementDefEqualityDifferentType() {
        val def = TestDataElementDef(
            nameSpace = mdlNamespace,
            identifier = DataElementIdentifier("birth_date"),
            presence = Presence.MANDATORY
        )

        assertFalse(def.equals("not a def"))
    }

    @Test
    fun testAbstractDataElementDefToString() {
        val def = TestDataElementDef(
            nameSpace = mdlNamespace,
            identifier = DataElementIdentifier("document_number"),
            presence = Presence.OPTIONAL
        )

        val str = def.toString()
        assertNotNull(str)
        assertTrue(str.contains("DataElementDef"))
        assertTrue(str.contains("document_number"))
    }
}
