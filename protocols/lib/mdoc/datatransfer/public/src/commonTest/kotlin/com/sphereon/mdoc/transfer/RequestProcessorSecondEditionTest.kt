package com.sphereon.mdoc.transfer

import com.sphereon.cbor.CborByteString
import com.sphereon.cbor.CborEncodedItem
import com.sphereon.core.api.Ok
import com.sphereon.core.api.asOkResult
import com.sphereon.core.defaults.log.AppNoLogService
import com.sphereon.crypto.core.cose.CoseHeaderCbor
import com.sphereon.crypto.core.cose.CoseSign1
import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.DeviceItemsRequest
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.data.device.DocRequest
import com.sphereon.mdoc.data.device.DocRequestInfo
import com.sphereon.mdoc.data.device.DeviceRequestInfo
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.data.device.DocumentWithKeyAlias
import com.sphereon.mdoc.data.device.IssuerSigned
import com.sphereon.mdoc.data.device.IssuerSignedItem
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.mdoc.data.device.ZkProofProvider
import com.sphereon.mdoc.data.device.ZkRequest
import com.sphereon.mdoc.data.device.ZkSystemSpec
import com.sphereon.mdoc.data.device.UseCase
import com.sphereon.mdoc.data.mso.DigestID
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RequestProcessorSecondEditionTest {
    @Test
    fun simpleSelectorHonorsDeclaredUseCaseDocumentSetOrder() =
        runTest {
            val firstRequest = request(requested = listOf("given_name"), docType = DOC_TYPE)
            val secondDocType = com.sphereon.mdoc.data.device.DocType("org.example.second")
            val secondRequest = request(requested = listOf("family_name"), docType = secondDocType)
            val provider = FixedDocumentProvider(
                setOf(
                    wrapped(sampleDocument(DOC_TYPE)),
                    wrapped(sampleDocument(secondDocType)),
                ),
            )
            val selector = SimpleRequestDocumentsSelector(
                log = AppNoLogService(),
                docRequestSingleDocSelect = SimpleDocumentRequestSingleDocumentSelector(AppNoLogService()),
                globalDocumentsProvider = provider,
            )
            val deviceRequest = DeviceRequest(
                docRequests = arrayOf(firstRequest, secondRequest),
                original = null,
                deviceRequestInfo = DeviceRequestInfo(
                    useCases = listOf(
                        UseCase(
                            mandatory = true,
                            documentSets = listOf(listOf(1U), listOf(0U)),
                        ),
                    ),
                ),
            )

            val result = selector.selectDocuments(deviceRequest)

            assertTrue(result.isOk)
            assertEquals(setOf(secondRequest), result.value.keys)
        }

    @Test
    fun simpleSelectorRejectsUseCaseDocumentIdOutsideRequestList() =
        runTest {
            val request = request(requested = listOf("given_name"))
            val selector = SimpleRequestDocumentsSelector(
                log = AppNoLogService(),
                docRequestSingleDocSelect = SimpleDocumentRequestSingleDocumentSelector(AppNoLogService()),
                globalDocumentsProvider = FixedDocumentProvider(setOf(wrapped(sampleDocument()))),
            )
            val deviceRequest = DeviceRequest(
                docRequests = arrayOf(request),
                original = null,
                deviceRequestInfo = DeviceRequestInfo(
                    useCases = listOf(UseCase(mandatory = true, documentSets = listOf(listOf(1U)))),
                ),
            )

            val result = selector.selectDocuments(deviceRequest)

            assertTrue(result.isErr)
        }

    @Test
    fun simpleSelectorSkipsAnUnsatisfiedOptionalUseCase() =
        runTest {
            val availableRequest = request(requested = listOf("given_name"))
            val unavailableRequest = request(
                requested = listOf("family_name"),
                docType = com.sphereon.mdoc.data.device.DocType("org.example.unavailable"),
            )
            val selector = SimpleRequestDocumentsSelector(
                log = AppNoLogService(),
                docRequestSingleDocSelect = SimpleDocumentRequestSingleDocumentSelector(AppNoLogService()),
                globalDocumentsProvider = FixedDocumentProvider(setOf(wrapped(sampleDocument()))),
            )
            val deviceRequest = DeviceRequest(
                docRequests = arrayOf(availableRequest, unavailableRequest),
                original = null,
                deviceRequestInfo = DeviceRequestInfo(
                    useCases = listOf(UseCase(mandatory = false, documentSets = listOf(listOf(1U)))),
                ),
            )

            val result = selector.selectDocuments(deviceRequest)

            assertTrue(result.isOk)
            assertTrue(result.value.isEmpty())
        }

    @Test
    fun simpleSelectorEnforcesTheRequestedMinimumForLegacyRequests() =
        runTest {
            val firstRequest = request(requested = listOf("given_name"), docType = DOC_TYPE)
            val secondRequest = request(
                requested = listOf("family_name"),
                docType = com.sphereon.mdoc.data.device.DocType("org.example.missing"),
            )
            val selector = SimpleRequestDocumentsSelector(
                log = AppNoLogService(),
                docRequestSingleDocSelect = SimpleDocumentRequestSingleDocumentSelector(AppNoLogService()),
                globalDocumentsProvider = FixedDocumentProvider(setOf(wrapped(sampleDocument()))),
            )

            val result = selector.selectDocuments(
                deviceRequest = DeviceRequest(docRequests = arrayOf(firstRequest, secondRequest), original = null),
                minDocRequests = 2,
            )

            assertTrue(result.isErr)
        }

    @Test
    fun compatibleZkpProviderProducesZkDocumentInsteadOfClearDocument() =
        runTest {
            val request = request(
                requested = listOf("given_name"),
                info = DocRequestInfo(
                    zkRequest = ZkRequest(
                        systemSpecs = listOf(ZkSystemSpec("example.zk", "test")),
                        zkRequired = true,
                    ),
                ),
            )
            val provider = object : ZkProofProvider {
                override val supportedSystemIds = setOf("example.zk")

                override suspend fun createProof(
                    request: ZkRequest,
                    documentData: com.sphereon.mdoc.data.device.ZkDocumentData,
                    sessionTranscript: com.sphereon.mdoc.transfer.reader.SessionTranscript?,
                ) = Ok("proof".encodeToByteArray())

                override suspend fun verifyProof(
                    request: ZkRequest,
                    document: com.sphereon.mdoc.data.device.ZkDocument,
                    sessionTranscript: com.sphereon.mdoc.transfer.reader.SessionTranscript?,
                ) = Ok(true)
            }
            val result = processor(request, provider).createDeviceResponse(DeviceRequest(docRequests = arrayOf(request), original = null))

            assertTrue(result.isOk)
            assertTrue(result.value.documents.isNullOrEmpty())
            val zkDocument = assertNotNull(result.value.zkDocuments).single()
            assertEquals("example.zk", zkDocument.documentData.zkSystemId)
            assertEquals(listOf("given_name"), zkDocument.documentData.issuerSigned!!.values.single().map { it.elementIdentifier.toString() })
            assertEquals("proof", zkDocument.proof.decodeToString())
        }

    @Test
    fun optionalZkpFallsBackToTheSelectedDocumentWhenNoProviderIsAvailable() =
        runTest {
            val request = request(
                requested = listOf("given_name"),
                info = DocRequestInfo(
                    zkRequest = ZkRequest(
                        systemSpecs = listOf(ZkSystemSpec("missing.zk", "unconfigured")),
                        zkRequired = false,
                    ),
                ),
            )

            val result = processor(request).createDeviceResponse(DeviceRequest(docRequests = arrayOf(request), original = null))

            assertTrue(result.isOk)
            assertEquals(1, assertNotNull(result.value.documents).size)
            assertTrue(result.value.zkDocuments.isNullOrEmpty())
        }

    @Test
    fun unsignedProcessorStillAppliesIssuerDisclosureSelection() =
        runTest {
            val request = request(requested = listOf("given_name"))
            val result = processor(request).createDeviceResponse(DeviceRequest(docRequests = arrayOf(request), original = null))

            assertTrue(result.isOk)
            val document = assertNotNull(result.value.documents).single()
            assertEquals(
                listOf("given_name"),
                document.issuerSigned.nameSpaces!!.values.single().map { it.data().elementIdentifier.toString() },
            )
        }

    private fun processor(
        request: DocRequest,
        provider: ZkProofProvider? = null,
    ): SimpleRequestResponseProcessor =
        SimpleRequestResponseProcessor(
            documentsSelector = FixedSelector(request, sampleDocument()),
            globalDocumentsProvider = null,
            sessionTranscript = null,
            mdocDeviceSignService = null,
            deviceResponseCborCodec = null,
            zkProofProviders = provider?.let(::listOf).orEmpty(),
        )

    private fun request(
        requested: List<String>,
        info: DocRequestInfo? = null,
        docType: com.sphereon.mdoc.data.device.DocType = DOC_TYPE,
    ): DocRequest =
        DocRequest(
            DeviceItemsRequest(
                docType = docType,
                nameSpaces = mapOf(
                    NS to requested.associate { DataElementIdentifier(it) to com.sphereon.mdoc.data.device.IntentToRetain(false) },
                ),
                docRequestInfo = info,
            ),
        )

    private class FixedSelector(
        private val request: DocRequest,
        private val document: Document,
    ) : RequestDocumentsSelector {
        override suspend fun selectDocuments(
            deviceRequest: DeviceRequest,
            minDocRequests: Int,
            documentProvider: DocumentProvider?,
        ) = mapOf(
            request to object : DocumentWithKeyAlias {
                override val providerId = ""
                override val keyAlias = ""
                override val document = this@FixedSelector.document
            },
        ).asOkResult()
    }

    private fun sampleDocument(docType: com.sphereon.mdoc.data.device.DocType = DOC_TYPE): Document {
        val items = listOf("given_name", "family_name").mapIndexed { index, identifier ->
            val item: IssuerSignedItem<Any> = IssuerSignedItem(
                digestID = DigestID(index.toUInt()),
                elementIdentifier = DataElementIdentifier(identifier),
                elementValue = identifier.replace('_', ' ').replaceFirstChar { it.uppercase() },
            )
            CborEncodedItem(byteArrayOf(index.toByte()), item)
        }.toTypedArray()
        val issuerAuth = CoseSign1<com.sphereon.mdoc.data.mso.MobileSecurityObject>(
            protectedHeader = CoseHeaderCbor(),
            unprotectedHeader = null,
            payload = null,
            signature = CborByteString(byteArrayOf(1)),
        )
        return Document(
            docType = docType,
            issuerSigned = IssuerSigned(mapOf(NS to items), issuerAuth, null),
            deviceSigned = null,
            original = null,
        )
    }

    private fun wrapped(source: Document): DocumentWithKeyAlias = object : DocumentWithKeyAlias {
        override val providerId = ""
        override val keyAlias = ""
        override val document = source
    }

    private class FixedDocumentProvider(
        private val documents: Set<DocumentWithKeyAlias>,
    ) : DocumentProvider {
        override suspend fun getDocuments(selectorData: Any?): Set<DocumentWithKeyAlias> = documents
    }

    private companion object {
        val DOC_TYPE = com.sphereon.mdoc.data.device.DocType("org.example.test")
        val NS = NameSpace("org.example")
    }
}
