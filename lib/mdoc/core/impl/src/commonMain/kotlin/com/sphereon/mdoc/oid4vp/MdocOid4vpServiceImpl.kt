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

package com.sphereon.mdoc.oid4vp

import com.sphereon.cbor.CborUInt
import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.SigningException
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.di.session.SessionScope
import com.sphereon.mdoc.MdocSignService
import com.sphereon.mdoc.MdocSignServiceImpl
import com.sphereon.mdoc.data.DeviceResponseDocumentErrorAlias
import com.sphereon.mdoc.data.device.DeviceAuthentication
import com.sphereon.mdoc.data.device.DeviceNameSpaces
import com.sphereon.mdoc.data.device.DeviceResponse
import com.sphereon.mdoc.data.device.DocType
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.data.device.DocumentError
import com.sphereon.mdoc.data.mso.MobileSecurityObject
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodec
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<MdocOid4vpService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocOid4vpServiceImpl", exact = true)
class MdocOid4vpServiceImpl(
    val signService: MdocSignService,
    private val logService: SessionLogService,
    private val mobileSecurityObjectCborCodec: MobileSecurityObjectCborCodec,
) : MdocOid4vpService {
    override suspend fun createDeviceResponse(
        matchingDocuments: Array<DocumentDescriptorMatchResult>,
        presentationDefinition: IOid4VPPresentationDefinition,
        clientId: String,
        responseUri: String,
        authorizationRequestNonce: String,
    ): DeviceResponse {
        var documentErrors = arrayOf<DeviceResponseDocumentErrorAlias>()
        var documents = arrayOf<Document>()
        matchingDocuments.forEach { doc ->
            val docType = doc.inputDescriptor.id // 18013-7 matches doc types to the id of the input descriptor
            var error = doc.documentError
            if (doc.document !== null && error === null) {
                val signed =
                    signDocument(
                        presentationDefinition = presentationDefinition,
                        clientId = clientId,
                        responseUri = responseUri,
                        authorizationRequestNonce = authorizationRequestNonce,
                        mdocNonce = doc.mdocNonce,
                        document = doc.document,
                        inputDescriptor = doc.inputDescriptor,
                        docType = docType,
                        deviceKeyInfo = doc.deviceKeyInfo,
                        deviceNamespaces = doc.deviceNamespaces ?: DeviceNameSpaces(mapOf()),
                    )
                error = signed.documentError
                val mdoc = signed.document
                if (mdoc === null && error === null) {
                    error = mapOf(Pair(docType, DocumentError(0)))
                }
                if (error === null && mdoc !== null) {
                    documents = documents.plus(mdoc)
                }
            }
            if (error !== null) {
                documentErrors = documentErrors.plus(error)
            }
        }
        check(documents.isNotEmpty() || documentErrors.isNotEmpty()) { "No documents and no errors present. We cannot create a device response with both not present" }

        return DeviceResponse(
            documents =
                if (documents.isEmpty()) {
                    null
                } else {
                    documents
                },
            documentErrors =
                if (documentErrors.isEmpty()) {
                    null
                } else {
                    documentErrors
                },
            original = null,
        )
    }

    override suspend fun signDocument(
        clientId: String,
        responseUri: String,
        mdocNonce: String,
        authorizationRequestNonce: String,
        deviceNamespaces: DeviceNameSpaces,
        document: Document?,
        inputDescriptor: IOid4VPInputDescriptor?,
        docType: DocType,
        deviceKeyInfo: KeyInfoType<*>?,
        presentationDefinition: IOid4VPPresentationDefinition,
    ): Oid4vpSignResult {
        val request = Oid4VPPresentationDefinition.fromDTO(presentationDefinition).toDocRequest()
        val deviceAuthentication =
            DeviceAuthentication.Companion.fromOid4vp(clientId, responseUri, mdocNonce, authorizationRequestNonce, docType, deviceNamespaces)
        val sessionTranscript = deviceAuthentication.sessionTranscript
        var resolvedKeyInfo =
            MdocSignServiceImpl.Utils.getSuppliedOrMSODerivedCborKeyInfo(
                keyInfo = deviceKeyInfo,
                mso = document?.let(::decodeMso),
            )
        val inputDescriptorAlg = inputDescriptor?.format?.mso_mdoc?.alg
        if (resolvedKeyInfo.signatureAlgorithm == null) {
            // if we do not have the signature algorithm on the key info, let's have a look
            if (inputDescriptorAlg.isNullOrEmpty()) {
                throw SigningException("Device key info, MSO and input descriptor mso_mdoc alg did not indicate their supported algorithms. Cannot sign mdoc")
            }
            val supportedSigAlgs =
                inputDescriptorAlg.flatMap { descAlg ->
                    SignatureAlgorithm.Companion.asList
                        .map { enumValue ->
                            if (enumValue.cose?.id == descAlg) {
                                return@map enumValue
                            } else {
                                return@map null
                            }
                        }.filterNotNull()
                        .filter { enumValue ->
                            (enumValue.cose?.keyType == resolvedKeyInfo.keyType) ||
                                (enumValue.cose?.keyType?.let { CborUInt(it.value.toLong()) } == resolvedKeyInfo.key.kty)
                        }
                }
            if (supportedSigAlgs.isEmpty()) {
                throw SigningException("Device key info, MSO and input descriptor mso_mdoc could not provide their their supported algorithms properly. Cannot sign mdoc")
            }
            resolvedKeyInfo = ResolvedKeyInfo.Companion.fromDTO(resolvedKeyInfo).copy(signatureAlgorithm = supportedSigAlgs[0])
        }

        val mdoc =
            document?.let {
                signService.deviceSignDocument(
                    request = request,
                    document = it,
                    deviceAuthentication = deviceAuthentication,
                    deviceKeyInfo = resolvedKeyInfo,
                )
            }
        val documentError: DeviceResponseDocumentErrorAlias? =
            if (document != null) {
                null
            } else {
                mapOf(Pair(docType, DocumentError(0)))
            }
        return Oid4vpSignResult(
            sessionTranscript = sessionTranscript,
            document = mdoc,
            presentationDefinition = presentationDefinition,
            documentError = documentError,
            deviceKeyInfo = resolvedKeyInfo,
        )
    }

    override fun filterApplicableDocumentsPerInputDescriptor(
        allDocument: Array<Document>,
        inputDescriptor: IOid4VPInputDescriptor,
    ): Array<Document> {
        /*
         * From 18013-7:
         * The value for id shall be set to the requested document type. This indicates that all requested data elements shall be selected from that document type.
         *
         * The Input Descriptor id shall be unique per Presentation Definition object. This implies that a document type can only be used once within the Presentation Definition.
         */
        return allDocument.filter { inputDescriptor.format.mso_mdoc !== null }.filter { it.docType == inputDescriptor.id }.toTypedArray()
    }

    override fun matchDocumentsAndDescriptors(
        mdocNonce: String, // mdoc nonce will be set on all document results
        applicableDocuments: Array<Document>,
        presentationDefinition: IOid4VPPresentationDefinition,
    ): Array<DocumentDescriptorMatchResult> =
        presentationDefinition.input_descriptors
            .map { inputDescriptor ->
                val matchingDocuments = filterApplicableDocumentsPerInputDescriptor(applicableDocuments, inputDescriptor)
                if (matchingDocuments.isEmpty()) {
                    logService.warn("No documents found satisfying descriptor id/document type ${inputDescriptor.id}")
                    DocumentDescriptorMatchResult(
                        inputDescriptor = inputDescriptor,
                        document = null,
                        mdocNonce = mdocNonce,
                        documentError = mapOf(Pair(inputDescriptor.id, DocumentError(0))),
                        deviceKeyInfo = null,
                    )
                } else if (matchingDocuments.size > 1) {
                    logService.warn("Multiple documents found satisfying descriptor id/document type ${inputDescriptor.id}, which is not allowed")
                    DocumentDescriptorMatchResult(
                        inputDescriptor = inputDescriptor,
                        document = null,
                        mdocNonce = mdocNonce,
                        documentError = mapOf(Pair(inputDescriptor.id, DocumentError(0))),
                        deviceKeyInfo = null,
                    )
                } else {
                    val document = matchingDocuments[0]
                    val deviceKeyInfo = MdocSignServiceImpl.Utils.getSuppliedOrMSODerivedCborKeyInfo(mso = decodeMso(document))
                    DocumentDescriptorMatchResult(
                        inputDescriptor = inputDescriptor,
                        document = document,
                        mdocNonce = mdocNonce,
                        documentError = null,
                        deviceKeyInfo = deviceKeyInfo,
                    )
                }
            }.toTypedArray()

    private fun decodeMso(document: Document): MobileSecurityObject {
        val payload =
            document.issuerSigned.issuerAuth.payload
                ?.value
                ?: throw IllegalArgumentException("Payload is null for MSO, that is not allowed")
        return mobileSecurityObjectCborCodec.decode(payload).getOrThrow().value
    }
}
