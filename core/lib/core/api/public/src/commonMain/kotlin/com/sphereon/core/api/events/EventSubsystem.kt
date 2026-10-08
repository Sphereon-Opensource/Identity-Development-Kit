/*
 * Copyright (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.core.api.events

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Extensible subsystem identifier using a value class.
 *
 * Identifies which system/module emitted the event.
 * This design allows EDK and VDX to define their own subsystems.
 *
 * Example:
 * ```kotlin
 * // IDK defaults
 * object EventSubsystems {
 *     val CRYPTO = EventSubsystem("crypto")
 * }
 *
 * // EDK can extend:
 * object EdkEventSubsystems {
 *     val PARTY = EventSubsystem("party")
 *     val AUTHZ = EventSubsystem("authz")
 * }
 *
 * // VDX can extend:
 * object VdxEventSubsystems {
 *     val RESOURCE = EventSubsystem("resource")
 *     val BOOKING = EventSubsystem("booking")
 * }
 * ```
 */
@Serializable
@JvmInline
value class EventSubsystem(
    val value: String,
) {
    /**
     * Check if this subsystem matches a pattern.
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
 * IDK default subsystems.
 * EDK and VDX can define their own objects with additional subsystems.
 */
object EventSubsystems {
    // Crypto subsystems
    val CRYPTO = EventSubsystem("crypto")
    val KMS = EventSubsystem("kms")

    // Mobile credential subsystems
    val MDOC = EventSubsystem("mdoc")
    val SDJWT = EventSubsystem("sdjwt")

    // Protocol subsystems
    val OAUTH = EventSubsystem("oauth")
    val OID4VP = EventSubsystem("oid4vp")
    val OID4VCI = EventSubsystem("oid4vci")

    // Infrastructure subsystems
    val HTTP = EventSubsystem("http")
    val SESSION = EventSubsystem("session")
    val EVENTS = EventSubsystem("events")

    // Generic/custom
    val CUSTOM = EventSubsystem("custom")
}
