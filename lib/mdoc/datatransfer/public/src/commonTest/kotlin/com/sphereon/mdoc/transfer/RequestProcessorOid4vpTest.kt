package com.sphereon.mdoc.transfer

import com.sphereon.core.api.Ok
import com.sphereon.core.defaults.log.AppNoLogService
import com.sphereon.mdoc.data.device.DataElementIdentifier
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.DocumentWithKeyAlias
import com.sphereon.mdoc.data.device.NameSpace
import com.sphereon.mdoc.oid4vp.Oid4VPConstraintField
import com.sphereon.mdoc.oid4vp.Oid4VPConstraints
import com.sphereon.mdoc.oid4vp.Oid4VPFormat
import com.sphereon.mdoc.oid4vp.Oid4VPInputDescriptor
import com.sphereon.mdoc.oid4vp.Oid4VPPresentationDefinition
import com.sphereon.mdoc.oid4vp.Oid4VPSupportedAlgorithm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RequestProcessorOid4vpTest {

    @Test
    fun derivesDocRequestsFromOid4vpRequestWhenMissing() = runTest {
        val presentationDefinition = Oid4VPPresentationDefinition(
            id = "pd-1",
            input_descriptors = arrayOf(
                Oid4VPInputDescriptor(
                    id = DocType("org.iso.18013.5.1.mDL"),
                    format = Oid4VPFormat(
                        mso_mdoc = Oid4VPSupportedAlgorithm(
                            alg = arrayOf("ES256")
                        )
                    ),
                    constraints = Oid4VPConstraints(
                        fields = arrayOf(
                            Oid4VPConstraintField(
                                path = arrayOf("$['org.iso.18013.5.1']['given_name']"),
                                intent_to_retain = false
                            )
                        )
                    )
                )
            )
        )

        val deviceRequest = DeviceRequest(
            docRequests = null,
            oid4vpRequest = presentationDefinition,
            macKeys = null,
            original = null
        )

        val selector = CapturingSelector()
        val documentsSelector = SimpleRequestDocumentsSelector(
            log = AppNoLogService(),
            docRequestSingleDocSelect = selector
        )

        val result = documentsSelector.selectDocuments(
            deviceRequest = deviceRequest,
            minDocRequests = 0,
            documentProvider = EmptyDocumentProvider()
        )

        assertTrue(result.isOk)
        val captured = selector.captured
        assertNotNull(captured)
        assertEquals(DocType("org.iso.18013.5.1.mDL"), captured.itemsRequest.docType)
        val identifiers = captured.itemsRequest.getIdentifiers(NameSpace("org.iso.18013.5.1"))
        assertTrue(identifiers.containsKey(DataElementIdentifier("given_name")))
    }

    private class CapturingSelector : DocumentRequestSingleDocumentSelector {
        var captured: com.sphereon.mdoc.data.device.DocRequest? = null

        override suspend fun select(
            docRequest: com.sphereon.mdoc.data.device.DocRequest,
            documentProvider: DocumentProvider?,
            selectorData: Any?
        ) = Ok<Pair<com.sphereon.mdoc.data.device.DocRequest, DocumentWithKeyAlias>?>(null).also {
            captured = docRequest
        }
    }

    private class EmptyDocumentProvider : DocumentProvider {
        override suspend fun getDocuments(selectorData: Any?): Set<DocumentWithKeyAlias> = emptySet()
    }
}
