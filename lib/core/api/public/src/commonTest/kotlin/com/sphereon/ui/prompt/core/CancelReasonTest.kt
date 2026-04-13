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

package com.sphereon.ui.prompt.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CancelReasonTest {
    @Test
    fun allReasonsExist() {
        val reasons = CancelReason.entries
        assertEquals(4, reasons.size)
        assertTrue(reasons.contains(CancelReason.USER))
        assertTrue(reasons.contains(CancelReason.SUPERSEDED))
        assertTrue(reasons.contains(CancelReason.BACKGROUND))
        assertTrue(reasons.contains(CancelReason.SYSTEM))
    }

    @Test
    fun userReasonName() {
        assertEquals("USER", CancelReason.USER.name)
    }

    @Test
    fun supersededReasonName() {
        assertEquals("SUPERSEDED", CancelReason.SUPERSEDED.name)
    }

    @Test
    fun backgroundReasonName() {
        assertEquals("BACKGROUND", CancelReason.BACKGROUND.name)
    }

    @Test
    fun systemReasonName() {
        assertEquals("SYSTEM", CancelReason.SYSTEM.name)
    }

    @Test
    fun valueOfWorks() {
        assertEquals(CancelReason.USER, CancelReason.valueOf("USER"))
        assertEquals(CancelReason.SUPERSEDED, CancelReason.valueOf("SUPERSEDED"))
        assertEquals(CancelReason.BACKGROUND, CancelReason.valueOf("BACKGROUND"))
        assertEquals(CancelReason.SYSTEM, CancelReason.valueOf("SYSTEM"))
    }
}
