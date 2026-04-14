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

package com.sphereon.core.api.conf

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * A ConfigBinder that resolves properties with module/service/command overlay.
 *
 * For a given [configSuffix] (e.g., "http.client"), it walks the prefixes generated
 * by [CommandConfigScope.prefixesFor] from least-specific to most-specific, collecting
 * properties from each level and merging them so that more specific values override
 * less specific ones at the individual property level.
 *
 * This works ON TOP OF the existing APP/TENANT/PRINCIPAL hierarchy. The [resolver]
 * is typically created from the `PrincipalConfigService` which already cascades through
 * TENANT and APP parent sources. The command scope adds an additional dimension of
 * prefix-based layering.
 *
 * Example for scope(kms, keys, get) and suffix "http.client":
 * ```
 * http.client.timeout.connect.ms=30000              → global default
 * cmd.kms.default.default.http.client.timeout.connect.ms=10000  → module override
 * cmd.kms.keys.get.http.client.logging.enabled=false            → command override
 * ```
 * Resolved: timeout.connect.ms=10000, logging.enabled=false (rest from global)
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CommandScopedConfigBinder", exact = true)
class CommandScopedConfigBinder(
    private val resolver: PropertyResolver,
    private val scope: CommandConfigScope,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        },
    private val mergeStrategy: JsonMergeStrategy = JsonMergeStrategy.DEEP_MERGE_REPLACE_ARRAYS,
) {
    private val keyNormalizer = PropertyKeyNormalizerImpl.Default

    /**
     * Resolve a typed config by merging properties from all scope levels.
     *
     * Properties from more-specific scopes override less-specific ones at the
     * individual key level (flat map merge), then the merged result is bound
     * to the target type via [DefaultConfigBinder].
     *
     * @param configSuffix The config suffix (e.g., "http.client", "logging.policy")
     * @param serializer The serializer for the target type
     * @return The merged config, or null if no properties found at any scope
     */
    fun <T> getConfig(
        configSuffix: String,
        serializer: KSerializer<T>,
    ): T? {
        val merged = collectMergedProperties(configSuffix)
        if (merged.isEmpty()) {
            return null
        }
        return bindFromMergedProperties(merged, serializer)
    }

    /**
     * Resolve a required typed config. Throws if no properties found at any scope.
     */
    fun <T> getRequiredConfig(
        configSuffix: String,
        serializer: KSerializer<T>,
    ): T =
        getConfig(configSuffix, serializer)
            ?: error(
                "Required config not found for suffix '$configSuffix' at command scope $scope",
            )

    /**
     * Resolve a typed config as an [IdkResult].
     */
    fun <T> getConfigResult(
        configSuffix: String,
        serializer: KSerializer<T>,
    ): IdkResult<T, IdkError> {
        val merged = collectMergedProperties(configSuffix)
        if (merged.isEmpty()) {
            return com.sphereon.core.api
                .Err(ConfigErrors.propertyNotFound(configSuffix))
        }
        return try {
            val result =
                bindFromMergedProperties(merged, serializer)
                    ?: return com.sphereon.core.api
                        .Err(ConfigErrors.propertyNotFound(configSuffix))
            com.sphereon.core.api
                .Ok(result)
        } catch (expected: Exception) {
            com.sphereon.core.api.Err(
                ConfigErrors.bindError(
                    prefix = configSuffix,
                    expectedType = serializer.descriptor.serialName,
                    reason = expected.message ?: "unknown error",
                    path = configSuffix,
                    receivedValue = merged.toString(),
                ),
            )
        }
    }

    /**
     * Collect and merge properties from all scope-level prefixes.
     * Properties from more-specific scopes overwrite less-specific ones.
     */
    private fun collectMergedProperties(configSuffix: String): Map<String, Any> {
        val prefixes = scope.prefixesFor(configSuffix) // least-specific first
        val merged = mutableMapOf<String, Any>()
        for (prefix in prefixes) {
            val normalizedPrefix = keyNormalizer.normalize(prefix)
            val props = resolver.getSubProperties(setOf(normalizedPrefix), stripPrefix = true)
            merged.putAll(props) // more-specific values overwrite less-specific
        }
        return merged
    }

    /**
     * Bind a flat merged property map to a typed object.
     *
     * Uses a synthetic root prefix to bridge into [DefaultConfigBinder]'s
     * prefix-based binding. The temporary resolver only contains the merged
     * properties, so there's no collision risk.
     */
    private fun <T> bindFromMergedProperties(
        properties: Map<String, Any>,
        serializer: KSerializer<T>,
    ): T? {
        val rootedProps = properties.mapKeys { (k, _) -> "$BIND_ROOT.$k" }
        val source =
            MutableMapPropertySource("command-scoped-merged").apply {
                addProperties(rootedProps)
            }
        val tempSources = DefaultPropertySources(mutableListOf<PropertySource<*>>(source))
        val tempResolver = PropertySourcesPropertyResolver(tempSources)
        return DefaultConfigBinder(tempResolver, json, mergeStrategy).getConfig(BIND_ROOT, serializer)
    }

    companion object {
        private const val BIND_ROOT = "root"
    }
}

/**
 * Reified extension for type-safe config resolution.
 */
inline fun <reified T> CommandScopedConfigBinder.getConfig(configSuffix: String): T? = getConfig(configSuffix, serializer())

/**
 * Reified extension for required config resolution.
 */
inline fun <reified T> CommandScopedConfigBinder.getRequiredConfig(configSuffix: String): T = getRequiredConfig(configSuffix, serializer())

/**
 * Reified extension for config resolution as IdkResult.
 */
inline fun <reified T> CommandScopedConfigBinder.getConfigResult(configSuffix: String): IdkResult<T, IdkError> = getConfigResult(configSuffix, serializer())

/**
 * Create a [CommandScopedConfigBinder] from a [ConfigService].
 *
 * The resolver is created from the config service's property sources (including parents),
 * with optional interpolation support.
 */
fun ConfigService.toCommandScopedBinder(
    scope: CommandConfigScope,
    json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        },
    mergeStrategy: JsonMergeStrategy = JsonMergeStrategy.DEEP_MERGE_REPLACE_ARRAYS,
    interpolate: Boolean = true,
): CommandScopedConfigBinder {
    val sources = getPropertySources(includeParents = true)
    val resolver =
        PropertyResolverFactory.create(
            propertySources = sources,
            interpolator =
                if (interpolate) {
                    DefaultPropertyInterpolator()
                } else {
                    null
                },
        )
    return CommandScopedConfigBinder(resolver, scope, json, mergeStrategy)
}

/**
 * Create a [CommandScopedConfigBinder] from a [ContextConfig].
 *
 * Uses the principal-level config service which cascades through TENANT and APP
 * parent sources, giving the full APP→TENANT→PRINCIPAL resolution.
 */
fun ContextConfig.toCommandScopedBinder(
    scope: CommandConfigScope,
    json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        },
    mergeStrategy: JsonMergeStrategy = JsonMergeStrategy.DEEP_MERGE_REPLACE_ARRAYS,
    interpolate: Boolean = true,
): CommandScopedConfigBinder = principal.toCommandScopedBinder(scope, json, mergeStrategy, interpolate)
