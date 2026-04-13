package com.sphereon.conf.theme.compose

import androidx.compose.runtime.Immutable
import com.sphereon.conf.theme.core.model.ResolvedTheme
import com.sphereon.conf.theme.core.token.TokenKeyConstants

/**
 * Cubic bezier easing specification parsed from a token value like "cubic-bezier(0.2, 0.0, 0, 1.0)".
 */
@Immutable
data class EasingSpec(val x1: Float, val y1: Float, val x2: Float, val y2: Float)

/**
 * Structured motion tokens mapped from a resolved theme.
 */
@Immutable
data class MotionTokens(
    val durationShort1: Int = 50,
    val durationShort2: Int = 100,
    val durationShort3: Int = 150,
    val durationShort4: Int = 200,
    val durationMedium1: Int = 250,
    val durationMedium2: Int = 300,
    val durationMedium3: Int = 350,
    val durationMedium4: Int = 400,
    val durationLong1: Int = 450,
    val durationLong2: Int = 500,
    val durationLong3: Int = 550,
    val durationLong4: Int = 600,
    val easingStandard: EasingSpec = EasingSpec(0.2f, 0.0f, 0f, 1.0f),
    val easingStandardDecelerate: EasingSpec = EasingSpec(0f, 0f, 0f, 1.0f),
    val easingStandardAccelerate: EasingSpec = EasingSpec(0.3f, 0f, 1f, 1f),
    val easingEmphasized: EasingSpec = EasingSpec(0.2f, 0.0f, 0f, 1.0f),
    val easingEmphasizedDecelerate: EasingSpec = EasingSpec(0.05f, 0.7f, 0.1f, 1.0f),
    val easingEmphasizedAccelerate: EasingSpec = EasingSpec(0.3f, 0.0f, 0.8f, 0.15f),
    val easingLinear: EasingSpec = EasingSpec(0f, 0f, 1f, 1f),
    val themeTransitionDuration: Int = 500,
    val themeTransitionEasing: EasingSpec = EasingSpec(0.2f, 0.0f, 0f, 1.0f),
) {
    companion object {
        val Default = MotionTokens()

        /** All durations set to 0 for reduced-motion preference. */
        val Zero = MotionTokens(
            durationShort1 = 0,
            durationShort2 = 0,
            durationShort3 = 0,
            durationShort4 = 0,
            durationMedium1 = 0,
            durationMedium2 = 0,
            durationMedium3 = 0,
            durationMedium4 = 0,
            durationLong1 = 0,
            durationLong2 = 0,
            durationLong3 = 0,
            durationLong4 = 0,
            themeTransitionDuration = 0,
        )
    }
}

/**
 * Maps resolved theme tokens to [MotionTokens].
 */
object MotionTokenMapper {

    fun toMotionTokens(theme: ResolvedTheme): MotionTokens {
        val tokens = theme.tokens
        return MotionTokens(
            durationShort1 = tokens[TokenKeyConstants.MOTION_DURATION_SHORT1]?.parseDurationMs() ?: 50,
            durationShort2 = tokens[TokenKeyConstants.MOTION_DURATION_SHORT2]?.parseDurationMs() ?: 100,
            durationShort3 = tokens[TokenKeyConstants.MOTION_DURATION_SHORT3]?.parseDurationMs() ?: 150,
            durationShort4 = tokens[TokenKeyConstants.MOTION_DURATION_SHORT4]?.parseDurationMs() ?: 200,
            durationMedium1 = tokens[TokenKeyConstants.MOTION_DURATION_MEDIUM1]?.parseDurationMs() ?: 250,
            durationMedium2 = tokens[TokenKeyConstants.MOTION_DURATION_MEDIUM2]?.parseDurationMs() ?: 300,
            durationMedium3 = tokens[TokenKeyConstants.MOTION_DURATION_MEDIUM3]?.parseDurationMs() ?: 350,
            durationMedium4 = tokens[TokenKeyConstants.MOTION_DURATION_MEDIUM4]?.parseDurationMs() ?: 400,
            durationLong1 = tokens[TokenKeyConstants.MOTION_DURATION_LONG1]?.parseDurationMs() ?: 450,
            durationLong2 = tokens[TokenKeyConstants.MOTION_DURATION_LONG2]?.parseDurationMs() ?: 500,
            durationLong3 = tokens[TokenKeyConstants.MOTION_DURATION_LONG3]?.parseDurationMs() ?: 550,
            durationLong4 = tokens[TokenKeyConstants.MOTION_DURATION_LONG4]?.parseDurationMs() ?: 600,
            easingStandard = tokens[TokenKeyConstants.MOTION_EASING_STANDARD]?.parseEasing()
                ?: EasingSpec(0.2f, 0.0f, 0f, 1.0f),
            easingStandardDecelerate = tokens[TokenKeyConstants.MOTION_EASING_STANDARD_DECELERATE]?.parseEasing()
                ?: EasingSpec(0f, 0f, 0f, 1.0f),
            easingStandardAccelerate = tokens[TokenKeyConstants.MOTION_EASING_STANDARD_ACCELERATE]?.parseEasing()
                ?: EasingSpec(0.3f, 0f, 1f, 1f),
            easingEmphasized = tokens[TokenKeyConstants.MOTION_EASING_EMPHASIZED]?.parseEasing()
                ?: EasingSpec(0.2f, 0.0f, 0f, 1.0f),
            easingEmphasizedDecelerate = tokens[TokenKeyConstants.MOTION_EASING_EMPHASIZED_DECELERATE]?.parseEasing()
                ?: EasingSpec(0.05f, 0.7f, 0.1f, 1.0f),
            easingEmphasizedAccelerate = tokens[TokenKeyConstants.MOTION_EASING_EMPHASIZED_ACCELERATE]?.parseEasing()
                ?: EasingSpec(0.3f, 0.0f, 0.8f, 0.15f),
            easingLinear = tokens[TokenKeyConstants.MOTION_EASING_LINEAR]?.parseEasing()
                ?: EasingSpec(0f, 0f, 1f, 1f),
            themeTransitionDuration = tokens[TokenKeyConstants.MOTION_THEME_TRANSITION_DURATION]?.parseDurationMs()
                ?: 500,
            themeTransitionEasing = tokens[TokenKeyConstants.MOTION_THEME_TRANSITION_EASING]?.parseEasing()
                ?: EasingSpec(0.2f, 0.0f, 0f, 1.0f),
        )
    }

    /**
     * Parses a duration string like "300ms" to an Int of milliseconds.
     */
    internal fun String.parseDurationMs(): Int? {
        val trimmed = trim()
        val numeric = when {
            trimmed.endsWith("ms") -> trimmed.removeSuffix("ms")
            trimmed.endsWith("s") -> {
                val seconds = trimmed.removeSuffix("s").toFloatOrNull() ?: return null
                return (seconds * 1000).toInt()
            }
            else -> trimmed
        }
        return numeric.toIntOrNull()
    }

    /**
     * Parses a cubic-bezier string like "cubic-bezier(0.2, 0.0, 0, 1.0)" to an [EasingSpec].
     */
    internal fun String.parseEasing(): EasingSpec? {
        val trimmed = trim()
        val inner = when {
            trimmed.startsWith("cubic-bezier(") && trimmed.endsWith(")") ->
                trimmed.removePrefix("cubic-bezier(").removeSuffix(")")
            else -> return null
        }
        val parts = inner.split(",").map { it.trim() }
        if (parts.size != 4) return null
        val floats = parts.mapNotNull { it.toFloatOrNull() }
        if (floats.size != 4) return null
        return EasingSpec(floats[0], floats[1], floats[2], floats[3])
    }
}
