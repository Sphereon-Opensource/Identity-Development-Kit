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

package com.sphereon.mdoc.transport

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.EngagementData
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Factory for creating transport-specific transfer instances.
 *
 * Each transport module (BLE, NFC, REST API, OID4VP) provides its own
 * implementation of this interface. Factories are automatically registered
 * via dependency injection when the transport module is on the classpath.
 *
 * ## Example Implementation
 * ```kotlin
 * @Inject
 * @ContributesBinding(AppScope::class)
 * @SingleIn(AppScope::class)
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("BleTransportFactory", exact = true)
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocTransportFactory", exact = true)
 * class BleTransportFactory(
 *     private val blePlatformClient: BlePlatformClient,
 *     private val blePlatformPeripheral: BlePlatformPeripheral
 * ) : MdocTransportFactory {
 *
 *     override val transportType = TransportType.BLE
 *
 *     override fun supports(connectionMethod: ConnectionMethod): Boolean {
 *         return connectionMethod is BleConnectionMethod
 *     }
 *
 *     override fun createTransfer(...): IMdocTransfer<*> {
 *         return BleMdocTransfer(...)
 *     }
 * }
 * ```
 */
interface MdocTransportFactory {
    /**
     * Transport type this factory handles.
     */
    val transportType: TransportType

    /**
     * Check if this factory can handle the given connection method.
     *
     * @param connectionMethod The connection method to check
     * @return true if this factory can create a transfer for it
     */
    fun supports(connectionMethod: ConnectionMethod): Boolean

    /**
     * Create a transfer instance for the given connection method.
     *
     * The role parameter determines the behavior and characteristics used:
     * - **MDOC_READER**: This party is the verifier/reader
     * - **MDOC**: This party is the holder/device
     *
     * Some transports (like BLE) use different service characteristics depending
     * on the role. The factory uses this information to configure the transfer
     * appropriately.
     *
     * @param connectionMethod The connection method (BLE, REST API, etc.)
     * @param execution Session execution context
     * @param role The role of this party (MDOC or MDOC_READER)
     * @param engagementData Optional engagement data containing device/reader engagements and keys.
     *                       Used by BLE transport to determine if we should attempt both modes
     *                       or pick one (BLE-specific feature). Required for REST API for session establishment.
     * @return Transfer instance for this transport
     * @throws IllegalArgumentException if connectionMethod is not supported
     */
    fun createTransfer(
        connectionMethod: ConnectionMethod,
        execution: SessionExecution,
        role: MdocRole,
        engagementData: EngagementData? = null,
    ): MdocTransport<*>

    /**
     * Get connection method factory for parsing device retrieval methods.
     *
     * This is used to convert DeviceRetrievalMethod objects from engagements
     * into ConnectionMethod instances.
     *
     * @return Factory for creating connection methods, or null if not applicable
     */
    fun getConnectionMethodFactory(): ConnectionMethodBase.Factory? = null
}

/**
 * Registry for transport factories.
 *
 * This is the central registry where all available transports are registered.
 * Transport modules register their factories via dependency injection, and
 * the engagement manager uses this registry to discover available transports
 * at runtime.
 *
 * ## Usage in Engagement Manager
 * ```kotlin
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocReaderEngagementManagerImpl", exact = true)
 * class MdocReaderEngagementManagerImpl(
 *     private val transportRegistry: MdocTransportRegistry
 * ) : MdocReaderEngagementManager {
 *
 *     init {
 *         log.info("Available transports: ${transportRegistry.supportedTransports}")
 *     }
 *
 *     suspend fun connect(deviceEngagement: DeviceEngagement) {
 *         val connectionMethod = extractConnectionMethod(deviceEngagement)
 *         val factory = transportRegistry.getFactory(connectionMethod)
 *             ?: error("Transport not available")
 *         val transfer = factory.createTransfer(...)
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocTransportRegistry", exact = true)
class MdocTransportRegistry {
    private val factories = mutableMapOf<TransportType, MdocTransportFactory>()
    private val connectionMethodFactories = mutableMapOf<TransportType, ConnectionMethodBase.Factory>()

    /**
     * Get all supported transport types.
     *
     * This returns only transports that have been registered (i.e., their
     * modules are on the classpath).
     */
    val supportedTransports: Set<TransportType>
        get() = factories.keys

    /**
     * Register a transport factory.
     *
     * This is typically called automatically by dependency injection when
     * a transport module is on the classpath.
     *
     * Note: [NoOpTransportFactory] is automatically filtered out as it's just
     * a placeholder for kotlin-inject multibinding.
     *
     * @param factory The transport factory to register
     */
    fun register(factory: MdocTransportFactory) {
        // Filter out the no-op factory (required for kotlin-inject multibinding)
        if (factory is NoOpTransportFactory) {
            return
        }

        factories[factory.transportType] = factory
        factory.getConnectionMethodFactory()?.let { cmFactory ->
            connectionMethodFactories[factory.transportType] = cmFactory
        }
    }

    /**
     * Get factory for a specific transport type.
     *
     * @param transportType The transport type
     * @return Factory instance, or null if transport is not available
     */
    fun getFactory(transportType: TransportType): MdocTransportFactory? = factories[transportType]

    /**
     * Get factory that supports the given connection method.
     *
     * @param connectionMethod The connection method to handle
     * @return Factory that can create transfers for this connection method,
     *         or null if no registered factory supports it
     */
    fun getFactory(connectionMethod: ConnectionMethod): MdocTransportFactory? = factories.values.firstOrNull { it.supports(connectionMethod) }

    /**
     * Get connection method factory for parsing device retrieval methods.
     *
     * @param deviceRetrievalMethod The retrieval method from engagement
     * @return Factory that can parse this retrieval method, or null if not supported
     */
    fun getConnectionMethodFactory(deviceRetrievalMethod: DeviceRetrievalMethod): ConnectionMethodBase.Factory? =
        connectionMethodFactories.values.firstOrNull {
            it.supports(deviceRetrievalMethod)
        }

    /**
     * Check if a specific transport is supported.
     *
     * @param transportType The transport to check
     * @return true if the transport module is on classpath and registered
     */
    fun isSupported(transportType: TransportType): Boolean = transportType in factories

    /**
     * Get all registered factories.
     *
     * @return Collection of all registered factories
     */
    fun getAllFactories(): Collection<MdocTransportFactory> = factories.values
}
