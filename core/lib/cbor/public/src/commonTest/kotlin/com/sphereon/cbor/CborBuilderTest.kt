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
 *
 */

package com.sphereon.cbor

import com.sphereon.cbor.dsl.cborArrayBuilder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class CborBuilderTest {
    @Test
    fun testCborBuilderWithSubject() {
        data class MyData(
            val id: Int,
        )
        val subject = MyData(42)

        val builder =
            cborArrayBuilder(subject) {
                add(1)
            }

        assertIs<CborArray<*>>(builder.build())
        assertNotNull(builder.build())
    }

    @Test
    fun testSimpleCborBuilder() {
        val item = CborUInt(42)
        val builder = SimpleCborBuilder(item)
        assertEquals(item, builder.build())
    }
}
