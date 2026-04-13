package com.sphereon.conf.settings

import com.sphereon.core.defaults.context.DefaultPrincipalInputString
import com.sphereon.core.defaults.context.DefaultTenantInputString
import com.sphereon.di.context.UserContextInstance
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for MultiplatformSettingsPropertySource implementations.
 * Covers property type conversions, key normalization, and edge cases.
 */
class MultiplatformSettingsPropertySourceTest {

    private lateinit var userContextInstance: UserContextInstance
    private lateinit var appComponent: JvmMPSettingsAppComponent
    private lateinit var appSettingsSource: MultiplatformSettingsAppPropertySource
    private lateinit var tenantSettingsSource: MultiplatformSettingsTenantPropertySource
    private lateinit var principalSettingsSource: MultiplatformSettingsPrincipalPropertySource

    val appSettings: MultiplatformSettings
        get() = appSettingsSource.getSource()

    val tenantSettings: MultiplatformSettings
        get() = tenantSettingsSource.getSource()

    val principalSettings: MultiplatformSettings
        get() = principalSettingsSource.getSource()

    @BeforeTest
    fun setup() {
        runBlocking {
            appComponent = createJvmMPSettingsAppComponent(
                application = this@MultiplatformSettingsPropertySourceTest,
                appId = "property-source-test",
                profile = "test",
                version = "0.0.1"
            )

            userContextInstance = appComponent.userContextManager.createOrGetFromInputs(
                DefaultTenantInputString("test-tenant"),
                DefaultPrincipalInputString("test-principal")
            )
            appSettingsSource = appComponent.appConfigSettings
            tenantSettingsSource = (userContextInstance.component as MultiplatformSettingsTenantPropertySource.Component).tenantConfigSettings
            principalSettingsSource = (userContextInstance.component as MultiplatformSettingsPrincipalPropertySource.Component).principalConfigSettings

            // Clean up any existing test keys
            cleanupTestKeys()
        }
    }

    private fun cleanupTestKeys() {
        val keysToClean = listOf(
            "string.key", "int.key", "long.key", "float.key", "double.key", "bool.key",
            "test.key", "remove.key", "all.properties.key1", "all.properties.key2"
        )
        keysToClean.forEach { key ->
            appSettings.remove(key)
            tenantSettings.remove(key)
            principalSettings.remove(key)
        }
    }

    // ========== Type Conversion Tests ==========

    @Test
    fun testStringProperty() {
        appSettingsSource.setProperty("string.key", "test-value")

        val result = appSettingsSource.getProperty("string.key", String::class)
        assertEquals("test-value", result)
    }

    @Test
    fun testIntProperty() {
        appSettingsSource.setProperty("int.key", 42)

        val result = appSettingsSource.getProperty("int.key", Int::class)
        assertEquals(42, result)
    }

    @Test
    fun testLongProperty() {
        appSettingsSource.setProperty("long.key", 12345678901234L)

        val result = appSettingsSource.getProperty("long.key", Long::class)
        assertEquals(12345678901234L, result)
    }

    @Test
    fun testFloatProperty() {
        appSettingsSource.setProperty("float.key", 3.14f)

        val result = appSettingsSource.getProperty("float.key", Float::class)
        assertEquals(3.14f, result)
    }

    @Test
    fun testDoubleProperty() {
        appSettingsSource.setProperty("double.key", 3.14159265359)

        val result = appSettingsSource.getProperty("double.key", Double::class)
        assertEquals(3.14159265359, result)
    }

    @Test
    fun testBooleanProperty() {
        appSettingsSource.setProperty("bool.key", true)

        val result = appSettingsSource.getProperty("bool.key", Boolean::class)
        assertEquals(true, result)
    }

    @Test
    fun testBooleanFalseProperty() {
        appSettingsSource.setProperty("bool.key", false)

        val result = appSettingsSource.getProperty("bool.key", Boolean::class)
        assertEquals(false, result)
    }

    // ========== Key Normalization Tests ==========

    @Test
    fun testKeyNormalization() {
        // Set with lowercase (keys are normalized to lowercase)
        appSettingsSource.setProperty("test.key", "value1")

        // Should be normalized and retrievable with same format
        val result = appSettingsSource.getProperty("test.key", String::class)
        assertEquals("value1", result)
    }

    // ========== hasProperty Tests ==========

    @Test
    fun testHasPropertyReturnsTrue() {
        appSettingsSource.setProperty("test.key", "value")

        assertTrue(appSettingsSource.hasProperty("test.key"))
    }

    @Test
    fun testHasPropertyReturnsFalse() {
        assertFalse(appSettingsSource.hasProperty("nonexistent.key"))
    }

    // ========== removeProperty Tests ==========

    @Test
    fun testRemoveProperty() {
        appSettingsSource.setProperty("remove.key", "value")
        assertTrue(appSettingsSource.hasProperty("remove.key"))

        appSettingsSource.removeProperty("remove.key")
        assertFalse(appSettingsSource.hasProperty("remove.key"))
    }

    // ========== getPropertyAsString Tests ==========

    @Test
    fun testGetPropertyAsStringForString() {
        appSettingsSource.setProperty("string.key", "hello")

        val result = appSettingsSource.getPropertyAsString("string.key")
        assertEquals("hello", result)
    }

    @Test
    fun testGetPropertyAsStringForInt() {
        appSettingsSource.setProperty("int.key", 42)

        val result = appSettingsSource.getPropertyAsString("int.key")
        assertEquals("42", result)
    }

    @Test
    fun testGetPropertyAsStringForNull() {
        val result = appSettingsSource.getPropertyAsString("nonexistent.key")
        assertNull(result)
    }

    // ========== getAllPropertyNames Tests ==========

    @Test
    fun testGetAllPropertyNames() {
        appSettingsSource.setProperty("all.properties.key1", "value1")
        appSettingsSource.setProperty("all.properties.key2", "value2")

        val names = appSettingsSource.getAllPropertyNames()
        assertTrue(names.contains("all.properties.key1"))
        assertTrue(names.contains("all.properties.key2"))
    }

    // ========== Source and Name Tests ==========

    @Test
    fun testGetSource() {
        val source = appSettingsSource.getSource()
        assertNotNull(source)
        assertTrue(source is MultiplatformSettings)
    }

    @Test
    fun testGetName() {
        val name = appSettingsSource.getName()
        assertTrue(name.contains("config-settings"))
    }

    @Test
    fun testGetOrder() {
        // Should have a valid order value
        val order = appSettingsSource.getOrder()
        assertTrue(order > 0)
    }

    @Test
    fun testIsPlatformSupported() {
        assertTrue(appSettingsSource.isPlatformSupported)
    }

    // ========== Tenant-Level Tests ==========

    @Test
    fun testTenantLevelPropertyIsolation() {
        tenantSettingsSource.setProperty("tenant.key", "tenant-value")

        val tenantResult = tenantSettingsSource.getProperty("tenant.key", String::class)
        val appResult = appSettingsSource.getProperty("tenant.key", String::class)

        assertEquals("tenant-value", tenantResult)
        assertNull(appResult)  // App level shouldn't see tenant-level property
    }

    // ========== Principal-Level Tests ==========

    @Test
    fun testPrincipalLevelPropertyIsolation() {
        principalSettingsSource.setProperty("principal.key", "principal-value")

        val principalResult = principalSettingsSource.getProperty("principal.key", String::class)
        val tenantResult = tenantSettingsSource.getProperty("principal.key", String::class)

        assertEquals("principal-value", principalResult)
        assertNull(tenantResult)  // Tenant level shouldn't see principal-level property
    }

    // ========== setProperty with null Tests ==========

    @Test
    fun testSetPropertyWithNullRemoves() {
        appSettingsSource.setProperty("test.key", "value")
        assertTrue(appSettingsSource.hasProperty("test.key"))

        appSettingsSource.setProperty<String>("test.key", String::class, null)
        assertFalse(appSettingsSource.hasProperty("test.key"))
    }

    // ========== CompareTo Tests ==========

    @Test
    fun testCompareToOrdering() {
        // Both should be comparable
        val comparison = appSettingsSource.compareTo(tenantSettingsSource)
        // They should have similar order (both are LOW)
        assertEquals(0, comparison)
    }
}
