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

import com.sphereon.mdoc.data.DataElement
import com.sphereon.mdoc.data.mdl.Mdl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for DeviceRequestNameSpace and DeviceRequestNameSpace.Builder.
 */
class DeviceRequestNameSpaceTest {

    private val mdlNamespace = NameSpace("org.iso.18013.5.1")

    @Test
    fun testDeviceRequestNameSpaceCreation() {
        val dataElements = mutableMapOf(
            DataElementIdentifier("family_name") to IntentToRetain(false)
        )
        val nsRequest = DeviceRequestNameSpace(mdlNamespace, dataElements)

        assertEquals(mdlNamespace, nsRequest.nameSpace)
        assertEquals(1, nsRequest.dataElements.size)
    }

    @Test
    fun testDeviceRequestNameSpaceEquality() {
        val dataElements1 = mutableMapOf(
            DataElementIdentifier("family_name") to IntentToRetain(false)
        )
        val dataElements2 = mutableMapOf(
            DataElementIdentifier("family_name") to IntentToRetain(false)
        )
        val ns1 = DeviceRequestNameSpace(mdlNamespace, dataElements1)
        val ns2 = DeviceRequestNameSpace(mdlNamespace, dataElements2)

        assertEquals(ns1, ns2)
    }

    @Test
    fun testDeviceRequestNameSpaceEqualitySameInstance() {
        val dataElements = mutableMapOf(
            DataElementIdentifier("family_name") to IntentToRetain(false)
        )
        val ns = DeviceRequestNameSpace(mdlNamespace, dataElements)

        assertEquals(ns, ns)
    }

    @Test
    fun testDeviceRequestNameSpaceInequalityDifferentNamespace() {
        val dataElements = mutableMapOf(
            DataElementIdentifier("family_name") to IntentToRetain(false)
        )
        val ns1 = DeviceRequestNameSpace(mdlNamespace, dataElements.toMutableMap())
        val ns2 = DeviceRequestNameSpace(NameSpace("other.namespace"), dataElements.toMutableMap())

        assertNotEquals(ns1, ns2)
    }

    @Test
    fun testDeviceRequestNameSpaceInequalityDifferentElements() {
        val ns1 = DeviceRequestNameSpace(
            mdlNamespace,
            mutableMapOf(DataElementIdentifier("family_name") to IntentToRetain(false))
        )
        val ns2 = DeviceRequestNameSpace(
            mdlNamespace,
            mutableMapOf(DataElementIdentifier("given_name") to IntentToRetain(false))
        )

        assertNotEquals(ns1, ns2)
    }

    @Test
    fun testDeviceRequestNameSpaceInequalityDifferentType() {
        val ns = DeviceRequestNameSpace(mdlNamespace, mutableMapOf())
        assertNotEquals<Any>(ns, "not a DeviceRequestNameSpace")
    }

    @Test
    fun testDeviceRequestNameSpaceHashCode() {
        val dataElements1 = mutableMapOf(
            DataElementIdentifier("family_name") to IntentToRetain(false)
        )
        val dataElements2 = mutableMapOf(
            DataElementIdentifier("family_name") to IntentToRetain(false)
        )
        val ns1 = DeviceRequestNameSpace(mdlNamespace, dataElements1)
        val ns2 = DeviceRequestNameSpace(mdlNamespace, dataElements2)

        assertEquals(ns1.hashCode(), ns2.hashCode())
    }

    @Test
    fun testDeviceRequestNameSpaceToString() {
        val dataElements = mutableMapOf(
            DataElementIdentifier("family_name") to IntentToRetain(false)
        )
        val ns = DeviceRequestNameSpace(mdlNamespace, dataElements)
        val str = ns.toString()

        assertTrue(str.contains("DeviceRequestNameSpace"))
        assertTrue(str.contains("nameSpace"))
        assertTrue(str.contains("dataElements"))
    }

    // Builder tests

    @Test
    fun testBuilderCreation() {
        val builder = DeviceRequestNameSpace.Builder()
        assertNotNull(builder)
        assertEquals(Mdl.MDL_NAMESPACE, builder.nameSpace)
    }

    @Test
    fun testBuilderWithNamespace() {
        val builder = DeviceRequestNameSpace.Builder(nameSpace = mdlNamespace)
        assertEquals(mdlNamespace, builder.nameSpace)
    }

    @Test
    fun testBuilderAdd() {
        val builder = DeviceRequestNameSpace.Builder(nameSpace = mdlNamespace)
            .add(DataElementIdentifier("family_name"))

        assertEquals(1, builder.dataElements.size)
        assertTrue(builder.dataElements.containsKey(DataElementIdentifier("family_name")))
    }

    @Test
    fun testBuilderAddWithIntentToRetain() {
        val builder = DeviceRequestNameSpace.Builder(nameSpace = mdlNamespace)
            .add(DataElementIdentifier("family_name"), IntentToRetain(true))

        assertEquals(IntentToRetain(true), builder.dataElements[DataElementIdentifier("family_name")])
    }

    @Test
    fun testBuilderAddMultiple() {
        val builder = DeviceRequestNameSpace.Builder(nameSpace = mdlNamespace)
            .add(DataElementIdentifier("family_name"))
            .add(DataElementIdentifier("given_name"))
            .add(DataElementIdentifier("birth_date"))

        assertEquals(3, builder.dataElements.size)
    }

    @Test
    fun testBuilderAddElements() {
        val element1 = DataElement(
            identifier = DataElementIdentifier("family_name"),
            intentToRetain = IntentToRetain(true)
        )
        val element2 = DataElement(
            identifier = DataElementIdentifier("given_name"),
            intentToRetain = IntentToRetain(false)
        )

        val builder = DeviceRequestNameSpace.Builder(nameSpace = mdlNamespace)
            .addElements(element1, element2)

        assertEquals(2, builder.dataElements.size)
        assertEquals(IntentToRetain(true), builder.dataElements[DataElementIdentifier("family_name")])
        assertEquals(IntentToRetain(false), builder.dataElements[DataElementIdentifier("given_name")])
    }

    @Test
    fun testBuilderBuild() {
        val ns = DeviceRequestNameSpace.Builder(nameSpace = mdlNamespace)
            .add(DataElementIdentifier("family_name"))
            .build()

        assertEquals(mdlNamespace, ns.nameSpace)
        assertEquals(1, ns.dataElements.size)
    }

    @Test
    fun testBuilderEndWithoutItemsRequestBuilder() {
        val builder = DeviceRequestNameSpace.Builder(nameSpace = mdlNamespace)

        assertFailsWith<IllegalArgumentException> {
            builder.end()
        }
    }

    @Test
    fun testBuilderEndWithItemsRequestBuilder() {
        val itemsBuilder = DeviceItemsRequest.Builder()
            .withDocType(DocType("org.iso.18013.5.1.mDL"))

        val nsBuilder = DeviceRequestNameSpace.Builder(
            itemsRequestBuilder = itemsBuilder,
            nameSpace = mdlNamespace
        ).add(DataElementIdentifier("family_name"))

        val returnedItemsBuilder = nsBuilder.end()
        assertEquals(itemsBuilder, returnedItemsBuilder)
    }

    @Test
    fun testBuilderFluentApi() {
        val ns = DeviceRequestNameSpace.Builder(nameSpace = mdlNamespace)
            .add(DataElementIdentifier("family_name"), IntentToRetain(true))
            .add(DataElementIdentifier("given_name"), IntentToRetain(false))
            .add(DataElementIdentifier("birth_date"))
            .build()

        assertEquals(3, ns.dataElements.size)
    }

    @Test
    fun testBuilderWithPredefinedDataElements() {
        val predefinedElements = mutableMapOf(
            DataElementIdentifier("family_name") to IntentToRetain(true)
        )
        val builder = DeviceRequestNameSpace.Builder(
            nameSpace = mdlNamespace,
            dataElements = predefinedElements
        )

        assertEquals(1, builder.dataElements.size)

        builder.add(DataElementIdentifier("given_name"))
        assertEquals(2, builder.dataElements.size)
    }
}
