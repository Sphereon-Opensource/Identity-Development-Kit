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

package com.sphereon.data.store.party.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * The type of tenant.
 *
 * This is an extensible type - core types (ORGANIZATION, NATURAL_PERSON) are predefined,
 * while downstream projects can add additional types for their specific use cases.
 *
 * Usage:
 * ```kotlin
 * // Use predefined constants
 * val type = TenantType.ORGANIZATION
 *
 * // Create custom types
 * val customType = TenantType("platform")
 * ```
 */
@Serializable
@JvmInline
value class TenantType(val value: String) {
    companion object {
        /** Organization tenant (business, company) */
        val ORGANIZATION = TenantType("organization")

        /** Natural person tenant (individual) */
        val NATURAL_PERSON = TenantType("natural_person")
    }

    override fun toString(): String = value
}
