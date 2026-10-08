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

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("for", exact = true)
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("BleConnectionMethod", exact = true)
@JsExportCompat
 * Base interface for all connection methods.
 *
 * A connection method represents a specific way to connect and exchange data
 * between holder and reader. Each transport type (BLE, NFC, REST API, etc.)
 * has its own ConnectionMethod implementation.
 *
 * ## Implementation Guidelines
 *
 * Transport-specific implementations should:
 * 1. Extend [ConnectionMethodBase] for common functionality
 * 2. Store transport-specific options (e.g., BleOptions, RestApiOptions)
 * 3. Implement conversion to/from DeviceRetrievalMethod
 * 4. Provide factory for parsing from engagement data
 *
 * ## Example
 * ```kotlin
 * data class BleConnectionMethod(
 *     override val options: BleOptions
 * ) : ConnectionMethodBase<BleOptions>(options) {
 *     override val transportType = TransportType.BLE
 *
 *     override fun toDeviceRetrievalMethod(): DeviceRetrievalMethod {
 *         return DeviceRetrievalMethod(
 *             type = DeviceRetrievalMethodType.BLE,
 *             retrievalOptions = options
 *         )
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ConnectionMethod", exact = true)
sealed interface ConnectionMethod {
    /**
     * Transport type identifier.
     */
    val transportType: TransportType

    /**
     * Convert this connection method to a DeviceRetrievalMethod for inclusion
     * in DeviceEngagement or ReaderEngagement.
     */
    fun toDeviceRetrievalMethod(): DeviceRetrievalMethod

    // Note: NDEF support is optional and provided by transport-specific implementations
    // that depend on the NFC module. Not included in transport-core to avoid circular dependencies.
}

/**
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("for", exact = true)
@JsExportCompat
 * Base class for connection methods with typed retrieval options.
 *
 * This provides common functionality for connection methods that have
 * transport-specific options (e.g., BleOptions, RestApiOptions).
 *
 * @param T The type of retrieval options (BleOptions, RestApiOptions, etc.)
 * @param options The transport-specific options
 */
abstract class ConnectionMethodBase<T>(
    open val options: T,
) : ConnectionMethod {
    /**
     @OptIn(ExperimentalObjCName::class)
     @ObjCName("for", exact = true)
     * Factory interface for creating connection methods from device retrieval methods.
     *
     * Each transport module should provide a Factory implementation that can
     * parse DeviceRetrievalMethod objects from engagements.
     */
    interface Factory {
        /**
         * Check if this factory supports the given retrieval method.
         *
         * @param deviceRetrievalMethod The retrieval method to check
         * @return true if this factory can create a connection method from it
         */
        fun supports(deviceRetrievalMethod: DeviceRetrievalMethod): Boolean

        /**
         * Create connection method from device retrieval method.
         *
         * @param deviceRetrievalMethod The retrieval method from engagement
         * @return ConnectionMethod instance, or null if not supported
         */
        fun create(deviceRetrievalMethod: DeviceRetrievalMethod): ConnectionMethod?
    }
}

/*
 * Extension functions for working with connection methods.
 */

/**
 * Convert array of DeviceRetrievalMethods to set of ConnectionMethods.
 *
 * This requires transport factories to be registered. Unsupported transport
 * types will be filtered out.
 *
 * @param registry Transport registry containing factories
 * @return Set of connection methods that could be parsed
 */
fun Array<DeviceRetrievalMethod>.toConnectionMethods(registry: MdocTransportRegistry): Set<ConnectionMethod> =
    mapNotNull { retrievalMethod ->
        registry.getConnectionMethodFactory(retrievalMethod)?.create(retrievalMethod)
    }.toSet()

/**
 * Convert collection of DeviceRetrievalMethods to set of ConnectionMethods.
 */
fun Collection<DeviceRetrievalMethod>.toConnectionMethods(registry: MdocTransportRegistry): Set<ConnectionMethod> =
    mapNotNull { retrievalMethod ->
        registry.getConnectionMethodFactory(retrievalMethod)?.create(retrievalMethod)
    }.toSet()

/**
 * Convert collection of ConnectionMethods to set of DeviceRetrievalMethods.
 */
fun Collection<ConnectionMethod>.toDeviceRetrievalMethods(): Set<DeviceRetrievalMethod> = map { it.toDeviceRetrievalMethod() }.toSet()
