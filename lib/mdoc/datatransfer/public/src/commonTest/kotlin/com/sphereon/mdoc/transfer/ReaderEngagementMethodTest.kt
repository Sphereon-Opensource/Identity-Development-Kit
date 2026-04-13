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

package com.sphereon.mdoc.transfer

import com.sphereon.cbor.CborEncodedItem
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.mdoc.DecodedMdoc
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethod
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType
import com.sphereon.mdoc.transfer.device.RestApiOptions
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import com.sphereon.mdoc.transfer.reader.ReaderEngagementCborCodec
import com.sphereon.mdoc.transfer.reader.ReaderEngagementSecurity
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ReaderEngagementMethodTest {
    @Test
    fun readerEngagementMethodFromUriRoundTripsExactUri() {
        val original = createReaderEngagement()
        val uri = "mdoc://test-reader-engagement"
        val readerEngagementCborCodec = createReaderEngagementCborCodec(original, uri)

        val method = ReaderEngagementMethod(readerEngagementCborCodec.decodeUri(uri).getOrThrow().value, readerEngagementCborCodec)

        assertEquals(EngagementType.TO_APP, method.type)
        assertEquals(uri, method.getEngagementDataUri())
        assertContentEquals(readerEngagementCborCodec.decodeUri(uri).getOrThrow().originalBytes, method.readerEngagement.original)
    }

    @Test
    fun readerEngagementMethodFromReaderEngagementGeneratesUri() {
        val original = createReaderEngagement()
        val uri = "mdoc://test-reader-engagement"
        val readerEngagementCborCodec = createReaderEngagementCborCodec(original, uri)
        val method = ReaderEngagementMethod(original, readerEngagementCborCodec)
        val decoded = readerEngagementCborCodec.decodeUri(uri).getOrThrow().value

        assertEquals(original.version, decoded.version)
        assertEquals(original.security, decoded.security)
        assertEquals(
            ((original.deviceRetrievalMethods?.single()?.retrievalOptions) as RestApiOptions).uri,
            ((decoded.deviceRetrievalMethods?.single()?.retrievalOptions) as RestApiOptions).uri,
        )
        assertContentEquals(decoded.original, method.readerEngagement.original ?: decoded.original)
        assertEquals(uri, method.getEngagementDataUri())
    }

    @Test
    fun readerEngagementMethodRejectsOid4vpQueryParameterUri() {
        val readerEngagementCborCodec = createFailingReaderEngagementCborCodec()
        val result = readerEngagementCborCodec.decodeUri("mdoc-openid4vp://?client_id=test")
        assertEquals(true, result.isErr)
    }

    @Test
    fun readerEngagementMethodRejectsMalformedPayloadUri() {
        val readerEngagementCborCodec = createFailingReaderEngagementCborCodec()
        val result = readerEngagementCborCodec.decodeUri("mdoc://%%%")
        assertEquals(true, result.isErr)
    }

    private fun createReaderEngagement(): ReaderEngagement =
        ReaderEngagement.V1_0(
            security =
                ReaderEngagementSecurity(
                    cipherSuite = 1u,
                    eReaderKeyBytes = createTestCoseKey().let { CborEncodedItem<CoseKeyType>(byteArrayOf(0x11, 0x22, 0x33), it) },
                ),
            deviceRetrievalMethods =
                arrayOf(
                    DeviceRetrievalMethod(
                        type = DeviceRetrievalMethodType.WEBSITE,
                        retrievalOptions = RestApiOptions("https://reader.example.com/mdoc/session/123"),
                    ),
                ),
            protocolInfo = null,
            additionalItems = null,
            original = byteArrayOf(0x01, 0x02, 0x03),
        )

    private fun createReaderEngagementCborCodec(
        readerEngagement: ReaderEngagement,
        uri: String,
    ): ReaderEngagementCborCodec =
        object : ReaderEngagementCborCodec {
            override fun encode(value: ReaderEngagement): IdkResult<ByteArray, IdkError> = Ok(readerEngagement.original ?: byteArrayOf())

            override fun encodeTag24(value: ReaderEngagement): IdkResult<ByteArray, IdkError> = Ok(readerEngagement.original ?: byteArrayOf())

            override fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<ReaderEngagement>, IdkError> = Ok(DecodedMdoc(readerEngagement, readerEngagement.original ?: byteArrayOf()))

            override fun encodeUri(
                value: ReaderEngagement,
                scheme: String,
            ): IdkResult<String, IdkError> = Ok(uri)

            override fun decodeUri(uri: String): IdkResult<DecodedMdoc<ReaderEngagement>, IdkError> = Ok(DecodedMdoc(readerEngagement, readerEngagement.original ?: byteArrayOf()))
        }

    private fun createFailingReaderEngagementCborCodec(): ReaderEngagementCborCodec =
        object : ReaderEngagementCborCodec {
            override fun encode(value: ReaderEngagement): IdkResult<ByteArray, IdkError> = throw UnsupportedOperationException()

            override fun encodeTag24(value: ReaderEngagement): IdkResult<ByteArray, IdkError> = throw UnsupportedOperationException()

            override fun decode(bytes: ByteArray): IdkResult<DecodedMdoc<ReaderEngagement>, IdkError> = throw UnsupportedOperationException()

            override fun encodeUri(
                value: ReaderEngagement,
                scheme: String,
            ): IdkResult<String, IdkError> = throw UnsupportedOperationException()

            override fun decodeUri(uri: String): IdkResult<DecodedMdoc<ReaderEngagement>, IdkError> = Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Invalid URI", arg = uri))
        }

    private fun createTestCoseKey(): CoseKey =
        CoseKeyJson
            .Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
            .build()
            .toCbor()
}
