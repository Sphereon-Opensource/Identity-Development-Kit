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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for DeviceItemsRequest and related classes.
 */
class DeviceItemsRequestTest {

    private val mdlDocType = DocType("org.iso.18013.5.1.mDL")
    private val mdlNamespace = NameSpace("org.iso.18013.5.1")

    @Test
    fun testDeviceItemsRequestCreation() {
        val identifiers = mapOf(
            DataElementIdentifier("given_name") to IntentToRetain(false),
            DataElementIdentifier("family_name") to IntentToRetain(true)
        )
        val nameSpaces = mapOf(mdlNamespace to identifiers)

        val request = DeviceItemsRequest(
            docType = mdlDocType,
            nameSpaces = nameSpaces,
            requestInfo = null
        )

        assertEquals(mdlDocType, request.docType)
        assertEquals(1, request.getNameSpaces().size)
        assertEquals(mdlNamespace, request.getNameSpaces()[0])
    }

    @Test
    fun testGetIdentifiers() {
        val identifiers = mapOf(
            DataElementIdentifier("given_name") to IntentToRetain(false),
            DataElementIdentifier("family_name") to IntentToRetain(true)
        )
        val nameSpaces = mapOf(mdlNamespace to identifiers)

        val request = DeviceItemsRequest(
            docType = mdlDocType,
            nameSpaces = nameSpaces
        )

        val retrieved = request.getIdentifiers(mdlNamespace)
        assertEquals(2, retrieved.size)
        assertEquals(IntentToRetain(false), retrieved[DataElementIdentifier("given_name")])
        assertEquals(IntentToRetain(true), retrieved[DataElementIdentifier("family_name")])
    }

    @Test
    fun testGetIdentifiersEmptyForUnknownNamespace() {
        val request = DeviceItemsRequest(
            docType = mdlDocType,
            nameSpaces = mapOf()
        )

        val retrieved = request.getIdentifiers(mdlNamespace)
        assertTrue(retrieved.isEmpty())
    }

    @Test
    fun testDeviceItemsRequestWithRequestInfo() {
        val nameSpaces = mapOf(
            mdlNamespace to mapOf(
                DataElementIdentifier("given_name") to IntentToRetain(false)
            )
        )
        val requestInfo = mapOf("version" to "1.0", "purpose" to "age_verification")

        val request = DeviceItemsRequest(
            docType = mdlDocType,
            nameSpaces = nameSpaces,
            requestInfo = requestInfo
        )

        assertEquals(requestInfo, request.requestInfo)
    }

    @Test
    fun testDeviceItemsRequestEquality() {
        val nameSpaces = mapOf(
            mdlNamespace to mapOf(
                DataElementIdentifier("given_name") to IntentToRetain(false)
            )
        )

        val request1 = DeviceItemsRequest(docType = mdlDocType, nameSpaces = nameSpaces)
        val request2 = DeviceItemsRequest(docType = mdlDocType, nameSpaces = nameSpaces)

        assertEquals(request1, request2)
        assertEquals(request1.hashCode(), request2.hashCode())
    }

    @Test
    fun testDeviceItemsRequestToString() {
        val request = DeviceItemsRequest(
            docType = mdlDocType,
            nameSpaces = mapOf()
        )

        val str = request.toString()
        assertNotNull(str)
        assertTrue(str.contains("DeviceItemsRequest"))
        assertTrue(str.contains("docType"))
    }

    @Test
    fun testBuilderBasicUsage() {
        val builder = DeviceItemsRequest.Builder()
            .withDocType(mdlDocType)
            .add(mdlNamespace, DataElementIdentifier("given_name"), IntentToRetain(false))
            .add(mdlNamespace, DataElementIdentifier("family_name"), IntentToRetain(true))

        val request = builder.build()

        assertEquals(mdlDocType, request.docType)
        assertEquals(1, request.getNameSpaces().size)
        assertEquals(2, request.getIdentifiers(mdlNamespace).size)
    }

    @Test
    fun testBuilderWithoutDocTypeThrows() {
        val builder = DeviceItemsRequest.Builder()
            .add(mdlNamespace, DataElementIdentifier("given_name"))

        assertFailsWith<IllegalArgumentException> {
            builder.build()
        }
    }

    @Test
    fun testBuilderNameSpaceMethod() {
        val builder = DeviceItemsRequest.Builder()
            .withDocType(mdlDocType)

        val nsBuilder = builder.nameSpace(mdlNamespace)
        nsBuilder.add(DataElementIdentifier("given_name"), IntentToRetain(false))
            .add(DataElementIdentifier("family_name"), IntentToRetain(true))

        val request = nsBuilder.end().build()

        assertEquals(2, request.getIdentifiers(mdlNamespace).size)
    }

    @Test
    fun testDeviceRequestNameSpaceCreation() {
        val dataElements = mutableMapOf(
            DataElementIdentifier("given_name") to IntentToRetain(false)
        )
        val ns = DeviceRequestNameSpace(mdlNamespace, dataElements)

        assertEquals(mdlNamespace, ns.nameSpace)
        assertEquals(1, ns.dataElements.size)
    }

    @Test
    fun testDeviceRequestNameSpaceEquality() {
        val dataElements = mutableMapOf(
            DataElementIdentifier("given_name") to IntentToRetain(false)
        )

        val ns1 = DeviceRequestNameSpace(mdlNamespace, dataElements)
        val ns2 = DeviceRequestNameSpace(mdlNamespace, mutableMapOf(
            DataElementIdentifier("given_name") to IntentToRetain(false)
        ))

        assertEquals(ns1, ns2)
        assertEquals(ns1.hashCode(), ns2.hashCode())
    }

    @Test
    fun testDeviceRequestNameSpaceToString() {
        val ns = DeviceRequestNameSpace(mdlNamespace, mutableMapOf())
        val str = ns.toString()

        assertNotNull(str)
        assertTrue(str.contains("DeviceRequestNameSpace"))
    }

    @Test
    fun testDeviceRequestNameSpaceBuilder() {
        val builder = DeviceRequestNameSpace.Builder(
            nameSpace = mdlNamespace
        )
        builder.add(DataElementIdentifier("given_name"), IntentToRetain(false))
            .add(DataElementIdentifier("family_name"), IntentToRetain(true))

        val ns = builder.build()

        assertEquals(mdlNamespace, ns.nameSpace)
        assertEquals(2, ns.dataElements.size)
    }

    @Test
    fun testDeviceRequestNameSpaceBuilderEndWithoutParentThrows() {
        val builder = DeviceRequestNameSpace.Builder(nameSpace = mdlNamespace)

        assertFailsWith<IllegalArgumentException> {
            builder.end()
        }
    }

    @Test
    fun testBuilderToString() {
        val builder = DeviceItemsRequest.Builder()
            .withDocType(mdlDocType)

        val str = builder.toString()
        assertNotNull(str)
        assertTrue(str.contains("Builder"))
    }

    @Test
    fun testCborEncodeDecode() {
        val identifiers = mapOf(
            DataElementIdentifier("given_name") to IntentToRetain(false),
            DataElementIdentifier("family_name") to IntentToRetain(true)
        )
        val nameSpaces = mapOf(mdlNamespace to identifiers)

        val original = DeviceItemsRequest(
            docType = mdlDocType,
            nameSpaces = nameSpaces
        )

        val encoded = original.encodeCbor()
        val decoded = DeviceItemsRequest.decodeCbor(encoded)

        assertEquals(original.docType, decoded.docType)
        assertEquals(original.nameSpaces.size, decoded.nameSpaces.size)
    }
}
