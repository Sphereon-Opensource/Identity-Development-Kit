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
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.json.Json
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.reflect.KClass

/**
 * Configuration binder for polymorphic types.
 *
 * Unlike standard ConfigBinder, this supports runtime type dispatch based on discriminator fields.
 * This is essential for configuration scenarios where the concrete type is determined by a `type`
 * field in the configuration (e.g., KMS providers, storage backends, auth providers).
 *
 * @param T The base type for polymorphic deserialization
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("PolymorphicConfigBinder", exact = true)
interface PolymorphicConfigBinder<T : Any> {
    /**
     * Gets all entry IDs from configuration.
     *
     * @param resolver The property resolver to read properties from
     * @return Set of detected entry IDs
     */
    fun getEntryIds(resolver: PropertyResolver): Set<String>

    /**
     * Gets a specific entry config by ID.
     *
     * @param resolver The property resolver to read properties from
     * @param entryId The entry ID to retrieve
     * @return The deserialized config object, or null if not found or invalid
     */
    fun getEntryConfig(
        resolver: PropertyResolver,
        entryId: String,
    ): T?

    /**
     * Gets all entry configs.
     *
     * @param resolver The property resolver to read properties from
     * @return List of all deserialized config objects
     */
    fun getEntryConfigs(resolver: PropertyResolver): List<T>

    /**
     * Gets all entry configs as a map keyed by entry ID.
     *
     * This is more efficient than calling getEntryIds() and getEntryConfig() separately
     * when you need both the ID and the config.
     *
     * @param resolver The property resolver to read properties from
     * @return Map of entry IDs to deserialized config objects
     */
    fun getEntryConfigsAsMap(resolver: PropertyResolver): Map<String, T>
}

/**
 * Default implementation of PolymorphicConfigBinder.
 *
 * This implementation extracts the common polymorphic configuration binding logic
 * that was originally in KmsProviderConfigBinderImpl, making it reusable for
 * other polymorphic configuration scenarios (storage backends, auth providers, etc.).
 *
 * @param prefix Config prefix (e.g., "kms.providers", "storage.backends")
 * @param baseClass The base class for polymorphic deserialization
 * @param json Json instance with polymorphic serializers registered
 * @param entryDetection Strategy for detecting entry IDs (default: TypeSuffixEntryDetection)
 * @param idFieldName Name of the ID field in deserialized objects for auto-populating (default: "id")
 * @param keyNormalizer Normalizer for property keys (default: PropertyKeyNormalizerImpl)
 * @param keyDenormalizer Denormalizer for property keys when converting to JSON (default: CamelCaseKeyDenormalizerImpl)
 * @param nestedPrefixAliases Optional map of nested object prefixes to their JSON field names.
 *                           Example: mapOf("keystore" to "keyStore") to build nested "keyStore" objects.
 * @param propertyNameAliases Optional aliases for property keys to JSON field names.
 *                           Alias lookup is relaxed: both normalized and canonicalized keys are matched.
 *                           Canonicalized means delimiters removed (e.g., "key.visibility" -> "keyvisibility").
 * @param redact Whether to redact sensitive values when converting to JSON (default: false for config binding)
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultPolymorphicConfigBinder", exact = true)
class DefaultPolymorphicConfigBinder<T : Any>(
    private val prefix: String,
    private val baseClass: KClass<T>,
    private val json: Json,
    private val entryDetection: EntryDetectionStrategy = TypeSuffixEntryDetection(),
    private val idFieldName: String? = "id",
    private val keyNormalizer: PropertyKeyNormalizer = PropertyKeyNormalizerImpl.Default,
    private val keyDenormalizer: PropertyKeyDenormalizer = CamelCaseKeyDenormalizerImpl(),
    private val nestedPrefixAliases: Map<String, String> = emptyMap(),
    private val propertyNameAliases: Map<String, String> = emptyMap(),
    private val redact: Boolean = false,
) : PolymorphicConfigBinder<T> {
    private data class PolymorphicBindFailure(
        val entry: String,
        val path: String,
        val expectedType: String,
        val receivedValue: String,
        val reason: String,
    )

    private sealed class BuildConfigOutcome<out T> {
        data class Success<T>(
            val value: T,
        ) : BuildConfigOutcome<T>()

        data class Failure(
            val failure: PolymorphicBindFailure,
        ) : BuildConfigOutcome<Nothing>()

        data object NotFound : BuildConfigOutcome<Nothing>()
    }

    private val normalizedNestedPrefixAliases: Map<String, String> =
        nestedPrefixAliases.entries.associate { (key, value) -> keyNormalizer.normalize(key) to value }
    private val normalizedPropertyNameAliases: Map<String, String> =
        propertyNameAliases.entries.associate { (key, value) -> keyNormalizer.normalize(key) to value }
    private val canonicalPropertyNameAliases: Map<String, String> =
        normalizedPropertyNameAliases.entries.associate { (key, value) -> canonicalizeKey(key) to value }
    private val expectedTypeName: String =
        baseClass.simpleName ?: "unknown"

    override fun getEntryIds(resolver: PropertyResolver): Set<String> {
        val normalizedPrefix = keyNormalizer.normalize(prefix)
        val allSubProps = resolver.getSubProperties(setOf(normalizedPrefix), stripPrefix = true)
        return entryDetection.detectEntryIds(allSubProps.keys)
    }

    override fun getEntryConfig(
        resolver: PropertyResolver,
        entryId: String,
    ): T? = getEntryConfigResult(resolver, entryId).getOrNull()

    /**
     * Gets a specific entry config by ID with structured diagnostics.
     */
    fun getEntryConfigResult(
        resolver: PropertyResolver,
        entryId: String,
    ): IdkResult<T, IdkError> {
        val normalizedPrefix = keyNormalizer.normalize(prefix)
        val normalizedEntryId = keyNormalizer.normalize(entryId)
        val allSubProps = resolver.getSubProperties(setOf(normalizedPrefix), stripPrefix = true)

        return when (val outcome = buildConfigFromProperties(allSubProps, normalizedEntryId, entryId)) {
            BuildConfigOutcome.NotFound -> {
                Err(ConfigErrors.propertyNotFound("$prefix.$entryId"))
            }

            is BuildConfigOutcome.Success -> {
                Ok(outcome.value)
            }

            is BuildConfigOutcome.Failure -> {
                Err(
                    ConfigErrors.bindError(
                        prefix = prefix,
                        expectedType = expectedTypeName,
                        reason = outcome.failure.reason,
                        entry = outcome.failure.entry,
                        path = outcome.failure.path,
                        receivedValue = outcome.failure.receivedValue,
                    ),
                )
            }
        }
    }

    override fun getEntryConfigs(resolver: PropertyResolver): List<T> = getEntryConfigsAsMapResult(resolver, strict = false).getOrElse { emptyMap() }.values.toList()

    /**
     * Gets all entry configs with strict diagnostic mode.
     *
     * In strict mode, any invalid entry returns an error with per-entry diagnostics.
     * In non-strict mode, invalid entries are skipped.
     */
    fun getEntryConfigsResult(
        resolver: PropertyResolver,
        strict: Boolean = true,
    ): IdkResult<List<T>, IdkError> {
        val mapResult = getEntryConfigsAsMapResult(resolver, strict)
        return if (mapResult.isErr) {
            Err(mapResult.error)
        } else {
            Ok(mapResult.value.values.toList())
        }
    }

    override fun getEntryConfigsAsMap(resolver: PropertyResolver): Map<String, T> = getEntryConfigsAsMapResult(resolver, strict = false).getOrElse { emptyMap() }

    /**
     * Gets all entry configs as a map keyed by entry ID with strict diagnostic mode.
     *
     * In strict mode, any invalid entry returns an error with per-entry diagnostics.
     * In non-strict mode, invalid entries are skipped.
     */
    fun getEntryConfigsAsMapResult(
        resolver: PropertyResolver,
        strict: Boolean = true,
    ): IdkResult<Map<String, T>, IdkError> {
        val normalizedPrefix = keyNormalizer.normalize(prefix)
        val allSubProps = resolver.getSubProperties(setOf(normalizedPrefix), stripPrefix = true)

        val entryIds = entryDetection.detectEntryIds(allSubProps.keys)
        val result = mutableMapOf<String, T>()
        val failures = mutableListOf<PolymorphicBindFailure>()

        entryIds.forEach { entryId ->
            val normalizedEntryId = keyNormalizer.normalize(entryId)
            when (val outcome = buildConfigFromProperties(allSubProps, normalizedEntryId, entryId)) {
                BuildConfigOutcome.NotFound -> {
                    // No-op: entry was detected but no bindable values found.
                }

                is BuildConfigOutcome.Success -> {
                    result[entryId] = outcome.value
                }

                is BuildConfigOutcome.Failure -> {
                    if (strict) {
                        failures += outcome.failure
                    }
                }
            }
        }

        if (strict && failures.isNotEmpty()) {
            return Err(bindFailureError(collectionType = "map", failures = failures))
        }

        return Ok(result)
    }

    /**
     * Builds a config object from pre-fetched properties.
     *
     * @param allSubProps All properties under the prefix
     * @param normalizedEntryId The normalized entry ID for property matching
     * @param originalEntryId The original entry ID for the id field value
     * @return The deserialized config object, or null if properties are empty or invalid
     */
    private fun buildConfigFromProperties(
        allSubProps: Map<String, Any>,
        normalizedEntryId: String,
        originalEntryId: String,
    ): BuildConfigOutcome<T> {
        // Filter properties for this entry
        val properties =
            allSubProps
                .filter { (key, _) ->
                    val normalizedKey = keyNormalizer.normalize(key.removePrefix("."))
                    normalizedKey.startsWith("$normalizedEntryId.")
                }.map { (key, value) ->
                    // Strip the entry ID prefix from the normalized key
                    val normalizedKey = keyNormalizer.normalize(key)
                    val propertyName = normalizedKey.removePrefix("$normalizedEntryId.")
                    propertyName to value
                }.toMap()
                .toMutableMap()

        if (properties.isEmpty()) {
            return BuildConfigOutcome.NotFound
        }

        // Auto-populate the ID field if configured
        if (idFieldName != null && !properties.containsKey(idFieldName)) {
            properties[idFieldName] = originalEntryId
        }

        // Convert to JSON and deserialize using polymorphic serializer.
        // Transform keys to support camelCase fields while keeping explicit nested objects (e.g., keystore.*).
        // Keep first value for duplicate output keys so higher-priority sources keep winning.
        val jsonReadyProperties = linkedMapOf<String, Any>()
        for ((key, value) in properties) {
            val normalizedKey = keyNormalizer.normalize(key.removePrefix("."))
            val nestedAlias =
                normalizedNestedPrefixAliases.entries.firstOrNull { (prefix, _) ->
                    normalizedKey == prefix || normalizedKey.startsWith("$prefix.")
                }

            val outputKey =
                if (nestedAlias != null) {
                    val (prefix, alias) = nestedAlias
                    val rest = normalizedKey.removePrefix(prefix).removePrefix(".")
                    if (rest.isEmpty()) {
                        alias
                    } else {
                        val denormalizedRest = resolveOutputKey(rest)
                        "$alias.$denormalizedRest"
                    }
                } else {
                    resolveOutputKey(normalizedKey)
                }

            if (!jsonReadyProperties.containsKey(outputKey)) {
                jsonReadyProperties[outputKey] = value
            }
        }

        val jsonString = jsonReadyProperties.toJsonString(redact = redact)

        return try {
            BuildConfigOutcome.Success(
                json.decodeFromString(PolymorphicSerializer(baseClass), jsonString),
            )
        } catch (expected: Exception) {
            BuildConfigOutcome.Failure(
                PolymorphicBindFailure(
                    entry = originalEntryId,
                    path = "$prefix.$originalEntryId",
                    expectedType = expectedTypeName,
                    receivedValue = jsonString,
                    reason = expected.message ?: "unknown deserialization error",
                ),
            )
        }
    }

    private fun bindFailureError(
        collectionType: String,
        failures: List<PolymorphicBindFailure>,
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
        val reason =
            failures.joinToString("; ") { failure ->
                "entry='${failure.entry}', path='${failure.path}', reason='${failure.reason}'"
            }
        return ConfigErrors.bindError(
            prefix = prefix,
            expectedType = expectedTypeName,
            collectionType = collectionType,
            reason = reason,
            failures = failureMaps,
        )
    }

    private fun canonicalizeKey(normalizedKey: String): String = normalizedKey.replace(PROPERTY_KEY_DELIMITER, "")

    private fun resolveOutputKey(normalizedKey: String): String {
        normalizedPropertyNameAliases[normalizedKey]?.let { return it }
        canonicalPropertyNameAliases[canonicalizeKey(normalizedKey)]?.let { return it }
        return keyDenormalizer.denormalize(normalizedKey)
    }
}

/**
 * Extension function to create a PolymorphicConfigBinder from a prefix and base class.
 *
 * @param prefix Config prefix (e.g., "kms.providers")
 * @param baseClass The base class for polymorphic deserialization
 * @param json Json instance with polymorphic serializers registered
 * @param entryDetection Strategy for detecting entry IDs
 * @return A configured PolymorphicConfigBinder
 */
fun <T : Any> polymorphicConfigBinder(
    prefix: String,
    baseClass: KClass<T>,
    json: Json,
    entryDetection: EntryDetectionStrategy = TypeSuffixEntryDetection(),
    propertyNameAliases: Map<String, String> = emptyMap(),
): PolymorphicConfigBinder<T> =
    DefaultPolymorphicConfigBinder(
        prefix = prefix,
        baseClass = baseClass,
        json = json,
        entryDetection = entryDetection,
        propertyNameAliases = propertyNameAliases,
    )

/**
 * Reified extension for creating a PolymorphicConfigBinder.
 */
inline fun <reified T : Any> polymorphicConfigBinder(
    prefix: String,
    json: Json,
    entryDetection: EntryDetectionStrategy = TypeSuffixEntryDetection(),
    propertyNameAliases: Map<String, String> = emptyMap(),
): PolymorphicConfigBinder<T> =
    polymorphicConfigBinder(
        prefix = prefix,
        baseClass = T::class,
        json = json,
        entryDetection = entryDetection,
        propertyNameAliases = propertyNameAliases,
    )

/**
 * Get a map of configuration objects using polymorphic deserialization.
 *
 * This is the primary way to retrieve polymorphic configuration entries from a PropertyResolver.
 * Unlike standard getConfigMap(), this supports runtime type dispatch based on discriminator fields.
 *
 * @param prefix The property prefix (e.g., "kms.providers", "storage.backends")
 * @param baseClass The base class for polymorphic deserialization
 * @param json Json instance with appropriate polymorphic serializers registered
 * @param entryDetection Strategy for detecting entry IDs (default: TypeSuffixEntryDetection)
 * @return Map of entry IDs to deserialized config objects
 */
fun <T : Any> PropertyResolver.getConfigMapPolymorphic(
    prefix: String,
    baseClass: KClass<T>,
    json: Json,
    entryDetection: EntryDetectionStrategy = TypeSuffixEntryDetection(),
    propertyNameAliases: Map<String, String> = emptyMap(),
): Map<String, T> {
    val binder =
        DefaultPolymorphicConfigBinder(
            prefix = prefix,
            baseClass = baseClass,
            json = json,
            entryDetection = entryDetection,
            propertyNameAliases = propertyNameAliases,
        )
    return binder.getEntryConfigsAsMap(this)
}

/**
 * Get a map of polymorphic configuration objects with strict diagnostics.
 *
 * In strict mode, any invalid entry returns `CONFIG_BIND_ERROR` with per-entry diagnostics.
 * In non-strict mode, invalid entries are skipped.
 */
fun <T : Any> PropertyResolver.getConfigMapPolymorphicResult(
    prefix: String,
    baseClass: KClass<T>,
    json: Json,
    entryDetection: EntryDetectionStrategy = TypeSuffixEntryDetection(),
    propertyNameAliases: Map<String, String> = emptyMap(),
    strict: Boolean = true,
): IdkResult<Map<String, T>, IdkError> {
    val binder =
        DefaultPolymorphicConfigBinder(
            prefix = prefix,
            baseClass = baseClass,
            json = json,
            entryDetection = entryDetection,
            propertyNameAliases = propertyNameAliases,
        )
    return binder.getEntryConfigsAsMapResult(this, strict = strict)
}

/**
 * Reified extension for getting a polymorphic config map.
 */
inline fun <reified T : Any> PropertyResolver.getConfigMapPolymorphic(
    prefix: String,
    json: Json,
    entryDetection: EntryDetectionStrategy = TypeSuffixEntryDetection(),
    propertyNameAliases: Map<String, String> = emptyMap(),
): Map<String, T> =
    getConfigMapPolymorphic(
        prefix = prefix,
        baseClass = T::class,
        json = json,
        entryDetection = entryDetection,
        propertyNameAliases = propertyNameAliases,
    )

/**
 * Reified extension for getting a polymorphic config map with diagnostics.
 */
inline fun <reified T : Any> PropertyResolver.getConfigMapPolymorphicResult(
    prefix: String,
    json: Json,
    entryDetection: EntryDetectionStrategy = TypeSuffixEntryDetection(),
    propertyNameAliases: Map<String, String> = emptyMap(),
    strict: Boolean = true,
): IdkResult<Map<String, T>, IdkError> =
    getConfigMapPolymorphicResult(
        prefix = prefix,
        baseClass = T::class,
        json = json,
        entryDetection = entryDetection,
        propertyNameAliases = propertyNameAliases,
        strict = strict,
    )
