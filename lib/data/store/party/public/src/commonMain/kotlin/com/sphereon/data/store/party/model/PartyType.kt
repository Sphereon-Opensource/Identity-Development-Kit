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

package com.sphereon.data.store.party.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * The type of party in the system.
 *
 * This is an extensible type - core types (NATURAL_PERSON, ORGANIZATION) are predefined,
 * while downstream projects can add additional types for their specific use cases.
 *
 * Usage:
 * ```kotlin
 * // Use predefined constants
 * val type = PartyType.NATURAL_PERSON
 *
 * // Create custom types
 * val customType = PartyType("resource")
 *
 * // Compare
 * if (type == PartyType.ORGANIZATION) { ... }
 *
 * // Use in when (non-exhaustive)
 * when (type) {
 *     PartyType.NATURAL_PERSON -> ...
 *     PartyType.ORGANIZATION -> ...
 *     else -> ... // handle custom types
 * }
 * ```
 */
@Serializable
@JvmInline
value class PartyType(
    val value: String,
) {
    companion object {
        /** A human individual */
        val NATURAL_PERSON = PartyType("natural_person")

        /** A company, institution, or other legal entity */
        val ORGANIZATION = PartyType("organization")
    }

    override fun toString(): String = value
}
