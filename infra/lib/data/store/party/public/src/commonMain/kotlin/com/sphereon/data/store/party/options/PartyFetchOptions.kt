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

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

/**
 * Options for controlling which associations to fetch when loading parties.
 *
 * By default, only the core party data is loaded. Use these options to
 * include related entities in a single query for better performance.
 *
 * Usage:
 * ```kotlin
 * // Load only core party data
 * PartyFetchOptions.MINIMAL
 *
 * // Load party with owner details
 * PartyFetchOptions.WITH_OWNER
 *
 * // Custom selection
 * PartyFetchOptions(
 *     includeOwner = true,
 *     includeIdentities = true
 * )
 * ```
 */
@JsExportCompat
@Serializable
data class PartyFetchOptions
    @JvmOverloads
    constructor(
        /** Include the owner party details */
        @SerialName("includeOwner")
        val includeOwner: Boolean = false,
        /** Include all identities associated with this party */
        @SerialName("includeIdentities")
        val includeIdentities: Boolean = false,
        /** Options for loading identities (only used if includeIdentities is true) */
        @SerialName("identityOptions")
        val identityOptions: IdentityFetchOptions = IdentityFetchOptions.MINIMAL,
    ) {
        companion object {
            /** Load only core party data (default) */
            val MINIMAL = PartyFetchOptions()

            /** Load party with owner */
            val WITH_OWNER = PartyFetchOptions(includeOwner = true)

            /** Load party with identities (minimal) */
            val WITH_IDENTITIES = PartyFetchOptions(includeIdentities = true)

            /** Load party with identities and their identity identifiers */
            val WITH_FULL_IDENTITIES =
                PartyFetchOptions(
                    includeIdentities = true,
                    identityOptions = IdentityFetchOptions.WITH_IDENTIFIERS,
                )

            /** Load everything */
            val FULL =
                PartyFetchOptions(
                    includeOwner = true,
                    includeIdentities = true,
                    identityOptions = IdentityFetchOptions.FULL,
                )
        }

        /** Builder method to include owner */
        fun withOwner() = copy(includeOwner = true)

        /** Builder method to include identities */
        fun withIdentities(options: IdentityFetchOptions = IdentityFetchOptions.MINIMAL) = copy(includeIdentities = true, identityOptions = options)

        /** Builder method to include identities with identity identifiers */
        fun withFullIdentities() =
            copy(
                includeIdentities = true,
                identityOptions = IdentityFetchOptions.WITH_IDENTIFIERS,
            )
    }
