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

package com.sphereon.data.store.party.options

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Options for controlling which associations to fetch when loading tenants.
 *
 * By default, only the core tenant data is loaded. Use these options to
 * include aggregate data or related entities.
 *
 * Usage:
 * ```kotlin
 * // Load only core tenant data
 * TenantFetchOptions.MINIMAL
 *
 * // Load tenant with identity count
 * TenantFetchOptions.WITH_COUNTS
 *
 * // Custom selection
 * TenantFetchOptions(
 *     includeIdentityCount = true,
 *     includeOwnerParty = true
 * )
 * ```
 */
@Serializable
data class TenantFetchOptions(
    /** Include count of identities in this tenant */
    @SerialName("includeIdentityCount")
    val includeIdentityCount: Boolean = false,
    /** Include the owner party details */
    @SerialName("includeOwnerParty")
    val includeOwnerParty: Boolean = false,
) {
    companion object {
        /** Load only core tenant data (default) */
        val MINIMAL = TenantFetchOptions()

        /** Load tenant with aggregate counts */
        val WITH_COUNTS = TenantFetchOptions(includeIdentityCount = true)

        /** Load tenant with owner party */
        val WITH_OWNER = TenantFetchOptions(includeOwnerParty = true)

        /** Load everything */
        val FULL =
            TenantFetchOptions(
                includeIdentityCount = true,
                includeOwnerParty = true,
            )
    }

    /** Builder method to include identity count */
    fun withIdentityCount() = copy(includeIdentityCount = true)

    /** Builder method to include owner party */
    fun withOwnerParty() = copy(includeOwnerParty = true)
}
