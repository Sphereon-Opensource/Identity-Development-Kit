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
 * Options for controlling which associations to fetch when loading identities.
 *
 * By default, only the core identity data is loaded. Use these options to
 * include related entities in a single query for better performance.
 *
 * Usage:
 * ```kotlin
 * // Load only core identity data
 * IdentityFetchOptions.MINIMAL
 *
 * // Load identity with all correlation identifiers
 * IdentityFetchOptions.WITH_IDENTIFIERS
 *
 * // Load everything
 * IdentityFetchOptions.FULL
 *
 * // Custom selection
 * IdentityFetchOptions(
 *     includeCorrelationIdentifiers = true,
 *     includeX509Extensions = true
 * )
 * ```
 */
@Serializable
data class IdentityFetchOptions(
    /** Include correlation identifiers (DIDs, emails, URLs, etc.) */
    @SerialName("includeCorrelationIdentifiers")
    val includeCorrelationIdentifiers: Boolean = false,
    /** Include X.509 certificate extensions on correlation identifiers */
    @SerialName("includeX509Extensions")
    val includeX509Extensions: Boolean = false,
    /** Include registration extensions on correlation identifiers */
    @SerialName("includeRegistrationExtensions")
    val includeRegistrationExtensions: Boolean = false,
    /** Include electronic address extensions on correlation identifiers */
    @SerialName("includeElectronicExtensions")
    val includeElectronicExtensions: Boolean = false,
) {
    companion object {
        /** Load only core identity data (default) */
        val MINIMAL = IdentityFetchOptions()

        /** Load identity with correlation identifiers */
        val WITH_IDENTIFIERS = IdentityFetchOptions(includeCorrelationIdentifiers = true)

        /** Load identity with identifiers and all extensions */
        val WITH_IDENTIFIERS_AND_EXTENSIONS =
            IdentityFetchOptions(
                includeCorrelationIdentifiers = true,
                includeX509Extensions = true,
                includeRegistrationExtensions = true,
                includeElectronicExtensions = true,
            )

        /** Load everything (all associations and extensions) */
        val FULL = WITH_IDENTIFIERS_AND_EXTENSIONS
    }

    /** Builder method to include correlation identifiers */
    fun withCorrelationIdentifiers() = copy(includeCorrelationIdentifiers = true)

    /** Builder method to include X.509 extensions */
    fun withX509Extensions() = copy(includeX509Extensions = true)

    /** Builder method to include registration extensions */
    fun withRegistrationExtensions() = copy(includeRegistrationExtensions = true)

    /** Builder method to include electronic extensions */
    fun withElectronicExtensions() = copy(includeElectronicExtensions = true)

    /** Builder method to include all extensions */
    fun withAllExtensions() =
        copy(
            includeX509Extensions = true,
            includeRegistrationExtensions = true,
            includeElectronicExtensions = true,
        )
}
