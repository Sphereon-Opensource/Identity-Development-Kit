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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for AbstractDataElementDef.
 */
class AbstractDataElementDefTest {

    private val mdlNamespace = NameSpace("org.iso.18013.5.1")
    private val pidNamespace = NameSpace("eu.europa.ec.eudi.pid.1")

    // Test implementation of AbstractDataElementDef
    private class TestDataElementDef(
        override val nameSpace: NameSpace,
        override val identifier: DataElementIdentifier,
        override val presence: Presence,
        override val details: String = "test details",
        override val cddls: Array<CDDL> = arrayOf(CDDL.tstr)
    ) : AbstractDataElementDef()

    @Test
    fun testCddlProperty() {
        val def = TestDataElementDef(
            mdlNamespace,
            DataElementIdentifier("given_name"),
            Presence.MANDATORY,
            cddls = arrayOf(CDDL.tstr, CDDL.bstr)
        )
        assertEquals(CDDL.tstr, def.cddl)
    }

    @Test
    fun testToElement() {
        val def = TestDataElementDef(mdlNamespace, DataElementIdentifier("given_name"), Presence.MANDATORY)
        val element = def.toElement()

        assertEquals(DataElementIdentifier("given_name"), element.identifier)
        assertEquals(IntentToRetain(false), element.intentToRetain)
    }

    @Test
    fun testToElementWithIntentToRetain() {
        val def = TestDataElementDef(mdlNamespace, DataElementIdentifier("given_name"), Presence.MANDATORY)
        val element = def.toElement(IntentToRetain(true))

        assertEquals(DataElementIdentifier("given_name"), element.identifier)
        assertEquals(IntentToRetain(true), element.intentToRetain)
    }

    @Test
    fun testToOid4VPConstraintField() {
        val def = TestDataElementDef(mdlNamespace, DataElementIdentifier("given_name"), Presence.MANDATORY)
        val constraintField = def.toOid4VPConstraintField(true)

        assertNotNull(constraintField)
    }

    @Test
    fun testEqualsSameInstance() {
        val def = TestDataElementDef(mdlNamespace, DataElementIdentifier("given_name"), Presence.MANDATORY)
        assertTrue(def.equals(def))
    }

    @Test
    fun testEqualsNull() {
        val def = TestDataElementDef(mdlNamespace, DataElementIdentifier("given_name"), Presence.MANDATORY)
        assertFalse(def.equals(null))
    }

    @Test
    fun testEqualsNonDataElementDef() {
        val def = TestDataElementDef(mdlNamespace, DataElementIdentifier("given_name"), Presence.MANDATORY)
        assertFalse(def.equals("string"))
    }

    @Test
    fun testEqualsDifferentNamespace() {
        val def1 = TestDataElementDef(mdlNamespace, DataElementIdentifier("given_name"), Presence.MANDATORY)
        val def2 = TestDataElementDef(pidNamespace, DataElementIdentifier("given_name"), Presence.MANDATORY)

        assertFalse(def1.equals(def2))
    }

    @Test
    fun testEqualsDifferentIdentifier() {
        val def1 = TestDataElementDef(mdlNamespace, DataElementIdentifier("given_name"), Presence.MANDATORY)
        val def2 = TestDataElementDef(mdlNamespace, DataElementIdentifier("family_name"), Presence.MANDATORY)

        assertFalse(def1.equals(def2))
    }

    @Test
    fun testEqualsDifferentPresence() {
        val def1 = TestDataElementDef(mdlNamespace, DataElementIdentifier("given_name"), Presence.MANDATORY)
        val def2 = TestDataElementDef(mdlNamespace, DataElementIdentifier("given_name"), Presence.OPTIONAL)

        assertFalse(def1.equals(def2))
    }

    @Test
    fun testEqualsDifferentCddl() {
        val def1 = TestDataElementDef(
            mdlNamespace,
            DataElementIdentifier("given_name"),
            Presence.MANDATORY,
            cddls = arrayOf(CDDL.tstr)
        )
        val def2 = TestDataElementDef(
            mdlNamespace,
            DataElementIdentifier("given_name"),
            Presence.MANDATORY,
            cddls = arrayOf(CDDL.bstr)
        )

        assertFalse(def1.equals(def2))
    }

    @Test
    fun testEqualsDifferentCddlsArray() {
        val def1 = TestDataElementDef(
            mdlNamespace,
            DataElementIdentifier("given_name"),
            Presence.MANDATORY,
            cddls = arrayOf(CDDL.tstr, CDDL.bstr)
        )
        val def2 = TestDataElementDef(
            mdlNamespace,
            DataElementIdentifier("given_name"),
            Presence.MANDATORY,
            cddls = arrayOf(CDDL.tstr, CDDL.uint)
        )

        assertFalse(def1.equals(def2))
    }

    @Test
    fun testEqualsIdentical() {
        val def1 = TestDataElementDef(mdlNamespace, DataElementIdentifier("given_name"), Presence.MANDATORY)
        val def2 = TestDataElementDef(mdlNamespace, DataElementIdentifier("given_name"), Presence.MANDATORY)

        assertTrue(def1.equals(def2))
    }

    @Test
    fun testHashCode() {
        val def1 = TestDataElementDef(mdlNamespace, DataElementIdentifier("given_name"), Presence.MANDATORY)
        val def2 = TestDataElementDef(mdlNamespace, DataElementIdentifier("given_name"), Presence.MANDATORY)

        assertEquals(def1.hashCode(), def2.hashCode())
    }

    @Test
    fun testHashCodeDifferent() {
        val def1 = TestDataElementDef(mdlNamespace, DataElementIdentifier("given_name"), Presence.MANDATORY)
        val def2 = TestDataElementDef(mdlNamespace, DataElementIdentifier("family_name"), Presence.MANDATORY)

        assertNotEquals(def1.hashCode(), def2.hashCode())
    }

    @Test
    fun testToString() {
        val def = TestDataElementDef(mdlNamespace, DataElementIdentifier("given_name"), Presence.MANDATORY)
        val str = def.toString()

        assertTrue(str.contains("DataElementDef"))
        assertTrue(str.contains("given_name"))
        assertTrue(str.contains("MANDATORY"))
    }
}
