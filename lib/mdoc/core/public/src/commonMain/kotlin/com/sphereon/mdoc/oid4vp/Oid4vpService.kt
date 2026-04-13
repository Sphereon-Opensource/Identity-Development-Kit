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

package com.sphereon.mdoc.oid4vp

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.mdoc.data.DeviceResponseDocumentErrorCborAlias
import com.sphereon.mdoc.data.device.DeviceNameSpaces
import com.sphereon.mdoc.data.device.DeviceResponse
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import com.sphereon.core.compat.JsExportCompat


@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpSignResult", exact = true)
data class Oid4vpSignResult(
    val sessionTranscript: SessionTranscript,
    val document: Document? = null,
    val documentError: DeviceResponseDocumentErrorCborAlias? = null,
    val presentationDefinition: IOid4VPPresentationDefinition,
    val deviceKeyInfo: KeyInfoType<CoseKeyType>?
)


@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DocumentDescriptorMatchResult", exact = true)
data class DocumentDescriptorMatchResult(
    val inputDescriptor: IOid4VPInputDescriptor,
    val document: Document?, // Either a document or documentError should be returned, not both
    val documentError: DeviceResponseDocumentErrorCborAlias?, // Either a document or documentError should be returned, not both
    val deviceKeyInfo: KeyInfoType<CoseKeyType>?, // Derived from the document. Cannot be derived if no document is passed in
    var deviceNamespaces: DeviceNameSpaces? = null,
    val mdocNonce: String // No default/uuid as the nonce's needs to match in case multiple mdocs are returned
)

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocOid4vpService", exact = true)
interface MdocOid4vpService {
    suspend fun createDeviceResponse(
        matchingDocuments: Array<DocumentDescriptorMatchResult>,
        presentationDefinition: IOid4VPPresentationDefinition,
        clientId: String,
        responseUri: String,
        authorizationRequestNonce: String
    ): DeviceResponse

    suspend fun signDocument(
        clientId: String,
        responseUri: String,
        mdocNonce: String,
        authorizationRequestNonce: String,
        deviceNamespaces: DeviceNameSpaces,
        document: Document?,
        inputDescriptor: IOid4VPInputDescriptor?,
        docType: DocType,
        deviceKeyInfo: KeyInfoType<*>?,
        presentationDefinition: IOid4VPPresentationDefinition
    ): Oid4vpSignResult

    fun filterApplicableDocumentsPerInputDescriptor(
        allDocument: Array<Document>,
        inputDescriptor: IOid4VPInputDescriptor
    ): Array<Document>

    fun matchDocumentsAndDescriptors(
        mdocNonce: String,
        applicableDocuments: Array<Document>,
        presentationDefinition: IOid4VPPresentationDefinition
    ): Array<DocumentDescriptorMatchResult>
}