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
 * The type of tenant.
 *
 * This is an extensible type - core types (PLATFORM, ENTERPRISE, PARTNER, SANDBOX) are predefined,
 * while downstream projects can add additional types for their specific use cases.
 *
 * Usage:
 * ```kotlin
 * // Use predefined constants
 * val type = TenantType.PLATFORM
 *
 * // Create custom types
 * val customType = TenantType("custom")
 * ```
 */
@Serializable
@JvmInline
value class TenantType(
    val value: String,
) {
    companion object {
        /** Platform/system tenant (for multi-tenant SaaS platforms) */
        val PLATFORM = TenantType("platform")

        /** Enterprise tenant (business, company) */
        val ENTERPRISE = TenantType("enterprise")

        /** Partner tenant (external partner organization) */
        val PARTNER = TenantType("partner")

        /** Sandbox tenant (for testing and development) */
        val SANDBOX = TenantType("sandbox")
    }

    override fun toString(): String = value
}
