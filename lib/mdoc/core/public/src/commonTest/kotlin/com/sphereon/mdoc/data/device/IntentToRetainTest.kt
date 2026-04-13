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

package com.sphereon.mdoc.data.device

import com.sphereon.cbor.CborBool
import com.sphereon.cbor.toCborBool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for IntentToRetain value class.
 */
class IntentToRetainTest {

    @Test
    fun testTrueValueToString() {
        val retain = IntentToRetain(true)
        assertEquals("true", retain.toString())
    }

    @Test
    fun testFalseValueToString() {
        val retain = IntentToRetain(false)
        assertEquals("false", retain.toString())
    }

    @Test
    fun testToCborStructureTrue() {
        val retain = IntentToRetain(true)
        val cbor = retain.toCborStructure()
        assertTrue(cbor.value)
    }

    @Test
    fun testToCborStructureFalse() {
        val retain = IntentToRetain(false)
        val cbor = retain.toCborStructure()
        assertFalse(cbor.value)
    }

    @Test
    fun testFromCborStructureTrue() {
        val cbor = true.toCborBool()
        val retain = IntentToRetain.Decoder.fromCborStructure(cbor)
        assertEquals("true", retain.toString())
    }

    @Test
    fun testFromCborStructureFalse() {
        val cbor = false.toCborBool()
        val retain = IntentToRetain.Decoder.fromCborStructure(cbor)
        assertEquals("false", retain.toString())
    }

    @Test
    fun testEquality() {
        val retain1 = IntentToRetain(true)
        val retain2 = IntentToRetain(true)
        assertEquals(retain1, retain2)
    }

    @Test
    fun testHashCodeConsistency() {
        val retain1 = IntentToRetain(false)
        val retain2 = IntentToRetain(false)
        assertEquals(retain1.hashCode(), retain2.hashCode())
    }

    @Test
    fun testToCborAndBackTrue() {
        val original = IntentToRetain(true)
        val cbor = original.toCborStructure()
        val decoded = IntentToRetain.Decoder.fromCborStructure(cbor)
        assertEquals(original, decoded)
    }

    @Test
    fun testToCborAndBackFalse() {
        val original = IntentToRetain(false)
        val cbor = original.toCborStructure()
        val decoded = IntentToRetain.Decoder.fromCborStructure(cbor)
        assertEquals(original, decoded)
    }
}
