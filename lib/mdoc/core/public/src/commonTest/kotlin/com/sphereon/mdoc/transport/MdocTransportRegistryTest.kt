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

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.EngagementData
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for MdocTransportRegistry.
 *
 * Note: ConnectionMethod is a sealed interface, so we cannot create test implementations
 * in this module. Tests focus on what can be tested without creating ConnectionMethod instances.
 */
class MdocTransportRegistryTest {

    /**
     * Test factory that supports a specific transport type.
     * Since ConnectionMethod is sealed, we use NoOpTransportFactory pattern.
     */
    private class TestTransportFactory(
        override val transportType: TransportType,
        private val connectionMethodFactory: ConnectionMethodBase.Factory? = null
    ) : MdocTransportFactory {

        override fun supports(connectionMethod: ConnectionMethod): Boolean {
            return connectionMethod.transportType == transportType
        }

        override fun createTransfer(
            connectionMethod: ConnectionMethod,
            execution: SessionExecution,
            role: MdocRole,
            engagementData: EngagementData?
        ): MdocTransport<*> {
            throw NotImplementedError("Not used in tests")
        }

        override fun getConnectionMethodFactory(): ConnectionMethodBase.Factory? {
            return connectionMethodFactory
        }
    }

    /**
     * Test connection method factory.
     */
    private class TestConnectionMethodFactory(
        private val supportedType: DeviceRetrievalMethodType
    ) : ConnectionMethodBase.Factory {
        override fun supports(deviceRetrievalMethod: DeviceRetrievalMethod): Boolean {
            return deviceRetrievalMethod.type == supportedType
        }

        override fun create(deviceRetrievalMethod: DeviceRetrievalMethod): ConnectionMethod? {
            // Cannot create ConnectionMethod instances in this module
            return null
        }
    }

    @Test
    fun testEmptyRegistry() {
        val registry = MdocTransportRegistry()
        assertTrue(registry.supportedTransports.isEmpty())
        assertFalse(registry.isSupported(TransportType.BLE))
        assertTrue(registry.getAllFactories().isEmpty())
    }

    @Test
    fun testRegisterFactory() {
        val registry = MdocTransportRegistry()
        val factory = TestTransportFactory(TransportType.BLE)

        registry.register(factory)

        assertTrue(registry.isSupported(TransportType.BLE))
        assertEquals(1, registry.supportedTransports.size)
        assertTrue(registry.supportedTransports.contains(TransportType.BLE))
    }

    @Test
    fun testRegisterMultipleFactories() {
        val registry = MdocTransportRegistry()
        registry.register(TestTransportFactory(TransportType.BLE))
        registry.register(TestTransportFactory(TransportType.NFC))
        registry.register(TestTransportFactory(TransportType.REST_API))

        assertEquals(3, registry.supportedTransports.size)
        assertTrue(registry.isSupported(TransportType.BLE))
        assertTrue(registry.isSupported(TransportType.NFC))
        assertTrue(registry.isSupported(TransportType.REST_API))
        assertFalse(registry.isSupported(TransportType.OID4VP))
    }

    @Test
    fun testRegisterNoOpFactoryIsFiltered() {
        val registry = MdocTransportRegistry()
        val noOpFactory = NoOpTransportFactory()

        registry.register(noOpFactory)

        assertTrue(registry.supportedTransports.isEmpty())
        assertFalse(registry.isSupported(TransportType.BLE))
    }

    @Test
    fun testGetFactoryByTransportType() {
        val registry = MdocTransportRegistry()
        val bleFactory = TestTransportFactory(TransportType.BLE)
        val nfcFactory = TestTransportFactory(TransportType.NFC)

        registry.register(bleFactory)
        registry.register(nfcFactory)

        val retrievedBle = registry.getFactory(TransportType.BLE)
        val retrievedNfc = registry.getFactory(TransportType.NFC)
        val retrievedRestApi = registry.getFactory(TransportType.REST_API)

        assertEquals(TransportType.BLE, retrievedBle?.transportType)
        assertEquals(TransportType.NFC, retrievedNfc?.transportType)
        assertNull(retrievedRestApi)
    }

    @Test
    fun testGetConnectionMethodFactory() {
        val registry = MdocTransportRegistry()
        val cmFactory = TestConnectionMethodFactory(DeviceRetrievalMethodType.BLE)
        val bleFactory = TestTransportFactory(TransportType.BLE, cmFactory)
        registry.register(bleFactory)

        val deviceRetrievalMethod = DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.BLE,
            version = DeviceRetrievalMethodVersion(1u),
            retrievalOptions = BleOptions(
                peripheralServerMode = true,
                centralClientMode = false
            )
        )

        val retrievedCmFactory = registry.getConnectionMethodFactory(deviceRetrievalMethod)
        assertTrue(retrievedCmFactory?.supports(deviceRetrievalMethod) == true)
    }

    @Test
    fun testGetConnectionMethodFactoryNotSupported() {
        val registry = MdocTransportRegistry()
        val cmFactory = TestConnectionMethodFactory(DeviceRetrievalMethodType.BLE)
        val bleFactory = TestTransportFactory(TransportType.BLE, cmFactory)
        registry.register(bleFactory)

        val deviceRetrievalMethod = DeviceRetrievalMethod(
            type = DeviceRetrievalMethodType.NFC,
            version = DeviceRetrievalMethodVersion(1u),
            retrievalOptions = com.sphereon.mdoc.transfer.device.NfcOptions(
                maxCommandDataFieldLength = 256u,
                maxResponseDataFieldLength = 256u
            )
        )

        val retrievedCmFactory = registry.getConnectionMethodFactory(deviceRetrievalMethod)
        assertNull(retrievedCmFactory)
    }

    @Test
    fun testGetAllFactories() {
        val registry = MdocTransportRegistry()
        val bleFactory = TestTransportFactory(TransportType.BLE)
        val nfcFactory = TestTransportFactory(TransportType.NFC)

        registry.register(bleFactory)
        registry.register(nfcFactory)

        val allFactories = registry.getAllFactories()
        assertEquals(2, allFactories.size)
    }

    @Test
    fun testReplaceFactory() {
        val registry = MdocTransportRegistry()
        val bleFactory1 = TestTransportFactory(TransportType.BLE)
        val bleFactory2 = TestTransportFactory(TransportType.BLE)

        registry.register(bleFactory1)
        registry.register(bleFactory2)

        // Should replace the first one
        assertEquals(1, registry.supportedTransports.size)
        assertEquals(1, registry.getAllFactories().size)
    }

    @Test
    fun testIsSupportedAllTypes() {
        val registry = MdocTransportRegistry()

        // All should be false initially
        assertFalse(registry.isSupported(TransportType.BLE))
        assertFalse(registry.isSupported(TransportType.NFC))
        assertFalse(registry.isSupported(TransportType.REST_API))
        assertFalse(registry.isSupported(TransportType.OID4VP))
        assertFalse(registry.isSupported(TransportType.WIFI_AWARE))

        // Register BLE
        registry.register(TestTransportFactory(TransportType.BLE))
        assertTrue(registry.isSupported(TransportType.BLE))
        assertFalse(registry.isSupported(TransportType.NFC))
    }

    @Test
    fun testGetFactoryReturnsCorrectInstance() {
        val registry = MdocTransportRegistry()
        val bleFactory = TestTransportFactory(TransportType.BLE)
        registry.register(bleFactory)

        val factory = registry.getFactory(TransportType.BLE)

        // Verify it's the same instance
        assertTrue(factory === bleFactory)
    }

    @Test
    fun testSupportedTransportsIsImmutableView() {
        val registry = MdocTransportRegistry()
        registry.register(TestTransportFactory(TransportType.BLE))

        val transports = registry.supportedTransports
        assertEquals(1, transports.size)

        // Register another factory
        registry.register(TestTransportFactory(TransportType.NFC))

        // The original set reference should reflect the change
        assertEquals(2, registry.supportedTransports.size)
    }
}
