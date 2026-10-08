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

package com.sphereon.mdoc.transport.nfc

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.EngagementData
import com.sphereon.mdoc.transport.ConnectionMethod
import com.sphereon.mdoc.transport.ConnectionMethodBase
import com.sphereon.mdoc.transport.MdocTransport
import com.sphereon.mdoc.transport.MdocTransportFactory
import com.sphereon.mdoc.transport.TransportType
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Factory for creating NFC transport instances.
 *
 * This factory is automatically registered via dependency injection when the
 * NFC transport module is on the classpath. It provides NFC-specific transport
 * creation and connection method parsing.
 *
 * ## Automatic Registration
 *
 * The `@ContributesBinding` annotation ensures that when this module is included
 * in the application, it will automatically register itself with the transport
 * registry without any manual configuration.
 *
 * ## SessionScope Rationale
 *
 * This factory is scoped to `SessionScope` to match the transport registry's scope.
 * While NFC itself doesn't require tenant-specific configuration, moving to SessionScope
 * allows for consistent scoping with other transport factories and the registry that
 * collects them.
 *
 * ## Usage
 *
 * ```kotlin
 * // In application code - the factory is auto-discovered
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocReaderEngagementManagerImpl", exact = true)
 * class MdocReaderEngagementManagerImpl(
 *     private val transportRegistry: MdocTransportRegistry
 * ) {
 *     init {
 *         // NFC factory is automatically available if module is on classpath
 *         val nfcSupported = transportRegistry.isSupported(TransportType.NFC)
 *     }
 *
 *     suspend fun connect(deviceEngagement: DeviceEngagement) {
 *         val connectionMethod = extractConnectionMethod(deviceEngagement)
 *         val factory = transportRegistry.getFactory(connectionMethod)
 *         val transfer = factory.createTransfer(connectionMethod, execution, MdocRole.MDOC_READER)
 *         transfer.open(readerKey, serviceUuid)
 *     }
 * }
 * ```
 *
 * ## NFC Characteristics
 *
 * Unlike BLE, NFC doesn't have multiple modes (central/peripheral). The connection
 * is always initiated by the reader tapping against the holder's device. This makes
 * the implementation simpler than BLE.
 *
 * @param logManager Logging service
 */
@Inject
@ContributesIntoSet(SessionScope::class, binding = binding<MdocTransportFactory>())
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("NfcTransportFactory", exact = true)
class NfcTransportFactory : MdocTransportFactory {
    override val transportType: TransportType = TransportType.NFC

    override fun supports(connectionMethod: ConnectionMethod): Boolean = connectionMethod is NfcConnectionMethod

    override fun getConnectionMethodFactory(): ConnectionMethodBase.Factory = NfcConnectionMethod.Companion

    /**
     * Creates an NFC transfer instance.
     *
     * NFC transfers are simpler than BLE as they don't have multiple modes.
     * The reader always initiates the connection by tapping against the holder's
     * device.
     *
     * ## Data Transfer
     *
     * NFC uses APDU (Application Protocol Data Unit) commands:
     * - Command APDUs: Sent from reader to holder
     * - Response APDUs: Sent from holder to reader
     *
     * The maximum data field lengths are specified in the connection method options
     * and were negotiated via the device engagement.
     *
     * @param connectionMethod The NFC connection method
     * @param execution Session execution context
     * @param role The role of this party (MDOC or MDOC_READER)
     * @return NFC transfer instance
     * @throws IllegalArgumentException if connectionMethod is not NfcConnectionMethod
     */
    override fun createTransfer(
        connectionMethod: ConnectionMethod,
        execution: SessionExecution,
        role: MdocRole,
        engagementData: EngagementData?,
    ): MdocTransport<*> {
        require(connectionMethod is NfcConnectionMethod) {
            "NfcTransportFactory requires NfcConnectionMethod, got ${connectionMethod::class.simpleName}"
        }

        val log = execution.log
        log.info("Creating NFC transfer: role=$role, maxCommand=${connectionMethod.options.maxCommandDataFieldLength}, maxResponse=${connectionMethod.options.maxResponseDataFieldLength}")

        // TODO: The NFC transfer will need to obtain the NfcApduDispatcher from
        // the execution context or session graph, since it's session-scoped
        // and we're in AppScope here
        return NfcTransport(
            connectionMethod = connectionMethod,
            execution = execution,
            role = role,
        )
    }
}
