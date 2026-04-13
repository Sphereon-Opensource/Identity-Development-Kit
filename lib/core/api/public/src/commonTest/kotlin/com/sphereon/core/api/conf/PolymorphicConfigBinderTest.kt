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

package com.sphereon.core.api.conf

import com.sphereon.di.Order
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Test polymorphic base interface and implementations
// Note: Using non-sealed interface with explicit polymorphic registration
interface TestProviderConfig {
    val id: String
    val enabled: Boolean
}

@Serializable
@SerialName("software")
data class SoftwareProviderConfig(
    override val id: String,
    override val enabled: Boolean = true,
    @SerialName("keySize") val keySize: Int = 256
) : TestProviderConfig

@Serializable
@SerialName("rest")
data class RestProviderConfig(
    override val id: String,
    override val enabled: Boolean = true,
    val endpoint: String,
    val timeout: Long = 30000
) : TestProviderConfig

// JSON with polymorphic serializers registered
// Uses "type" as the class discriminator field
private val testJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    classDiscriminator = "type"
    serializersModule = SerializersModule {
        polymorphic(TestProviderConfig::class) {
            subclass(SoftwareProviderConfig::class, SoftwareProviderConfig.serializer())
            subclass(RestProviderConfig::class, RestProviderConfig.serializer())
        }
    }
}

/**
 * Tests for TypeSuffixEntryDetection strategy.
 */
class TypeSuffixEntryDetectionTest {

    @Test
    fun detectsEntriesByTypeSuffix() {
        val detection = TypeSuffixEntryDetection()
        val propertyKeys = setOf(
            "primary.type",
            "primary.keysize",
            "secondary.type",
            "secondary.endpoint"
        )

        val entryIds = detection.detectEntryIds(propertyKeys)

        assertEquals(2, entryIds.size)
        assertTrue(entryIds.contains("primary"))
        assertTrue(entryIds.contains("secondary"))
    }

    @Test
    fun excludesSpecifiedSuffixes() {
        val detection = TypeSuffixEntryDetection(
            discriminatorSuffix = ".type",
            excludedSuffixes = setOf(".keystore.type")
        )
        val propertyKeys = setOf(
            "provider1.type",
            "provider1.keystore.type",
            "provider2.type"
        )

        val entryIds = detection.detectEntryIds(propertyKeys)

        assertEquals(2, entryIds.size)
        assertTrue(entryIds.contains("provider1"))
        assertTrue(entryIds.contains("provider2"))
    }

    @Test
    fun handlesEmptyPropertyKeys() {
        val detection = TypeSuffixEntryDetection()

        val entryIds = detection.detectEntryIds(emptySet())

        assertTrue(entryIds.isEmpty())
    }

    @Test
    fun handlesNoMatchingKeys() {
        val detection = TypeSuffixEntryDetection()
        val propertyKeys = setOf(
            "primary.keysize",
            "secondary.endpoint"
        )

        val entryIds = detection.detectEntryIds(propertyKeys)

        assertTrue(entryIds.isEmpty())
    }

    @Test
    fun handlesCustomDiscriminatorSuffix() {
        val detection = TypeSuffixEntryDetection(discriminatorSuffix = ".kind")
        val propertyKeys = setOf(
            "provider1.kind",
            "provider1.value",
            "provider2.kind"
        )

        val entryIds = detection.detectEntryIds(propertyKeys)

        assertEquals(2, entryIds.size)
        assertTrue(entryIds.contains("provider1"))
        assertTrue(entryIds.contains("provider2"))
    }
}

/**
 * Tests for TopLevelKeyEntryDetection strategy.
 */
class TopLevelKeyEntryDetectionTest {

    @Test
    fun detectsEntriesByTopLevelKey() {
        val detection = TopLevelKeyEntryDetection()
        val propertyKeys = setOf(
            "primary.host",
            "primary.port",
            "replica.host",
            "replica.port"
        )

        val entryIds = detection.detectEntryIds(propertyKeys)

        assertEquals(2, entryIds.size)
        assertTrue(entryIds.contains("primary"))
        assertTrue(entryIds.contains("replica"))
    }

    @Test
    fun handlesEmptyPropertyKeys() {
        val detection = TopLevelKeyEntryDetection()

        val entryIds = detection.detectEntryIds(emptySet())

        assertTrue(entryIds.isEmpty())
    }

    @Test
    fun handlesKeysWithoutDots() {
        val detection = TopLevelKeyEntryDetection()
        val propertyKeys = setOf(
            "simplekey",
            "anotherkey"
        )

        val entryIds = detection.detectEntryIds(propertyKeys)

        assertEquals(2, entryIds.size)
        assertTrue(entryIds.contains("simplekey"))
        assertTrue(entryIds.contains("anotherkey"))
    }
}

/**
 * Tests for DefaultPolymorphicConfigBinder.
 */
class DefaultPolymorphicConfigBinderTest {

    private fun createResolver(vararg properties: Pair<String, Any>): PropertyResolver {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        return PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
    }

    private fun createResolverFromSources(vararg sources: PropertySource<*>): PropertyResolver {
        val propertySources = DefaultPropertySources()
        sources.forEach { propertySources.add(it) }
        return PropertySourcesPropertyResolver(propertySources)
    }

    @Test
    fun getEntryIdsDetectsProviders() {
        // Note: Property keys with hyphens are normalized to dots by PropertyKeyNormalizerImpl
        // So "providers.primary.type" stays as "primary" but "software-primary" becomes "software.primary"
        val resolver = createResolver(
            "providers.primary.type" to "software",
            "providers.primary.keysize" to 256,
            "providers.backup.type" to "rest",
            "providers.backup.endpoint" to "https://api.example.com"
        )
        val binder = DefaultPolymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson
        )

        val entryIds = binder.getEntryIds(resolver)

        assertEquals(2, entryIds.size)
        assertTrue(entryIds.contains("primary"))
        assertTrue(entryIds.contains("backup"))
    }

    @Test
    fun getEntryConfigDeserializesSoftwareProvider() {
        // Property keys must be normalized (dot-separated lowercase) to be denormalized to camelCase
        // "key.size" -> denormalized -> "keySize" matches @SerialName("keySize")
        val resolver = createResolver(
            "providers.primary.type" to "software",
            "providers.primary.key.size" to 512,
            "providers.primary.enabled" to true
        )
        val binder = DefaultPolymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson
        )

        val config = binder.getEntryConfig(resolver, "primary")

        assertNotNull(config)
        assertTrue(config is SoftwareProviderConfig)
        assertEquals("primary", config.id)
        assertEquals(512, (config as SoftwareProviderConfig).keySize)
        assertTrue(config.enabled)
    }

    @Test
    fun getEntryConfigDeserializesSoftwareProviderWithLowercaseFlatKeyAlias() {
        val resolver = createResolver(
            "providers.primary.type" to "software",
            "providers.primary.keysize" to 384,
            "providers.primary.enabled" to true
        )
        val binder = DefaultPolymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson,
            propertyNameAliases = mapOf(
                "keysize" to "keySize"
            )
        )

        val config = binder.getEntryConfig(resolver, "primary")

        assertNotNull(config)
        assertTrue(config is SoftwareProviderConfig)
        assertEquals(384, (config as SoftwareProviderConfig).keySize)
    }

    @Test
    fun getEntryConfigDeserializesRestProvider() {
        val resolver = createResolver(
            "providers.backup.type" to "rest",
            "providers.backup.endpoint" to "https://api.example.com",
            "providers.backup.timeout" to 60000
        )
        val binder = DefaultPolymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson
        )

        val config = binder.getEntryConfig(resolver, "backup")

        assertNotNull(config)
        assertTrue(config is RestProviderConfig)
        assertEquals("backup", config.id)
        assertEquals("https://api.example.com", (config as RestProviderConfig).endpoint)
        assertEquals(60000, config.timeout)
    }

    @Test
    fun getEntryConfigReturnsNullForMissingEntry() {
        val resolver = createResolver(
            "providers.primary.type" to "software"
        )
        val binder = DefaultPolymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson
        )

        val config = binder.getEntryConfig(resolver, "nonexistent")

        assertNull(config)
    }

    @Test
    fun getEntryConfigResultReturnsBindErrorWithMetadataForInvalidEntry() {
        val resolver = createResolver(
            "providers.primary.type" to "unknown",
            "providers.primary.enabled" to true
        )
        val binder = DefaultPolymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson
        )

        val result = binder.getEntryConfigResult(resolver, "primary")

        assertTrue(result.isErr)
        assertEquals("CONFIG_BIND_ERROR", result.error.code)
        assertEquals("providers", result.error.meta["prefix"])
        assertEquals("primary", result.error.meta["entry"])
        assertEquals("providers.primary", result.error.meta["path"])
    }

    @Test
    fun getEntryConfigsReturnsAllProviders() {
        val resolver = createResolver(
            "providers.primary.type" to "software",
            "providers.primary.keysize" to 256,
            "providers.backup.type" to "rest",
            "providers.backup.endpoint" to "https://api.example.com"
        )
        val binder = DefaultPolymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson
        )

        val configs = binder.getEntryConfigs(resolver)

        assertEquals(2, configs.size)
        assertTrue(configs.any { it is SoftwareProviderConfig && it.id == "primary" })
        assertTrue(configs.any { it is RestProviderConfig && it.id == "backup" })
    }

    @Test
    fun getEntryConfigsAsMapReturnsMapWithIds() {
        val resolver = createResolver(
            "providers.primary.type" to "software",
            "providers.primary.keysize" to 256,
            "providers.backup.type" to "rest",
            "providers.backup.endpoint" to "https://api.example.com"
        )
        val binder = DefaultPolymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson
        )

        val configMap = binder.getEntryConfigsAsMap(resolver)

        assertEquals(2, configMap.size)
        assertTrue(configMap.containsKey("primary"))
        assertTrue(configMap.containsKey("backup"))
        assertTrue(configMap["primary"] is SoftwareProviderConfig)
        assertTrue(configMap["backup"] is RestProviderConfig)
    }

    @Test
    fun getEntryConfigsAsMapResultStrictReturnsBindErrorForInvalidEntries() {
        val resolver = createResolver(
            "providers.primary.type" to "software",
            "providers.primary.keysize" to 256,
            "providers.backup.type" to "unknown",
            "providers.backup.endpoint" to "https://api.example.com"
        )
        val binder = DefaultPolymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson
        )

        val result = binder.getEntryConfigsAsMapResult(resolver, strict = true)

        assertTrue(result.isErr)
        assertEquals("CONFIG_BIND_ERROR", result.error.code)
        assertEquals("map", result.error.meta["collectionType"])
        val failures = result.error.meta["failures"] as? List<*>
        assertNotNull(failures)
        assertEquals(1, failures.size)
    }

    @Test
    fun getEntryConfigsAsMapResultLenientSkipsInvalidEntries() {
        val resolver = createResolver(
            "providers.primary.type" to "software",
            "providers.primary.keysize" to 256,
            "providers.backup.type" to "unknown",
            "providers.backup.endpoint" to "https://api.example.com"
        )
        val binder = DefaultPolymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson
        )

        val result = binder.getEntryConfigsAsMapResult(resolver, strict = false)

        assertTrue(result.isOk)
        assertEquals(1, result.value.size)
        assertTrue(result.value["primary"] is SoftwareProviderConfig)
    }

    @Test
    fun handlesEmptyProperties() {
        val resolver = createResolver()
        val binder = DefaultPolymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson
        )

        val entryIds = binder.getEntryIds(resolver)
        val configs = binder.getEntryConfigs(resolver)

        assertTrue(entryIds.isEmpty())
        assertTrue(configs.isEmpty())
    }

    @Test
    fun autoPopulatesIdField() {
        val resolver = createResolver(
            "providers.myprovider.type" to "software",
            "providers.myprovider.keysize" to 256
            // Note: no explicit id field
        )
        val binder = DefaultPolymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson,
            idFieldName = "id"
        )

        val config = binder.getEntryConfig(resolver, "myprovider")

        assertNotNull(config)
        assertEquals("myprovider", config.id)
    }

    @Test
    fun respectsExplicitIdField() {
        val resolver = createResolver(
            "providers.myprovider.type" to "software",
            "providers.myprovider.id" to "explicit-id",
            "providers.myprovider.keysize" to 256
        )
        val binder = DefaultPolymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson,
            idFieldName = "id"
        )

        val config = binder.getEntryConfig(resolver, "myprovider")

        assertNotNull(config)
        assertEquals("explicit-id", config.id)
    }

    @Test
    fun handlesHyphenatedPropertyKeys() {
        // Hyphenated keys get normalized: software-primary-provider becomes software.primary.provider
        // So the entry ID will be software.primary.provider
        val resolver = createResolver(
            "providers.software-primary-provider.type" to "software",
            "providers.software-primary-provider.key-size" to 512
        )
        val binder = DefaultPolymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson
        )

        // The entry ID is the normalized version
        val config = binder.getEntryConfig(resolver, "software.primary.provider")

        assertNotNull(config)
        assertTrue(config is SoftwareProviderConfig)
        // The auto-populated ID uses the original entry ID from detection
        assertEquals("software.primary.provider", config.id)
    }

    @Test
    fun excludesNestedTypeFields() {
        val resolver = createResolver(
            "providers.myprovider.type" to "software",
            "providers.myprovider.keystore.type" to "pkcs12",
            "providers.myprovider.keysize" to 256
        )
        val binder = DefaultPolymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson,
            entryDetection = TypeSuffixEntryDetection(
                discriminatorSuffix = ".type",
                excludedSuffixes = setOf(".keystore.type")
            )
        )

        val entryIds = binder.getEntryIds(resolver)

        assertEquals(1, entryIds.size)
        assertTrue(entryIds.contains("myprovider"))
    }

    @Test
    fun mixedSourcePrecedenceKeepsProviderMapValueOverPropertyFileFlatAlias() {
        val providerMapSource = MutableMapPropertySource(
            name = "provider-map",
            order = Order.MEDIUM.orderValue
        ).addProperties(
            mapOf(
                "providers.primary.type" to "software",
                "providers.primary.key.size" to 768
            )
        )
        val propertyFileSource = MapPropertySource(
            name = "property-file",
            source = mapOf(
                "providers.primary.keysize" to 256
            ),
            order = Order.LOW.orderValue
        )
        val resolver = createResolverFromSources(providerMapSource, propertyFileSource)
        val binder = DefaultPolymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson,
            propertyNameAliases = mapOf("keysize" to "keySize")
        )

        val config = binder.getEntryConfig(resolver, "primary")

        assertNotNull(config)
        assertTrue(config is SoftwareProviderConfig)
        assertEquals(768, (config as SoftwareProviderConfig).keySize)
    }

    @Test
    fun mixedSourcePrecedenceKeepsProviderMapFlatAliasOverPropertyFileDottedVariant() {
        val providerMapSource = MutableMapPropertySource(
            name = "provider-map",
            order = Order.MEDIUM.orderValue
        ).addProperties(
            mapOf(
                "providers.primary.type" to "software",
                "providers.primary.keysize" to 640
            )
        )
        val propertyFileSource = MapPropertySource(
            name = "property-file",
            source = mapOf(
                "providers.primary.key.size" to 256
            ),
            order = Order.LOW.orderValue
        )
        val resolver = createResolverFromSources(providerMapSource, propertyFileSource)
        val binder = DefaultPolymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson,
            propertyNameAliases = mapOf("keysize" to "keySize")
        )

        val config = binder.getEntryConfig(resolver, "primary")

        assertNotNull(config)
        assertTrue(config is SoftwareProviderConfig)
        assertEquals(640, (config as SoftwareProviderConfig).keySize)
    }
}

/**
 * Tests for PropertyResolver.getConfigMapPolymorphic extension.
 */
class PropertyResolverPolymorphicExtensionTest {

    private fun createResolver(vararg properties: Pair<String, Any>): PropertyResolver {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        return PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
    }

    @Test
    fun getConfigMapPolymorphicReturnsMap() {
        val resolver = createResolver(
            "providers.primary.type" to "software",
            "providers.primary.keysize" to 256,
            "providers.backup.type" to "rest",
            "providers.backup.endpoint" to "https://api.example.com"
        )

        val configMap = resolver.getConfigMapPolymorphic(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson
        )

        assertEquals(2, configMap.size)
        assertTrue(configMap.containsKey("primary"))
        assertTrue(configMap.containsKey("backup"))
        assertTrue(configMap["primary"] is SoftwareProviderConfig)
        assertTrue(configMap["backup"] is RestProviderConfig)
    }

    @Test
    fun getConfigMapPolymorphicReturnsEmptyMapForNoMatches() {
        val resolver = createResolver(
            "other.key" to "value"
        )

        val configMap = resolver.getConfigMapPolymorphic(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson
        )

        assertTrue(configMap.isEmpty())
    }

    @Test
    fun getConfigMapPolymorphicSupportsLowercaseFlatAliases() {
        val resolver = createResolver(
            "providers.primary.type" to "software",
            "providers.primary.keysize" to 448
        )

        val configMap = resolver.getConfigMapPolymorphic(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson,
            propertyNameAliases = mapOf("keysize" to "keySize")
        )

        assertEquals(1, configMap.size)
        assertTrue(configMap["primary"] is SoftwareProviderConfig)
        val config = configMap["primary"] as SoftwareProviderConfig
        assertEquals(448, config.keySize)
    }

    @Test
    fun getConfigMapPolymorphicResultStrictReturnsDiagnostics() {
        val resolver = createResolver(
            "providers.primary.type" to "software",
            "providers.primary.keysize" to 448,
            "providers.backup.type" to "unknown"
        )

        val result = resolver.getConfigMapPolymorphicResult(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson,
            strict = true
        )

        assertTrue(result.isErr)
        assertEquals("CONFIG_BIND_ERROR", result.error.code)
    }

    @Test
    fun getConfigMapPolymorphicResultLenientSkipsInvalidEntries() {
        val resolver = createResolver(
            "providers.primary.type" to "software",
            "providers.primary.keysize" to 448,
            "providers.backup.type" to "unknown"
        )

        val result = resolver.getConfigMapPolymorphicResult(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson,
            strict = false
        )

        assertTrue(result.isOk)
        assertEquals(1, result.value.size)
        assertTrue(result.value["primary"] is SoftwareProviderConfig)
    }
}

/**
 * Tests for polymorphicConfigBinder factory functions.
 */
class PolymorphicConfigBinderFactoryTest {

    private fun createResolver(vararg properties: Pair<String, Any>): PropertyResolver {
        val source = MutableMapPropertySource("test")
        properties.forEach { (key, value) -> source.addProperty(key, value) }
        return PropertySourcesPropertyResolver(DefaultPropertySources().apply { add(source) })
    }

    @Test
    fun factoryFunctionCreatesWorkingBinder() {
        val resolver = createResolver(
            "providers.primary.type" to "software",
            "providers.primary.keysize" to 256
        )

        val binder = polymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson
        )

        val configs = binder.getEntryConfigs(resolver)

        assertEquals(1, configs.size)
        assertTrue(configs[0] is SoftwareProviderConfig)
    }

    @Test
    fun factoryFunctionWithCustomEntryDetection() {
        val resolver = createResolver(
            "backends.primary.kind" to "software",
            "backends.primary.value" to "test"
        )

        val binder = polymorphicConfigBinder(
            prefix = "backends",
            baseClass = TestProviderConfig::class,
            json = testJson,
            entryDetection = TypeSuffixEntryDetection(discriminatorSuffix = ".kind")
        )

        val entryIds = binder.getEntryIds(resolver)

        assertEquals(1, entryIds.size)
        assertTrue(entryIds.contains("primary"))
    }

    @Test
    fun factoryFunctionSupportsPropertyNameAliases() {
        val resolver = createResolver(
            "providers.primary.type" to "software",
            "providers.primary.keysize" to 521
        )

        val binder = polymorphicConfigBinder(
            prefix = "providers",
            baseClass = TestProviderConfig::class,
            json = testJson,
            propertyNameAliases = mapOf("keysize" to "keySize")
        )

        val config = binder.getEntryConfig(resolver, "primary")

        assertNotNull(config)
        assertTrue(config is SoftwareProviderConfig)
        assertEquals(521, (config as SoftwareProviderConfig).keySize)
    }
}
