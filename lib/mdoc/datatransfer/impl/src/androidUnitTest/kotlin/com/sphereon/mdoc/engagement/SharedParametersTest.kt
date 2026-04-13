package com.sphereon.mdoc.engagement

import com.sphereon.mdoc.engagement.SharedParametersImpl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/**
 * Unit tests for SharedParameters testing business logic:
 * - UUID regeneration produces different values
 * - Key alias regeneration
 * - Same UUID configuration for both modes
 * - Independent UUID generation by default
 */
class SharedParametersTest {

    @Test
    fun `regenerate should create new central client UUID`() = runTest {
        // Given
        val params = SharedParametersImpl()
        val oldCentralUuid = params.bleCentralClientUuid.value

        // When
        params.regenerate()

        // Then
        val newCentralUuid = params.bleCentralClientUuid.value
        assertNotEquals(oldCentralUuid, newCentralUuid, "Central UUID should be regenerated")
    }

    @Test
    fun `regenerate should create new peripheral server UUID`() = runTest {
        // Given
        val params = SharedParametersImpl()
        val oldPeripheralUuid = params.blePeripheralServerUuid.value

        // When
        params.regenerate()

        // Then
        val newPeripheralUuid = params.blePeripheralServerUuid.value
        assertNotEquals(oldPeripheralUuid, newPeripheralUuid, "Peripheral UUID should be regenerated")
    }

    @Test
    fun `regenerate should create new ephemeral key alias`() = runTest {
        // Given
        val params = SharedParametersImpl()
        val oldKeyAlias = params.ephemeralKeyAlias.value

        // When
        params.regenerate()

        // Then
        val newKeyAlias = params.ephemeralKeyAlias.value
        assertNotEquals(oldKeyAlias, newKeyAlias, "Ephemeral key alias should be regenerated")
    }

    @Test
    fun `regenerate should create all different values`() = runTest {
        // Given
        val params = SharedParametersImpl()
        val oldCentralUuid = params.bleCentralClientUuid.value
        val oldPeripheralUuid = params.blePeripheralServerUuid.value
        val oldKeyAlias = params.ephemeralKeyAlias.value

        // When
        params.regenerate()

        // Then - all three values should be different
        assertNotEquals(oldCentralUuid, params.bleCentralClientUuid.value)
        assertNotEquals(oldPeripheralUuid, params.blePeripheralServerUuid.value)
        assertNotEquals(oldKeyAlias, params.ephemeralKeyAlias.value)
    }

    @Test
    fun `useSameUuidForBothModes should sync UUIDs`() = runTest {
        // Given
        val params = SharedParametersImpl()

        // Initially different UUIDs
        assertNotEquals(
            params.bleCentralClientUuid.value,
            params.blePeripheralServerUuid.value,
            "Initial UUIDs should be different"
        )

        // When
        params.useSameUuidForBothModes()

        // Then
        assertEquals(
            params.bleCentralClientUuid.value,
            params.blePeripheralServerUuid.value,
            "Both UUIDs should be the same after sync"
        )
    }

    @Test
    fun `useSameUuidForBothModes with provided UUID should use that UUID`() = runTest {
        // Given
        val params = SharedParametersImpl()
        val customUuid = Uuid.random()

        // When
        params.useSameUuidForBothModes(customUuid)

        // Then
        assertEquals(customUuid, params.bleCentralClientUuid.value, "Central UUID should be the custom UUID")
        assertEquals(customUuid, params.blePeripheralServerUuid.value, "Peripheral UUID should be the custom UUID")
        assertEquals(
            params.bleCentralClientUuid.value,
            params.blePeripheralServerUuid.value,
            "Both UUIDs should match"
        )
    }

    @Test
    fun `useSameUuidForBothModes with null UUID should generate new matching UUIDs`() = runTest {
        // Given
        val params = SharedParametersImpl()
        val oldCentralUuid = params.bleCentralClientUuid.value
        val oldPeripheralUuid = params.blePeripheralServerUuid.value

        // When
        params.useSameUuidForBothModes(null)

        // Then - both should be new and matching
        val newCentralUuid = params.bleCentralClientUuid.value
        val newPeripheralUuid = params.blePeripheralServerUuid.value

        assertEquals(newCentralUuid, newPeripheralUuid, "New UUIDs should match")
        assertNotEquals(oldCentralUuid, newCentralUuid, "Central UUID should be new")
        assertNotEquals(oldPeripheralUuid, newPeripheralUuid, "Peripheral UUID should be new")
    }

    @Test
    fun `ephemeral key alias should have ephemeral-key prefix`() = runTest {
        // Given
        val params = SharedParametersImpl()

        // When
        val keyAlias = params.ephemeralKeyAlias.value

        // Then
        assertTrue(
            keyAlias.startsWith("ephemeral-key-"),
            "Key alias should start with 'ephemeral-key-' prefix, got: $keyAlias"
        )
    }

    @Test
    fun `regenerate should change ephemeral key alias but keep prefix`() = runTest {
        // Given
        val params = SharedParametersImpl()
        val oldKeyAlias = params.ephemeralKeyAlias.value

        // When
        params.regenerate()

        // Then
        val newKeyAlias = params.ephemeralKeyAlias.value
        assertNotEquals(oldKeyAlias, newKeyAlias, "Key alias should change")
        assertTrue(
            newKeyAlias.startsWith("ephemeral-key-"),
            "New key alias should still have prefix"
        )
    }

    @Test
    fun `initial UUIDs should be different for central and peripheral`() = runTest {
        // Given/When
        val params = SharedParametersImpl()

        // Then - by default, the two UUIDs should be different
        assertNotEquals(
            params.bleCentralClientUuid.value,
            params.blePeripheralServerUuid.value,
            "Default UUIDs should be different for central and peripheral modes"
        )
    }

    @Test
    fun `multiple regenerations should produce different values each time`() = runTest {
        // Given
        val params = SharedParametersImpl()
        val uuids = mutableSetOf<Uuid>()
        val aliases = mutableSetOf<String>()

        // When - regenerate multiple times
        repeat(5) {
            params.regenerate()
            uuids.add(params.bleCentralClientUuid.value)
            uuids.add(params.blePeripheralServerUuid.value)
            aliases.add(params.ephemeralKeyAlias.value)
        }

        // Then - all values should be unique (10 UUIDs, 5 aliases)
        assertEquals(10, uuids.size, "All regenerated UUIDs should be unique")
        assertEquals(5, aliases.size, "All regenerated aliases should be unique")
    }
}
