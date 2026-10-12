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

package com.sphereon.mdoc.oid4vp

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.mdoc.PreparedMdocDeviceAuthenticationSigning
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.mdoc.data.DeviceResponseDocumentErrorAlias
import com.sphereon.mdoc.data.device.DeviceNameSpaces
import com.sphereon.mdoc.data.device.DeviceResponse
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlinx.serialization.Serializable

/** Prepared mdoc DeviceAuth signing operation associated with its OID4VP input descriptor. */
@Serializable
@JsExportCompat
data class PreparedMdocDeviceResponseSigning(
    val descriptorId: String,
    val signing: PreparedMdocDeviceAuthenticationSigning,
)

/** External raw COSE signature associated with a prepared OID4VP input descriptor. */
@Serializable
@JsExportCompat
data class MdocDeviceResponseSignature(
    val descriptorId: String,
    val signature: ByteArray,
)

/** Exact validated OID4VP request inputs used to build the holder's mdoc transcript. */
@Serializable
@JsExportCompat
data class Oid4vpDeviceAuthenticationContext(
    val responseMode: String,
    val clientId: String,
    val authorizationRequestNonce: String,
    val responseUri: String? = null,
    val redirectUri: String? = null,
    /** Raw browser origin from validated Digital Credentials API transport context. */
    val callerOrigin: String? = null,
    /** Raw 32-byte RFC 7638 thumbprint of the exact selected response-encryption JWK. */
    val verifierEncryptionJwkThumbprint: ByteArray? = null,
    /** Non-null only when explicitly using ISO/IEC TS 18013-7 Annex B. */
    val iso18013MdocGeneratedNonce: String? = null,
)

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpSignResult", exact = true)
data class Oid4vpSignResult(
    val sessionTranscript: SessionTranscript,
    val document: Document? = null,
    val documentError: DeviceResponseDocumentErrorAlias? = null,
    val presentationDefinition: IOid4VPPresentationDefinition,
    val deviceKeyInfo: KeyInfoType<CoseKeyType>?,
)

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("DocumentDescriptorMatchResult", exact = true)
data class DocumentDescriptorMatchResult(
    val inputDescriptor: IOid4VPInputDescriptor,
    // Either a document or documentError should be returned, not both
    val document: Document?,
    // Either a document or documentError should be returned, not both
    val documentError: DeviceResponseDocumentErrorAlias?,
    // Derived from the document. Cannot be derived if no document is passed in
    val deviceKeyInfo: KeyInfoType<CoseKeyType>?,
    var deviceNamespaces: DeviceNameSpaces? = null,
    // No default/uuid as the nonce's needs to match in case multiple mdocs are returned
    val mdocNonce: String,
    /** The transcript used by the most recent successful device-auth signature. */
    var sessionTranscript: SessionTranscript? = null,
)

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocOid4vpService", exact = true)
interface MdocOid4vpService {
    suspend fun createDeviceResponse(
        matchingDocuments: Array<DocumentDescriptorMatchResult>,
        presentationDefinition: IOid4VPPresentationDefinition,
        clientId: String,
        responseUri: String?,
        authorizationRequestNonce: String,
        verifierEncryptionJwkThumbprint: ByteArray?,
        /** Non-null only for the ISO/IEC TS 18013-7 Annex B profile. */
        iso18013MdocGeneratedNonce: String? = null,
        context: Oid4vpDeviceAuthenticationContext? = null,
    ): DeviceResponse

    suspend fun prepareDeviceResponseSignings(
        matchingDocuments: Array<DocumentDescriptorMatchResult>,
        presentationDefinition: IOid4VPPresentationDefinition,
        clientId: String,
        responseUri: String?,
        authorizationRequestNonce: String,
        verifierEncryptionJwkThumbprint: ByteArray?,
        iso18013MdocGeneratedNonce: String? = null,
        context: Oid4vpDeviceAuthenticationContext? = null,
    ): IdkResult<List<PreparedMdocDeviceResponseSigning>, IdkError>

    suspend fun createDeviceResponseWithSignatures(
        matchingDocuments: Array<DocumentDescriptorMatchResult>,
        presentationDefinition: IOid4VPPresentationDefinition,
        clientId: String,
        responseUri: String?,
        authorizationRequestNonce: String,
        verifierEncryptionJwkThumbprint: ByteArray?,
        preparedSignings: List<PreparedMdocDeviceResponseSigning>,
        signatures: List<MdocDeviceResponseSignature>,
        iso18013MdocGeneratedNonce: String? = null,
        context: Oid4vpDeviceAuthenticationContext? = null,
    ): IdkResult<DeviceResponse, IdkError>

    suspend fun signDocument(
        clientId: String,
        responseUri: String?,
        mdocNonce: String,
        authorizationRequestNonce: String,
        verifierEncryptionJwkThumbprint: ByteArray?,
        deviceNamespaces: DeviceNameSpaces,
        document: Document?,
        inputDescriptor: IOid4VPInputDescriptor?,
        docType: DocType,
        deviceKeyInfo: KeyInfoType<*>?,
        presentationDefinition: IOid4VPPresentationDefinition,
        /** Non-null only for the ISO/IEC TS 18013-7 Annex B profile. */
        iso18013MdocGeneratedNonce: String? = null,
        context: Oid4vpDeviceAuthenticationContext? = null,
    ): Oid4vpSignResult

    fun filterApplicableDocumentsPerInputDescriptor(
        allDocument: Array<Document>,
        inputDescriptor: IOid4VPInputDescriptor,
    ): Array<Document>

    fun matchDocumentsAndDescriptors(
        mdocNonce: String,
        applicableDocuments: Array<Document>,
        presentationDefinition: IOid4VPPresentationDefinition,
    ): Array<DocumentDescriptorMatchResult>
}
