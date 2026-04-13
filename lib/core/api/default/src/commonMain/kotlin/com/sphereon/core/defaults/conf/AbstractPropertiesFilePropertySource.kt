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

package com.sphereon.core.defaults.conf

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.DefaultProtectionKeyParser
import com.sphereon.core.api.conf.MapPropertySource
import com.sphereon.core.api.conf.PropertyKeyNormalizerImpl
import com.sphereon.core.api.conf.ProtectedMutableMapPropertySource
import com.sphereon.core.api.conf.ProtectionKeyParser
import com.sphereon.core.api.conf.readClasspathResource
import com.sphereon.core.api.conf.readPropertiesFromPath
import com.sphereon.core.compat.readBrowserStorageItem
import com.sphereon.di.Order
import kotlinx.io.files.Path
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

private const val STORAGE_KEY_PREFIX = "sphereon.config."
private const val DEFAULT_PROFILE = "default"
private const val PROPERTIES_EXTENSION = ".properties"

/**
 * Parses a properties-format string (key=value lines) into a map.
 * Used when loading properties from browser localStorage instead of the filesystem.
 */
private fun parsePropertiesString(data: String): Map<String, Any> {
    val commentPrefixes = listOf("#", "!", ";")
    return data
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && commentPrefixes.none { prefix -> it.startsWith(prefix) } }
        .mapNotNull { line ->
            val idx = line.indexOfFirst { it == '=' || it == ':' }
            if (idx > 0) {
                line.substring(0, idx).trim() to line.substring(idx + 1).trim()
            } else {
                null
            }
        }.toMap()
}

/**
 * Loads properties from browser localStorage using the same key convention as file paths.
 *
 * Storage keys follow the pattern: `sphereon.config.{filePrefix}` and
 * `sphereon.config.{filePrefix}-{profile}`. Values are stored in `.properties` format
 * (key=value lines), the same format used for file-based property sources.
 */
private fun loadPropertiesFromBrowserStorage(
    profile: String,
    filePrefix: String,
    subPath: String?,
    normalize: Boolean = false,
): Map<String, Any> {
    val keyNormalizer =
        if (normalize) {
            PropertyKeyNormalizerImpl.Default
        } else {
            null
        }
    val storageKeyBase = STORAGE_KEY_PREFIX + (subPath?.let { "$it/" } ?: "") + filePrefix
    val properties = mutableMapOf<String, Any>()

    // Load base properties
    readBrowserStorageItem(storageKeyBase)?.let { data ->
        val parsed = parsePropertiesString(data)
        properties.putAll(
            if (keyNormalizer != null) {
                parsed.mapKeys { (k, _) -> keyNormalizer.normalize(k) }
            } else {
                parsed
            }
        )
    }

    // Load profile-specific properties (override base)
    if (profile != DEFAULT_PROFILE) {
        readBrowserStorageItem("$storageKeyBase-$profile")?.let { data ->
            val parsed = parsePropertiesString(data)
            properties.putAll(
                if (keyNormalizer != null) {
                    parsed.mapKeys { (k, _) -> keyNormalizer.normalize(k) }
                } else {
                    parsed
                }
            )
        }
    }

    return properties
}

/**
 * Abstract base class for property sources that load properties from `.properties` files.
 *
 * This class handles:
 * - Loading base properties files (e.g., `application.properties`, `tenant.properties`)
 * - Loading profile-specific overrides (e.g., `application-production.properties`)
 * - Merging profile-specific properties over base properties
 *
 * In browser environments where filesystem access is unavailable, properties are loaded
 * from browser localStorage instead, using keys like `sphereon.config.{filePrefix}`.
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
    subPath: String? = null,
) : MapPropertySource(
        name = name,
        source = loadProperties(configLocation, profile, filePrefix, subPath),
        order = Order.LOW.orderValue,
    ) {
    companion object {
        private val keyNormalizer = PropertyKeyNormalizerImpl.Default

        private fun normalizeKeys(properties: Map<String, Any>): Map<String, Any> = properties.mapKeys { (key, _) -> keyNormalizer.normalize(key) }

        fun loadProperties(
            configLocation: Path,
            profile: String,
            filePrefix: String,
            subPath: String?,
        ): Map<String, Any> =
            try {
                loadPropertiesFromFiles(configLocation, profile, filePrefix, subPath)
            } catch (_: UnsupportedOperationException) {
                // Filesystem unavailable (browser) — fall back to browser localStorage
                loadPropertiesFromBrowserStorage(profile, filePrefix, subPath, normalize = true)
            }

        private fun loadPropertiesFromFiles(
            configLocation: Path,
            profile: String,
            filePrefix: String,
            subPath: String?,
        ): Map<String, Any> {
            val basePath =
                if (subPath != null) {
                    subPath.split("/").fold(configLocation) { parent, segment ->
                        if (segment.isNotEmpty()) {
                            Path(parent, segment)
                        } else {
                            parent
                        }
                    }
                } else {
                    configLocation
                }

            val properties = mutableMapOf<String, Any>()

            val baseFile = Path(basePath, "$filePrefix$PROPERTIES_EXTENSION")
            val baseProps = readPropertiesFromPath(baseFile)
            properties.putAll(normalizeKeys(baseProps))

            if (profile != DEFAULT_PROFILE) {
                val profileFile = Path(basePath, "$filePrefix-$profile$PROPERTIES_EXTENSION")
                properties.putAll(normalizeKeys(readPropertiesFromPath(profileFile)))
            }

            // Classpath fallback (only if no filesystem base file found)
            if (baseProps.isEmpty()) {
                readClasspathResource("$filePrefix$PROPERTIES_EXTENSION")?.let { content ->
                    properties.putAll(normalizeKeys(parsePropertiesString(content)))
                }
                if (profile != DEFAULT_PROFILE) {
                    readClasspathResource("$filePrefix-$profile$PROPERTIES_EXTENSION")?.let { content ->
                        properties.putAll(normalizeKeys(parsePropertiesString(content)))
                    }
                }
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
 * In browser environments where filesystem access is unavailable, properties are loaded
 * from browser localStorage instead, using keys like `sphereon.config.{filePrefix}`.
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
    keyParser: ProtectionKeyParser = DefaultProtectionKeyParser,
) : ProtectedMutableMapPropertySource(
        sourceName = name,
        sourceLevel = sourceLevel,
        order = Order.LOW.orderValue,
        keyParser = keyParser,
    ) {
    init {
        val rawProperties = loadRawProperties(configLocation, profile, filePrefix, subPath)
        for ((key, value) in rawProperties) {
            addProperty(key, value)
        }
    }

    companion object {
        fun loadRawProperties(
            configLocation: Path,
            profile: String,
            filePrefix: String,
            subPath: String?,
        ): Map<String, Any> =
            try {
                loadRawPropertiesFromFiles(configLocation, profile, filePrefix, subPath)
            } catch (_: UnsupportedOperationException) {
                // Filesystem unavailable (browser) — fall back to browser localStorage
                loadPropertiesFromBrowserStorage(profile, filePrefix, subPath, normalize = false)
            }

        private fun loadRawPropertiesFromFiles(
            configLocation: Path,
            profile: String,
            filePrefix: String,
            subPath: String?,
        ): Map<String, Any> {
            val basePath =
                if (subPath != null) {
                    subPath.split("/").fold(configLocation) { parent, segment ->
                        if (segment.isNotEmpty()) {
                            Path(parent, segment)
                        } else {
                            parent
                        }
                    }
                } else {
                    configLocation
                }

            val properties = mutableMapOf<String, Any>()

            val baseFile = Path(basePath, "$filePrefix$PROPERTIES_EXTENSION")
            val baseProps = readPropertiesFromPath(baseFile)
            properties.putAll(baseProps)

            if (profile != DEFAULT_PROFILE) {
                val profileFile = Path(basePath, "$filePrefix-$profile$PROPERTIES_EXTENSION")
                properties.putAll(readPropertiesFromPath(profileFile))
            }

            // Classpath fallback (only if no filesystem base file found)
            if (baseProps.isEmpty()) {
                readClasspathResource("$filePrefix$PROPERTIES_EXTENSION")?.let { content ->
                    properties.putAll(parsePropertiesString(content))
                }
                if (profile != DEFAULT_PROFILE) {
                    readClasspathResource("$filePrefix-$profile$PROPERTIES_EXTENSION")?.let { content ->
                        properties.putAll(parsePropertiesString(content))
                    }
                }
            }

            return properties
        }
    }
}
