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

package com.sphereon.mdoc.transport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for NoOpTransportFactory class.
 *
 * Note: Since ConnectionMethod is a sealed interface defined in this module,
 * we cannot create test implementations. Tests for supports() and createTransfer()
 * that require ConnectionMethod instances are in the transport-specific modules
 * where actual ConnectionMethod implementations exist.
 */
class NoOpTransportFactoryTest {

    @Test
    fun testTransportTypeReturnsBle() {
        val factory = NoOpTransportFactory()
        // NoOpTransportFactory returns BLE as a dummy value since it's never actually used
        assertEquals(TransportType.BLE, factory.transportType)
    }

    @Test
    fun testGetConnectionMethodFactoryReturnsNull() {
        val factory = NoOpTransportFactory()
        // NoOpTransportFactory doesn't provide a connection method factory
        assertNull(factory.getConnectionMethodFactory())
    }

    @Test
    fun testFactoryCanBeInstantiated() {
        // Verify the factory can be instantiated without issues
        val factory = NoOpTransportFactory()
        // Just verify it exists and has the expected interface
        assertEquals(TransportType.BLE, factory.transportType)
        assertNull(factory.getConnectionMethodFactory())
    }
}
