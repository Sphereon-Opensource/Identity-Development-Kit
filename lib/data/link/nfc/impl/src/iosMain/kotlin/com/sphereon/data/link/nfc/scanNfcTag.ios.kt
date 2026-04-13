package com.sphereon.data.link.nfc

import com.sphereon.core.util.toKotlinError
import com.sphereon.data.link.nfc.model.NfcIsoTag
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreNFC.NFCISO7816TagProtocol
import platform.CoreNFC.NFCPollingISO14443
import platform.CoreNFC.NFCPollingISO15693
import platform.CoreNFC.NFCTagProtocol
import platform.CoreNFC.NFCTagReaderSession
import platform.CoreNFC.NFCTagReaderSessionDelegateProtocol
import platform.Foundation.NSError
import platform.CoreNFC.NFCErrorDomain
import platform.CoreNFC.NFCReaderSessionInvalidationErrorUserCanceled
import platform.darwin.NSObject
import kotlin.coroutines.resumeWithException

actual val nfcSupported = true
actual val nfcTagScanningSupported = true

actual val nfcTagScanningSupportedWithoutDialog: Boolean = false

private class NfcTagReader<T> {

    companion object {
        private const val TAG = "NfcTagReader"
        private var currentSession: NFCTagReaderSession? = null
        private var sessionTimeout: Long = 60000 // 60 seconds

        // Clean up any existing session before creating a new one
        private fun ensureCleanState() {
            currentSession?.let { session ->
                try {
                    session.invalidateSession()
                } catch (e: Throwable) {
                    // Ignore cleanup errors
                }
            }
            currentSession = null
        }
    }

    val session: NFCTagReaderSession

    @OptIn(ExperimentalCoroutinesApi::class)
    private val tagReaderSessionDelegate = object : NSObject(), NFCTagReaderSessionDelegateProtocol {
        override fun tagReaderSession(
            session: NFCTagReaderSession,
            didInvalidateWithError: NSError
        ) {
            val errorMessage = didInvalidateWithError.localizedDescription
            val errorDomain = didInvalidateWithError.domain
            val errorCode = didInvalidateWithError.code

            // Handle specific NFC errors
            if (errorDomain == NFCErrorDomain) {
                when (errorCode) {
                    NFCReaderSessionInvalidationErrorUserCanceled -> {
                        continuation?.resumeWithException(CancellationException("Dialog was canceled"))
                        continuation = null
                        return
                    }

                    203L -> {
                        // NFCError Code 203: System resource unavailable
                        continuation?.resumeWithException(Exception("NFC system resource unavailable. Please wait a moment and try again, or restart the app if the problem persists."))
                        continuation = null
                        return
                    }
                }
            }

            continuation?.resumeWithException(Exception(errorMessage))
            continuation = null
        }

        override fun tagReaderSessionDidBecomeActive(
            session: NFCTagReaderSession
        ) {
            // Session is now active and polling for tags
        }

        override fun tagReaderSession(
            session: NFCTagReaderSession,
            didDetectTags: List<*>
        ) {
            if (didDetectTags.isEmpty()) {
                session.invalidateSessionWithErrorMessage("No tags detected")
                continuation?.resumeWithException(Exception("No tags detected"))
                continuation = null
                return
            }

            // Currently we only consider the first tag. We might need to look at all tags.
            val tag = didDetectTags[0] as? NFCTagProtocol
            if (tag == null) {
                session.invalidateSessionWithErrorMessage("Invalid tag type")
                continuation?.resumeWithException(Exception("Invalid tag type"))
                continuation = null
                return
            }

            session.connectToTag(tag) { error ->
                if (error != null) {
                    session.invalidateSessionWithErrorMessage("Connection failed: ${error.localizedDescription}")
                    continuation?.resumeWithException(Exception("Connection failed: ${error.localizedDescription}"))
                    continuation = null
                } else {
                    val isoTag = NfcIsoTagIos(tag as NFCISO7816TagProtocol)
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val ret = tagInteractionFunc(isoTag) { message ->
                                session.alertMessage = message
                            }
                            if (ret != null) {
                                session.alertMessage = "Success!"
                                continuation?.resume(ret, null)
                                continuation = null
                            } else {
                                session.alertMessage = "No data found, try again"
                                session.restartPolling()
                            }
                        } catch (e: Throwable) {
                            session.invalidateSessionWithErrorMessage("Error: ${e.message}")
                            continuation?.resumeWithException(e)
                            continuation = null
                        }
                    }
                }
            }
        }
    }

    init {
        // Ensure clean state before creating new session
        ensureCleanState()

        // Use both ISO14443 and ISO15693 polling to maximize compatibility
        val pollingOptions = NFCPollingISO14443 or NFCPollingISO15693

        session = NFCTagReaderSession(
            pollingOption = pollingOptions,
            delegate = tagReaderSessionDelegate,
            queue = null,
        )

        currentSession = session
    }

    private var continuation: CancellableContinuation<T>? = null

    private lateinit var tagInteractionFunc: suspend (
        tag: NfcIsoTag,
        updateMessage: (message: String) -> Unit
    ) -> T?

    @Throws(IllegalStateException::class, Exception::class)
    suspend fun beginSession(
        alertMessage: String,
        tagInteractionFunc: suspend (
            tag: NfcIsoTag,
            updateMessage: (message: String) -> Unit
        ) -> T?
    ): T {
        if (!NFCTagReaderSession.readingAvailable) {
            throw IllegalStateException("NFC tag reading is not available on this device. Make sure NFC is enabled in Settings.")
        }
        try {
            val ret = suspendCancellableCoroutine { continuation ->
                this.continuation = continuation
                this.tagInteractionFunc = tagInteractionFunc
                session.setAlertMessage(alertMessage)
                session.beginSession()
            }
            try {
                session.invalidateSession()
            } catch (e: Throwable) {
                // Ignore cleanup errors
            } finally {
                currentSession = null
            }
            return ret
        } catch (e: CancellationException) {
            try {
                session.invalidateSessionWithErrorMessage("Dialog was canceled")
            } catch (ex: Throwable) {
                // Ignore cleanup errors
            } finally {
                currentSession = null
            }
            throw e
        } catch (e: Throwable) {
            try {
                session.invalidateSessionWithErrorMessage(e.message!!)
            } catch (ex: Throwable) {
                // Ignore cleanup errors
            } finally {
                currentSession = null
            }
            throw e
        }
    }
}

actual suspend fun<T: Any> scanNfcTag(
    message: String?,
    tagInteractionFunc: suspend (
        tag: NfcIsoTag,
        updateMessage: (message: String) -> Unit
    ) -> T?
): T {
    require(message != null) { "Cannot not show the NFC tag scanning dialog on iOS" }
    val reader = NfcTagReader<T>()
    return reader.beginSession(message, tagInteractionFunc)
}
