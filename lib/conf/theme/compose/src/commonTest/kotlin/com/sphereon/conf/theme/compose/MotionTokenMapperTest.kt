/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.conf.theme.compose

import com.sphereon.conf.theme.core.defaults.SystemDefaults
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.token.TokenFlattener
import com.sphereon.conf.theme.core.token.TokenKeyConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock

class MotionTokenMapperTest {
    private fun resolvedTheme(tokens: Map<String, String> = emptyMap()) =
        ResolvedTheme(
            tokens = tokens,
            resolvedAt = Clock.System.now(),
        )

    // ===== Duration parsing =====

    @Test
    fun parseDurationMs() {
        with(MotionTokenMapper) {
            assertEquals(300, "300ms".parseDurationMs())
            assertEquals(50, "50ms".parseDurationMs())
            assertEquals(0, "0ms".parseDurationMs())
        }
    }

    @Test
    fun parseDurationSeconds() {
        with(MotionTokenMapper) {
            assertEquals(1000, "1s".parseDurationMs())
            assertEquals(500, "0.5s".parseDurationMs())
        }
    }

    @Test
    fun parseDurationInvalid() {
        with(MotionTokenMapper) {
            assertNull("abc".parseDurationMs())
            assertNull("".parseDurationMs())
        }
    }

    @Test
    fun parseDurationPlainNumber() {
        with(MotionTokenMapper) {
            assertEquals(250, "250".parseDurationMs())
        }
    }

    // ===== Easing parsing =====

    @Test
    fun parseEasingValid() {
        with(MotionTokenMapper) {
            val result = "cubic-bezier(0.2, 0.0, 0, 1.0)".parseEasing()
            assertNotNull(result)
            assertEquals(0.2f, result.x1)
            assertEquals(0.0f, result.y1)
            assertEquals(0.0f, result.x2)
            assertEquals(1.0f, result.y2)
        }
    }

    @Test
    fun parseEasingLinear() {
        with(MotionTokenMapper) {
            val result = "cubic-bezier(0, 0, 1, 1)".parseEasing()
            assertNotNull(result)
            assertEquals(0f, result.x1)
            assertEquals(0f, result.y1)
            assertEquals(1f, result.x2)
            assertEquals(1f, result.y2)
        }
    }

    @Test
    fun parseEasingInvalid() {
        with(MotionTokenMapper) {
            assertNull("not-a-bezier".parseEasing())
            assertNull("cubic-bezier(1, 2)".parseEasing())
            assertNull("".parseEasing())
        }
    }

    // ===== Mapper from theme tokens =====

    @Test
    fun mapFromSystemDefaults() {
        val tokens = TokenFlattener.merge(listOf(SystemDefaults.baseline))
        val theme = resolvedTheme(tokens)
        val motion = MotionTokenMapper.toMotionTokens(theme)

        assertEquals(50, motion.durationShort1)
        assertEquals(300, motion.durationMedium2)
        assertEquals(600, motion.durationLong4)
        assertEquals(500, motion.themeTransitionDuration)
        assertEquals(EasingSpec(0.2f, 0.0f, 0f, 1.0f), motion.easingStandard)
        assertEquals(EasingSpec(0f, 0f, 1f, 1f), motion.easingLinear)
    }

    @Test
    fun mapFallbacksForMissingTokens() {
        val motion = MotionTokenMapper.toMotionTokens(resolvedTheme())
        // Should fall back to defaults when no tokens present
        assertEquals(MotionTokens.Default, motion)
    }

    @Test
    fun mapCustomDuration() {
        val tokens = mapOf(TokenKeyConstants.MOTION_DURATION_SHORT1 to "75ms")
        val motion = MotionTokenMapper.toMotionTokens(resolvedTheme(tokens))
        assertEquals(75, motion.durationShort1)
        // Other durations should use defaults
        assertEquals(100, motion.durationShort2)
    }

    @Test
    fun mapCustomEasing() {
        val tokens = mapOf(TokenKeyConstants.MOTION_EASING_LINEAR to "cubic-bezier(0.1, 0.2, 0.3, 0.4)")
        val motion = MotionTokenMapper.toMotionTokens(resolvedTheme(tokens))
        assertEquals(EasingSpec(0.1f, 0.2f, 0.3f, 0.4f), motion.easingLinear)
    }

    // ===== Zero (reduced motion) =====

    @Test
    fun zeroMotionTokensHaveZeroDurations() {
        assertEquals(0, MotionTokens.Zero.durationShort1)
        assertEquals(0, MotionTokens.Zero.durationMedium2)
        assertEquals(0, MotionTokens.Zero.durationLong4)
        assertEquals(0, MotionTokens.Zero.themeTransitionDuration)
    }
}
