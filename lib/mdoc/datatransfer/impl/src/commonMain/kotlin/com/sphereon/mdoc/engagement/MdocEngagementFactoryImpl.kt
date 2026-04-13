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

package com.sphereon.mdoc.engagement

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.cbor.CborEncodedItem
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import dev.zacsweers.metro.Inject
import software.amazon.app.platform.scope.coroutine.CoroutineScopeScoped
import dev.zacsweers.metro.ContributesBinding
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.KeyEncoding
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.di.session.SessionScope
import com.sphereon.mdoc.MdocSignService
import com.sphereon.mdoc.logging.MdocDebugLoggerImpl
import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.reader.ReaderEngagementSecurity
import kotlinx.datetime.Clock
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.binding

@Inject
//@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<MdocEngagementFactory>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocEngagementFactoryImpl", exact = true)
class MdocEngagementFactoryImpl(
    execution: SessionExecution,
    kms: KeyManagerService,
    mdocSignService: MdocSignService,
    private val transferFactory: com.sphereon.mdoc.transfer.MdocTransferFactory,
//    @ForScope(SessionScope::class)
//    private val coroutineScoped: CoroutineScopeScoped,
) : MdocEngagementFactory {

    private val log = execution.log.logManager.withTag("MdocEngagementFactory")

    init {
        log.warn("=== MdocEngagementFactoryImpl created. Session: ${execution.sessionContext.sessionId}, user: ${execution.sessionContext.context.principal.toString()} ===")
    }

    // TODO: Do we need a mutex? Test whether multiple ble and nfc instances can be ran at the same time
    override val holder: MdocEngagementFactory.Holder by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        log.info("Creating Holder")
        Holder(execution, kms, mdocSignService, transferFactory)
    }


    override val reader: MdocEngagementFactory.Reader by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        log.info("Creating Reader factory")
        Reader(execution, kms, mdocSignService, transferFactory)
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Reader", exact = true)
    class Reader(
        private val execution: SessionExecution,
        private val kms: KeyManagerService,
        private val mdocSignService: MdocSignService,
        private val transferFactory: com.sphereon.mdoc.transfer.MdocTransferFactory,
    ) : MdocEngagementFactory.Reader {

        val coroutineScoped = CoroutineScopeScoped(Dispatchers.Default + SupervisorJob() + CoroutineName("ReaderSessionScope-${execution.sessionContext.sessionId}"))

        override suspend fun createEngagement(init: ReaderConfiguration.() -> Unit): IdkResult<EngagementInstance, IdkError> {
            return try {
                val log = execution.log.logManager.withTag("MdocEngagementFactory.Reader")

                // Apply configuration
                val config = ReaderConfiguration().apply(init)

                // Validate that retrieval methods are provided
                if (config.retrievalMethods.isEmpty()) {
                    return IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "At least one retrieval method must be specified for reader engagement"
                    ).asErrorResult()
                }

                // Generate reader ephemeral key
                val alias = "reader-ephemeral-key-${Clock.System.now().epochSeconds}"
                val ephemeralKey = kms.getKmsBySignatureAlgorithm(SignatureAlgorithm.ECDSA_SHA256)
                    .generateKeyAsync(alias = alias)
                    .toManagedKeyInfo<CoseKeyType>(
                        visibility = KeyVisibility.PRIVATE,
                        keyEncoding = KeyEncoding.COSE
                    )

                log.info("Generated reader ephemeral key with alias: $alias")

                // Generate UUIDs for BLE retrieval methods if not provided
                val retrievalMethodsWithUuids = config.retrievalMethods.map { method ->
                    if (method.retrievalOptions is com.sphereon.mdoc.transfer.device.BleOptions) {
                        val bleOptions = method.retrievalOptions as com.sphereon.mdoc.transfer.device.BleOptions
                        val updatedOptions = bleOptions.copy(
                            centralClientModeUuid = if (bleOptions.centralClientMode && bleOptions.centralClientModeUuid == null)
                                kotlin.uuid.Uuid.random() else bleOptions.centralClientModeUuid,
                            peripheralServerModeUuid = if (bleOptions.peripheralServerMode && bleOptions.peripheralServerModeUuid == null)
                                kotlin.uuid.Uuid.random() else bleOptions.peripheralServerModeUuid
                        )
                        method.copy(retrievalOptions = updatedOptions)
                    } else {
                        method
                    }
                }.toSet()

                // Create ReaderEngagement
                val security = ReaderEngagementSecurity(
                    cipherSuite = CoseCurve.P_256.value.toUInt(),
                    eReaderKeyBytes = CborEncodedItem.fromData(
                        CoseKey.fromDTO(ephemeralKey.key).toPublicKey()
                    )
                )

                val readerEngagement = com.sphereon.mdoc.transfer.reader.ReaderEngagement.V1_0(
                    security = security,
                    deviceRetrievalMethods = retrievalMethodsWithUuids.toTypedArray(),
                    protocolInfo = null,
                    additionalItems = null,
                    original = null
                )

                log.info("Created ReaderEngagement with ${config.retrievalMethods.size} retrieval method(s)")

                // Create MdocEngagementData using readerBuilder (sets role to MDOC_READER)
                val builder = EngagementData.readerBuilder()
                    .withEphemeralKey(ephemeralKey)
                    .withRetrievalMethods(retrievalMethodsWithUuids)  // Use methods with generated UUIDs
                    .withReaderEngagement(readerEngagement)

                val data = builder.build()

                // Log BLE UUID configuration
                data.getBleRetrievalMethod()?.let { bleMethod ->
                    val bleOptions = bleMethod.retrievalOptions as? BleOptions
                    if (bleOptions != null) {
                        if (bleOptions.peripheralServerMode) {
                            log.info("*** READER ENGAGEMENT: Peripheral Server Mode - Will advertise UUID: ${bleOptions.peripheralServerModeUuid} ***")
                        }
                        if (bleOptions.centralClientMode) {
                            log.info("*** READER ENGAGEMENT: Central Client Mode - Will scan for UUID: ${bleOptions.centralClientModeUuid} ***")
                        }
                    }
                }

                // Create EngagementInstance with MDOC_READER role
                val engagementInstance = EngagementInstanceImpl(
                    execution = execution,
                    kms = kms,
                    data = data,
                    sessionCoroutineScope = coroutineScoped,
                    mdocSignService = mdocSignService,
                    transferFactory = transferFactory
                )

                log.info("Created reader EngagementInstance: ${engagementInstance.id}")

                engagementInstance.asOkResult()

            } catch (e: Exception) {
                execution.log.logManager.withTag("MdocEngagementFactory.Reader")
                    .error("Failed to create reader engagement", exception = e)
                IdkError.UNKNOWN_ERROR(
                    message = "Failed to create reader engagement: ${e.message}",
                    exception = e
                ).asErrorResult()
            }
        }
    }


    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Holder", exact = true)
    class Holder(
        private val execution: SessionExecution,
        private val kms: KeyManagerService,
        private val mdocSignService: MdocSignService,
        private val transferFactory: com.sphereon.mdoc.transfer.MdocTransferFactory,
    ) : MdocEngagementFactory.Holder {

        // Debug logger for ISO 18013-5 debugging - wraps the session log service
        private val debugLogger = MdocDebugLoggerImpl(execution.log)

        val coroutineScoped = CoroutineScopeScoped(Dispatchers.Default + SupervisorJob() + CoroutineName("SessionScope-${execution.sessionContext.sessionId}"))

        // Track the last created engagement to manage BLE service lifecycle
        private var lastEngagementWithBle: EngagementInstanceImpl? = null
        private fun assertBleAndQrOnlyMethod(dataService: EngagementData): IdkError? {
            // Check if at least one supported retrieval method exists (BLE, NFC, REST API, or OID4VP)
            val hasBle = dataService.getBleRetrievalMethod() != null
            val hasNfc = dataService.getRetrievalMethods().any {
                it.retrievalOptions is com.sphereon.mdoc.transfer.device.NfcOptions
            }
            val hasRestApi = dataService.isRestApiRetrievalSupported()
            val hasOid4vp = dataService.getRetrievalMethods().any {
                it.retrievalOptions is com.sphereon.mdoc.transfer.device.Oid4vpOptions
            }

            if (!hasBle && !hasNfc && !hasRestApi && !hasOid4vp) {
                return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "At least one retrieval method (BLE, NFC, REST API, or OID4VP) is required")
            }

            // Check that only supported retrieval methods are used (BLE, NFC, REST API, and/or OID4VP)
            val unsupportedMethods = dataService.getRetrievalMethods().filter {
                it.retrievalOptions !is BleOptions &&
                        it.retrievalOptions !is com.sphereon.mdoc.transfer.device.NfcOptions &&
                        it.retrievalOptions !is com.sphereon.mdoc.transfer.device.RestApiOptions &&
                        it.retrievalOptions !is com.sphereon.mdoc.transfer.device.Oid4vpOptions
            }
            if (unsupportedMethods.isNotEmpty()) {
                return IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Unsupported retrieval method type. Only BLE, NFC, REST API, and OID4VP are supported currently"
                )
            }

            // For TO_APP (reverse engagement including OID4VP), QR/NFC is not required - holder responds to reader's engagement
            // Check if this is a remote engagement (ReaderEngagement) or OID4VP engagement
            val isRemoteOrOid4vp = dataService.isReaderEngagement() ||
                    dataService.getEngagementMethods().any { it is Oid4vpEngagementMethod }

            if (!isRemoteOrOid4vp && !dataService.isQrEngagementSupported() && !dataService.isNfcEngagementSupported()) {
                return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "QR and/or NFC engagement is required, but neither are selected")
            }
            return null
        }

        private suspend fun createInstance(data: EngagementData): IdkResult<EngagementInstanceImpl, IdkError> {
            assertBleAndQrOnlyMethod(data)?.let { return it.asErrorResult() }

            // Log engagement type and configuration
            val log = execution.log.logManager.withTag("MdocEngagementFactory")
            val hasOid4vp = data.getRetrievalMethods().any {
                it.retrievalOptions is com.sphereon.mdoc.transfer.device.Oid4vpOptions
            }

            if (hasOid4vp) {
                log.info("*** ENGAGEMENT FACTORY: Creating OID4VP engagement (HTTPS-based OAuth 2.0 protocol) ***")
                // Log OID4VP configuration
                data.getRetrievalMethods().forEach { method ->
                    if (method.retrievalOptions is com.sphereon.mdoc.transfer.device.Oid4vpOptions) {
                        val oid4vpOptions = method.retrievalOptions as com.sphereon.mdoc.transfer.device.Oid4vpOptions
                        log.info("*** OID4VP: client_id=${oid4vpOptions.clientId}, request_uri=${oid4vpOptions.requestUri} ***")
                    }
                }
            } else {
                // Log BLE UUID configuration only if BLE is used
                log.info("*** ENGAGEMENT FACTORY: Creating engagement with BLE/REST API ***")
                data.getBleRetrievalMethod()?.let { bleMethod ->
                    val bleOptions = bleMethod.retrievalOptions as? BleOptions
                    if (bleOptions != null) {
                        if (bleOptions.centralClientMode) {
                            log.info("*** ENGAGEMENT FACTORY: Central Client Mode - Will scan for UUID: ${bleOptions.centralClientModeUuid} ***")
                        }
                        if (bleOptions.peripheralServerMode) {
                            log.info("*** ENGAGEMENT FACTORY: Peripheral Server Mode - Will advertise UUID: ${bleOptions.peripheralServerModeUuid} ***")
                        }
                    }
                }
            }

            val newEngagement = EngagementInstanceImpl(
                execution = execution,
                kms = kms,
                data = data,
                sessionCoroutineScope = coroutineScoped,
                mdocSignService = mdocSignService,
                transferFactory = transferFactory
            )

            // Track this engagement for potential cleanup (only relevant for BLE engagements)
            if (!hasOid4vp) {
                lastEngagementWithBle = newEngagement
            }

            return newEngagement.asOkResult()
        }

        override suspend fun createFromBuilder(builder: EngagementData.HolderBuilder): IdkResult<EngagementInstance, IdkError> {
            val data = builder.build()
            return createInstance(data)
        }

        override suspend fun createFromEphemeralKey(
            ephemeralKey: ResolvedKeyInfoType<*>,
            configBuilder: EngagementConfiguration.() -> Unit,
        ): IdkResult<EngagementInstance, IdkError> {
            val config = EngagementConfiguration().apply(configBuilder)
            return createFromBuilder(
                EngagementData
                    .holderBuilder()
                    .withEphemeralKey(ephemeralKey)
                    .withRetrievalMethods(config.retrievalMethods)
                    .withEngagementMethods(*config.engagementMethods.toTypedArray())
                    .withDebugLogger(debugLogger)
            )
        }

        override suspend fun createEngagement(
            configBuilder: EngagementConfiguration.() -> Unit,
        ): IdkResult<EngagementInstance, IdkError> {
            // TODO: Cleanup support in KMS
            val alias = "ephemeral-key-${Clock.System.now().epochSeconds}"
            val ephemeralKey = kms.getKmsBySignatureAlgorithm(SignatureAlgorithm.ECDSA_SHA256).generateKeyAsync(alias = alias)
            return createFromEphemeralKey(
                ephemeralKey = ephemeralKey.toManagedKeyInfo<CoseKeyType>(
                    visibility = KeyVisibility.PRIVATE, keyEncoding = KeyEncoding.COSE
                ), configBuilder = configBuilder
            )
        }
    }

    @ContributesTo(SessionScope::class)
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Component", exact = true)
    interface Component {
        val engagementFactory: MdocEngagementFactory
    }
}
