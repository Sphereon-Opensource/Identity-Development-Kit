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

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Interface for binding configuration properties to typed objects.
 * Uses kotlinx.serialization for type-safe deserialization.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ConfigBinder", exact = true)
interface ConfigBinder {
    /**
     * Get a configuration object bound from properties with a given prefix.
     *
     * @param prefix The property prefix (e.g., "database" for database.host, database.port)
     * @param serializer The KSerializer for the target type
     * @return The deserialized config object, or null if prefix not found
     */
    fun <T> getConfig(
        prefix: String,
        serializer: KSerializer<T>,
    ): T?

    /**
     * Get a required configuration object.
     *
     * @param prefix The property prefix
     * @param serializer The KSerializer for the target type
     * @return The deserialized config object
     * @throws IllegalStateException if the configuration is not found
     */
    fun <T> getRequiredConfig(
        prefix: String,
        serializer: KSerializer<T>,
    ): T

    /**
     * Get a configuration object as a result type.
     *
     * @param prefix The property prefix
     * @param serializer The KSerializer for the target type
     * @return IdkResult containing the config or an error
     */
    fun <T> getConfigResult(
        prefix: String,
        serializer: KSerializer<T>,
    ): IdkResult<T, IdkError>

    /**
     * Get a list of configuration objects from indexed properties.
     * Properties should be like: prefix[0].field, prefix[1].field, etc.
     * Or: prefix.0.field, prefix.1.field, etc.
     *
     * @param prefix The property prefix
     * @param serializer The KSerializer for the list element type
     * @return List of deserialized config objects
     */
    fun <T> getConfigList(
        prefix: String,
        serializer: KSerializer<T>,
    ): List<T>

    /**
     * Get a list configuration with diagnostics.
     *
     * In `strict` mode, any invalid entry returns an error with per-entry diagnostics.
     * In non-strict mode, invalid entries are skipped.
     */
    fun <T> getConfigListResult(
        prefix: String,
        serializer: KSerializer<T>,
        strict: Boolean = true,
    ): IdkResult<List<T>, IdkError>

    /**
     * Get a map of configuration objects from keyed properties.
     * Properties should be like: prefix.key1.field, prefix.key2.field, etc.
     *
     * @param prefix The property prefix
     * @param serializer The KSerializer for the map value type
     * @return Map of string keys to deserialized config objects
     */
    fun <T> getConfigMap(
        prefix: String,
        serializer: KSerializer<T>,
    ): Map<String, T>

    /**
     * Get a map configuration with diagnostics.
     *
     * In `strict` mode, any invalid map entry returns an error with per-entry diagnostics.
     * In non-strict mode, invalid entries are skipped.
     */
    fun <T> getConfigMapResult(
        prefix: String,
        serializer: KSerializer<T>,
        strict: Boolean = true,
    ): IdkResult<Map<String, T>, IdkError>
}

/**
 * Reified extension for type-safe config binding.
 */
inline fun <reified T> ConfigBinder.getConfig(prefix: String): T? = getConfig(prefix, serializer())

/**
 * Reified extension for required config binding.
 */
inline fun <reified T> ConfigBinder.getRequiredConfig(prefix: String): T = getRequiredConfig(prefix, serializer())

/**
 * Reified extension for config binding with result.
 */
inline fun <reified T> ConfigBinder.getConfigResult(prefix: String): IdkResult<T, IdkError> = getConfigResult(prefix, serializer())

/**
 * Reified extension for list config binding.
 */
inline fun <reified T> ConfigBinder.getConfigList(prefix: String): List<T> = getConfigList(prefix, serializer())

/**
 * Reified extension for list config binding with diagnostics.
 */
inline fun <reified T> ConfigBinder.getConfigListResult(
    prefix: String,
    strict: Boolean = true,
): IdkResult<List<T>, IdkError> = getConfigListResult(prefix, serializer(), strict)

/**
 * Reified extension for map config binding.
 */
inline fun <reified T> ConfigBinder.getConfigMap(prefix: String): Map<String, T> = getConfigMap(prefix, serializer())

/**
 * Reified extension for map config binding with diagnostics.
 */
inline fun <reified T> ConfigBinder.getConfigMapResult(
    prefix: String,
    strict: Boolean = true,
): IdkResult<Map<String, T>, IdkError> = getConfigMapResult(prefix, serializer(), strict)

/**
 * Strategy for merging JSON objects from multiple scopes.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("JsonMergeStrategy", exact = true)
enum class JsonMergeStrategy {
    /** Higher scope completely replaces lower scope values */
    REPLACE,

    /** Deep merge objects, but arrays are replaced entirely (default) */
    DEEP_MERGE_REPLACE_ARRAYS,

    /** Deep merge objects, arrays are concatenated */
    DEEP_MERGE_CONCAT_ARRAYS,
}

/**
 * Default implementation of ConfigBinder using PropertyResolver.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultConfigBinder", exact = true)
class DefaultConfigBinder(
    private val resolver: PropertyResolver,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        },
    private val mergeStrategy: JsonMergeStrategy = JsonMergeStrategy.DEEP_MERGE_REPLACE_ARRAYS,
) : ConfigBinder {
    private data class BindFailure(
        val entry: String,
        val path: String,
        val expectedType: String,
        val receivedValue: String,
        val reason: String,
    )

    private val keyNormalizer = PropertyKeyNormalizerImpl.Default

    override fun <T> getConfig(
        prefix: String,
        serializer: KSerializer<T>,
    ): T? = getConfigResult(prefix, serializer).getOrNull()

    override fun <T> getRequiredConfig(
        prefix: String,
        serializer: KSerializer<T>,
    ): T =
        getConfigResult(prefix, serializer).getOrElse { error ->
            throw IllegalStateException("Required config not found for prefix '$prefix': ${error.message.defaultMessage}")
        }

    override fun <T> getConfigResult(
        prefix: String,
        serializer: KSerializer<T>,
    ): IdkResult<T, IdkError> {
        val normalizedPrefix = keyNormalizer.normalize(prefix)
        val properties = resolver.getSubProperties(setOf(normalizedPrefix), stripPrefix = true)
        val expectedType = serializer.descriptor.serialName

        if (properties.isEmpty()) {
            return Err(ConfigErrors.propertyNotFound(prefix))
        }

        return try {
            val jsonObject = propertiesToJson(properties, serializer.descriptor)
            val result = json.decodeFromJsonElement(serializer, jsonObject)
            Ok(result)
        } catch (e: SerializationException) {
            Err(
                ConfigErrors.bindError(
                    prefix = prefix,
                    expectedType = expectedType,
                    reason = e.message ?: "unknown deserialization error",
                    path = prefix,
                    receivedValue = properties.toString(),
                ),
            )
        } catch (expected: Exception) {
            Err(
                ConfigErrors.bindError(
                    prefix = prefix,
                    expectedType = expectedType,
                    reason = "unexpected error: ${expected.message ?: "unknown"}",
                    path = prefix,
                    receivedValue = properties.toString(),
                ),
            )
        }
    }

    override fun <T> getConfigList(
        prefix: String,
        serializer: KSerializer<T>,
    ): List<T> = getConfigListResult(prefix, serializer, strict = false).getOrElse { emptyList() }

    override fun <T> getConfigListResult(
        prefix: String,
        serializer: KSerializer<T>,
        strict: Boolean,
    ): IdkResult<List<T>, IdkError> {
        val normalizedPrefix = keyNormalizer.normalize(prefix)
        val properties = resolver.getSubProperties(setOf(normalizedPrefix), stripPrefix = true)

        if (properties.isEmpty()) {
            return Ok(emptyList())
        }

        // Find all indexed entries and group them
        val indexedGroups = mutableMapOf<Int, MutableMap<String, Any>>()
        val indexPattern = Regex("""^(\d+)\.(.+)$""")
        val bracketPattern = Regex("""^\[(\d+)\]\.(.+)$""")

        for ((key, value) in properties) {
            val matchIndex = indexPattern.matchEntire(key) ?: bracketPattern.matchEntire(key)
            if (matchIndex != null) {
                val index = matchIndex.groupValues[1].toInt()
                val remainingKey = matchIndex.groupValues[2]
                indexedGroups.getOrPut(index) { mutableMapOf() }[remainingKey] = value
            }
        }

        val results = mutableListOf<T>()
        val failures = mutableListOf<BindFailure>()
        val expectedType = serializer.descriptor.serialName

        indexedGroups.keys.sorted().forEach { index ->
            val groupProperties = indexedGroups[index] ?: return@forEach
            try {
                val jsonObject = propertiesToJson(groupProperties, serializer.descriptor)
                results += json.decodeFromJsonElement(serializer, jsonObject)
            } catch (expected: Exception) {
                if (strict) {
                    failures +=
                        BindFailure(
                            entry = index.toString(),
                            path = "$prefix.$index",
                            expectedType = expectedType,
                            receivedValue = groupProperties.toString(),
                            reason = expected.message ?: "unknown deserialization error",
                        )
                }
            }
        }

        if (strict && failures.isNotEmpty()) {
            return Err(bindFailureError(prefix, expectedType, "list", failures))
        }

        return Ok(results)
    }

    override fun <T> getConfigMap(
        prefix: String,
        serializer: KSerializer<T>,
    ): Map<String, T> = getConfigMapResult(prefix, serializer, strict = false).getOrElse { emptyMap() }

    override fun <T> getConfigMapResult(
        prefix: String,
        serializer: KSerializer<T>,
        strict: Boolean,
    ): IdkResult<Map<String, T>, IdkError> {
        val normalizedPrefix = keyNormalizer.normalize(prefix)
        val properties = resolver.getSubProperties(setOf(normalizedPrefix), stripPrefix = true)

        if (properties.isEmpty()) {
            return Ok(emptyMap())
        }

        // Group properties by top-level key
        val keyedGroups = mutableMapOf<String, MutableMap<String, Any>>()

        for ((key, value) in properties) {
            val dotIndex = key.indexOf('.')
            if (dotIndex > 0) {
                val topKey = key.substring(0, dotIndex)
                val remainingKey = key.substring(dotIndex + 1)
                keyedGroups.getOrPut(topKey) { mutableMapOf() }[remainingKey] = value
            } else {
                // Simple key with primitive value
                keyedGroups.getOrPut(key) { mutableMapOf() }[""] = value
            }
        }

        val result = mutableMapOf<String, T>()
        val failures = mutableListOf<BindFailure>()
        val expectedType = serializer.descriptor.serialName

        keyedGroups.forEach { (mapKey, groupProperties) ->
            try {
                val jsonObject =
                    if (groupProperties.size == 1 && groupProperties.containsKey("")) {
                        // Simple primitive value
                        JsonPrimitive(groupProperties[""].toString())
                    } else {
                        propertiesToJson(groupProperties, serializer.descriptor)
                    }
                result[mapKey] = json.decodeFromJsonElement(serializer, jsonObject)
            } catch (expected: Exception) {
                if (strict) {
                    failures +=
                        BindFailure(
                            entry = mapKey,
                            path = "$prefix.$mapKey",
                            expectedType = expectedType,
                            receivedValue = groupProperties.toString(),
                            reason = expected.message ?: "unknown deserialization error",
                        )
                }
            }
        }

        if (strict && failures.isNotEmpty()) {
            return Err(bindFailureError(prefix, expectedType, "map", failures))
        }

        return Ok(result)
    }

    /**
     * Convert flat properties map to nested JSON structure.
     */
    private fun propertiesToJson(properties: Map<String, Any>): JsonObject {
        val root = mutableMapOf<String, Any?>()

        for ((key, value) in properties) {
            setNestedValue(root, key.split('.'), value)
        }

        return mapToJsonObject(root)
    }

    /**
     * Convert flat properties map to nested JSON structure using schema descriptor
     * to resolve camelCase field name ambiguity.
     *
     * The normalizer splits camelCase into dots (e.g., "defaultIdp" -> "default.idp"),
     * so when we later reconstruct JSON we can't tell if "default.idp" is one camelCase
     * field or two nesting levels. The descriptor tells us which fields actually exist
     * at each level, so we can greedily match normalized segments back to original names.
     */
    private fun propertiesToJson(
        properties: Map<String, Any>,
        descriptor: SerialDescriptor,
    ): JsonObject {
        val root = mutableMapOf<String, Any?>()

        for ((key, value) in properties) {
            val path = reconstructPath(key, descriptor)
            setNestedValue(root, path, value)
        }

        return mapToJsonObject(root)
    }

    private data class FieldInfo(
        val originalName: String,
        val childDescriptor: SerialDescriptor?,
    )

    private fun buildFieldMap(descriptor: SerialDescriptor): Map<String, FieldInfo> {
        val map = mutableMapOf<String, FieldInfo>()
        for (i in 0 until descriptor.elementsCount) {
            val originalName = descriptor.getElementName(i)
            val normalizedName = keyNormalizer.normalize(originalName)
            val elementDescriptor = descriptor.getElementDescriptor(i)
            val childDescriptor =
                if (elementDescriptor.kind == StructureKind.CLASS) {
                    elementDescriptor
                } else {
                    null
                }
            map[normalizedName] = FieldInfo(originalName, childDescriptor)
        }
        return map
    }

    private fun reconstructPath(
        normalizedKey: String,
        descriptor: SerialDescriptor,
    ): List<String> {
        val segments = normalizedKey.split('.')
        val result = mutableListOf<String>()
        reconstructPathRecursive(segments, 0, descriptor, result)
        return result
    }

    private fun reconstructPathRecursive(
        segments: List<String>,
        startIdx: Int,
        descriptor: SerialDescriptor,
        result: MutableList<String>,
    ) {
        if (startIdx >= segments.size) {
            return
        }
        val fieldMap = buildFieldMap(descriptor)

        // Greedy: try longest segment prefix first
        for (endIdx in segments.size downTo startIdx + 1) {
            val candidate = segments.subList(startIdx, endIdx).joinToString(".")
            val field = fieldMap[candidate]
            if (field != null) {
                result.add(field.originalName)
                if (endIdx < segments.size && field.childDescriptor != null) {
                    reconstructPathRecursive(segments, endIdx, field.childDescriptor, result)
                } else {
                    // Remaining segments past the schema — add as-is
                    for (i in endIdx until segments.size) result.add(segments[i])
                }
                return
            }
        }
        // No match — add remaining segments as-is (preserves current behavior for unknowns)
        for (i in startIdx until segments.size) result.add(segments[i])
    }

    /**
     * Set a value in a nested map structure.
     */
    private fun setNestedValue(
        root: MutableMap<String, Any?>,
        path: List<String>,
        value: Any,
    ) {
        if (path.isEmpty()) {
            return
        }

        var current: MutableMap<String, Any?> = root

        for (i in 0 until path.size - 1) {
            val segment = path[i]
            @Suppress("UNCHECKED_CAST")
            current = current.getOrPut(segment) { mutableMapOf<String, Any?>() } as MutableMap<String, Any?>
        }

        val lastKey = path.last()
        val existing = current[lastKey]

        if (existing is MutableMap<*, *> && value is Map<*, *>) {
            // Merge maps based on strategy
            @Suppress("UNCHECKED_CAST")
            when (mergeStrategy) {
                JsonMergeStrategy.REPLACE -> {
                    current[lastKey] = value
                }

                JsonMergeStrategy.DEEP_MERGE_REPLACE_ARRAYS,
                JsonMergeStrategy.DEEP_MERGE_CONCAT_ARRAYS,
                -> {
                    mergeMaps(existing as MutableMap<String, Any?>, value as Map<String, Any?>)
                }
            }
        } else {
            current[lastKey] = value
        }
    }

    /**
     * Merge source map into target map.
     */
    private fun mergeMaps(
        target: MutableMap<String, Any?>,
        source: Map<String, Any?>,
    ) {
        for ((key, sourceValue) in source) {
            val targetValue = target[key]
            if (targetValue is MutableMap<*, *> && sourceValue is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                mergeMaps(targetValue as MutableMap<String, Any?>, sourceValue as Map<String, Any?>)
            } else if (targetValue is List<*> && sourceValue is List<*> &&
                mergeStrategy == JsonMergeStrategy.DEEP_MERGE_CONCAT_ARRAYS
            ) {
                target[key] = targetValue + sourceValue
            } else {
                target[key] = sourceValue
            }
        }
    }

    /**
     * Convert a nested map to JsonObject.
     */
    private fun mapToJsonObject(map: Map<String, Any?>): JsonObject =
        buildJsonObject {
            for ((key, value) in map) {
                put(key, anyToJsonElement(value))
            }
        }

    /**
     * Convert any value to JsonElement.
     */
    private fun anyToJsonElement(value: Any?): JsonElement =
        when (value) {
            null -> {
                JsonNull
            }

            is String -> {
                JsonPrimitive(value)
            }

            is Number -> {
                JsonPrimitive(value)
            }

            is Boolean -> {
                JsonPrimitive(value)
            }

            is Map<*, *> -> {
                @Suppress("UNCHECKED_CAST")
                mapToJsonObject(value as Map<String, Any?>)
            }

            is List<*> -> {
                JsonArray(value.map { anyToJsonElement(it) })
            }

            else -> {
                JsonPrimitive(value.toString())
            }
        }

    private fun bindFailureError(
        prefix: String,
        expectedType: String,
        collectionType: String,
        failures: List<BindFailure>,
    ): IdkError {
        val failureMaps =
            failures.map { failure ->
                mapOf(
                    "entry" to failure.entry,
                    "path" to failure.path,
                    "expectedType" to failure.expectedType,
                    "receivedValue" to failure.receivedValue,
                    "reason" to failure.reason,
                )
            }
        val details =
            failures.joinToString("; ") { failure ->
                "entry='${failure.entry}', path='${failure.path}', reason='${failure.reason}'"
            }
        return ConfigErrors.bindError(
            prefix = prefix,
            expectedType = expectedType,
            collectionType = collectionType,
            reason = details,
            failures = failureMaps,
        )
    }
}

/**
 * Extension to create a ConfigBinder from a PropertyResolver.
 */
fun PropertyResolver.toConfigBinder(
    json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        },
    mergeStrategy: JsonMergeStrategy = JsonMergeStrategy.DEEP_MERGE_REPLACE_ARRAYS,
): ConfigBinder = DefaultConfigBinder(this, json, mergeStrategy)

/**
 * Extension to create a ConfigBinder from a ConfigEnvironment.
 *
 * @param json JSON configuration for deserialization
 * @param mergeStrategy Strategy for merging nested JSON objects
 * @param interpolate Whether to enable property interpolation (${...} placeholders).
 *                    When true, uses PropertyResolverFactory with an interpolator.
 *                    When false, uses plain PropertySourcesPropertyResolver (faster, no interpolation).
 * @param interpolator Optional custom interpolator. If not provided and interpolate is true,
 *                     a DefaultPropertyInterpolator will be used.
 */
fun ConfigEnvironment.toConfigBinder(
    json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        },
    mergeStrategy: JsonMergeStrategy = JsonMergeStrategy.DEEP_MERGE_REPLACE_ARRAYS,
    interpolate: Boolean = true,
    interpolator: PropertyInterpolator? = null,
): ConfigBinder {
    val sources = getPropertySources(includeParents = true)
    val resolver =
        PropertyResolverFactory.create(
            propertySources = sources,
            interpolator =
                if (interpolate) {
                    interpolator ?: DefaultPropertyInterpolator()
                } else {
                    null
                },
        )
    return DefaultConfigBinder(resolver, json, mergeStrategy)
}

/**
 * ConfigBinder that supports hierarchical scope merging.
 * Merges configuration from parent environments into child environments.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("HierarchicalConfigBinder", exact = true)
class HierarchicalConfigBinder(
    private val environment: ConfigEnvironment,
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        },
    private val mergeStrategy: JsonMergeStrategy = JsonMergeStrategy.DEEP_MERGE_REPLACE_ARRAYS,
) : ConfigBinder {
    private val delegate =
        DefaultConfigBinder(
            PropertySourcesPropertyResolver(environment.getPropertySources(includeParents = true)),
            json,
            mergeStrategy,
        )

    override fun <T> getConfig(
        prefix: String,
        serializer: KSerializer<T>,
    ): T? = delegate.getConfig(prefix, serializer)

    override fun <T> getRequiredConfig(
        prefix: String,
        serializer: KSerializer<T>,
    ): T = delegate.getRequiredConfig(prefix, serializer)

    override fun <T> getConfigResult(
        prefix: String,
        serializer: KSerializer<T>,
    ): IdkResult<T, IdkError> = delegate.getConfigResult(prefix, serializer)

    override fun <T> getConfigList(
        prefix: String,
        serializer: KSerializer<T>,
    ): List<T> = delegate.getConfigList(prefix, serializer)

    override fun <T> getConfigListResult(
        prefix: String,
        serializer: KSerializer<T>,
        strict: Boolean,
    ): IdkResult<List<T>, IdkError> = delegate.getConfigListResult(prefix, serializer, strict)

    override fun <T> getConfigMap(
        prefix: String,
        serializer: KSerializer<T>,
    ): Map<String, T> = delegate.getConfigMap(prefix, serializer)

    override fun <T> getConfigMapResult(
        prefix: String,
        serializer: KSerializer<T>,
        strict: Boolean,
    ): IdkResult<Map<String, T>, IdkError> = delegate.getConfigMapResult(prefix, serializer, strict)

    /**
     * Get the current scope level.
     */
    fun getLevel(): ConfigLevel = environment.level

    /**
     * Get the active profile.
     */
    fun getActiveProfile(): String = environment.getActiveProfile()
}
