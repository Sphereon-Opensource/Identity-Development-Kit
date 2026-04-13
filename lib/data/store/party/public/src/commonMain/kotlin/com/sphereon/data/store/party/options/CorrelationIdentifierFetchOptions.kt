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
 * Options for controlling which extensions to fetch when loading correlation identifiers.
 *
 * By default, only the core identifier data is loaded. Use these options to
 * include extension data based on identifier type.
 *
 * Usage:
 * ```kotlin
 * // Load only core identifier data
 * CorrelationIdentifierFetchOptions.MINIMAL
 *
 * // Load identifier with X.509 certificate data
 * CorrelationIdentifierFetchOptions.WITH_X509
 *
 * // Load everything
 * CorrelationIdentifierFetchOptions.FULL
 * ```
 */
@Serializable
data class CorrelationIdentifierFetchOptions(
    /** Include X.509 certificate extension data */
    @SerialName("includeX509Extension")
    val includeX509Extension: Boolean = false,
    /** Include registration extension data */
    @SerialName("includeRegistrationExtension")
    val includeRegistrationExtension: Boolean = false,
    /** Include electronic address extension data */
    @SerialName("includeElectronicExtension")
    val includeElectronicExtension: Boolean = false,
) {
    companion object {
        /** Load only core identifier data (default) */
        val MINIMAL = CorrelationIdentifierFetchOptions()

        /** Load identifier with X.509 extension */
        val WITH_X509 = CorrelationIdentifierFetchOptions(includeX509Extension = true)

        /** Load identifier with registration extension */
        val WITH_REGISTRATION = CorrelationIdentifierFetchOptions(includeRegistrationExtension = true)

        /** Load identifier with electronic extension */
        val WITH_ELECTRONIC = CorrelationIdentifierFetchOptions(includeElectronicExtension = true)

        /** Load identifier with all extensions */
        val FULL =
            CorrelationIdentifierFetchOptions(
                includeX509Extension = true,
                includeRegistrationExtension = true,
                includeElectronicExtension = true,
            )
    }

    /** Builder method to include X.509 extension */
    fun withX509Extension() = copy(includeX509Extension = true)

    /** Builder method to include registration extension */
    fun withRegistrationExtension() = copy(includeRegistrationExtension = true)

    /** Builder method to include electronic extension */
    fun withElectronicExtension() = copy(includeElectronicExtension = true)

    /** Builder method to include all extensions */
    fun withAllExtensions() =
        copy(
            includeX509Extension = true,
            includeRegistrationExtension = true,
            includeElectronicExtension = true,
        )
}
