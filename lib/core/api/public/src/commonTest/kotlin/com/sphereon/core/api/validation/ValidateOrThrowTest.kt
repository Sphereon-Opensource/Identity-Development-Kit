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

package com.sphereon.core.api.validation

import io.konform.validation.Validation
import io.konform.validation.constraints.notBlank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class ValidateOrThrowTest {
    private data class Window(val name: String, val from: Int, val until: Int)

    private val validateWindow: Validation<Window> = Validation {
        Window::name { notBlank() hint "must not be blank" }
        constrain("must not end before it starts") { it.until >= it.from }
    }

    @Test
    fun shouldReturnTheReceiverItselfWhenTheValidationPasses() {
        val window = Window("summer", 1, 2)

        assertSame(window, window.validateOrThrow(validateWindow))
    }

    @Test
    fun shouldNameTheFailingFieldWithoutItsLeadingDot() {
        val failure = assertFailsWith<IllegalArgumentException> { Window(" ", 1, 2).validateOrThrow(validateWindow) }

        assertEquals("name: must not be blank", failure.message)
    }

    @Test
    fun shouldNameARuleOnTheWholeReceiverAsValue() {
        val failure = assertFailsWith<IllegalArgumentException> { Window("summer", 2, 1).validateOrThrow(validateWindow) }

        assertEquals("value: must not end before it starts", failure.message)
    }

    @Test
    fun shouldReportEveryFailureInOneMessage() {
        val failure = assertFailsWith<IllegalArgumentException> { Window("", 2, 1).validateOrThrow(validateWindow) }

        assertEquals(
            setOf("name: must not be blank", "value: must not end before it starts"),
            failure.message.orEmpty().split("; ").toSet(),
        )
    }
}
