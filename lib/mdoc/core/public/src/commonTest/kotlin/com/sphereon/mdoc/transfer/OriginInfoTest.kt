/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.mdoc.transfer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OriginInfoTest {
    @Test
    fun originInfoCategory_and_type_are_domain_wrappers() {
        assertEquals(1u, OriginInfoCategory(1u).category)
        assertEquals("5", OriginInfoCategory(5u).toString())
        assertEquals(2u, OriginInfoType(2u).infoType)
        assertEquals("7", OriginInfoType(7u).toString())
    }

    @Test
    fun originInfoDetails_delegates_to_map() {
        val details = OriginInfoDetails(mapOf("key1" to "value1", "key2" to null))

        assertEquals(2, details.size)
        assertTrue(details.containsKey("key1"))
        assertNull(details["key2"])
        assertEquals("domain", OriginInfoDetails.DOMAIN)
    }

    @Test
    fun originInfo_exposes_fields_and_labels() {
        val details = OriginInfoDetails(mapOf("domain" to "example.com"))
        val originInfo =
            OriginInfo(
                cat = OriginInfoCategory(1u),
                type = OriginInfoType(2u),
                details = details,
                original = null,
            )

        assertEquals(1u, originInfo.cat.category)
        assertEquals(2u, originInfo.type.infoType)
        assertNotNull(originInfo.details)
        assertEquals("example.com", originInfo.details?.get("domain"))
        assertEquals("cat", OriginInfo.CAT.value)
        assertEquals("type", OriginInfo.TYPE.value)
        assertEquals("details", OriginInfo.DETAILS.value)
    }

    @Test
    fun originInfos_alias_behaves_like_array() {
        val infos: OriginInfos =
            arrayOf(
                OriginInfo(OriginInfoCategory(1u), OriginInfoType(1u), null, null),
                OriginInfo(OriginInfoCategory(2u), OriginInfoType(2u), null, null),
            )

        assertEquals(2, infos.size)
        assertEquals(2u, infos[1].cat.category)
    }
}
