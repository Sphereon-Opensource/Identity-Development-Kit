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

import com.sphereon.core.api.log.LogService
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.Uuid
import com.sphereon.mdoc.data.device.DeviceResponse
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.transfer.reader.SessionTranscript
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * mdoc holder-flow coordinator for the profile selected by its caller.
 *
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("implements", exact = true)
 * This class contains the shared document-signing coordinator used by the ISO
 * 18013-7 Annex B restricted Presentation-Exchange flow. It is not the regular
 * OpenID4VP/DCQL request router; that route remains in the regular OID4VP
 * holder stack. Callers select the Annex B transcript by supplying the optional
 * ISO mdoc-generated nonce.
 * It coordinates:
 * - Processing the mdoc Presentation Exchange definition supplied by the caller
 * - Matching documents to requested input descriptors
 * - Creating the profile-selected SessionTranscript from OID4VP parameters
 * - Signing documents with device authentication
 * - Building the DeviceResponse with presentation_submission
 *
 * When invoked by the ISO adapter, the flow follows ISO 18013-7 B.1.2 (holder perspective):
 * 1. Receive Authorization Request (with request_uri)
 * 2. Fetch Authorization Request Object
 * 3. Verify JWT signature
 * 4. Resolve the profile-selected Presentation Exchange definition
 * 5. Match available documents
 * 6. User authentication and consent
 * 7. Sign documents with mdoc authentication
 * 8. Create encrypted Authorization Response
 * 9. POST to response_uri
 * 10. Redirect user
 *
 * @property oid4vpService The service handling OID4VP operations (signing, matching)
 */

@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpHolderFlow", exact = true)
class Oid4vpHolderFlow(
    private val oid4vpService: MdocOid4vpService,
    private val logService: LogService,
) {
    /**
     * Process an OID4VP Authorization Request and create a DeviceResponse.
     *
     * This method:
     * 1. Matches available documents against the resolved request definition
     * 2. Creates the profile-selected SessionTranscript; Annex B is opt-in
     * 3. Signs each document with device authentication
     * 4. Returns the complete DeviceResponse ready for transmission
     *
     * Per ISO 18013-7 B.4.2.3.3:
     * - Input Descriptor `id` MUST match the document type (docType)
     * - Each document type can only appear once in the presentation definition
     * - The `mso_mdoc` format MUST be present in the input descriptor
     *
     * @param presentationDefinition The Presentation Exchange definition selected by the caller
     * @param availableDocuments Array of documents available to present
     * @param clientId The client_id from Authorization Request
     * @param responseUri The response_uri from Authorization Request
     * @param authorizationRequestNonce The nonce from Authorization Request
     * @param verifierEncryptionJwkThumbprint Raw RFC 7638 SHA-256 thumbprint of the verifier's
     * encryption JWK for encrypted response modes; null for unencrypted responses
     * @param mdocNonce mdoc nonce used for matching documents. It is also retained in the result.
     * @param iso18013MdocGeneratedNonce Optional ISO 18013-7 Annex B mdoc-generated nonce. When
     * non-null, the ISO restricted-PE transcript is used; null preserves regular OID4VP behavior.
     * @return DeviceResponse containing signed documents and/or errors
     */
    suspend fun processAuthorizationRequest(
        presentationDefinition: IOid4VPPresentationDefinition,
        availableDocuments: Array<Document>,
        clientId: String,
        responseUri: String,
        authorizationRequestNonce: String,
        verifierEncryptionJwkThumbprint: ByteArray?,
        mdocNonce: String = Uuid.v4String(),
        iso18013MdocGeneratedNonce: String? = null,
    ): Oid4vpHolderResult {
        logService.info("[OID4VP Holder] Processing Authorization Request for client: $clientId")
        logService.debug("[OID4VP Holder] Presentation Definition ID: ${presentationDefinition.id}")
        logService.debug("[OID4VP Holder] Input Descriptors: ${presentationDefinition.input_descriptors.size}")
        logService.debug("[OID4VP Holder] Available Documents: ${availableDocuments.size}")

        // Match documents to input descriptors
        val matchedDocuments =
            oid4vpService.matchDocumentsAndDescriptors(
                mdocNonce = mdocNonce,
                applicableDocuments = availableDocuments,
                presentationDefinition = presentationDefinition,
            )

        logService.debug("[OID4VP Holder] Matched ${matchedDocuments.size} document descriptors")

        // Log matching results
        matchedDocuments.forEach { match ->
            if (match.document != null) {
                logService.debug("[OID4VP Holder] Matched document ${match.document.docType} to descriptor ${match.inputDescriptor.id}")
            } else {
                logService.warn("[OID4VP Holder] No document found for descriptor ${match.inputDescriptor.id}")
            }
        }

        // Create DeviceResponse with signed documents
        val deviceResponse =
            oid4vpService.createDeviceResponse(
                matchingDocuments = matchedDocuments,
                presentationDefinition = presentationDefinition,
                clientId = clientId,
                responseUri = responseUri,
                authorizationRequestNonce = authorizationRequestNonce,
                verifierEncryptionJwkThumbprint = verifierEncryptionJwkThumbprint,
                iso18013MdocGeneratedNonce = iso18013MdocGeneratedNonce,
            )

        // Create presentation submission
        val presentationSubmission = Oid4VPPresentationSubmission.fromPresentationDefinition(presentationDefinition)

        // Validate presentation submission against definition
        presentationSubmission.assertValid(presentationDefinition)

        logService.info("[OID4VP Holder] Created DeviceResponse with ${deviceResponse.documents?.size ?: 0} documents and ${deviceResponse.documentErrors?.size ?: 0} errors")

        return Oid4vpHolderResult(
            deviceResponse = deviceResponse,
            presentationSubmission = presentationSubmission,
            sessionTranscript = extractSessionTranscript(matchedDocuments),
            mdocNonce = mdocNonce,
        )
    }

    /**
     * Extract the SessionTranscript from the first successfully matched document.
     *
     * All documents in an OID4VP presentation share the same SessionTranscript,
     * so we can extract it from any successfully signed document result.
     *
     * @param matchedDocuments Array of match results with signing information
     * @return The SessionTranscript used for signing, or null if no documents were signed
     */
    private fun extractSessionTranscript(matchedDocuments: Array<DocumentDescriptorMatchResult>): SessionTranscript? {
        // All documents share the same session transcript in OID4VP
        // Extract from the first document that has device namespaces (indicating it was processed)
        val signedMatch = matchedDocuments.firstOrNull { it.document != null }
        return signedMatch?.sessionTranscript
    }
}

/**
 * Result of processing an OID4VP Authorization Request on the holder side.
 *
 * This contains all the components needed to construct the OID4VP Authorization Response
 * per ISO 18013-7 B.4.3:
 * - DeviceResponse containing signed mdocs
 * - Presentation Submission mapping descriptors to documents
 * - SessionTranscript used for signing
 *
 * @property deviceResponse The signed device response domain model
 * @property presentationSubmission The presentation submission mapping
 * @property sessionTranscript The session transcript used for mdoc authentication
 * @property mdocNonce The mdoc-generated nonce used in SessionTranscript
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpHolderResult", exact = true)
data class Oid4vpHolderResult(
    val deviceResponse: DeviceResponse,
    val presentationSubmission: Oid4VPPresentationSubmission,
    val sessionTranscript: SessionTranscript?,
    val mdocNonce: String,
)

/**
 * Builder for convenient creation of OID4VP holder flows.
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpHolderFlowBuilder", exact = true)
class Oid4vpHolderFlowBuilder {
    private var oid4vpService: MdocOid4vpService? = null
    private var logService: LogService? = null

    fun withOid4vpService(service: MdocOid4vpService) =
        apply {
            this.oid4vpService = service
        }

    fun withLogService(service: LogService) =
        apply {
            this.logService = service
        }

    fun build(): Oid4vpHolderFlow {
        requireNotNull(oid4vpService) { "OID4VP service must be provided" }
        requireNotNull(logService) { "LogService must be provided" }
        return Oid4vpHolderFlow(oid4vpService!!, logService!!)
    }
}
