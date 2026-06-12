/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.conf.yaml

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.DefaultProtectionKeyParser
import com.sphereon.core.api.conf.ProtectedMutableMapPropertySource
import com.sphereon.core.api.conf.ProtectionKeyParser
import com.sphereon.core.api.conf.readClasspathResource
import com.sphereon.di.Order
import it.krzeminski.snakeyaml.engine.kmp.api.Load
import it.krzeminski.snakeyaml.engine.kmp.api.LoadSettings
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString

private const val DEFAULT_PROFILE = "default"

// Both standard YAML extensions are accepted; when both files exist, `.yml`
// loads first and `.yaml` overlays it (last write wins per key).
private val YML_EXTENSIONS = listOf(".yml", ".yaml")

/**
 * YAML property source implementation using snakeyaml-engine-kmp (multiplatform).
 *
 * Follows the same scoped-directory, profile-suffix, bare-key model as
 * [AbstractProtectedPropertiesFilePropertySource]. No `sphereon.app.*` prefixes.
 *
 * Extends [ProtectedMutableMapPropertySource] so that protection prefixes
 * (final., protected.) in YAML keys are parsed and stored as metadata via [addProperty].
 *
 * @param name source instance name (e.g., "yaml.app")
 * @param sourceLevel the configuration level (APP, TENANT, PRINCIPAL) for protection enforcement
 * @param configLocation base config directory as a [Path]
 * @param profile active profile (default: "default")
 * @param filePrefix file name prefix (e.g., "application", "tenant", "principal")
 * @param subPath optional subdirectory relative to configLocation (e.g., "tenant/acme-corp")
 * @param keyParser the parser for extracting protection prefixes
 */
open class YamlPropertySourceImpl(
    name: String,
    sourceLevel: ConfigLevel,
    configLocation: Path,
    profile: String = DEFAULT_PROFILE,
    filePrefix: String = "application",
    subPath: String? = null,
    keyParser: ProtectionKeyParser = DefaultProtectionKeyParser,
) : ProtectedMutableMapPropertySource(
        sourceName = name,
        sourceLevel = sourceLevel,
        order = Order.LOW.orderValue,
        keyParser = keyParser,
    ),
    YamlPropertySource {
    init {
        val rawProperties = loadYaml(configLocation, profile, filePrefix, subPath)
        for ((key, value) in rawProperties) {
            addProperty(key, value)
        }
    }

    companion object {
        fun loadYaml(
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
            val load = Load(LoadSettings())

            // 1. Load base file from filesystem (.yml and .yaml)
            var baseFileExists = false
            for (extension in YML_EXTENSIONS) {
                val baseFile = Path(basePath, "$filePrefix$extension")
                if (loadYamlFromPath(load, baseFile, properties)) baseFileExists = true
            }

            // 2. Load profile override from filesystem
            if (profile != DEFAULT_PROFILE) {
                for (extension in YML_EXTENSIONS) {
                    val profileFile = Path(basePath, "$filePrefix-$profile$extension")
                    loadYamlFromPath(load, profileFile, properties)
                }
            }

            // 3. Classpath fallback (only if no filesystem file found in step 1)
            if (!baseFileExists) {
                for (extension in YML_EXTENSIONS) {
                    readClasspathResource("$filePrefix$extension")?.let { content ->
                        parseYamlString(load, content, properties)
                    }
                }
                // Also try classpath profile override
                if (profile != DEFAULT_PROFILE) {
                    for (extension in YML_EXTENSIONS) {
                        readClasspathResource("$filePrefix-$profile$extension")?.let { content ->
                            parseYamlString(load, content, properties)
                        }
                    }
                }
            }

            return properties
        }

        /**
         * Attempts to load YAML from a filesystem path.
         *
         * @return true if the file existed and was loaded, false if the file does not exist
         */
        private fun loadYamlFromPath(
            load: Load,
            path: Path,
            target: MutableMap<String, Any>,
        ): Boolean {
            if (!SystemFileSystem.exists(path)) {
                return false
            }
            val content = SystemFileSystem.source(path).buffered().readString()
            parseYamlString(load, content, target)
            return true
        }

        private fun parseYamlString(
            load: Load,
            content: String,
            target: MutableMap<String, Any>,
        ) {
            @Suppress("UNCHECKED_CAST")
            val data = load.loadOne(content) as? Map<String, Any> ?: return
            flattenYaml("", data, target)
        }

        @Suppress("UNCHECKED_CAST")
        private fun flattenYaml(
            prefix: String,
            map: Map<String, Any>,
            result: MutableMap<String, Any>,
        ) {
            for ((key, value) in map) {
                val fullKey =
                    if (prefix.isEmpty()) {
                        key
                    } else {
                        "$prefix.$key"
                    }
                when (value) {
                    is Map<*, *> -> {
                        flattenYaml(fullKey, value as Map<String, Any>, result)
                    }

                    is List<*> -> {
                        value.forEachIndexed { index, item ->
                            when (item) {
                                is Map<*, *> -> {
                                    flattenYaml("$fullKey[$index]", item as Map<String, Any>, result)
                                }

                                else -> {
                                    if (item != null) {
                                        result["$fullKey[$index]"] = item
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        result[fullKey] = value
                    }
                }
            }
        }
    }
}
