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

package com.sphereon.mdoc.transport.ble

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.data.link.ble.client.BlePlatformClient
import com.sphereon.data.link.ble.peripheral.BlePlatformPeripheral
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.EngagementData
import com.sphereon.mdoc.transport.ConnectionMethod
import com.sphereon.mdoc.transport.ConnectionMethodBase
import com.sphereon.mdoc.transport.MdocTransport
import com.sphereon.mdoc.transport.MdocTransportFactory
import com.sphereon.mdoc.transport.TransportType
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet
import kotlin.uuid.Uuid

/**
 * Factory for creating BLE transport instances.
 *
 * This factory is automatically registered via dependency injection when the
 * BLE transport module is on the classpath. It provides BLE-specific transport
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
 * While BLE itself doesn't require tenant-specific configuration, moving to SessionScope
 * allows for future tenant-specific BLE configurations and maintains consistency with
 * other transport factories that do require session-specific resources.
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
 *         // BLE factory is automatically available if module is on classpath
 *         val bleSupported = transportRegistry.isSupported(TransportType.BLE)
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
 * @param blePlatformClient Platform-specific BLE client (for central mode)
 * @param blePlatformPeripheral Platform-specific BLE peripheral (for peripheral mode)
 */
@Inject
@ContributesIntoSet(SessionScope::class, binding = binding<MdocTransportFactory>())
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("BleTransportFactory", exact = true)
class BleTransportFactory(
    private val blePlatformClient: BlePlatformClient,
    private val blePlatformPeripheral: BlePlatformPeripheral
) : MdocTransportFactory {

    override val transportType: TransportType = TransportType.BLE

    /**
     * Strategy for selecting BLE mode when both modes are available.
     * Defaults to PREFER_CENTRAL for maximum reliability.
     */
    private var modeSelectionStrategy = BleModeSelectionStrategy.PREFER_CENTRAL

    /**
     * Set the mode selection strategy for when both BLE modes are available.
     *
     * This only affects the party that RECEIVES an engagement with both modes.
     * The party that CREATES an engagement with both modes will always attempt
     * both modes simultaneously (connection racing).
     *
     * @param strategy The strategy to use for mode selection
     */
    fun setModeSelectionStrategy(strategy: BleModeSelectionStrategy) {
        this.modeSelectionStrategy = strategy
    }

    override fun supports(connectionMethod: ConnectionMethod): Boolean {
        return connectionMethod is BleConnectionMethod
    }

    override fun getConnectionMethodFactory(): ConnectionMethodBase.Factory {
        return BleConnectionMethod.Companion
    }

    /**
     * Creates a BLE transfer instance.
     *
     * This method creates a fully functional BLE transfer with all necessary
     * components (services, data channels) wired up based on the connection
     * method configuration.
     *
     * ## Mode Selection
     *
     * The BLE connection method specifies which modes are supported:
     * - **Central Client Mode**: This device scans for and connects to peripherals
     * - **Peripheral Server Mode**: This device advertises and accepts connections
     *
     * The factory creates the appropriate transfer type based on the mode.
     *
     * ## Role-Based Characteristics
     *
     * The role determines which BLE service characteristics to use:
     * - **MDOC_READER**: Uses holder's characteristics (connects to holder's service)
     * - **MDOC**: Uses reader's characteristics (connects to reader's service)
     *
     * @param connectionMethod The BLE connection method
     * @param execution Session execution context
     * @param role The role of this party (MDOC or MDOC_READER)
     * @return BLE transfer instance (central or peripheral)
     * @throws IllegalArgumentException if connectionMethod is not BleConnectionMethod
     * @throws IllegalStateException if connection method doesn't specify any mode
     */
    override fun createTransfer(
        connectionMethod: ConnectionMethod,
        execution: SessionExecution,
        role: MdocRole,
        engagementData: EngagementData?
    ): MdocTransport<*> {
        require(connectionMethod is BleConnectionMethod) {
            "BleTransportFactory requires BleConnectionMethod, got ${connectionMethod::class.simpleName}"
        }

        val options = connectionMethod.options
        val log = execution.log

        // Determine which mode to use based on ISO 18013-5 Section 11.1.2
        log.info("BLE options: centralClientMode=${options.centralClientMode}, peripheralServerMode=${options.peripheralServerMode}, role=$role")

        // Determine if we should attempt both modes or pick one
        // Forward: Holder has DeviceEngagement → attempts both
        // Reverse: Reader has ReaderEngagement → attempts both
        val shouldAttemptBothModes = engagementData?.let { data ->
            when (role) {
                MdocRole.MDOC -> data.isDeviceEngagement()  // Holder with DeviceEngagement
                MdocRole.MDOC_READER -> data.isReaderEngagement()  // Reader with ReaderEngagement
            }
        } ?: false

        val useCentralMode = when {
            options.centralClientMode && options.peripheralServerMode -> {
                // Both modes declared in engagement
                if (shouldAttemptBothModes) {
                    // We're the one who created this engagement - should attempt BOTH
                    // This code path should NOT be reached - MdocEngagementData.Builder
                    // should have split this into TWO separate BleConnectionMethod objects
                    log.error("BUG: BLE method with both modes should have been split at engagement creation")
                    throw IllegalStateException(
                        "When both BLE modes are supported and we created the engagement, " +
                                "MdocEngagementData.Builder should have split this into TWO separate " +
                                "BleConnectionMethod objects. This indicates the engagement was not " +
                                "properly created. Check MdocEngagementData.addRetrievalMethod()."
                    )
                } else {
                    // We're receiving an engagement with both modes - PICK ONE
                    val choice = when (modeSelectionStrategy) {
                        BleModeSelectionStrategy.PREFER_CENTRAL -> {
                            log.info("Strategy: PREFER_CENTRAL → using central mode")
                            true
                        }

                        BleModeSelectionStrategy.PREFER_PERIPHERAL -> {
                            log.info("Strategy: PREFER_PERIPHERAL → using peripheral mode")
                            false
                        }

                        BleModeSelectionStrategy.READER_CENTRAL -> {
                            val useCentral = role == MdocRole.MDOC_READER
                            log.info("Strategy: READER_CENTRAL → reader uses central=$useCentral")
                            useCentral
                        }

                        BleModeSelectionStrategy.HOLDER_CENTRAL -> {
                            val useCentral = role == MdocRole.MDOC
                            log.info("Strategy: HOLDER_CENTRAL → holder uses central=$useCentral")
                            useCentral
                        }
                    }
                    choice
                }
            }

            options.centralClientMode -> {
                // Only central client mode supported by creator
                // Consumer uses opposite mode (peripheral)
                val useMode = role == MdocRole.MDOC
                log.info("centralClientMode only, role=$role → using central=$useMode")
                useMode
            }

            options.peripheralServerMode -> {
                // Only peripheral server mode supported by creator
                // Consumer uses opposite mode (central)
                val useMode = role == MdocRole.MDOC_READER
                log.info("peripheralServerMode only, role=$role → using central=$useMode")
                useMode
            }

            else -> throw IllegalStateException(
                "BLE connection method must specify at least one mode (central or peripheral)"
            )
        }

        log.info("Creating BLE transfer: role=$role, mode=${if (useCentralMode) "central" else "peripheral"}")

        // Get characteristics based on role AND mode
        // When acting as PERIPHERAL SERVER: Use your own service characteristics
        //   - Reader peripheral: Uses READER characteristics (reader's service)
        //   - Holder peripheral: Uses HOLDER characteristics (holder's service)
        // When acting as CENTRAL CLIENT: Use the remote peripheral's characteristics
        //   - Reader central: Uses HOLDER characteristics (connects to holder's service)
        //   - Holder central: Uses READER characteristics (connects to reader's service)
        val characteristics = if (useCentralMode) {
            // Central mode: Connect to remote peripheral's service
            when (role) {
                MdocRole.MDOC_READER -> {
                    log.info("Reader in central mode → using HOLDER characteristics (connect to holder's service)")
                    MdocHolderBleServiceCharacteristics // Reader connects to holder
                }

                MdocRole.MDOC -> {
                    log.info("Holder in central mode → using READER characteristics (connect to reader's service)")
                    MdocReaderBleServiceCharacteristics // Holder connects to reader
                }
            }
        } else {
            // Peripheral mode: Use own service characteristics
            when (role) {
                MdocRole.MDOC_READER -> {
                    log.info("Reader in peripheral mode → using READER characteristics (own service)")
                    MdocReaderBleServiceCharacteristics // Reader's own service
                }

                MdocRole.MDOC -> {
                    log.info("Holder in peripheral mode → using HOLDER characteristics (own service)")
                    MdocHolderBleServiceCharacteristics // Holder's own service
                }
            }
        }

        log.info("Selected characteristics: state=${characteristics.state}, client2Server=${characteristics.client2Server}, server2Client=${characteristics.server2Client}")

        return if (useCentralMode) {
            createCentralClientTransfer(connectionMethod, execution, role, characteristics)
        } else {
            createPeripheralServerTransfer(connectionMethod, execution, role, characteristics)
        }
    }

    /**
     * Create a central client mode transfer.
     *
     * In central mode, this device scans for and connects to a peripheral.
     */
    private fun createCentralClientTransfer(
        connectionMethod: BleConnectionMethod,
        execution: SessionExecution,
        role: MdocRole,
        characteristics: MdocBleServiceCharacteristics
    ): BleCentralClientTransport {
        val instanceId = Uuid.random()
        val logManager = execution.log.logManager

        // Create data channels FIRST so we can pass them to the central service
        val incomingChannel = BleIncomingDataChannelImpl(
            logManager = logManager,
            role = role,
            instanceId = instanceId,
            incomingCharacteristicId = characteristics.server2Client,
            eventDispatcher = null // No event dispatcher for now
        )

        // Service UUID will be determined during connection (from scanned peripheral)
        // For now, use a placeholder - it will be updated when service is discovered
        val placeholderServiceUuid = connectionMethod.options.centralClientModeUuid
            ?: Uuid.random()

        val outgoingChannel = BleOutgoingDataChannelImpl(
            logManager = logManager,
            role = role,
            instanceId = instanceId,
            characteristicWriter = CentralCharacteristicWriter(blePlatformClient),
            outgoingCharacteristicId = characteristics.client2Server,
            stateCharacteristicId = characteristics.state,
            serviceUuid = placeholderServiceUuid,
            eventDispatcher = null // No event dispatcher for now
        )

        // Create BLE central service WITH the channels so it can register them as listeners
        val centralService = BleCentralServiceImpl(
            blePlatformClient = blePlatformClient,
            logManager = logManager,
            role = role,
            characteristics = characteristics,
            incomingChannel = incomingChannel,
            outgoingChannel = outgoingChannel
        )

        // Create and return transfer
        return BleCentralClientTransport(
            connectionMethod = connectionMethod,
            execution = execution,
            bleCentralService = centralService,
            incomingChannel = incomingChannel,
            outgoingChannel = outgoingChannel,
            role = role
        )
    }

    /**
     * Create a peripheral server mode transfer.
     *
     * In peripheral mode, this device advertises and accepts connections from centrals.
     */
    private fun createPeripheralServerTransfer(
        connectionMethod: BleConnectionMethod,
        execution: SessionExecution,
        role: MdocRole,
        characteristics: MdocBleServiceCharacteristics
    ): BlePeripheralServerTransport {
        val instanceId = Uuid.random()
        val logManager = execution.log.logManager

        // Get service UUID from connection method
        // Per ISO 18013-5 Section 11.1.2:
        // - Key 10 (peripheralServerModeUuid): UUID advertised by the peripheral
        // - Key 11 (centralClientModeUuid): UUID scanned for by the central
        //
        // FORWARD ENGAGEMENT (holder creates engagement):
        //   - Holder peripheral: advertises with key 10 (peripheralServerModeUuid)
        //   - Reader central: scans for key 10
        //   - Reader peripheral: advertises with key 11 (centralClientModeUuid)
        //   - Holder central: scans for key 11
        //
        // REVERSE ENGAGEMENT (reader creates engagement):
        //   - Reader peripheral: advertises with key 10 (peripheralServerModeUuid)
        //   - Holder central: scans for key 10
        //   - Reader central: scans for key 11 (centralClientModeUuid)
        //   - Holder peripheral: advertises with key 11
        //
        // KEY INSIGHT: The peripheral advertises with a UUID that the central will scan for.
        // In forward engagement (holder creates engagement):
        //   - Holder announces centralClientMode + centralClientModeUuid (key 11)
        //   - Reader acts as peripheral → should advertise with key 11 (what holder scans for)
        // In reverse engagement (reader creates engagement):
        //   - Reader announces peripheralServerMode + peripheralServerModeUuid (key 10)
        //   - Reader acts as peripheral → should advertise with key 10
        //
        // So the logic is:
        // - If peripheralServerModeUuid is present → use it (reverse engagement case)
        // - Otherwise use centralClientModeUuid (forward engagement case - reader advertises with what holder scans for)
        val serviceUuid = connectionMethod.options.peripheralServerModeUuid
            ?: connectionMethod.options.centralClientModeUuid
            ?: throw IllegalStateException(
                "Either peripheralServerModeUuid (key 10) or centralClientModeUuid (key 11) must be set when acting as peripheral server"
            )

        // Create data channels (peripheral service will manage them)
        val incomingChannel = BleIncomingDataChannelImpl(
            logManager = logManager,
            role = role,
            instanceId = instanceId,
            incomingCharacteristicId = characteristics.client2Server,
            eventDispatcher = null
        )

        val outgoingChannel = BleOutgoingDataChannelImpl(
            logManager = logManager,
            role = role,
            instanceId = instanceId,
            characteristicWriter = PeripheralCharacteristicWriter(blePlatformPeripheral),
            outgoingCharacteristicId = characteristics.server2Client,
            stateCharacteristicId = characteristics.state,
            serviceUuid = serviceUuid,
            eventDispatcher = null
        )

        // Create BLE peripheral service
        val peripheralService = BlePeripheralServiceImpl(
            blePlatformPeripheral = blePlatformPeripheral,
            logManager = logManager,
            role = role,
            characteristics = characteristics,
            serviceUuid = serviceUuid,
            _incomingDataChannel = incomingChannel,
            _outgoingDataChannel = outgoingChannel,
            hkdfProvider = createPlatformHkdfProvider()
        )

        // Create and return transfer
        return BlePeripheralServerTransport(
            connectionMethod = connectionMethod,
            execution = execution,
            blePeripheralService = peripheralService,
            role = role
        )
    }
}
