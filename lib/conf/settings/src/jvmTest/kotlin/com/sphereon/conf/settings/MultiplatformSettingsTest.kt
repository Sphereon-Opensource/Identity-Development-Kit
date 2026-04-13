/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.conf.settings

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultiplatformSettingsTest {
    private lateinit var appGraph: JvmMPSettingsAppGraph
    private lateinit var multiplatformSettings: MultiplatformSettings

    @BeforeTest
    fun setup() {
        runBlocking {
            appGraph =
                createJvmMPSettingsAppGraph(
                    application = this@MultiplatformSettingsTest,
                    appId = "mp-settings-test",
                    profile = "test",
                    version = "0.0.1",
                )

            val contextGraph =
                appGraph.userContextManager.createOrGetFromInputs(
                    DefaultTenantInputString("test@principal.com"),
                    DefaultPrincipalInputString("test@principal.com"),
                )

            val context = contextGraph.context

            multiplatformSettings =
                MultiplatformSettings(
                    app = appGraph,
                    configLevel = ConfigLevel.PRINCIPAL,
                    userContext = context,
                )
        }
    }

    @Test
    fun testPlatformSupported() {
        assertTrue(multiplatformSettings.isPlatformSupported, "MultiplatformSettings should be supported")
    }

    @Test
    fun testIntOperations() {
        val key = "test.int"
        val value = 42

        multiplatformSettings.set(key, value)
        assertEquals(value, multiplatformSettings.get<Int>(key))
        assertTrue(multiplatformSettings.getKeys().contains(key))

        multiplatformSettings.set<Int>(key, null)
        assertNull(multiplatformSettings.get<Int>(key))
        assertFalse(multiplatformSettings.getKeys().contains(key))
    }

    @Test
    fun testLongOperations() {
        val key = "test.long"
        val value = 9876543210L

        multiplatformSettings.set(key, value)
        assertEquals(value, multiplatformSettings.get<Long>(key))
        assertTrue(multiplatformSettings.getKeys().contains(key))

        multiplatformSettings.set<Long>(key, null)
        assertNull(multiplatformSettings.get<Long>(key))
        assertFalse(multiplatformSettings.getKeys().contains(key))
    }

    @Test
    fun testStringOperations() {
        val key = "test.string"
        val value = "Hello, World!"

        multiplatformSettings.set(key, value)
        assertEquals(value, multiplatformSettings.get<String>(key))
        assertTrue(multiplatformSettings.getKeys().contains(key))

        multiplatformSettings.remove(key)
        assertNull(multiplatformSettings.get<String>(key))
        assertFalse(multiplatformSettings.getKeys().contains(key))
    }

    @Test
    fun testFloatOperations() {
        val key = "test.float"
        val value = 3.14f

        multiplatformSettings.set(key, value)
        assertEquals(value, multiplatformSettings.get<Float>(key))
        assertTrue(multiplatformSettings.getKeys().contains(key))

        multiplatformSettings.set<Float>(key, null)
        assertNull(multiplatformSettings.get<Float>(key))
        assertFalse(multiplatformSettings.getKeys().contains(key))
    }

    @Test
    fun testDoubleOperations() {
        val key = "test.double"
        val value = 2.718281828

        multiplatformSettings.set(key, value)
        assertEquals(value, multiplatformSettings.get<Double>(key))
        assertTrue(multiplatformSettings.getKeys().contains(key))

        multiplatformSettings.set<Double>(key, null)
        assertNull(multiplatformSettings.get<Double>(key))
        assertFalse(multiplatformSettings.getKeys().contains(key))
    }

    @Test
    fun testBooleanOperations() {
        val key = "test.boolean"
        val value = true

        multiplatformSettings.set(key, value)
        assertEquals(value, multiplatformSettings.get<Boolean>(key))
        assertTrue(multiplatformSettings.getKeys().contains(key))

        multiplatformSettings.set<Boolean>(key, null)
        assertNull(multiplatformSettings.get<Boolean>(key))
        assertFalse(multiplatformSettings.getKeys().contains(key))
    }

    @Test
    fun testAllTypesWithDefaultValues() {
        // Test with default values when keys don't exist
        assertEquals(100, multiplatformSettings.get("nonexistent.int", 100))
        assertEquals(200L, multiplatformSettings.get("nonexistent.long", 200L))
        assertEquals("default", multiplatformSettings.get("nonexistent.string", "default"))
        assertEquals(1.5f, multiplatformSettings.get("nonexistent.float", 1.5f))
        assertEquals(2.5, multiplatformSettings.get("nonexistent.double", 2.5))
        assertEquals(false, multiplatformSettings.get("nonexistent.boolean", false))
    }

    @Test
    fun testGetAsStringForAllTypes() {
        // Set values of different types
        multiplatformSettings.set("int.val", 123)
        multiplatformSettings.set("long.val", 456L)
        multiplatformSettings.set("string.val", "hello")
        multiplatformSettings.set("float.val", 7.89f)
        multiplatformSettings.set("double.val", 12.34)
        multiplatformSettings.set("boolean.val", true)

        // Test getAsString conversion
        assertEquals("123", multiplatformSettings.getAsString("int.val"))
        assertEquals("456", multiplatformSettings.getAsString("long.val"))
        assertEquals("hello", multiplatformSettings.getAsString("string_val"))
        assertEquals("7.89", multiplatformSettings.getAsString("float_val"))
        assertEquals("12.34", multiplatformSettings.getAsString("double_val"))
        assertEquals("true", multiplatformSettings.getAsString("boolean_val"))
        assertNull(multiplatformSettings.getAsString("nonexistent"))

        // Cleanup
        listOf("int_val", "long_val", "string_val", "float_val", "double_val", "boolean_val")
            .forEach { multiplatformSettings.set<String>(it, null) }
    }

    @Test
    fun testMultipleKeysTracking() {
        val keys = listOf("key1", "key2", "key3", "key4", "key5", "key6")
        val values = listOf("value1", 123, 456L, 7.89f, 12.34, true)

        // Set multiple values of different types
        multiplatformSettings.set(keys[0], values[0] as String)
        multiplatformSettings.set(keys[1], values[1] as Int)
        multiplatformSettings.set(keys[2], values[2] as Long)
        multiplatformSettings.set(keys[3], values[3] as Float)
        multiplatformSettings.set(keys[4], values[4] as Double)
        multiplatformSettings.set(keys[5], values[5] as Boolean)

        // Verify all keys exist
        val storedKeys = multiplatformSettings.getKeys()
        keys.forEach { key ->
            assertTrue(storedKeys.contains(key), "Key '$key' should exist")
        }

        // Verify values
        assertEquals(values[0] as String, multiplatformSettings.get<String>(keys[0]))
        assertEquals(values[1] as Int, multiplatformSettings.get<Int>(keys[1]))
        assertEquals(values[2] as Long, multiplatformSettings.get<Long>(keys[2]))
        assertEquals(values[3] as Float, multiplatformSettings.get<Float>(keys[3]))
        assertEquals(values[4] as Double, multiplatformSettings.get<Double>(keys[4]))
        assertEquals(values[5] as Boolean, multiplatformSettings.get<Boolean>(keys[5]))

        // Clean up
        keys.forEach { key ->
            multiplatformSettings.remove(key)
        }

        // Verify cleanup
        keys.forEach { key ->
            assertFalse(multiplatformSettings.getKeys().contains(key), "Key '$key' should not exist after cleanup")
        }
    }

    @Test
    fun testOverwriteExistingValue() {
        val key = "test_overwrite"

        // Test overwriting with same type
        multiplatformSettings.set(key, "initial")
        assertEquals("initial", multiplatformSettings.get<String>(key))

        multiplatformSettings.set(key, "updated")
        assertEquals("updated", multiplatformSettings.get<String>(key))

        // Test overwriting with different type
        multiplatformSettings.set(key, 42)
        assertEquals(42, multiplatformSettings.get<Int>(key))

        multiplatformSettings.remove(key)
    }

    @Test
    fun testEdgeCaseValues() {
        // Int edge cases
        multiplatformSettings.set("int_min", Int.MIN_VALUE)
        multiplatformSettings.set("int_max", Int.MAX_VALUE)
        multiplatformSettings.set("int_zero", 0)

        assertEquals(Int.MIN_VALUE, multiplatformSettings.get<Int>("int_min"))
        assertEquals(Int.MAX_VALUE, multiplatformSettings.get<Int>("int_max"))
        assertEquals(0, multiplatformSettings.get<Int>("int_zero"))

        // Long edge cases
        multiplatformSettings.set("long_min", Long.MIN_VALUE)
        multiplatformSettings.set("long_max", Long.MAX_VALUE)

        assertEquals(Long.MIN_VALUE, multiplatformSettings.get<Long>("long_min"))
        assertEquals(Long.MAX_VALUE, multiplatformSettings.get<Long>("long_max"))

        // Float edge cases
        multiplatformSettings.set("float_min", Float.MIN_VALUE)
        multiplatformSettings.set("float_max", Float.MAX_VALUE)
        multiplatformSettings.set("float_nan", Float.NaN)
        multiplatformSettings.set("float_inf", Float.POSITIVE_INFINITY)
        multiplatformSettings.set("float_neg_inf", Float.NEGATIVE_INFINITY)

        assertEquals(Float.MIN_VALUE, multiplatformSettings.get<Float>("float_min"))
        assertEquals(Float.MAX_VALUE, multiplatformSettings.get<Float>("float_max"))
        assertTrue(multiplatformSettings.get<Float>("float_nan")?.isNaN() == true)
        assertEquals(Float.POSITIVE_INFINITY, multiplatformSettings.get<Float>("float_inf"))
        assertEquals(Float.NEGATIVE_INFINITY, multiplatformSettings.get<Float>("float_neg_inf"))

        // Double edge cases
        multiplatformSettings.set("double_min", Double.MIN_VALUE)
        multiplatformSettings.set("double_max", Double.MAX_VALUE)
        multiplatformSettings.set("double_nan", Double.NaN)

        assertEquals(Double.MIN_VALUE, multiplatformSettings.get<Double>("double_min"))
        assertEquals(Double.MAX_VALUE, multiplatformSettings.get<Double>("double_max"))
        assertTrue(multiplatformSettings.get<Double>("double_nan")?.isNaN() == true)

        // String edge cases
        multiplatformSettings.set("empty_string", "")
        multiplatformSettings.set("unicode_string", "🌟 Unicode test 中文 العربية")
        multiplatformSettings.set("special_chars", "!@#$%^&*()_+-=[]{}|;':\",./<>?")

        assertEquals("", multiplatformSettings.get<String>("empty_string"))
        assertEquals("🌟 Unicode test 中文 العربية", multiplatformSettings.get<String>("unicode_string"))
        assertEquals("!@#$%^&*()_+-=[]{}|;':\",./<>?", multiplatformSettings.get<String>("special_chars"))

        // Clean up
        listOf(
            "int_min",
            "int_max",
            "int_zero",
            "long_min",
            "long_max",
            "float_min",
            "float_max",
            "float_nan",
            "float_inf",
            "float_neg_inf",
            "double_min",
            "double_max",
            "double_nan",
            "empty_string",
            "unicode_string",
            "special_chars",
        ).forEach { key ->
            multiplatformSettings.remove(key)
        }
    }

    @Test
    fun testNamespaceIsolation() {
        runBlocking {
            // Create a second instance with different tenant/principal
            val contextGraph2 =
                appGraph.userContextManager
                    .createOrGetFromInputs(
                        DefaultTenantInputString("different@tenant.com"),
                        DefaultPrincipalInputString("different@principal.com"),
                    )

            val multiplatformSettings2 =
                MultiplatformSettings(
                    app = appGraph,
                    configLevel = ConfigLevel.PRINCIPAL,
                    userContext = contextGraph2.context,
                )

            val key = "namespace.test"
            val value1 = "value.from.namespace.1"
            val value2 = "value.from.namespace.2"

            // Set same key in both namespaces
            multiplatformSettings.set(key, value1)
            multiplatformSettings2.set(key, value2)

            // Verify isolation - each namespace should have its own value
            assertEquals(value1, multiplatformSettings.get<String>(key))
            assertEquals(value2, multiplatformSettings2.get<String>(key))

            // Verify keys are isolated
            assertTrue(multiplatformSettings.getKeys().contains(key))
            assertTrue(multiplatformSettings2.getKeys().contains(key))

            // Remove from first namespace using remove() method
            multiplatformSettings.remove(key)
            assertNull(multiplatformSettings.get<String>(key))
            assertFalse(multiplatformSettings.getKeys().contains(key))
            // Second namespace should still have its value
            assertEquals(value2, multiplatformSettings2.get<String>(key))
            assertTrue(multiplatformSettings2.getKeys().contains(key))

            // Remove from second namespace using set(null)
            multiplatformSettings2.set<String>(key, null)
            assertNull(multiplatformSettings2.get<String>(key))
            assertFalse(multiplatformSettings2.getKeys().contains(key))
            // First namespace should still be empty
            assertNull(multiplatformSettings.get<String>(key))
        }
    }

    @Test
    fun testNamespaceIsolationWithDifferentAppIds() {
        runBlocking {
            // Create a second app graph with different appId
            val appGraph2 =
                createJvmMPSettingsAppGraph(
                    application = this@MultiplatformSettingsTest,
                    appId = "different-app-id",
                    profile = "test",
                    version = "0.0.1",
                )

            val contextGraph2 =
                appGraph2.userContextManager
                    .createOrGetFromInputs(
                        DefaultTenantInputString("test@principal.com"),
                        DefaultPrincipalInputString("test@principal.com"),
                    )

            val multiplatformSettings2 =
                MultiplatformSettings(
                    app = appGraph2,
                    configLevel = ConfigLevel.PRINCIPAL,
                    userContext = contextGraph2.context,
                )

            val key = "app_isolation_test"
            val value1 = "app1_value"
            val value2 = "app2_value"

            // Set same key in both app namespaces
            multiplatformSettings.set(key, value1)
            multiplatformSettings2.set(key, value2)

            // Verify isolation between different apps
            assertEquals(value1, multiplatformSettings.get<String>(key))
            assertEquals(value2, multiplatformSettings2.get<String>(key))

            // Clean up
            multiplatformSettings.remove(key)
            multiplatformSettings2.set<String>(key, null)
        }
    }
}
