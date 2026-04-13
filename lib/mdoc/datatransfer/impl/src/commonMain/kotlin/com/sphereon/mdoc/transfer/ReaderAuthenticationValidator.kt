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

package com.sphereon.mdoc.transfer

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.crypto.core.CoseCryptoService
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.generic.VerifySignatureResult
import com.sphereon.crypto.core.generic.VerifySignatureResultType
import com.sphereon.mdoc.ReaderAuthenticationCborCodec
import com.sphereon.mdoc.SessionTranscriptCborCodec
import com.sphereon.mdoc.data.device.DeviceItemsRequest
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.transfer.reader.ReaderAuthentication
import com.sphereon.mdoc.transfer.reader.SessionTranscript

internal class ReaderAuthenticationValidator(
    private val readerAuthenticationCborCodec: ReaderAuthenticationCborCodec,
    private val sessionTranscriptCborCodec: SessionTranscriptCborCodec,
    private val coseCryptoService: CoseCryptoService,
) {
    suspend fun validate(
        docRequest: DocRequest,
        expectedSessionTranscript: CborEncodedItem<SessionTranscript>?,
        requireReaderAuthentication: Boolean,
    ): VerifySignatureResultType<CoseKeyType> {
        if (expectedSessionTranscript == null) {
            return failure("Session transcript not initialized. Call receiveDeviceRequest() first.")
        }

        val readerAuth = docRequest.readerAuth
        if (readerAuth == null) {
            return VerifySignatureResult(
                error = requireReaderAuthentication,
                critical = requireReaderAuthentication,
                message = "No reader authentication found in request",
                name = READER_AUTHENTICATION_NAME,
            )
        }

        val payload =
            readerAuth.payload?.value
                ?: return failure("Reader authentication payload is missing")

        val decodedReaderAuthentication =
            readerAuthenticationCborCodec
                .decode(payload)
                .getOrElse { error ->
                    return failure(
                        message = "Failed to decode reader authentication payload: ${error.message.defaultMessage}",
                        detailMessage = error.message.defaultMessage,
                    )
                }.value

        if (!matchesSessionTranscript(expectedSessionTranscript, decodedReaderAuthentication)) {
            return failure("Reader authentication session transcript does not match the active session")
        }

        if (!matchesItemsRequest(docRequest.itemsRequest, decodedReaderAuthentication)) {
            return failure("Reader authentication items request does not match the requested items")
        }

        val verifyResult =
            try {
                coseCryptoService.verify1(
                    input = readerAuth,
                    keyInfo = null,
                    requireX5Chain = true,
                )
            } catch (expected: Throwable) {
                return failure(
                    message = "Reader authentication signature verification failed: ${expected.message ?: expected::class.simpleName}",
                    detailMessage = expected.message,
                )
            }

        return VerifySignatureResult(
            error = verifyResult.error,
            critical = verifyResult.critical,
            message =
                verifyResult.message
                    ?: if (verifyResult.error) {
                        "Reader authentication signature verification failed"
                    } else {
                        "Reader authentication validated successfully"
                    },
            detailMessage = verifyResult.detailMessage,
            name = READER_AUTHENTICATION_NAME,
            keyInfo = verifyResult.keyInfo,
        )
    }

    private fun matchesSessionTranscript(
        expected: CborEncodedItem<SessionTranscript>,
        actual: ReaderAuthentication,
    ): Boolean {
        val expectedBytes = expected.value.taggedItem.value
        val actualBytes =
            actual.sessionTranscript.original
                ?: sessionTranscriptCborCodec.encode(actual.sessionTranscript).getOrElse { return false }
        return expectedBytes.contentEquals(actualBytes)
    }

    private fun matchesItemsRequest(
        expected: DeviceItemsRequest,
        actual: ReaderAuthentication,
    ): Boolean {
        val expectedOriginal = expected.original
        val actualEncoded = actual.itemsRequestBytes.value.taggedItem.value
        return if (expectedOriginal != null) {
            expectedOriginal.contentEquals(actualEncoded)
        } else {
            expected == actual.itemsRequestBytes.data()
        }
    }

    private fun failure(
        message: String,
        detailMessage: String? = null,
        error: Boolean = true,
        critical: Boolean = true,
    ): VerifySignatureResult<CoseKeyType> =
        VerifySignatureResult(
            error = error,
            critical = critical,
            message = message,
            detailMessage = detailMessage,
            name = READER_AUTHENTICATION_NAME,
        )

    private companion object {
        const val READER_AUTHENTICATION_NAME = "ReaderAuthentication"
    }
}
