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

package com.sphereon.ui.prompt.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class PromptIdTest {

    @Test
    fun randomGeneratesUniqueIds() {
        val id1 = PromptId.random()
        val id2 = PromptId.random()

        assertNotEquals(id1, id2)
    }

    @Test
    fun fromStringCreatesIdWithValue() {
        val value = "test-id-123"
        val id = PromptId.fromString(value)

        assertEquals(value, id.value)
        assertEquals(value, id.toString())
    }

    @Test
    fun equalityWorksCorrectly() {
        val value = "test-id"
        val id1 = PromptId(value)
        val id2 = PromptId(value)
        val id3 = PromptId("different-id")

        assertEquals(id1, id2)
        assertNotEquals(id1, id3)
    }

    @Test
    fun toStringReturnsValue() {
        val value = "my-prompt-id"
        val id = PromptId(value)

        assertEquals(value, id.toString())
    }
}
