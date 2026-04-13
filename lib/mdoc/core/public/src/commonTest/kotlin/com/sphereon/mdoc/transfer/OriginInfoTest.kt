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

package com.sphereon.mdoc.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for OriginInfo, OriginInfoCategory, OriginInfoType, and OriginInfoDetails.
 */
class OriginInfoTest {

    // OriginInfoCategory tests

    @Test
    fun testOriginInfoCategoryCreation() {
        val category = OriginInfoCategory(1u)
        assertEquals(1u, category.category)
    }

    @Test
    fun testOriginInfoCategoryToCborStructure() {
        val category = OriginInfoCategory(2u)
        val cbor = category.toCborStructure()
        assertEquals(2L, cbor.value)
    }

    @Test
    fun testOriginInfoCategoryFromCborStructure() {
        val category = OriginInfoCategory(3u)
        val cbor = category.toCborStructure()
        val decoded = OriginInfoCategory.fromCborStructure(cbor)
        assertEquals(category.category, decoded.category)
    }

    @Test
    fun testOriginInfoCategoryToString() {
        val category = OriginInfoCategory(5u)
        assertEquals("5", category.toString())
    }

    // OriginInfoType tests

    @Test
    fun testOriginInfoTypeCreation() {
        val type = OriginInfoType(1u)
        assertEquals(1u, type.infoType)
    }

    @Test
    fun testOriginInfoTypeToCborStructure() {
        val type = OriginInfoType(2u)
        val cbor = type.toCborStructure()
        assertEquals(2L, cbor.value)
    }

    @Test
    fun testOriginInfoTypeFromCborStructure() {
        val type = OriginInfoType(3u)
        val cbor = type.toCborStructure()
        val decoded = OriginInfoType.fromCborStructure(cbor)
        assertEquals(type.infoType, decoded.infoType)
    }

    @Test
    fun testOriginInfoTypeToString() {
        val type = OriginInfoType(7u)
        assertEquals("7", type.toString())
    }

    // OriginInfoDetails tests

    @Test
    fun testOriginInfoDetailsCreation() {
        val details = OriginInfoDetails(mapOf("domain" to "example.com"))
        assertEquals("example.com", details["domain"])
    }

    @Test
    fun testOriginInfoDetailsMapDelegation() {
        val details = OriginInfoDetails(mapOf("key1" to "value1", "key2" to null))
        assertEquals(2, details.size)
        assertTrue(details.containsKey("key1"))
        assertTrue(details.containsKey("key2"))
        assertNull(details["key2"])
    }

    @Test
    fun testOriginInfoDetailsDomainConstant() {
        assertEquals("domain", OriginInfoDetails.DOMAIN)
    }

    // OriginInfo tests

    @Test
    fun testOriginInfoCreation() {
        val originInfo = OriginInfo(
            cat = OriginInfoCategory(1u),
            type = OriginInfoType(1u),
            details = null,
            original = null
        )

        assertEquals(1u, originInfo.cat.category)
        assertEquals(1u, originInfo.type.infoType)
        assertNull(originInfo.details)
        assertNull(originInfo.original)
    }

    @Test
    fun testOriginInfoWithDetails() {
        val details = OriginInfoDetails(mapOf("domain" to "example.com"))
        val originInfo = OriginInfo(
            cat = OriginInfoCategory(1u),
            type = OriginInfoType(1u),
            details = details,
            original = null
        )

        assertNotNull(originInfo.details)
        assertEquals("example.com", originInfo.details?.get("domain"))
    }

    @Test
    fun testOriginInfoCborBuilder() {
        val originInfo = OriginInfo(
            cat = OriginInfoCategory(1u),
            type = OriginInfoType(1u),
            details = null,
            original = null
        )
        val builder = originInfo.cborBuilder()
        assertNotNull(builder)
    }

    @Test
    fun testOriginInfoEncodeDecodeWithoutDetails() {
        val originInfo = OriginInfo(
            cat = OriginInfoCategory(1u),
            type = OriginInfoType(2u),
            details = null,
            original = null
        )
        val bytes = originInfo.encodeCbor()
        val decoded = OriginInfo.decodeCbor(bytes)

        assertEquals(originInfo.cat.category, decoded.cat.category)
        assertEquals(originInfo.type.infoType, decoded.type.infoType)
        assertNull(decoded.details)
    }

    @Test
    fun testOriginInfoCompanionLabels() {
        assertEquals("cat", OriginInfo.CAT.value)
        assertEquals("type", OriginInfo.TYPE.value)
        assertEquals("details", OriginInfo.DETAILS.value)
    }

    @Test
    fun testOriginInfoFromCborStructure() {
        val originInfo = OriginInfo(
            cat = OriginInfoCategory(1u),
            type = OriginInfoType(1u),
            details = null,
            original = null
        )
        val bytes = originInfo.encodeCbor()
        val decoded = OriginInfo.decodeCbor(bytes)

        assertEquals(originInfo.cat.category, decoded.cat.category)
        assertEquals(originInfo.type.infoType, decoded.type.infoType)
    }

    @Test
    fun testOriginInfoEquality() {
        val originInfo1 = OriginInfo(
            cat = OriginInfoCategory(1u),
            type = OriginInfoType(1u),
            details = null,
            original = null
        )
        val originInfo2 = OriginInfo(
            cat = OriginInfoCategory(1u),
            type = OriginInfoType(1u),
            details = null,
            original = null
        )

        // Data class equality
        assertEquals(originInfo1.cat.category, originInfo2.cat.category)
        assertEquals(originInfo1.type.infoType, originInfo2.type.infoType)
    }

    @Test
    fun testOriginInfosTypeAlias() {
        // Test that OriginInfos works as an array type alias
        val infos: OriginInfos = arrayOf(
            OriginInfo(OriginInfoCategory(1u), OriginInfoType(1u), null, null),
            OriginInfo(OriginInfoCategory(2u), OriginInfoType(2u), null, null)
        )

        assertEquals(2, infos.size)
        assertEquals(1u, infos[0].cat.category)
        assertEquals(2u, infos[1].cat.category)
    }
}
