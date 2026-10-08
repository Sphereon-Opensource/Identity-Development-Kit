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
 */

package com.sphereon.oauth2.common.config

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Which client-registry source an authorization server treats as authoritative when the same
 * client id exists both in persistent storage and in configuration.
 *
 * The choice applies to every read path of the registry at once: client lookup, credential
 * verification, and listing all resolve the same source first, so an operator never sees a client
 * listed from one source while it authenticates against the other.
 */
@JsExportCompat
@Serializable
enum class ClientRegistrySourcePrecedence {
    /**
     * Persistent client registrations win; configuration is the secondary source.
     *
     * A tenant authorization server administers its clients through the registry API, so its
     * durable rows are the record of truth and configuration only fills gaps.
     */
    @SerialName("persistence-primary")
    PERSISTENCE_PRIMARY,

    /**
     * Configured client registrations win; persistent storage is the secondary source.
     *
     * The platform authorization server is provisioned from deployment configuration, with every
     * secret resolved through the secrets abstraction, so configuration is its record of truth.
     */
    @SerialName("configuration-primary")
    CONFIGURATION_PRIMARY,
    ;

    /** Value an operator writes in configuration for this precedence. */
    val configValue: String
        get() =
            when (this) {
                PERSISTENCE_PRIMARY -> "persistence-primary"
                CONFIGURATION_PRIMARY -> "configuration-primary"
            }

    companion object {
        /** Parses a configured value, accepting the kebab-case form and the enum constant name. */
        fun fromConfigValue(value: String?): ClientRegistrySourcePrecedence? {
            val normalized = value?.trim()?.lowercase()?.replace('_', '-') ?: return null
            return entries.firstOrNull { it.configValue == normalized }
        }
    }
}
