package com.sphereon.mdoc.data.device

import com.sphereon.cbor.CborItem
import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.toCborItem
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyJson
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import com.sphereon.mdoc.transfer.reader.QrHandover
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import kotlin.test.Test
import kotlin.test.assertEquals

class SecondEditionPrimitiveTest {
    @Test
    fun typedRequestInfoAndReaderAuthenticationAllRoundTrip() {
        val requestInfo = DocRequestInfo(
            alternativeDataElements = listOf(
                AlternativeDataElementsSet(
                    requestedElement = NameSpace("org.example") to DataElementIdentifier("age"),
                    alternativeElementSets = listOf(
                        listOf(NameSpace("org.example") to DataElementIdentifier("birth_date")),
                    ),
                ),
            ),
            issuerIdentifiers = listOf(byteArrayOf(1, 2, 3)),
            uniqueDocSetRequired = true,
            maximumResponseSize = 4096u,
            zkRequest = ZkRequest(zkRequired = true, systemSpecs = listOf(ZkSystemSpec("example", "groth16", params = mapOf("curve" to "P-256".toCborItem())))),
            docResponseEncryption = EncryptionParameters(createTestCoseKey(), nonce = byteArrayOf(9, 8, 7)),
        )
        @Suppress("UNCHECKED_CAST")
        val transcript = SessionTranscript(handover = QrHandover() as com.sphereon.mdoc.transfer.reader.Handover<*, CborItem<*>>, original = null)
        val authContent = ReaderAuthenticationAll(transcript, listOf(byteArrayOf(4, 5)), null)
        val auth =
            com.sphereon.crypto.core.cose.CoseSign1<ReaderAuthenticationAllBytes>(
                protectedHeader = CoseHeaderCbor(),
                unprotectedHeader = null,
                payload = null,
                signature = CborByteString(byteArrayOf(6, 7)),
            )
        val request = DeviceRequest(
            docRequests = arrayOf(
                DocRequest(
                    DeviceItemsRequest(DocType("org.example.doc"), emptyMap(), docRequestInfo = requestInfo),
                ),
            ),
            original = null,
            deviceRequestInfo = DeviceRequestInfo(useCases = listOf(UseCase(true, listOf(listOf(0u)), mapOf("reader" to 7)))),
            readerAuthAll = arrayOf(auth),
        )

        val codec = DeviceRequestCborCodecImpl()
        val decoded = codec.decode(codec.encode(request).getOrThrow()).getOrThrow().value
        assertEquals(request.deviceRequestInfo, decoded.deviceRequestInfo)
        assertEquals(request.docRequests!![0].itemsRequest.docRequestInfo, decoded.docRequests!![0].itemsRequest.docRequestInfo)
        val decodedReaderAuthAllEntries = requireNotNull(decoded.readerAuthAll)
        assertEquals(1, decodedReaderAuthAllEntries.size)
        val decodedReaderAuthAll = decodedReaderAuthAllEntries.single()
        assertEquals(null, decodedReaderAuthAll.payload)
        assertEquals(auth.signature.value.toList(), decodedReaderAuthAll.signature.value.toList())
        assertEquals(authContent.itemsRequestBytesAll.single().toList(), byteArrayOf(4, 5).toList())
    }

    private fun createTestCoseKey(): CoseKey =
        CoseKeyJson.Builder()
            .withKty(CoseKeyTypeEnum.EC2)
            .withCrv(CoseCurve.P_256)
            .withX("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")
            .withY("BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB")
            .build()
            .toCbor()
}
