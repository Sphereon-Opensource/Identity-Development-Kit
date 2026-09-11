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

package com.sphereon.data.store.okd.server.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpBody
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.requirePathParam
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.core.api.http.util.ResponseUtils
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.okd.OkdBlobMapping
import com.sphereon.data.store.okd.generated.models.DocumentMetadata
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.StringKey
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

private val json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

/** OKD OAuth2 scopes for endpoint authorization */
private object OkdScopes {
    const val ALL_DOCUMENTS = "okd:alldocuments"
    const val EXAM_DOCUMENTS = "okd:examdocuments"
    const val BPV_DOCUMENTS = "okd:bpvdocuments"
    const val GRADUATION_DOCUMENTS = "okd:graduationdocuments"
    const val ENROLLMENT = "okd:enrollmentderollment"
    const val DESTROYED_NOTIFICATION = "okd:destroyednotification"
    const val STUDENT_INFO = "okd:studentinfo"
}

// ================================================================================================
// GET /documents/{documentId} — Download document binary
// ================================================================================================

interface OkdGetDocumentCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "okd.documents.get"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/documents/{documentId}",
                produces = setOf(MediaType.ApplicationOctetStream),
                operationId = "getDocumentById",
                handlerCommandId = COMMAND_ID,
                tags = setOf("documents"),
                summary = "Get binary document content",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(OkdGetDocumentCommand.COMMAND_ID)
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdGetDocumentCommandImpl", exact = true)
class OkdGetDocumentCommandImpl(
    execution: SessionExecution,
    private val blobService: BlobService,
) : HttpEndpointCommandAdapter(
        id = OkdGetDocumentCommand.COMMAND_ID,
        execution = execution,
        endpoint = OkdGetDocumentCommand.ENDPOINT,
    ),
    OkdGetDocumentCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        checkScope(request, listOf(OkdScopes.ALL_DOCUMENTS, OkdScopes.EXAM_DOCUMENTS, OkdScopes.BPV_DOCUMENTS, OkdScopes.GRADUATION_DOCUMENTS))?.let { return Err(it) }
        val req = request.withExtractedParams(endpoint.pathPattern)
        val documentId = req.requirePathParam("documentId").getOrElse { return Err(it) }

        val tenantId = execution.tenantId
        val result = blobService.getBlob(info = BlobInfo(tenantId = tenantId, path = documentId))

        if (result.isErr) {
            return Err(result.error)
        }

        val content = result.value
        return Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers =
                    buildMap {
                        put("Content-Type", content.descriptor.contentType ?: "application/octet-stream")
                        content.descriptor.filename?.let { put("Content-Disposition", ResponseUtils.contentDisposition(it)) }
                    },
                bodyContent = GenericHttpBody.Bytes(content.data),
            ),
        )
    }
}

// ================================================================================================
// PATCH /documents/{documentId} — Replace document content
// ================================================================================================

interface OkdUpdateDocumentCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "okd.documents.update"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.PATCH,
                pathPattern = "/documents/{documentId}",
                consumes = setOf(MediaType.ApplicationOctetStream),
                operationId = "patchDocumentById",
                handlerCommandId = COMMAND_ID,
                tags = setOf("documents"),
                summary = "Replace document binary content",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(OkdUpdateDocumentCommand.COMMAND_ID)
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdUpdateDocumentCommandImpl", exact = true)
class OkdUpdateDocumentCommandImpl(
    execution: SessionExecution,
    private val blobService: BlobService,
) : HttpEndpointCommandAdapter(
        id = OkdUpdateDocumentCommand.COMMAND_ID,
        execution = execution,
        endpoint = OkdUpdateDocumentCommand.ENDPOINT,
    ),
    OkdUpdateDocumentCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        checkScope(request, listOf(OkdScopes.ALL_DOCUMENTS, OkdScopes.EXAM_DOCUMENTS, OkdScopes.BPV_DOCUMENTS, OkdScopes.GRADUATION_DOCUMENTS))?.let { return Err(it) }
        val req = request.withExtractedParams(endpoint.pathPattern)
        val documentId = req.requirePathParam("documentId").getOrElse { return Err(it) }

        val data =
            request.bodyContent.asBytesOrNull()
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing request body"))

        val tenantId = execution.tenantId
        val contentType = request.contentType

        val result =
            blobService.storeBlob(
                target =
                    BlobInfo(
                        tenantId = tenantId,
                        path = documentId,
                        contentType = contentType,
                    ),
                data = data,
                options = PutOptions(overwrite = true),
            )

        if (result.isErr) {
            return Err(result.error)
        }
        return Ok(GenericHttpResponse(statusCode = 204))
    }
}

// ================================================================================================
// DELETE /documents/{documentId} — Delete document
// ================================================================================================

interface OkdDeleteDocumentCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "okd.documents.delete"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "/documents/{documentId}",
                operationId = "deleteDocumentById",
                handlerCommandId = COMMAND_ID,
                tags = setOf("documents"),
                summary = "Delete document from DMS",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(OkdDeleteDocumentCommand.COMMAND_ID)
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdDeleteDocumentCommandImpl", exact = true)
class OkdDeleteDocumentCommandImpl(
    execution: SessionExecution,
    private val blobService: BlobService,
) : HttpEndpointCommandAdapter(
        id = OkdDeleteDocumentCommand.COMMAND_ID,
        execution = execution,
        endpoint = OkdDeleteDocumentCommand.ENDPOINT,
    ),
    OkdDeleteDocumentCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        checkScope(request, listOf(OkdScopes.ALL_DOCUMENTS, OkdScopes.EXAM_DOCUMENTS, OkdScopes.BPV_DOCUMENTS, OkdScopes.GRADUATION_DOCUMENTS))?.let { return Err(it) }
        val req = request.withExtractedParams(endpoint.pathPattern)
        val documentId = req.requirePathParam("documentId").getOrElse { return Err(it) }

        val tenantId = execution.tenantId
        val result = blobService.deleteBlob(info = BlobInfo(tenantId = tenantId, path = documentId))

        if (result.isErr) {
            return Err(result.error)
        }
        return Ok(GenericHttpResponse(statusCode = 204))
    }
}

// ================================================================================================
// GET /documents/{documentId}/metadata — Get document metadata
// ================================================================================================

interface OkdGetDocumentMetadataCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "okd.documents.metadata"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/documents/{documentId}/metadata",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getDocumentMetadataById",
                handlerCommandId = COMMAND_ID,
                tags = setOf("documents"),
                summary = "Get document metadata",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(OkdGetDocumentMetadataCommand.COMMAND_ID)
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdGetDocumentMetadataCommandImpl", exact = true)
class OkdGetDocumentMetadataCommandImpl(
    execution: SessionExecution,
    private val blobService: BlobService,
) : HttpEndpointCommandAdapter(
        id = OkdGetDocumentMetadataCommand.COMMAND_ID,
        execution = execution,
        endpoint = OkdGetDocumentMetadataCommand.ENDPOINT,
    ),
    OkdGetDocumentMetadataCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        checkScope(request, listOf(OkdScopes.ALL_DOCUMENTS, OkdScopes.EXAM_DOCUMENTS, OkdScopes.BPV_DOCUMENTS, OkdScopes.GRADUATION_DOCUMENTS))?.let { return Err(it) }
        val req = request.withExtractedParams(endpoint.pathPattern)
        val documentId = req.requirePathParam("documentId").getOrElse { return Err(it) }

        val tenantId = execution.tenantId
        val result = blobService.getBlobInfo(info = BlobInfo(tenantId = tenantId, path = documentId))

        if (result.isErr) {
            return Err(result.error)
        }

        val okdMetadata = OkdBlobMapping.toOkdMetadata(result.value)
        return Ok(jsonResponse(200, json.encodeToString(DocumentMetadata.serializer(), okdMetadata)))
    }
}

// ================================================================================================
// POST /associations/{associationId} — Upload document to association
// ================================================================================================

interface OkdUploadDocumentCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "okd.associations.upload"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/associations/{associationId}",
                consumes = setOf(MediaType.ApplicationOctetStream),
                produces = setOf(MediaType.ApplicationJson),
                operationId = "postFileOnAssociationById",
                handlerCommandId = COMMAND_ID,
                tags = setOf("associations"),
                summary = "Upload document to association",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(OkdUploadDocumentCommand.COMMAND_ID)
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdUploadDocumentCommandImpl", exact = true)
class OkdUploadDocumentCommandImpl(
    execution: SessionExecution,
    private val blobService: BlobService,
) : HttpEndpointCommandAdapter(
        id = OkdUploadDocumentCommand.COMMAND_ID,
        execution = execution,
        endpoint = OkdUploadDocumentCommand.ENDPOINT,
    ),
    OkdUploadDocumentCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        checkScope(request, listOf(OkdScopes.ALL_DOCUMENTS, OkdScopes.ENROLLMENT))?.let { return Err(it) }
        val req = request.withExtractedParams(endpoint.pathPattern)
        val associationId = req.requirePathParam("associationId").getOrElse { return Err(it) }

        val data =
            request.bodyContent.asBytesOrNull()
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Missing request body"))

        val tenantId = execution.tenantId
        val contentType = request.contentType

        val result =
            blobService.storeBlob(
                target =
                    BlobInfo(
                        tenantId = tenantId,
                        contentType = contentType,
                        metadata =
                            mapOf(
                                "okd.associationId" to associationId,
                                "okd.documentType" to (request.queryParameters["documentType"] ?: "unknown"),
                            ),
                    ),
                data = data,
            )

        if (result.isErr) {
            return Err(result.error)
        }

        val descriptor = result.value
        val responseBody = """{"dmsDocumentId":"${descriptor.path}"}"""
        return Ok(jsonResponse(200, responseBody))
    }
}

// ================================================================================================
// GET /persons — List persons (simplified, metadata-backed)
// ================================================================================================

interface OkdListPersonsCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "okd.persons.list"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/persons",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "listPersons",
                handlerCommandId = COMMAND_ID,
                tags = setOf("persons"),
                summary = "List persons filtered by primaryCode",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(OkdListPersonsCommand.COMMAND_ID)
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdListPersonsCommandImpl", exact = true)
class OkdListPersonsCommandImpl(
    execution: SessionExecution,
    private val blobService: BlobService,
) : HttpEndpointCommandAdapter(
        id = OkdListPersonsCommand.COMMAND_ID,
        execution = execution,
        endpoint = OkdListPersonsCommand.ENDPOINT,
    ),
    OkdListPersonsCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        checkScope(request, listOf(OkdScopes.STUDENT_INFO))?.let { return Err(it) }
        val tenantId = execution.tenantId
        val primaryCode = request.queryParameters["primaryCode"]

        // Search blob metadata for persons by primaryCode
        val query =
            com.sphereon.data.store.blob.MetadataSearchQuery(
                customMetadata =
                    buildMap {
                        if (primaryCode != null) {
                            put("okd.primaryCode", primaryCode)
                        }
                    },
                maxResults = 100,
            )
        val searchResult = blobService.findByMetadata(info = BlobInfo(tenantId = tenantId), query = query)
        if (searchResult.isErr) {
            return Err(searchResult.error)
        }

        // Extract unique persons from document metadata
        val persons =
            searchResult.value
                .mapNotNull { desc -> desc.metadata.custom["okd.personId"] }
                .distinct()
                .map { personId ->
                    buildPersonJson(personId, searchResult.value.filter { it.metadata.custom["okd.personId"] == personId })
                }

        val body = """{"items":[${persons.joinToString(",")}]}"""
        return Ok(jsonResponse(200, body))
    }
}

// ================================================================================================
// GET /persons/{personId} — Get person by ID (simplified)
// ================================================================================================

interface OkdGetPersonCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "okd.persons.get"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/persons/{personId}",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getPersonById",
                handlerCommandId = COMMAND_ID,
                tags = setOf("persons"),
                summary = "Get person by ID",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(OkdGetPersonCommand.COMMAND_ID)
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdGetPersonCommandImpl", exact = true)
class OkdGetPersonCommandImpl(
    execution: SessionExecution,
    private val blobService: BlobService,
) : HttpEndpointCommandAdapter(
        id = OkdGetPersonCommand.COMMAND_ID,
        execution = execution,
        endpoint = OkdGetPersonCommand.ENDPOINT,
    ),
    OkdGetPersonCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        checkScope(request, listOf(OkdScopes.STUDENT_INFO))?.let { return Err(it) }
        val req = request.withExtractedParams(endpoint.pathPattern)
        val personId = req.requirePathParam("personId").getOrElse { return Err(it) }

        val tenantId = execution.tenantId

        // Search blob metadata for documents belonging to this person
        val query =
            com.sphereon.data.store.blob.MetadataSearchQuery(
                customMetadata = mapOf("okd.personId" to personId),
                maxResults = 100,
            )
        val searchResult = blobService.findByMetadata(info = BlobInfo(tenantId = tenantId), query = query)
        if (searchResult.isErr) {
            return Err(searchResult.error)
        }

        if (searchResult.value.isEmpty()) {
            return Err(IdkError.NOT_FOUND_ERROR(message = "Person not found: $personId"))
        }

        val body = buildPersonJson(personId, searchResult.value)
        return Ok(jsonResponse(200, body))
    }
}

// ================================================================================================
// GET / — Service metadata
// ================================================================================================

interface OkdServiceMetadataCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "okd.service.metadata"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/",
                produces = setOf(MediaType.ApplicationJson),
                operationId = "getServiceMetadata",
                handlerCommandId = COMMAND_ID,
                tags = setOf("service"),
                summary = "OKD service metadata and version info",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesIntoMap(SessionScope::class, binding = binding<HttpEndpointCommand>())
@StringKey(OkdServiceMetadataCommand.COMMAND_ID)
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdServiceMetadataCommandImpl", exact = true)
class OkdServiceMetadataCommandImpl(
    execution: SessionExecution,
) : HttpEndpointCommandAdapter(
        id = OkdServiceMetadataCommand.COMMAND_ID,
        execution = execution,
        endpoint = OkdServiceMetadataCommand.ENDPOINT,
    ),
    OkdServiceMetadataCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        applyDuring(args)
        val body = """{"apiVersion":"0.9.1","provider":"sphereon-idk-blob-store"}"""
        return Ok(jsonResponse(200, body))
    }
}

// ================================================================================================
// Shared helpers
// ================================================================================================

private fun checkScope(
    request: GenericHttpRequest,
    requiredScopes: List<String>,
): IdkError? {
    // Extract scopes from the request (typically set by the auth middleware)
    val grantedScopes =
        request.headers["X-OAuth-Scopes"]
            ?.split(",")
            ?.map { it.trim() }
            ?: return null // No scope header = no enforcement (auth middleware not configured)

    if (requiredScopes.any { it in grantedScopes }) {
        return null
    } // At least one required scope granted
    return IdkError.UNAUTHORIZED_ERROR(message = "Insufficient OKD scope. Required one of: ${requiredScopes.joinToString(", ")}")
}

private fun buildPersonJson(
    personId: String,
    documents: List<com.sphereon.data.store.blob.BlobDescriptor>,
): String {
    val firstDoc = documents.firstOrNull()
    val primaryCode = firstDoc?.metadata?.custom?.get("okd.primaryCode") ?: ""
    val givenName = firstDoc?.metadata?.custom?.get("okd.givenName") ?: ""
    val surname = firstDoc?.metadata?.custom?.get("okd.surname") ?: ""

    val docEntries =
        documents.map { desc ->
            buildJsonObject {
                put("documentId", JsonPrimitive(desc.path))
                put("documentType", JsonPrimitive(desc.metadata.custom["okd.documentType"] ?: "unknown"))
                put("documentName", JsonPrimitive(desc.filename ?: ""))
            }
        }

    val personJson =
        buildJsonObject {
            put("personId", JsonPrimitive(personId))
            put(
                "primaryCode",
                buildJsonObject {
                    put("codeType", JsonPrimitive("studentNumber"))
                    put("code", JsonPrimitive(primaryCode))
                },
            )
            put("givenName", JsonPrimitive(givenName))
            put("surname", JsonPrimitive(surname))
            put(
                "consumers",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("consumerKey", JsonPrimitive("nl-okd"))
                            put(
                                "personDocuments",
                                buildJsonArray {
                                    docEntries.forEach { add(it) }
                                },
                            )
                        },
                    )
                },
            )
        }

    return personJson.toString()
}
