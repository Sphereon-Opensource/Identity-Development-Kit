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

package com.sphereon.core.api.conf

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

enum class ConfigScope(
    scopeKey: String,
) {
    GLOBAL("global"),

    // PLUGIN,
    SERVICE("services"),
}

enum class ConfigSource(
    key: String,
) {
    BUILD("build"),
    APP("app"),
    PROFILE("profile"),
    TENANT("tenant"),
    PRINCIPAL("principal"),
}

/**
 * Configuration scope levels representing the hierarchy of configuration contexts.
 *
 * Lower level numbers indicate higher priority/scope in the hierarchy:
 * - APP (10): Application-level configuration, highest scope
 * - TENANT (20): Tenant-specific configuration
 * - PRINCIPAL (30): Principal/user-specific configuration, lowest scope
 *
 * Properties can be protected based on scope level:
 * - FINAL properties cannot be overridden at lower scopes (higher level numbers)
 * - PROTECTED properties cannot be interpolated from lower scopes
 *
 * @property key The string identifier for this level
 * @property level The numeric level for comparison (lower = higher scope)
 */
enum class ConfigLevel(
    val key: String,
    val level: Int,
) {
    @Suppress("detekt.MagicNumber")
    APP("app", 10),

    @Suppress("detekt.MagicNumber")
    TENANT("tenant", 20),

    @Suppress("detekt.MagicNumber")
    PRINCIPAL("principal", 30),
    ;

    companion object {
        /**
         * Parse a ConfigLevel from a string value.
         *
         * Accepts either the key (e.g., "app", "tenant", "principal") or
         * the enum name (e.g., "APP", "TENANT", "PRINCIPAL").
         *
         * @param value The string to parse
         * @return The matching ConfigLevel, or null if not found
         */
        fun fromString(value: String): ConfigLevel? =
            entries.find {
                it.key.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
            }

        /**
         * Get ConfigLevel by its numeric level value.
         *
         * @param level The numeric level (10, 20, or 30)
         * @return The matching ConfigLevel, or null if not found
         */
        fun fromLevel(level: Int): ConfigLevel? = entries.find { it.level == level }
    }
}

/**
 * app.(profile|default).(global|services.servicea).my-key
 * app.global.my-key
 * app.services.servicea.my-key
 *
 *
 */

@OptIn(ExperimentalObjCName::class)
@ObjCName("ConfigContext", exact = true)
@CoverageExcludedDataClass
data class ConfigContext(
    val scope: ConfigScope,
    val source: ConfigSource,
)

@OptIn(ExperimentalObjCName::class)
@ObjCName("ConfigContextHierarchy", exact = true)
@CoverageExcludedDataClass
data class ConfigContextHierarchy(
    val scope: ConfigScope,
    val sources: List<ConfigSource>,
)
