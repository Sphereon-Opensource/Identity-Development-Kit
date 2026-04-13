/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.api.events

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Extensible event category using a value class.
 *
 * Categories classify events for filtering and routing.
 * This design allows EDK and VDX to define their own categories.
 *
 * Example:
 * ```kotlin
 * // IDK defaults
 * object EventCategories {
 *     val LIFECYCLE = EventCategory("lifecycle")
 * }
 *
 * // EDK/VDX can extend:
 * object CustomEventCategories {
 *     val COMPLIANCE = EventCategory("compliance")
 *     val AUDIT = EventCategory("audit")
 * }
 * ```
 */
@Serializable
@JvmInline
value class EventCategory(
    val value: String,
) {
    /**
     * Check if this category matches a pattern.
     */
    fun matches(pattern: String): Boolean {
        if (pattern == "*" || pattern == "**") {
            return true
        }
        if (pattern == value) {
            return true
        }

        val regexPattern =
            pattern
                .replace("*", ".*")
        return value.matches(Regex(regexPattern))
    }

    override fun toString(): String = value

    companion object
}

/**
 * IDK default event categories.
 * EDK and VDX can define their own objects with additional categories.
 */
object EventCategories {
    /**
     * Lifecycle events: start/stop, create/delete, open/close
     */
    val LIFECYCLE = EventCategory("lifecycle")

    /**
     * Operation events: business operations, transactions
     */
    val OPERATION = EventCategory("operation")

    /**
     * Error events: failures, exceptions, validation errors
     */
    val ERROR = EventCategory("error")

    /**
     * Verbose events: trace, verbose logging
     */
    val VERBOSE = EventCategory("verbose")

    /**
     * Security events: authentication, authorization, audit
     */
    val SECURITY = EventCategory("security")
}
