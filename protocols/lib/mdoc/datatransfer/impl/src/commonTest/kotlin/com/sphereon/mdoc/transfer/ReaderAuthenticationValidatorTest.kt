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

import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.CoseCryptoService
import com.sphereon.crypto.core.CoseMac0Result
import com.sphereon.crypto.core.CoseSign1Result
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseMac0InputCbor
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.crypto.core.cose.CoseSign1Input
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.generic.VerifySignatureResult
import com.sphereon.crypto.core.generic.VerifySignatureResultType
import com.sphereon.mdoc.DecodedMdoc
import com.sphereon.mdoc.ReaderAuthenticationCborCodec
import com.sphereon.mdoc.SessionTranscriptCborCodec
import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.DeviceItemsRequest
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.IntentToRetain
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.mdoc.transfer.reader.Handover
import com.sphereon.mdoc.transfer.reader.QrHandover
import com.sphereon.mdoc.transfer.reader.ReaderAuthentication
import com.sphereon.mdoc.transfer.reader.RestApiHandover
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReaderAuthenticationValidatorTest {
    @Test
    fun validate_returns_success_for_matching_payload_and_verified_signature() =
        runTest {
            val verifyResult =
                VerifySignatureResult<CoseKeyType>(
                    error = false,
                    critical = false,
                    message = "signature ok",
                    name = "COSE",
                )
            val coseCryptoService = RecordingCoseCryptoService(verifyResult)
            val sessionTranscript = sampleSessionTranscript()
            val expectedSessionTranscript = expectedSessionTranscript(sessionTranscript)
            val readerAuthenticationCodec = FakeReaderAuthenticationCborCodec()
            val sessionTranscriptCodec = FakeSessionTranscriptCborCodec()
            val validator =
                ReaderAuthenticationValidator(
                    readerAuthenticationCborCodec = readerAuthenticationCodec,
                    sessionTranscriptCborCodec = sessionTranscriptCodec,
                    coseCryptoService = coseCryptoService,
                )
            val docRequest =
                sampleDocRequest(
                    sessionTranscript = sessionTranscript,
                    readerAuthenticationCodec = readerAuthenticationCodec,
                )

            val result =
                validator.validate(
                    docRequest = docRequest,
                    expectedSessionTranscript = expectedSessionTranscript,
                    requireReaderAuthentication = true,
                )

            assertFalse(result.error)
            assertFalse(result.critical)
            assertEquals("ReaderAuthentication", result.name)
            assertEquals("signature ok", result.message)
            assertEquals(docRequest.readerAuth, coseCryptoService.lastVerifiedInput)
            assertEquals(true, coseCryptoService.lastRequireX5Chain)
        }

    @Test
    fun validate_fails_when_reader_authentication_is_required_but_missing() =
        runTest {
            val sessionTranscript = sampleSessionTranscript()
            val validator =
                ReaderAuthenticationValidator(
                    readerAuthenticationCborCodec = FakeReaderAuthenticationCborCodec(),
                    sessionTranscriptCborCodec = FakeSessionTranscriptCborCodec(),
                    coseCryptoService =
                        RecordingCoseCryptoService(
                            VerifySignatureResult<CoseKeyType>(error = false, critical = false, name = "COSE"),
                        ),
                )
            val expectedSessionTranscript = expectedSessionTranscript(sessionTranscript)
            val docRequest = DocRequest(itemsRequest = sampleItemsRequest())

            val result =
                validator.validate(
                    docRequest = docRequest,
                    expectedSessionTranscript = expectedSessionTranscript,
                    requireReaderAuthentication = true,
                )

            assertTrue(result.error)
            assertTrue(result.critical)
            assertEquals("No reader authentication found in request", result.message)
        }

    @Test
    fun validate_fails_before_crypto_when_session_transcript_mismatches() =
        runTest {
            val coseCryptoService =
                RecordingCoseCryptoService(
                    VerifySignatureResult<CoseKeyType>(error = false, critical = false, name = "COSE"),
                )
            val expectedSessionTranscript = expectedSessionTranscript(sampleSessionTranscript())
            val readerAuthenticationCodec = FakeReaderAuthenticationCborCodec()
            val validator =
                ReaderAuthenticationValidator(
                    readerAuthenticationCborCodec = readerAuthenticationCodec,
                    sessionTranscriptCborCodec = FakeSessionTranscriptCborCodec(),
                    coseCryptoService = coseCryptoService,
                )
            val docRequest =
                sampleDocRequest(
                    sessionTranscript =
                        sampleSessionTranscript(
                            handover = differentHandover(),
                            original = byteArrayOf(0x55, 0x66, 0x77, 0x00),
                        ),
                    readerAuthenticationCodec = readerAuthenticationCodec,
                )

            val result =
                validator.validate(
                    docRequest = docRequest,
                    expectedSessionTranscript = expectedSessionTranscript,
                    requireReaderAuthentication = true,
                )

            assertTrue(result.error)
            assertTrue(result.critical)
            assertEquals("Reader authentication session transcript does not match the active session", result.message)
            assertNull(coseCryptoService.lastVerifiedInput)
        }

    @Test
    fun validate_returns_crypto_failure_after_payload_checks_pass() =
        runTest {
            val coseCryptoService =
                RecordingCoseCryptoService(
                    VerifySignatureResult<CoseKeyType>(
                        error = true,
                        critical = true,
                        message = "signature invalid",
                        name = "COSE",
                    ),
                )
            val sessionTranscript = sampleSessionTranscript()
            val expectedSessionTranscript = expectedSessionTranscript(sessionTranscript)
            val readerAuthenticationCodec = FakeReaderAuthenticationCborCodec()
            val validator =
                ReaderAuthenticationValidator(
                    readerAuthenticationCborCodec = readerAuthenticationCodec,
                    sessionTranscriptCborCodec = FakeSessionTranscriptCborCodec(),
                    coseCryptoService = coseCryptoService,
                )
            val docRequest =
                sampleDocRequest(
                    sessionTranscript = sessionTranscript,
                    readerAuthenticationCodec = readerAuthenticationCodec,
                )

            val result =
                validator.validate(
                    docRequest = docRequest,
                    expectedSessionTranscript = expectedSessionTranscript,
                    requireReaderAuthentication = true,
                )

            assertTrue(result.error)
            assertTrue(result.critical)
            assertEquals("signature invalid", result.message)
            assertEquals("ReaderAuthentication", result.name)
        }

    @Test
    fun validate_fails_when_items_request_original_bytes_do_not_match_reader_auth_bytes() =
        runTest {
            val sessionTranscript = sampleSessionTranscript()
            val expectedSessionTranscript = expectedSessionTranscript(sessionTranscript)
            val readerAuthenticationCodec = FakeReaderAuthenticationCborCodec()
            val coseCryptoService =
                RecordingCoseCryptoService(
                    VerifySignatureResult<CoseKeyType>(error = false, critical = false, name = "COSE"),
                )
            val validator =
                ReaderAuthenticationValidator(
                    readerAuthenticationCborCodec = readerAuthenticationCodec,
                    sessionTranscriptCborCodec = FakeSessionTranscriptCborCodec(),
                    coseCryptoService = coseCryptoService,
                )

            val docItemsRequest = sampleItemsRequest().copy(original = byteArrayOf(0x10, 0x20, 0x30))
            val readerAuthItemsRequest = docItemsRequest.copy(original = byteArrayOf(0x30, 0x20, 0x10))
            val payload = byteArrayOf(nextPayloadId++.toByte(), 0x02, 0x03)
            val readerAuthentication =
                ReaderAuthentication(
                    sessionTranscript = sessionTranscript,
                    itemsRequestBytes =
                        CborEncodedItem(
                            readerAuthItemsRequest.original ?: error("reader auth items request bytes missing"),
                            readerAuthItemsRequest,
                        ),
                )
            readerAuthenticationCodec.register(payload, readerAuthentication)
            val docRequest =
                DocRequest(
                    itemsRequest = docItemsRequest,
                    readerAuth =
                        CoseSign1(
                            protectedHeader = CoseHeaderCbor(),
                            unprotectedHeader = CoseHeaderCbor(),
                            payload = CborByteString(payload),
                            signature = CborByteString(byteArrayOf(0x01)),
                        ),
                )

            val result =
                validator.validate(
                    docRequest = docRequest,
                    expectedSessionTranscript = expectedSessionTranscript,
                    requireReaderAuthentication = true,
                )

            assertTrue(result.error)
            assertTrue(result.critical)
            assertEquals("Reader authentication items request does not match the requested items", result.message)
            assertNull(coseCryptoService.lastVerifiedInput)
        }

    private fun sampleDocRequest(
        sessionTranscript: SessionTranscript,
        readerAuthenticationCodec: FakeReaderAuthenticationCborCodec,
    ): DocRequest {
        val itemsRequest = sampleItemsRequest()
        val payload = byteArrayOf(nextPayloadId++.toByte(), 0x01, 0x02)
        val readerAuthentication =
            ReaderAuthentication(
                sessionTranscript = sessionTranscript,
                itemsRequestBytes = CborEncodedItem(payload, itemsRequest),
            )
        readerAuthenticationCodec.register(payload, readerAuthentication)
        return DocRequest(
            itemsRequest = itemsRequest,
            readerAuth =
                CoseSign1(
                    protectedHeader = CoseHeaderCbor(),
                    unprotectedHeader = CoseHeaderCbor(),
                    payload = CborByteString(payload),
                    signature = CborByteString(byteArrayOf(0x01)),
                ),
        )
    }

    private fun sampleItemsRequest(): DeviceItemsRequest =
        DeviceItemsRequest(
            docType = DocType("org.iso.18013.5.1.mDL"),
            nameSpaces =
                mapOf(
                    NameSpace("org.iso.18013.5.1") to
                        mapOf(
                            DataElementIdentifier("family_name") to IntentToRetain(true),
                        ),
                ),
        )

    @Suppress("UNCHECKED_CAST")
    private fun sampleSessionTranscript(
        handover: Handover<*, com.sphereon.cbor.CborItem<*>> = QrHandover() as Handover<*, com.sphereon.cbor.CborItem<*>>,
        original: ByteArray = byteArrayOf(0x11, 0x22, 0x33, 0x44),
    ): SessionTranscript =
        SessionTranscript(
            deviceEngagement = null,
            eReaderKey = null,
            handover = handover,
            original = original,
        )

    @Suppress("UNCHECKED_CAST")
    private fun differentHandover(): Handover<*, com.sphereon.cbor.CborItem<*>> = RestApiHandover(byteArrayOf(0x01, 0x02, 0x03)) as Handover<*, com.sphereon.cbor.CborItem<*>>

    private fun expectedSessionTranscript(sessionTranscript: SessionTranscript): CborEncodedItem<SessionTranscript> =
        CborEncodedItem(sessionTranscript.original ?: error("Expected transcript original bytes"), sessionTranscript)

    private class FakeReaderAuthenticationCborCodec : ReaderAuthenticationCborCodec {
        private val decodedByPayload = mutableMapOf<String, ReaderAuthentication>()

        fun register(
            payload: ByteArray,
            readerAuthentication: ReaderAuthentication,
        ) {
            decodedByPayload[payload.joinToString(separator = ",")] = readerAuthentication
        }

        override fun encode(value: ReaderAuthentication) = Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Encoding is not used in this test")).asResult<ByteArray>()

        override fun encodeItem(value: ReaderAuthentication) = Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Encoding is not used in this test")).asResult<CborEncodedItem<ReaderAuthentication>>()

        override fun encodeTag24(value: ReaderAuthentication) = Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Encoding is not used in this test")).asResult<ByteArray>()

        override fun decode(bytes: ByteArray) =
            decodedByPayload[bytes.joinToString(separator = ",")]
                ?.let { Ok(DecodedMdoc(it, bytes)).asResult<IdkError>() }
                ?: Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unknown payload")).asResult<DecodedMdoc<ReaderAuthentication>>()
    }

    private class FakeSessionTranscriptCborCodec : SessionTranscriptCborCodec {
        override fun encode(value: SessionTranscript) =
            value.original
                ?.let { Ok(it).asResult<IdkError>() }
                ?: Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing original bytes")).asResult<ByteArray>()

        override fun encodeItem(value: SessionTranscript) =
            value.original
                ?.let { Ok(CborEncodedItem(it, value)).asResult<IdkError>() }
                ?: Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing original bytes")).asResult<CborEncodedItem<SessionTranscript>>()

        override fun encodeTag24(value: SessionTranscript) = Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Tag 24 encoding is not used in this test")).asResult<ByteArray>()

        override fun decode(bytes: ByteArray) = Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Decoding is not used in this test")).asResult<DecodedMdoc<SessionTranscript>>()
    }

    private class RecordingCoseCryptoService(
        private val verifyResult: VerifySignatureResultType<CoseKeyType>,
    ) : CoseCryptoService {
        var lastVerifiedInput: CoseSign1<*>? = null
        var lastRequireX5Chain: Boolean? = null

        override suspend fun <CborType : Any> sign1(
            input: CoseSign1Input,
            keyInfo: KeyInfoType<*>?,
            requireX5Chain: Boolean?,
        ): CoseSign1Result<CborType> = throw NotImplementedError()

        override suspend fun verify1(
            input: CoseSign1<*>,
            keyInfo: KeyInfoType<*>?,
            requireX5Chain: Boolean?,
        ): VerifySignatureResultType<CoseKeyType> {
            lastVerifiedInput = input
            lastRequireX5Chain = requireX5Chain
            return verifyResult
        }

        override suspend fun mac0(
            input: CoseMac0InputCbor,
            sharedSecret: ByteArray,
            alg: SignatureAlgorithm,
        ): CoseMac0Result = throw NotImplementedError()

        override suspend fun <KeyType : com.sphereon.crypto.core.KeyType> resolvePublicKey(keyInfo: KeyInfoType<KeyType>): ResolvedKeyInfoType<KeyType> = throw NotImplementedError()
    }

    private companion object {
        private var nextPayloadId: Int = 0x10
    }
}
