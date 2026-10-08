/*
 * Copyright 2023-2026 Sphereon International B.V.
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
 */

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
import platform.CoreNFC.NFCErrorDomain
import platform.CoreNFC.NFCISO7816TagProtocol
import platform.CoreNFC.NFCPollingISO14443
import platform.CoreNFC.NFCPollingISO15693
import platform.CoreNFC.NFCReaderSessionInvalidationErrorUserCanceled
import platform.CoreNFC.NFCTagProtocol
import platform.CoreNFC.NFCTagReaderSession
import platform.CoreNFC.NFCTagReaderSessionDelegateProtocol
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.coroutines.resumeWithException

actual val nfcSupported = true
actual val nfcTagScanningSupported = true

actual val nfcTagScanningSupportedWithoutDialog: Boolean = false

private class NfcTagReader<T> {
    companion object {
        private const val TAG = "NfcTagReader"

        /** CoreNFC error code for system resource unavailable. */
        private const val NFC_ERROR_SYSTEM_RESOURCE_UNAVAILABLE = 203L

        // Static because iOS only allows one active NFCTagReaderSession at a time
        private var currentSession: NFCTagReaderSession? = null

        // Clean up any existing session before creating a new one
        private fun ensureCleanState() {
            currentSession?.let { session ->
                try {
                    session.invalidateSession()
                } catch (_: Throwable) {
                    // Ignore cleanup errors
                }
            }
            currentSession = null
        }
    }

    val session: NFCTagReaderSession

    @OptIn(ExperimentalCoroutinesApi::class)
    private val tagReaderSessionDelegate =
        object : NSObject(), NFCTagReaderSessionDelegateProtocol {
            override fun tagReaderSession(
                session: NFCTagReaderSession,
                didInvalidateWithError: NSError,
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

                        NFC_ERROR_SYSTEM_RESOURCE_UNAVAILABLE -> {
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

            override fun tagReaderSessionDidBecomeActive(session: NFCTagReaderSession) {
                // Session is now active and polling for tags
            }

            override fun tagReaderSession(
                session: NFCTagReaderSession,
                didDetectTags: List<*>,
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
                                val ret =
                                    tagInteractionFunc(isoTag) { message ->
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
                            } catch (expected: Throwable) {
                                session.invalidateSessionWithErrorMessage("Error: ${expected.message}")
                                continuation?.resumeWithException(expected)
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

        session =
            NFCTagReaderSession(
                pollingOption = pollingOptions,
                delegate = tagReaderSessionDelegate,
                queue = null,
            )

        currentSession = session
    }

    private var continuation: CancellableContinuation<T>? = null

    private lateinit var tagInteractionFunc: suspend (
        tag: NfcIsoTag,
        updateMessage: (message: String) -> Unit,
    ) -> T?

    @Throws(IllegalStateException::class, Exception::class)
    suspend fun beginSession(
        alertMessage: String,
        tagInteractionFunc: suspend (
            tag: NfcIsoTag,
            updateMessage: (message: String) -> Unit,
        ) -> T?,
    ): T {
        check(NFCTagReaderSession.readingAvailable) {
            "NFC tag reading is not available on this device. Make sure NFC is enabled in Settings."
        }
        try {
            val ret =
                suspendCancellableCoroutine { continuation ->
                    this.continuation = continuation
                    this.tagInteractionFunc = tagInteractionFunc
                    session.setAlertMessage(alertMessage)
                    session.beginSession()
                }
            try {
                session.invalidateSession()
            } catch (_: Throwable) {
                // Ignore cleanup errors
            } finally {
                currentSession = null
            }
            return ret
        } catch (e: CancellationException) {
            try {
                session.invalidateSessionWithErrorMessage("Dialog was canceled")
            } catch (_: Throwable) {
                // Ignore cleanup errors
            } finally {
                currentSession = null
            }
            throw e
        } catch (expected: Throwable) {
            try {
                session.invalidateSessionWithErrorMessage(expected.message!!)
            } catch (_: Throwable) {
                // Ignore cleanup errors
            } finally {
                currentSession = null
            }
            throw expected
        }
    }
}

actual suspend fun <T : Any> scanNfcTag(
    message: String?,
    tagInteractionFunc: suspend (
        tag: NfcIsoTag,
        updateMessage: (message: String) -> Unit,
    ) -> T?,
): T {
    require(message != null) { "Cannot not show the NFC tag scanning dialog on iOS" }
    val reader = NfcTagReader<T>()
    return reader.beginSession(message, tagInteractionFunc)
}
