package com.sphereon.data.store.okd.server.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpBody
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.jsonResponse
import com.sphereon.core.api.http.errorResponse
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobMetadata
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.BlobStoreError
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.okd.OkdBlobMapping
import com.sphereon.data.store.blob.okd.OkdBlobStoreConfig
import com.sphereon.data.store.okd.generated.models.DocumentMetadata
import com.sphereon.data.store.okd.generated.models.DocumentUploadResponse
import com.sphereon.di.session.SessionScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.GET,
            pathPattern = "/documents/{documentId}",
            produces = setOf(MediaType.ApplicationOctetStream),
            operationId = "getDocumentById",
            tags = setOf("documents"),
            summary = "Get binary document content",
        )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OkdGetDocumentCommand>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdGetDocumentCommandImpl", exact = true)
class OkdGetDocumentCommandImpl(
    execution: SessionExecution,
    private val blobService: BlobService,
) : HttpEndpointCommandAdapter(
    id = OkdGetDocumentCommand.COMMAND_ID,
    execution = execution,
    endpoint = OkdGetDocumentCommand.ENDPOINT,
), OkdGetDocumentCommand {

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        checkScope(request, listOf(OkdScopes.ALL_DOCUMENTS, OkdScopes.EXAM_DOCUMENTS, OkdScopes.BPV_DOCUMENTS, OkdScopes.GRADUATION_DOCUMENTS))?.let { return Ok(it) }
        val documentId = request.withExtractedParams(endpoint.pathPattern).pathParameters["documentId"]
            ?: return Ok(errorResponse(400, "Missing documentId"))

        val tenantId = resolveTenantId(request)
        val result = blobService.getBlob(info = BlobInfo(tenantId = tenantId, path = documentId))

        if (result.isErr) return Ok(mapBlobErrorToResponse(result.error))

        val content = result.value
        return Ok(
            GenericHttpResponse(
                statusCode = 200,
                headers = buildMap {
                    put("Content-Type", content.descriptor.contentType ?: "application/octet-stream")
                    content.descriptor.filename?.let { put("Content-Disposition", "attachment; filename=\"$it\"") }
                },
                bodyContent = GenericHttpBody.Bytes(content.data),
            )
        )
    }
}

// ================================================================================================
// PATCH /documents/{documentId} — Replace document content
// ================================================================================================

interface OkdUpdateDocumentCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "okd.documents.update"
        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.PATCH,
            pathPattern = "/documents/{documentId}",
            consumes = setOf(MediaType.ApplicationOctetStream),
            operationId = "patchDocumentById",
            tags = setOf("documents"),
            summary = "Replace document binary content",
        )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OkdUpdateDocumentCommand>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdUpdateDocumentCommandImpl", exact = true)
class OkdUpdateDocumentCommandImpl(
    execution: SessionExecution,
    private val blobService: BlobService,
) : HttpEndpointCommandAdapter(
    id = OkdUpdateDocumentCommand.COMMAND_ID,
    execution = execution,
    endpoint = OkdUpdateDocumentCommand.ENDPOINT,
), OkdUpdateDocumentCommand {

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        checkScope(request, listOf(OkdScopes.ALL_DOCUMENTS, OkdScopes.EXAM_DOCUMENTS, OkdScopes.BPV_DOCUMENTS, OkdScopes.GRADUATION_DOCUMENTS))?.let { return Ok(it) }
        val documentId = request.withExtractedParams(endpoint.pathPattern).pathParameters["documentId"]
            ?: return Ok(errorResponse(400, "Missing documentId"))

        val data = request.bodyContent.asBytesOrNull()
            ?: return Ok(errorResponse(400, "Missing request body"))

        val tenantId = resolveTenantId(request)
        val contentType = request.contentType

        val result = blobService.storeBlob(
            target = BlobInfo(
                tenantId = tenantId,
                path = documentId,
                contentType = contentType,
            ),
            data = data,
            options = PutOptions(overwrite = true),
        )

        if (result.isErr) return Ok(mapBlobErrorToResponse(result.error))
        return Ok(GenericHttpResponse(statusCode = 204))
    }
}

// ================================================================================================
// DELETE /documents/{documentId} — Delete document
// ================================================================================================

interface OkdDeleteDocumentCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "okd.documents.delete"
        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.DELETE,
            pathPattern = "/documents/{documentId}",
            operationId = "deleteDocumentById",
            tags = setOf("documents"),
            summary = "Delete document from DMS",
        )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OkdDeleteDocumentCommand>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdDeleteDocumentCommandImpl", exact = true)
class OkdDeleteDocumentCommandImpl(
    execution: SessionExecution,
    private val blobService: BlobService,
) : HttpEndpointCommandAdapter(
    id = OkdDeleteDocumentCommand.COMMAND_ID,
    execution = execution,
    endpoint = OkdDeleteDocumentCommand.ENDPOINT,
), OkdDeleteDocumentCommand {

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        checkScope(request, listOf(OkdScopes.ALL_DOCUMENTS, OkdScopes.EXAM_DOCUMENTS, OkdScopes.BPV_DOCUMENTS, OkdScopes.GRADUATION_DOCUMENTS))?.let { return Ok(it) }
        val documentId = request.withExtractedParams(endpoint.pathPattern).pathParameters["documentId"]
            ?: return Ok(errorResponse(400, "Missing documentId"))

        val tenantId = resolveTenantId(request)
        val result = blobService.deleteBlob(info = BlobInfo(tenantId = tenantId, path = documentId))

        if (result.isErr) return Ok(mapBlobErrorToResponse(result.error))
        return Ok(GenericHttpResponse(statusCode = 204))
    }
}

// ================================================================================================
// GET /documents/{documentId}/metadata — Get document metadata
// ================================================================================================

interface OkdGetDocumentMetadataCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "okd.documents.metadata"
        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.GET,
            pathPattern = "/documents/{documentId}/metadata",
            produces = setOf(MediaType.ApplicationJson),
            operationId = "getDocumentMetadataById",
            tags = setOf("documents"),
            summary = "Get document metadata",
        )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OkdGetDocumentMetadataCommand>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdGetDocumentMetadataCommandImpl", exact = true)
class OkdGetDocumentMetadataCommandImpl(
    execution: SessionExecution,
    private val blobService: BlobService,
) : HttpEndpointCommandAdapter(
    id = OkdGetDocumentMetadataCommand.COMMAND_ID,
    execution = execution,
    endpoint = OkdGetDocumentMetadataCommand.ENDPOINT,
), OkdGetDocumentMetadataCommand {

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        checkScope(request, listOf(OkdScopes.ALL_DOCUMENTS, OkdScopes.EXAM_DOCUMENTS, OkdScopes.BPV_DOCUMENTS, OkdScopes.GRADUATION_DOCUMENTS))?.let { return Ok(it) }
        val documentId = request.withExtractedParams(endpoint.pathPattern).pathParameters["documentId"]
            ?: return Ok(errorResponse(400, "Missing documentId"))

        val tenantId = resolveTenantId(request)
        val result = blobService.getBlobInfo(info = BlobInfo(tenantId = tenantId, path = documentId))

        if (result.isErr) return Ok(mapBlobErrorToResponse(result.error))

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
        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.POST,
            pathPattern = "/associations/{associationId}",
            consumes = setOf(MediaType.ApplicationOctetStream),
            produces = setOf(MediaType.ApplicationJson),
            operationId = "postFileOnAssociationById",
            tags = setOf("associations"),
            summary = "Upload document to association",
        )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OkdUploadDocumentCommand>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdUploadDocumentCommandImpl", exact = true)
class OkdUploadDocumentCommandImpl(
    execution: SessionExecution,
    private val blobService: BlobService,
) : HttpEndpointCommandAdapter(
    id = OkdUploadDocumentCommand.COMMAND_ID,
    execution = execution,
    endpoint = OkdUploadDocumentCommand.ENDPOINT,
), OkdUploadDocumentCommand {

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        checkScope(request, listOf(OkdScopes.ALL_DOCUMENTS, OkdScopes.ENROLLMENT))?.let { return Ok(it) }
        val associationId = request.withExtractedParams(endpoint.pathPattern).pathParameters["associationId"]
            ?: return Ok(errorResponse(400, "Missing associationId"))

        val data = request.bodyContent.asBytesOrNull()
            ?: return Ok(errorResponse(400, "Missing request body"))

        val tenantId = resolveTenantId(request)
        val contentType = request.contentType

        val result = blobService.storeBlob(
            target = BlobInfo(
                tenantId = tenantId,
                contentType = contentType,
                metadata = mapOf(
                    "okd.associationId" to associationId,
                    "okd.documentType" to (request.queryParameters["documentType"] ?: "unknown"),
                ),
            ),
            data = data,
        )

        if (result.isErr) return Ok(mapBlobErrorToResponse(result.error))

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
        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.GET,
            pathPattern = "/persons",
            produces = setOf(MediaType.ApplicationJson),
            operationId = "listPersons",
            tags = setOf("persons"),
            summary = "List persons filtered by primaryCode",
        )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OkdListPersonsCommand>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdListPersonsCommandImpl", exact = true)
class OkdListPersonsCommandImpl(
    execution: SessionExecution,
    private val blobService: BlobService,
) : HttpEndpointCommandAdapter(
    id = OkdListPersonsCommand.COMMAND_ID,
    execution = execution,
    endpoint = OkdListPersonsCommand.ENDPOINT,
), OkdListPersonsCommand {

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        checkScope(request, listOf(OkdScopes.STUDENT_INFO))?.let { return Ok(it) }
        val tenantId = resolveTenantId(request)
        val primaryCode = request.queryParameters["primaryCode"]

        // Search blob metadata for persons by primaryCode
        val query = com.sphereon.data.store.blob.MetadataSearchQuery(
            customMetadata = buildMap {
                if (primaryCode != null) put("okd.primaryCode", primaryCode)
            },
            maxResults = 100,
        )
        val searchResult = blobService.findByMetadata(info = BlobInfo(tenantId = tenantId), query = query)
        if (searchResult.isErr) return Ok(mapBlobErrorToResponse(searchResult.error))

        // Extract unique persons from document metadata
        val persons = searchResult.value
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
        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.GET,
            pathPattern = "/persons/{personId}",
            produces = setOf(MediaType.ApplicationJson),
            operationId = "getPersonById",
            tags = setOf("persons"),
            summary = "Get person by ID",
        )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OkdGetPersonCommand>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdGetPersonCommandImpl", exact = true)
class OkdGetPersonCommandImpl(
    execution: SessionExecution,
    private val blobService: BlobService,
) : HttpEndpointCommandAdapter(
    id = OkdGetPersonCommand.COMMAND_ID,
    execution = execution,
    endpoint = OkdGetPersonCommand.ENDPOINT,
), OkdGetPersonCommand {

    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)
        checkScope(request, listOf(OkdScopes.STUDENT_INFO))?.let { return Ok(it) }
        val personId = request.withExtractedParams(endpoint.pathPattern).pathParameters["personId"]
            ?: return Ok(errorResponse(400, "Missing personId"))

        val tenantId = resolveTenantId(request)

        // Search blob metadata for documents belonging to this person
        val query = com.sphereon.data.store.blob.MetadataSearchQuery(
            customMetadata = mapOf("okd.personId" to personId),
            maxResults = 100,
        )
        val searchResult = blobService.findByMetadata(info = BlobInfo(tenantId = tenantId), query = query)
        if (searchResult.isErr) return Ok(mapBlobErrorToResponse(searchResult.error))

        if (searchResult.value.isEmpty()) {
            return Ok(errorResponse(404, "Person not found: $personId"))
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
        val ENDPOINT = HttpEndpointDescriptor(
            method = HttpMethod.GET,
            pathPattern = "/",
            produces = setOf(MediaType.ApplicationJson),
            operationId = "getServiceMetadata",
            tags = setOf("service"),
            summary = "OKD service metadata and version info",
        )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OkdServiceMetadataCommand>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdServiceMetadataCommandImpl", exact = true)
class OkdServiceMetadataCommandImpl(
    execution: SessionExecution,
) : HttpEndpointCommandAdapter(
    id = OkdServiceMetadataCommand.COMMAND_ID,
    execution = execution,
    endpoint = OkdServiceMetadataCommand.ENDPOINT,
), OkdServiceMetadataCommand {

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

private fun resolveTenantId(request: GenericHttpRequest): String {
    return request.headers["X-Tenant-ID"]
        ?: request.queryParameters["tenantId"]
        ?: "default"
}

private fun checkScope(request: GenericHttpRequest, requiredScopes: List<String>): GenericHttpResponse? {
    // Extract scopes from the request (typically set by the auth middleware)
    val grantedScopes = request.headers["X-OAuth-Scopes"]
        ?.split(",")
        ?.map { it.trim() }
        ?: return null // No scope header = no enforcement (auth middleware not configured)

    if (requiredScopes.any { it in grantedScopes }) return null // At least one required scope granted
    return errorResponse(403, "Insufficient OKD scope. Required one of: ${requiredScopes.joinToString(", ")}")
}

private fun mapBlobErrorToResponse(error: IdkError): GenericHttpResponse {
    val statusCode = when {
        error.code?.contains("NOT_FOUND", ignoreCase = true) == true -> 404
        error.code?.contains("ALREADY_EXISTS", ignoreCase = true) == true -> 409
        error.code?.contains("PERMISSION_DENIED", ignoreCase = true) == true -> 403
        error.code?.contains("UNAUTHORIZED", ignoreCase = true) == true -> 401
        error.code?.contains("QUOTA_EXCEEDED", ignoreCase = true) == true -> 413
        error.code?.contains("PRECONDITION_FAILED", ignoreCase = true) == true -> 422
        error.code?.contains("UNSUPPORTED", ignoreCase = true) == true -> 405
        error.code?.contains("INTEGRITY_ERROR", ignoreCase = true) == true -> 422
        error.code?.contains("IO_ERROR", ignoreCase = true) == true -> 500
        error.code?.contains("BACKEND_ERROR", ignoreCase = true) == true -> 502
        else -> 500
    }
    return errorResponse(statusCode, error.message.defaultMessage)
}

private fun buildPersonJson(personId: String, documents: List<com.sphereon.data.store.blob.BlobDescriptor>): String {
    val firstDoc = documents.firstOrNull()
    val primaryCode = firstDoc?.metadata?.custom?.get("okd.primaryCode") ?: ""
    val givenName = firstDoc?.metadata?.custom?.get("okd.givenName") ?: ""
    val surname = firstDoc?.metadata?.custom?.get("okd.surname") ?: ""

    val docEntries = documents.map { desc ->
        buildJsonObject {
            put("documentId", JsonPrimitive(desc.path))
            put("documentType", JsonPrimitive(desc.metadata.custom["okd.documentType"] ?: "unknown"))
            put("documentName", JsonPrimitive(desc.filename ?: ""))
        }
    }

    val personJson = buildJsonObject {
        put("personId", JsonPrimitive(personId))
        put("primaryCode", buildJsonObject {
            put("codeType", JsonPrimitive("studentNumber"))
            put("code", JsonPrimitive(primaryCode))
        })
        put("givenName", JsonPrimitive(givenName))
        put("surname", JsonPrimitive(surname))
        put("consumers", buildJsonArray {
            add(buildJsonObject {
                put("consumerKey", JsonPrimitive("nl-okd"))
                put("personDocuments", buildJsonArray {
                    docEntries.forEach { add(it) }
                })
            })
        })
    }

    return personJson.toString()
}
