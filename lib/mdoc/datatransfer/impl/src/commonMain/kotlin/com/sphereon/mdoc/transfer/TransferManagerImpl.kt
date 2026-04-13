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

package com.sphereon.mdoc.transfer

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import dev.whyoleg.cryptography.CryptographyProvider
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.toException
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.generic.VerifySignatureResultType
import com.sphereon.crypto.core.generic.VerifySignatureResult
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.MdocSignService
import com.sphereon.mdoc.SessionEncryption
import com.sphereon.mdoc.logging.IMdocDebugLogger
import com.sphereon.mdoc.logging.MdocDebugLoggerImpl
import com.sphereon.mdoc.data.device.DeviceAuthentication
import com.sphereon.mdoc.data.device.DeviceNameSpaces
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.data.device.DeviceResponse
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.engagement.EngagementInstance
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import com.sphereon.mdoc.transport.DataChannelEventDispatcher
import com.sphereon.mdoc.transport.IncomingDataChannel
import com.sphereon.mdoc.transport.OutgoingDataChannel
import com.sphereon.mdoc.transport.TransportType
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.AtomicBoolean
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableDeferred
import kotlin.uuid.Uuid

// Removed expect/actual for injectBleEventDispatcher - using NEW transport event system instead

@OptIn(ExperimentalObjCName::class)
@ObjCName("TransferManagerImpl", exact = true)
class TransferManagerImpl(
    override val engagement: EngagementInstance,
    internal val execution: SessionExecution,
    private val mdocSignService: MdocSignService,
    private val transferFactory: MdocTransferFactory,
) : TransferManager, MdocRetrievalEvent.Dispatcher {

    // Debug logger for ISO 18013-5 debugging - wraps the session log service
    private val debugLogger: IMdocDebugLogger = MdocDebugLoggerImpl(execution.log)

    // Session establishment processor - handles CBOR decoding, transcript generation, ECDH, and decryption
    private val sessionEstablishmentProcessor: SessionEstablishmentProcessor by lazy {
        SessionEstablishmentProcessor(
            debugLogger = debugLogger,
            log = log,
            logId = logId
        )
    }

    // Note: BLE characteristic and UUID management is now handled by the modular transport layer
    // The transport-ble module manages these details internally
    // No need for helper methods here

    // Data channels are provided by the modular transport after it's created
    // The TransferInstance will set these after creating the transfer via MdocTransferFactory
    // Using common channel interfaces from transport-core
    private var _incomingDataChannel: IncomingDataChannel? = null
    private var _outgoingDataChannel: OutgoingDataChannel? = null

    // Deferred for synchronizing channel access - completed when channels are set
    private val channelsReady = CompletableDeferred<Unit>()

    override val incomingDataChannel: IncomingDataChannel
        get() {
            log.debug("$logId: incomingDataChannel getter called")
            return _incomingDataChannel
                ?: throw IllegalStateException(
                    "Data channels not initialized. TransferInstance must call setDataChannels() after creating the modular transport."
                )
        }

    override val outgoingDataChannel: OutgoingDataChannel
        get() {
            log.debug("$logId: outgoingDataChannel getter called")
            return _outgoingDataChannel
                ?: throw IllegalStateException(
                    "Data channels not initialized. TransferInstance must call setDataChannels() after creating the modular transport."
                )
        }

    /**
     * Suspend until data channels are initialized.
     * This should be called before accessing channels to ensure they're ready.
     *
     * Made public for lib-mdoc-reader access.
     */
    override suspend fun awaitChannels() {
        channelsReady.await()
    }

    /**
     * Set the data channels from the modular transport.
     * Called by TransferInstance after creating the transport via MdocTransferFactory.
     *
     * Uses common channel interfaces from transport-core.
     *
     * @param incoming The incoming data channel from the modular transport
     * @param outgoing The outgoing data channel from the modular transport
     */
    internal fun setDataChannels(
        incoming: IncomingDataChannel,
        outgoing: OutgoingDataChannel
    ) {
        log.info("$logId: *** setDataChannels() CALLED ***")
        log.info("$logId: Current incoming channel: $_incomingDataChannel")
        log.info("$logId: Current outgoing channel: $_outgoingDataChannel")
        log.info("$logId: New incoming channel: $incoming")
        log.info("$logId: New outgoing channel: $outgoing")

        // If channels are already set to the same instances, this is idempotent - just return
        if (_incomingDataChannel === incoming && _outgoingDataChannel === outgoing) {
            log.debug("$logId: Data channels already set to the same instances, skipping")
            return
        }

        // If channels are set but to DIFFERENT instances, that's an error
        if (_incomingDataChannel != null || _outgoingDataChannel != null) {
            log.error("$logId: Attempting to set different channels! Already have: in=$_incomingDataChannel, out=$_outgoingDataChannel")
            throw IllegalStateException("Data channels already set to different instances")
        }

        log.info("$logId: Setting data channels from modular transport")
        _incomingDataChannel = incoming
        _outgoingDataChannel = outgoing

        // Set event dispatcher on channels to bridge events to TransferManager
        // The incoming channel needs to dispatch SessionEstablishmentReceived events
        // that the TransferManager's listener can process
        val eventBridge = TransportToMdocEventBridge()
        incoming.setEventDispatcher(eventBridge)
        log.info("$logId: Event dispatcher set on incoming channel")

        // Complete the deferred to signal channels are ready
        channelsReady.complete(Unit)
        log.info("$logId: *** Data channels set successfully and channelsReady completed ***")

        log.trace("$logId: Data channels set - event handling managed by modular transport")
    }

    // Thread-safe atomic references for custom selectors
    private val customRequestResponseProcesser: AtomicRef<RequestResponseProcessor?> = atomic(null)
    private val customRequestDocumentsSelector: AtomicRef<RequestDocumentsSelector?> = atomic(null)
    private val customDocRequestSingleDocSelector: AtomicRef<DocumentRequestSingleDocumentSelector?> = atomic(null)

    private val sessionTranscript: AtomicRef<CborEncodedItem<SessionTranscript>?> = atomic(null)

    // Early listeners set - intended to be modified before start() is called
    // Operations on this set should be done before multi-threaded access begins
    private val _earlyListeners = mutableSetOf<MdocRetrievalEvent.Listener>()

    /**
     @OptIn(ExperimentalObjCName::class)
     @ObjCName("for", exact = true)
     * Inner adapter class for handling retrieval events.
     * Extends MdocRetrievalEventAdapter to only override the events we care about.
     */
    private inner class RetrievalEventListenerAdapter : MdocRetrievalEventAdapter() {
        override suspend fun onSessionEstablishmentReceived(event: MdocRetrievalEvent.SessionEstablishmentReceived) {
            log.info("$logId: TransferManager received SessionEstablishmentReceived event")
            // Process the session establishment data to extract device request
            // The method will also set deviceRequest on the instance
            val result = processSessionEstablishmentToDeviceRequest(event.data)
            if (!result.isOk) {
                log.error("$logId: Failed to process session establishment in listener: ${result.error.message.defaultMessage}")
            }
        }
    }

    /**
     * Bridge dispatcher that converts BLE channel events to MdocRetrievalEvents.
     * This allows the new modular transport channels to dispatch events that
     * the old datatransfer layer can understand and process.
     *
     @OptIn(ExperimentalObjCName::class)
     @ObjCName("from", exact = true)
     * Implements the common DataChannelEventDispatcher interface from transport-core.
     */
    private inner class TransportToMdocEventBridge : DataChannelEventDispatcher {
        override fun dispatchSessionEstablishmentReceived(instanceId: Uuid, data: ByteArray) {
            log.info("$logId: Transport channel dispatched SessionEstablishmentReceived, bridging to MdocRetrievalEvent")
            // Dispatch as MdocRetrievalEvent that the existing handlers understand
            dispatch(MdocRetrievalEvent.SessionEstablishmentReceived(engagement.id, data))
        }

        override fun dispatchSessionDataReceived(instanceId: Uuid, data: ByteArray) {
            log.info("$logId: Transport channel dispatched SessionDataReceived")
            // Not currently used in the request/response flow, but could be in the future
        }

        override fun dispatchTerminationReceived(instanceId: Uuid, data: ByteArray) {
            log.info("$logId: Transport channel dispatched TerminationReceived")
            // Could dispatch a termination event if needed
        }

        override fun dispatchSessionDataSent(instanceId: Uuid, data: ByteArray) {
            log.info("$logId: Transport channel dispatched SessionDataSent")
            // Already dispatched in sendDeviceResponseInternal, no need to duplicate
        }
    }

    // Delegate instance for event listening - will be registered as a listener
    private val eventListenerAdapter = RetrievalEventListenerAdapter()

    init {
        // Register the adapter as a listener BEFORE creating the instance
        // This ensures we receive SessionEstablishmentReceived events without replay issues
        _earlyListeners.add(eventListenerAdapter)
    }

    private var _instance: TransferInstanceImpl = initTransferInstance() as TransferInstanceImpl
    private val _isClosed: AtomicBoolean = atomic(false)
    override val isClosed: Boolean
        get() = _isClosed.value

    // Signal for deviceRequest readiness (solves collector race)
    private val deviceRequestReady = CompletableDeferred<DeviceRequest>()

    private val _listeners: Set<MdocRetrievalEvent.Listener>
        // fixme: This makes no sense. Just register the earlyListeners on the session when creating it
        get() = if (!isClosed) _instance.getRetrievalEventListeners() else _earlyListeners.toSet()

    override val instance: TransferInstance
        get() = assertedInstance(minState = MdocRetrievalState.INIT)

    private val selectorLog = execution.log.logManager.withTag("RequestResponseSelection")


    private val defaultDocRequestSingleDocumentSelector: DocumentRequestSingleDocumentSelector = SimpleDocumentRequestSingleDocumentSelector(log = selectorLog)
    private val defaultRequestDocumentsSelector: RequestDocumentsSelector =
        SimpleRequestDocumentsSelector(docRequestSingleDocSelect = defaultDocRequestSingleDocumentSelector, log = selectorLog, globalDocumentsProvider = null)
    private val defaultRequestResponseProcesser: RequestResponseProcessor = SimpleRequestResponseProcessor(documentsSelector = defaultRequestDocumentsSelector)

    private val sessionEncryption: AtomicRef<SessionEncryption?> = atomic(null)

    private val log = execution.log.logManager.withTag("TransferManager-${_instance.id}")
    private val logId = _instance.id.toString()


    override fun getSessionTranscript(): CborEncodedItem<SessionTranscript> {
        log.info("${logId}: Getting session transcript")
        return sessionTranscript.value
            ?: throw IllegalStateException(
                "Session transcript not initialized. You must call receiveDeviceRequest() first to establish the session."
            )
    }

    /**
     * Try to get the session transcript without throwing an exception.
     *
     * @return IdkResult containing the session transcript or an error if not initialized
     */
    fun tryGetSessionTranscript(): IdkResult<CborEncodedItem<SessionTranscript>, IdkError> {
        val transcript = sessionTranscript.value
        return if (transcript != null) {
            transcript.asOkResult()
        } else {
            IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Session transcript not initialized. Call receiveDeviceRequest() first."
            ).asErrorResult()
        }
    }

    override suspend fun validateReaderAuthentication(docRequest: DocRequest, requireReaderAuthentication: Boolean): VerifySignatureResultType<CoseKeyType> {
        log.info("$logId: Validating reader authentication")
        log.debug("$logId: Reader authentication required: $requireReaderAuthentication")

        val transcriptValue = sessionTranscript.value
        val transcript = transcriptValue?.data()
        if (transcript == null) {
            val error = "Session transcript not initialized. Call receiveDeviceRequest() first."
            log.error("$logId: $error")
            return VerifySignatureResult<CoseKeyType>(
                error = true,
                critical = true,
                message = error,
                name = "ReaderAuthentication"
            )
        }

        log.debug("$logId: Session transcript: $transcript")
        val readerAuth = docRequest.readerAuth
        log.debug("$logId: Reader authentication: $readerAuth")

        if (readerAuth == null) {
            log.info("$logId: No reader authentication found in request")
            if (requireReaderAuthentication) {
                log.warn("$logId: Reader authentication required, but not provided")
            }
            return VerifySignatureResult<CoseKeyType>(
                error = requireReaderAuthentication,
                critical = requireReaderAuthentication,
                message = "No reader authentication found in request",
                name = "ReaderAuthentication"
            )
        }

        // TODO: Implement full reader authentication validation
        // The ReaderAuth is a COSE_Sign1 structure where:
        // - protected header contains the algorithm
        // - unprotected header may contain x5chain (certificate chain)
        // - payload is ReaderAuthenticationBytes = SessionTranscript
        // - signature is over: Sig_structure = ["Signature1", protected, external_aad, payload]
        //
        // Steps needed:
        // 1. Extract reader's public key from x5chain or kid
        // 2. Verify the COSE_Sign1 signature over the session transcript
        // 3. Validate the certificate chain if present
        // 4. Check certificate validity period and trust anchor
        //
        // For now, we'll return a placeholder result
        log.warn("$logId: Reader authentication validation not fully implemented yet")

        return VerifySignatureResult<CoseKeyType>(
            error = false,  // TODO: Actually verify the signature
            critical = false,
            message = "Reader authentication validation not fully implemented. ReaderAuth present but not verified.",
            name = "ReaderAuthentication"
        )
    }

    override suspend fun signDocument(
        request: DocRequest,
        document: Document,
        deviceKeyInfo: KeyInfoType<*>?,
        deviceNamespaces: DeviceNameSpaces,
        unprotectedHeader: CoseHeaderCbor?,
        protectedHeader: CoseHeaderCbor?,
        requireDeviceX5Chain: Boolean
    ): Document {
        log.info("$logId: Signing document with auto-bound session transcript")

        // Validate request matches document with detailed error message
        validateDocumentRequest(request, document)

        // Get session transcript from context
        val transcript = getSessionTranscript().data()

        // Build device authentication with transcript auto-bound
        val deviceAuthentication = DeviceAuthentication(
            sessionTranscript = transcript,
            docType = document.docType,
            deviceNamespaces = deviceNamespaces,
            original = null
        )

        // Sign the document
        return mdocSignService.deviceSignDocument(
            request = request,
            document = document,
            deviceAuthentication = deviceAuthentication,
            deviceKeyInfo = deviceKeyInfo,
            unprotectedHeader = unprotectedHeader,
            protectedHeader = protectedHeader,
            requireDeviceX5Chain = requireDeviceX5Chain
        ).also {
            log.info("$logId: Document signed successfully")
        }
    }

    /**
     * Validates that a DocRequest matches the Document being signed.
     * Provides detailed error messages to help developers debug issues.
     */
    private fun validateDocumentRequest(request: DocRequest, document: Document) {
        require(request.itemsRequest.docType == document.docType) {
            """
            DocType mismatch:
              Request expects: '${request.itemsRequest.docType}'
              Document has: '${document.docType}'
              
            Ensure the DocRequest matches the Document you're signing.
            """.trimIndent()
        }

        // Additional validation: Check that document has required namespaces
        val requestedNamespaces = request.itemsRequest.nameSpaces.keys
        val documentNamespaces = document.issuerSigned.nameSpaces?.keys ?: emptySet()

        val missingNamespaces = requestedNamespaces - documentNamespaces
        if (missingNamespaces.isNotEmpty()) {
            log.warn("$logId: Requested namespaces not in document: $missingNamespaces")
        }
    }



    override fun close() {
        log.debug("$logId: Transfer manager closing...")

        if (_isClosed.value) {
            log.debug("$logId: Already closed, skipping")
            return
        }

        // Note: BLE event listeners are now managed by the modular transport layer
        // No need to manually remove them here

        try {
            _instance.close()
            _earlyListeners.clear()
            _isClosed.value = true
            log.debug("$logId: Transfer manager closed")
        } catch (e: Exception) {
            log.error("$logId: Error during close", exception = e)
        }
    }


    override suspend fun receiveDeviceRequest(): DeviceRequest {
        val res = tryOps().receiveDeviceRequest()
        return if (res.isOk) res.value else throw res.error.toException()
    }

    override suspend fun sendDeviceResponse(deviceResponse: DeviceResponse): Int {
        val res = tryOps().sendDeviceResponse(deviceResponse)
        return if (res.isOk) res.value else throw res.error.toException()
    }

    override suspend fun createDeviceResponse(deviceRequest: DeviceRequest, documentProvider: DocumentProvider?): IdkResult<DeviceResponse, IdkErrorType> {
        log.info("$logId: Creating device response using request processor")
        // Read atomic values once
        val customSingleDocSelector = this.customDocRequestSingleDocSelector.value
        val customDocumentsSelector = this.customRequestDocumentsSelector.value
        val customResponseProcesser = this.customRequestResponseProcesser.value

        val singleDocumentSelector = customSingleDocSelector ?: defaultDocRequestSingleDocumentSelector
        val requestDocumentsSelector = customDocumentsSelector ?: if (customSingleDocSelector != null) SimpleRequestDocumentsSelector(
            docRequestSingleDocSelect = singleDocumentSelector,
            log = selectorLog,
            globalDocumentsProvider = documentProvider
        ) else defaultRequestDocumentsSelector

        val requestResponseProcesser = customResponseProcesser ?: if (customDocumentsSelector != null) SimpleRequestResponseProcessor(
            requestDocumentsSelector,
            documentProvider
        ) else defaultRequestResponseProcesser
        return requestResponseProcesser.createDeviceResponse(deviceRequest, documentProvider).also { log.info("$logId: Device response created") }
    }

    override suspend fun createResponse(deviceRequest: DeviceRequest, documentProvider: DocumentProvider?): DeviceResponse {
        val res = tryOps().createResponse(deviceRequest, documentProvider)
        return if (res.isOk) res.value else throw res.error.toException()
    }

    override fun registerCustomResponseSelectors(
        requestResponseProcesser: RequestResponseProcessor?,
        requestDocumentsSelector: RequestDocumentsSelector,
        docRequestSingleDocumentSelector: DocumentRequestSingleDocumentSelector,
    ) {
        this.customDocRequestSingleDocSelector.value = docRequestSingleDocumentSelector
        this.customRequestDocumentsSelector.value = requestDocumentsSelector
        this.customRequestResponseProcesser.value = requestResponseProcesser
    }

    override fun dispatch(event: MdocRetrievalEvent) = assertedInstance().dispatch(event)

    override fun getRetrievalEventListeners(): Set<MdocRetrievalEvent.Listener> {
        // Return defensive copy to avoid concurrent modification
        return _earlyListeners.toSet()
    }


    override fun addRetrievalEventListener(vararg listener: MdocRetrievalEvent.Listener) {
        _earlyListeners.addAll(listener)
    }

    override fun removeRetrievalEventListener(listener: MdocRetrievalEvent.Listener) {
        _earlyListeners.remove(listener)
    }

    override fun clearRetrievalEventListeners() {
        _earlyListeners.clear()
    }

    private fun initTransferInstance(type: DataInstanceType = DataInstanceType.PER_ENGAGEMENT): TransferInstance {
        return when (type) {
            DataInstanceType.SINGLETON -> {
                throw IllegalArgumentException("Singleton transfer not supported yet. Please use PER_ENGAGEMENT mode instead")
            }

            DataInstanceType.PER_ENGAGEMENT -> {
                TransferInstanceImpl(
                    engagement = engagement,
                    manager = this,
                    execution = execution,
                    transferFactory = transferFactory
                )
            }
        }
    }

    private fun assertedInstance(minState: MdocRetrievalState? = null, maxState: MdocRetrievalState? = null): TransferInstanceImpl {
        val instance = this._instance
        if (minState != null && instance.getCurrentState().order < minState.order) {
            throw IllegalStateException("Engagement is already in progress with a state ${instance.getCurrentState()} smaller than ${minState}")
        } else if (maxState != null && instance.getCurrentState().order > maxState.order) {
            throw IllegalStateException("Engagement is already in progress with a state ${instance.getCurrentState()} bigger than ${maxState}")
        }
        if (_earlyListeners.isNotEmpty()) {
            // Make sure we register any already listeners with the engagement
            // Since the instance is using a set, we simply add them always, so the _earlyListeners can act as shared listeners across instances
            instance.addRetrievalEventListener(*_earlyListeners.toTypedArray())
        }
        return instance
    }

    @OptIn(ExperimentalObjCName::class)
    @ObjCName("DeviceResponseSelectors", exact = true)
    class DeviceResponseSelectors(
        requestResponseProcesser: RequestResponseProcessor?,
        requestDocumentsSelector: RequestDocumentsSelector,
        docRequestSingleDocumentSelector: DocumentRequestSingleDocumentSelector,
    )

    /**
     * Private implementation of DocumentSigningBuilder that accumulates signing requests
     * and executes them through the TransferManager.
     */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DocumentSigningBuilderImpl", exact = true)
    private class DocumentSigningBuilderImpl(
        private val transferManager: TransferManagerImpl
    ) : DocumentSigningBuilder {
        private val signingRequests = mutableListOf<DocumentSigningRequest>()

        override fun add(
            request: DocRequest,
            document: Document,
            deviceKeyInfo: KeyInfoType<*>?,
            deviceNamespaces: DeviceNameSpaces
        ): DocumentSigningBuilder = apply {
            signingRequests.add(
                DocumentSigningRequest(
                    request = request,
                    document = document,
                    deviceKeyInfo = deviceKeyInfo,
                    deviceNamespaces = deviceNamespaces
                )
            )
        }

        override fun add(signingRequest: DocumentSigningRequest): DocumentSigningBuilder = apply {
            signingRequests.add(signingRequest)
        }

        override fun addAll(signingRequests: List<DocumentSigningRequest>): DocumentSigningBuilder = apply {
            this.signingRequests.addAll(signingRequests)
        }

        override suspend fun signAll(): List<Document> {
            return transferManager.signDocuments(signingRequests)
        }

        override suspend fun buildResponse(): DeviceResponse {
            val signedDocuments = signAll()
            return DeviceResponse.Builder()
                .withDocuments(signedDocuments.toTypedArray())
                .build()
        }
    }

    override suspend fun signDocuments(
        signingRequests: List<DocumentSigningRequest>
    ): List<Document> {
        log.info("$logId: Signing ${signingRequests.size} documents")

        return signingRequests.map { signingRequest ->
            signDocument(
                request = signingRequest.request,
                document = signingRequest.document,
                deviceKeyInfo = signingRequest.deviceKeyInfo,
                deviceNamespaces = signingRequest.deviceNamespaces,
                unprotectedHeader = signingRequest.unprotectedHeader,
                protectedHeader = signingRequest.protectedHeader,
                requireDeviceX5Chain = signingRequest.requireDeviceX5Chain
            )
        }.also {
            log.info("$logId: Signed ${it.size} documents successfully")
        }
    }

    override fun documentsBuilder(): DocumentSigningBuilder {
        return DocumentSigningBuilderImpl(this)
    }

    override suspend fun start() = apply {
        val res = tryOps().start()
        if (!res.isOk) throw res.error.toException()
    }

    override fun tryOps(): TransferManager.Try = object : TransferManager.Try {
        override suspend fun start(): IdkResult<TransferManager, IdkErrorType> = try {
            // Start the transfer instance first - this will create the modular transport
            // and set the data channels via ConnectionManager.advertise()
            this@TransferManagerImpl.apply {
                log.info("$logId: Starting transfer session")
                val instance = assertedInstance(minState = MdocRetrievalState.INIT)
                if (instance.getCurrentState().order > MdocRetrievalState.INIT.order) {
                    throw IllegalStateException(
                        "$logId: Transfer already initialized for engagement ${engagement.id}. " +
                                "Each transfer can only be started once. Current state: ${instance.getCurrentState()}. " +
                                "To restart a transfer, close the engagement and create a new one."
                    )
                }

                instance.start()
                log.info("$logId: Transfer session started: ${instance.id}")
                log.debug("$logId: Transfer session: $instance")

                // Note: For TO_APP engagements (REST API, OID4VP, BLE/NFC reverse):
                // DeviceRequest is NOT extracted here from ReaderEngagement
                // Instead, it comes via the transport layer:
                // - REST API: DeviceRequest is in SessionEstablishment response from server
                // - BLE/NFC reverse: DeviceRequest comes via channel after connection
                // - OID4VP: DeviceRequest is embedded in OpenID4VP request
                //
                // The transport layer (RestApiTransfer, BLE, etc.) will call
                // setDeviceRequest() when it receives the request, which will then
                // be returned by receiveDeviceRequest()

                // Note: BLE listener registration is now handled in setDataChannels()
                // This avoids race conditions where channels are accessed before they're set
            }.asOkResult()
        } catch (t: Throwable) {
            log.error("$logId: Error starting transfer session", exception = t)
            IdkError.UNKNOWN_ERROR(exception = t).asErrorResult()
        }

        override suspend fun receiveDeviceRequest(): IdkResult<DeviceRequest, IdkErrorType> {
            return receiveDeviceRequestInternal()
        }

        override suspend fun sendDeviceResponse(deviceResponse: DeviceResponse): IdkResult<Int, IdkErrorType> {
            return sendDeviceResponseInternal(deviceResponse)
        }

        override suspend fun createResponse(deviceRequest: DeviceRequest, documentProvider: DocumentProvider?): IdkResult<DeviceResponse, IdkErrorType> {
            return createDeviceResponse(deviceRequest, documentProvider)
        }
    }

    /**
     * Private method to process SessionEstablishment data and extract DeviceRequest.
     * This includes decoding session establishment, generating session transcript,
     * setting up session encryption, and decrypting the device request.
     *
     * @param readerData The raw CBOR-encoded session establishment data
     * @return IdkResult containing the extracted DeviceRequest or an error
     */
    private suspend fun processSessionEstablishmentToDeviceRequest(readerData: ByteArray): IdkResult<DeviceRequest, IdkErrorType> {
        assertNotClosed()

        // Delegate to the processor for the complex session establishment logic
        val result = sessionEstablishmentProcessor.process(readerData, engagement)

        return if (result.isOk) {
            val processResult = result.value

            // Update state with results from processor
            sessionTranscript.value = processResult.sessionTranscript
            sessionEncryption.value = processResult.sessionEncryption

            // Set deviceRequest on the instance before dispatching the event
            val deviceRequest = processResult.deviceRequest
            _instance.deviceRequest = deviceRequest
            log.info("$logId: Device request set on instance")

            // Signal any receivers waiting for deviceRequest
            if (!deviceRequestReady.isCompleted) {
                deviceRequestReady.complete(deviceRequest)
            }

            // Dispatch event for UI
            dispatch(MdocRetrievalEvent.DocumentsSelectionProcessStart(engagement.id, deviceRequest.encodeCbor()))
            log.info("$logId: DocumentsSelectionProcessStart event dispatched")

            deviceRequest.asOkResult().also { log.info("Device request extracted from session establishment") }
        } else {
            val error = result.error
            log.error("$logId: Error processing session establishment: ${error.message.defaultMessage}")
            val exception = error.exception ?: RuntimeException(error.message.defaultMessage)
            dispatch(MdocRetrievalEvent.Error(engagement.id, data = byteArrayOf(), error = exception))
            error.asErrorResult()
        }
    }

    private suspend fun receiveDeviceRequestInternal(): IdkResult<DeviceRequest, IdkErrorType> {
        assertNotClosed()
        return try {
            // Check if deviceRequest is already set (by background event handler or transport)
            val existingRequest = _instance.deviceRequest
            if (existingRequest != null) {
                log.info("$logId: Device request already available from background processing")
                if (!deviceRequestReady.isCompleted) {
                    deviceRequestReady.complete(existingRequest)
                }
                return existingRequest.asOkResult()
            }

            // Determine transport type from engagement's connection methods
            val isChannelBasedTransport = isChannelBasedTransport()
            log.info("$logId: Transport type: ${if (isChannelBasedTransport) "channel-based (BLE/NFC)" else "direct (OID4VP/REST)"}")

            val deviceRequest = if (isChannelBasedTransport) {
                receiveViaChannelTransport()
            } else {
                receiveViaDirectTransport()
            }

            deviceRequest.asOkResult()
        } catch (e: Exception) {
            handleReceiveError(e)
        }
    }

    /**
     * Check if the engagement uses channel-based transport (BLE, NFC).
     */
    private fun isChannelBasedTransport(): Boolean {
        val connectionMethods = engagement.getConnectionMethods(supportedOnly = true)
        return connectionMethods.any {
            it.transportType == TransportType.BLE || it.transportType == TransportType.NFC
        }
    }

    /**
     * Receive device request via channel-based transport (BLE, NFC).
     * Waits for channels to be ready, then awaits the background event.
     */
    private suspend fun receiveViaChannelTransport(): DeviceRequest {
        log.info("$logId: Waiting for channels and device request")
        awaitChannels()
        log.info("$logId: Channels ready, waiting for device request via background event")
        return deviceRequestReady.await().also {
            log.info("$logId: Device request obtained via deviceRequestReady deferred")
        }
    }

    /**
     * Receive device request via direct transport (OID4VP, REST API).
     * Fetches request via blocking message receive.
     */
    private suspend fun receiveViaDirectTransport(): DeviceRequest {
        val transfer = _instance.transfer
            ?: throw IllegalStateException("Transfer not initialized. Call start() first.")

        setupDirectTransportSessionContext(transfer)

        val deviceRequestBytes = transfer.messageReceiveBlocking()
        val deviceRequest = DeviceRequest.decodeCbor(deviceRequestBytes)

        // Set on instance and complete deferred
        _instance.deviceRequest = deviceRequest
        if (!deviceRequestReady.isCompleted) {
            deviceRequestReady.complete(deviceRequest)
        }

        log.info("$logId: Device request created from direct transport")
        dispatch(MdocRetrievalEvent.DocumentsSelectionProcessStart(engagement.id, deviceRequestBytes))
        log.info("$logId: DocumentsSelectionProcessStart event dispatched")

        return deviceRequest
    }

    /**
     * Setup session context for direct transports (OID4VP, REST API).
     */
    private suspend fun setupDirectTransportSessionContext(transfer: IMdocTransfer<*>) {
        if (transfer.connectionMethod.transportType == TransportType.OID4VP) {
            setupOid4vpSessionContext(transfer)
            return
        }

        val transcript = transfer.getContext("sessionTranscript") as? SessionTranscript ?: return
        if (sessionTranscript.value == null) {
            val encodedTranscript = CborEncodedItem.fromData(transcript)
            sessionTranscript.value = encodedTranscript
            log.info("$logId: Session transcript set from transport context")
        }
    }

    /**
     * Setup OID4VP session context (transcript and encryption).
     * OID4VP doesn't use session-level encryption for transport (uses JARM instead),
     * but we need SessionEncryption initialized for document signing to work.
     */
    private suspend fun setupOid4vpSessionContext(transfer: IMdocTransfer<*>) {
        log.info("$logId: Setting up OID4VP session context")
        try {
            // Get SessionTranscript from transport via context API
            val transcript = transfer.getContext("sessionTranscript") as? SessionTranscript
                ?: throw IllegalStateException("OID4VP transport did not provide SessionTranscript")

            // Wrap and store the SessionTranscript
            val encodedTranscript = com.sphereon.cbor.CborEncodedItem.fromData(transcript)
            sessionTranscript.value = encodedTranscript
            val transcriptBytes = encodedTranscript.encodeCbor()
            log.info("$logId: OID4VP session transcript set")

            // Initialize session encryption (for document signing context)
            // OID4VP has no reader ephemeral key exchange, so we use the engagement key for both
            val newSessionEncryption = SessionEncryption.Builder()
                .withProvider(CryptographyProvider.Default)
                .withSessionTranscriptBytes(transcriptBytes)
                .withSelfRole(MdocRole.MDOC)
                .withSelfPrivateEphemeralKey(ResolvedKeyInfo.fromKey(engagement.getEphemeralKey()))
                .withRemotePublicEphemeralKey(ResolvedKeyInfo.fromKey(engagement.getEphemeralKey()))
                .build()
            sessionEncryption.value = newSessionEncryption
            log.info("$logId: OID4VP session encryption initialized")
        } catch (e: Exception) {
            log.error("$logId: Failed to setup OID4VP session context", exception = e)
            throw e
        }
    }

    /**
     * Handle errors during device request reception.
     */
    private fun handleReceiveError(e: Exception): IdkResult<DeviceRequest, IdkErrorType> {
        if (isClosed) {
            log.debug("$logId: device request Exception but we were already closed: ${e.message}")
        }
        log.error("$logId: Error receiving device request: ${e.message}", exception = e)
        dispatch(MdocRetrievalEvent.Error(engagement.id, data = byteArrayOf(), error = e))
        return IdkError.UNKNOWN_ERROR(exception = e).asErrorResult().also {
            log.error(it.error.message.defaultMessage)
        }
    }



    private suspend fun sendDeviceResponseInternal(deviceResponse: DeviceResponse): IdkResult<Int, IdkErrorType> {
        assertNotClosed()
        return try {
            log.info("$logId: Sending device response")
            instance.deviceResponse = deviceResponse
            val deviceResponseBytes = deviceResponse.encodeCbor()

            // Log DeviceResponse before encryption
            debugLogger.logDeviceResponse("DeviceResponse to be encrypted and sent to reader", deviceResponseBytes)

            // Check if we have data channels (channel-based transport like BLE)
            val hasChannels = _incomingDataChannel != null && _outgoingDataChannel != null

            if (hasChannels) {
                // Channel-based transport (BLE, NFC): use session encryption and data channels
                log.info("$logId: Channel-based transport - using session encryption")
                val currentEncryption = sessionEncryption.value ?: throw IllegalStateException("Session encryption not initialized. Call receiveDeviceRequest() first.")
                val sessionData = currentEncryption.encryptAsSessionData(deviceResponseBytes)
                val sessionDataBytes = sessionData.encodeCbor()

                // Log encrypted SessionData being sent
                debugLogger.logSessionData("Encrypted SessionData (contains DeviceResponse) being sent to reader", sessionDataBytes)

                dispatch(MdocRetrievalEvent.SessionDataSend(engagement.id, sessionDataBytes, deviceResponse))
                val result = outgoingDataChannel.sendRaw(sessionDataBytes)

                log.info("$logId: Device response was sent to outgoing datachannel. Result: $result")

                // Per ISO 18013-5, after sending the DeviceResponse, the holder (mdoc) should
                // wait for the reader to initiate session termination or disconnect.
                // The holder should NOT send SessionTerminationSend - only the reader does that.
                // The SessionDataSend event already signals that data was sent successfully.
                // The engagement will be cleaned up when:
                // 1. Reader sends SessionTerminationReceived, OR
                // 2. Reader disconnects the BLE connection, OR
                // 3. Timeout (handled by MdocEngagementManagerImpl)
                log.info("$logId: Device response sent successfully. Waiting for reader to disconnect or send termination.")

                result.asOkResult()
            } else {
                // Non-channel transport (OID4VP, REST API): use direct message send
                // These transports handle their own encryption (JARM for OID4VP, session encryption for REST API)
                log.info("$logId: Non-channel transport - using direct message send (transport handles encryption)")
                val transfer = _instance.transfer
                    ?: throw IllegalStateException("Transfer not initialized. Call start() first.")

                // For OID4VP: The device response passed to this method might not be the signed one
                // from executeHolderFlow(). We need to check if this is OID4VP and get the proper
                // DeviceResponse that was created with the correct session transcript and signatures.
                val actualDeviceResponseBytes: ByteArray
                val actualDeviceResponse: DeviceResponse

                if (transfer.connectionMethod.transportType == TransportType.OID4VP) {
                    log.info("$logId: OID4VP transport - setting DeviceResponse object via context")
                    // For OID4VP, pass the DeviceResponse object directly to avoid encode/decode issues
                    transfer.setContextForSending("deviceResponse", deviceResponse)

                    actualDeviceResponse = deviceResponse
                    actualDeviceResponseBytes = deviceResponseBytes
                } else {
                    actualDeviceResponse = deviceResponse
                    actualDeviceResponseBytes = deviceResponseBytes
                }

                dispatch(MdocRetrievalEvent.SessionDataSend(engagement.id, actualDeviceResponseBytes, actualDeviceResponse))
                transfer.messageSendBlocking(actualDeviceResponseBytes)
                val result = actualDeviceResponseBytes.size

                log.info("$logId: Device response sent via direct message send. Result: $result")

                // For non-channel transports (OID4VP, REST API), the HTTP POST completing
                // successfully indicates the transaction is complete. However, per ISO 18013-5,
                // the holder should not dispatch SessionTerminationSend - that's for the reader.
                // The SessionDataSend event already signals successful data transmission.
                // For HTTP-based transports, the application can treat successful SessionDataSend
                // as transaction complete since there's no separate "disconnect" to wait for.
                log.info("$logId: Device response sent successfully via non-channel transport.")

                result.asOkResult()
            }
        } catch (e: Exception) {
            if (isClosed) {
                log.debug("Exception but we were already closed: ${e.message}")
                return 0.asOkResult()
            }
            log.error("$logId: Error sending device response: ${e.message}", exception = e)
            dispatch(MdocRetrievalEvent.Error(engagement.id, data = byteArrayOf(), error = e))
            IdkError.UNKNOWN_ERROR(exception = e).asErrorResult().also { execution.log.error(it.error.message.defaultMessage) }
        }
    }

    private fun assertNotClosed() = require(!isClosed) { "Transfer manager is closed"}
}
