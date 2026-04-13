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

package com.sphereon.core.defaults.conf

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.DefaultProtectionKeyParser
import com.sphereon.core.api.conf.MapPropertySource
import com.sphereon.core.api.conf.PropertyKeyNormalizerImpl
import com.sphereon.core.api.conf.ProtectedMutableMapPropertySource
import com.sphereon.core.api.conf.ProtectionKeyParser
import com.sphereon.core.api.conf.readPropertiesFromPath
import com.sphereon.di.Order
import kotlinx.io.files.Path
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Abstract base class for property sources that load properties from `.properties` files.
 *
 * This class handles:
 * - Loading base properties files (e.g., `application.properties`, `tenant.properties`)
 * - Loading profile-specific overrides (e.g., `application-production.properties`)
 * - Merging profile-specific properties over base properties
 *
 * Missing files are silently ignored - they are optional.
 *
 * Note: This version does NOT parse protection prefixes. For protection-aware loading,
 * use [AbstractProtectedPropertiesFilePropertySource] instead.
 *
 * @param name the name of this property source
 * @param configLocation the base configuration directory (e.g., `./config`)
 * @param profile the active profile (e.g., `production`, `default`)
 * @param filePrefix the prefix for property files (e.g., `application`, `tenant`, `principal`)
 * @param subPath optional subdirectory path relative to configLocation (e.g., `tenant/acme-corp`)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AbstractPropertiesFilePropertySource", exact = true)
abstract class AbstractPropertiesFilePropertySource(
    name: String,
    configLocation: Path,
    profile: String,
    filePrefix: String,
    subPath: String? = null
) : MapPropertySource(
    name = name,
    source = loadProperties(configLocation, profile, filePrefix, subPath),
    order = Order.LOW.orderValue
) {

    companion object {
        private const val DEFAULT_PROFILE = "default"
        private const val PROPERTIES_EXTENSION = ".properties"
        private val keyNormalizer = PropertyKeyNormalizerImpl()

        /**
         * Normalizes property keys so they can be looked up consistently.
         * MapPropertySource normalizes lookup keys, so source keys must also be normalized.
         */
        private fun normalizeKeys(properties: Map<String, Any>): Map<String, Any> =
            properties.mapKeys { (key, _) -> keyNormalizer.normalize(key) }

        /**
         * Loads properties from base and profile-specific files.
         *
         * Profile-specific properties override base properties.
         * All property keys are normalized for consistent lookup.
         *
         * @param configLocation the base configuration directory
         * @param profile the active profile
         * @param filePrefix the prefix for property files
         * @param subPath optional subdirectory path
         * @return merged properties map with normalized keys
         */
        fun loadProperties(
            configLocation: Path,
            profile: String,
            filePrefix: String,
            subPath: String?
        ): Map<String, Any> {
            val basePath = if (subPath != null) {
                // Build the path by splitting on forward slashes and appending each segment
                // This handles cross-platform path construction correctly
                subPath.split("/").fold(configLocation) { parent, segment ->
                    if (segment.isNotEmpty()) Path(parent, segment) else parent
                }
            } else {
                configLocation
            }

            val properties = mutableMapOf<String, Any>()

            // Load base properties (e.g., application.properties)
            val baseFile = Path(basePath, "$filePrefix$PROPERTIES_EXTENSION")
            properties.putAll(normalizeKeys(readPropertiesFromPath(baseFile)))

            // Load profile-specific properties (e.g., application-production.properties)
            // Profile overrides base properties
            if (profile != DEFAULT_PROFILE) {
                val profileFile = Path(basePath, "$filePrefix-$profile$PROPERTIES_EXTENSION")
                properties.putAll(normalizeKeys(readPropertiesFromPath(profileFile)))
            }

            return properties
        }
    }
}

/**
 * Abstract base class for property sources that load properties from `.properties` files
 * with support for protection prefixes (final., protected.).
 *
 * This class handles:
 * - Loading base properties files (e.g., `application.properties`, `tenant.properties`)
 * - Loading profile-specific overrides (e.g., `application-production.properties`)
 * - Merging profile-specific properties over base properties
 * - Parsing protection prefixes (final.db.host, protected.api.key) and storing protection metadata
 *
 * Missing files are silently ignored - they are optional.
 *
 * @param name the name of this property source
 * @param sourceLevel the configuration level (APP, TENANT, PRINCIPAL) for protection enforcement
 * @param configLocation the base configuration directory (e.g., `./config`)
 * @param profile the active profile (e.g., `production`, `default`)
 * @param filePrefix the prefix for property files (e.g., `application`, `tenant`, `principal`)
 * @param subPath optional subdirectory path relative to configLocation (e.g., `tenant/acme-corp`)
 * @param keyParser the parser for extracting protection prefixes
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("AbstractProtectedPropertiesFilePropertySource", exact = true)
abstract class AbstractProtectedPropertiesFilePropertySource(
    name: String,
    sourceLevel: ConfigLevel,
    configLocation: Path,
    profile: String,
    filePrefix: String,
    subPath: String? = null,
    keyParser: ProtectionKeyParser = DefaultProtectionKeyParser
) : ProtectedMutableMapPropertySource(
    sourceName = name,
    sourceLevel = sourceLevel,
    order = Order.LOW.orderValue,
    keyParser = keyParser
) {
    init {
        // Load properties and add them using the protection-aware addProperty method
        val rawProperties = loadRawProperties(configLocation, profile, filePrefix, subPath)
        for ((key, value) in rawProperties) {
            addProperty(key, value)
        }
    }

    companion object {
        private const val DEFAULT_PROFILE = "default"
        private const val PROPERTIES_EXTENSION = ".properties"

        /**
         * Loads raw properties from base and profile-specific files WITHOUT normalizing keys.
         * Key normalization and protection parsing is handled by [ProtectedMutableMapPropertySource.addProperty].
         *
         * @param configLocation the base configuration directory
         * @param profile the active profile
         * @param filePrefix the prefix for property files
         * @param subPath optional subdirectory path
         * @return merged raw properties map (keys not normalized)
         */
        fun loadRawProperties(
            configLocation: Path,
            profile: String,
            filePrefix: String,
            subPath: String?
        ): Map<String, Any> {
            val basePath = if (subPath != null) {
                // Build the path by splitting on forward slashes and appending each segment
                // This handles cross-platform path construction correctly
                subPath.split("/").fold(configLocation) { parent, segment ->
                    if (segment.isNotEmpty()) Path(parent, segment) else parent
                }
            } else {
                configLocation
            }

            val properties = mutableMapOf<String, Any>()

            // Load base properties (e.g., application.properties)
            val baseFile = Path(basePath, "$filePrefix$PROPERTIES_EXTENSION")
            properties.putAll(readPropertiesFromPath(baseFile))

            // Load profile-specific properties (e.g., application-production.properties)
            // Profile overrides base properties
            if (profile != DEFAULT_PROFILE) {
                val profileFile = Path(basePath, "$filePrefix-$profile$PROPERTIES_EXTENSION")
                properties.putAll(readPropertiesFromPath(profileFile))
            }

            return properties
        }
    }
}
