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

package com.sphereon.data.store.blob.http

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobInfoType
import com.sphereon.data.store.blob.BlobMetadata
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.BlobStoreError
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.ListResult
import com.sphereon.data.store.blob.MetadataSearchQuery
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.ResolvedBlobInfo
import com.sphereon.data.store.blob.TempUrlOptions
import com.sphereon.data.store.blob.TempUrlResult
import com.sphereon.data.store.blob.cas.ContentAddress
import com.sphereon.data.store.blob.cas.ContentAddressDescriptor
import com.sphereon.data.store.blob.command.BlobDeleteOutput
import com.sphereon.data.store.blob.command.CasVerifyOutput
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * HTTP client implementing [BlobService] by calling service-data's blob REST API.
 *
 * Endpoint mapping follows [BlobStoreHttpAdapterDescriptors]:
 * - PUT `/{storeId}/blobs/{path}` — store blob
 * - GET `/{storeId}/blobs/{path}/content` — download raw bytes
 * - GET `/{storeId}/blobs/{path}/stat` — metadata only
 * - DELETE `/{storeId}/blobs/{path}` — delete
 * - GET `/{storeId}/blobs` — list
 * - POST `/{storeId}/blobs/{path}/copy` — copy
 * - POST `/{storeId}/blobs/{path}/move` — move
 */
class HttpBlobServiceClient(
    private val config: HttpBlobServiceClientConfig,
    private val http: HttpClient,
    private val execution: SessionExecution?,
) : BlobService {
    private val jsonParser = Json { ignoreUnknownKeys = true }

    override fun defaultStoreId(): String = config.defaultStoreId

    private fun resolveStoreId(storeId: String?): String = storeId?.takeIf { it.isNotBlank() && it != "default" } ?: config.defaultStoreId

    private fun blobsUrl(
        storeId: String?,
        path: String? = null,
    ): String {
        val store = resolveStoreId(storeId)
        val base = "${config.baseUrl}/api/blob-stores/$store/blobs"
        return if (path != null) {
            "$base/$path"
        } else {
            base
        }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuth() {
        val authConfig = config.auth

        when (authConfig.mode) {
            HttpBlobAuthMode.BEARER -> {
                val jwt =
                    try {
                        execution
                            ?.sessionContext
                            ?.context
                            ?.secureDetails
                            ?.jwt
                    } catch (_: Exception) {
                        // Ignored: session context not available for JWT extraction
                        null
                    }
                if (jwt != null) {
                    header("Authorization", "Bearer $jwt")
                }
            }

            HttpBlobAuthMode.STATIC_TOKEN -> {
                val token = authConfig.token
                if (token != null) {
                    header("Authorization", "Bearer $token")
                }
            }

            HttpBlobAuthMode.CLIENT_CREDENTIALS -> {
                // Ktor Auth plugin handles token acquisition automatically
            }
        }

        // Fallback tenant header for dev/anonymous mode
        val tenantHeader = authConfig.tenantHeader
        if (tenantHeader != null && execution != null) {
            try {
                val tenantId = execution.sessionContext.context.tenant.tenantId
                if (tenantId.isNotBlank() && tenantId != "<anonymous>") {
                    header(tenantHeader, tenantId)
                }
            } catch (_: Exception) {
                // Ignored: no session context available for tenant header
            }
        }
    }

    // ── Standard CRUD ──────────────────────────────────────────────────

    override suspend fun storeBlob(
        target: BlobInfo,
        data: ByteArray,
        options: PutOptions,
    ): IdkResult<BlobDescriptor, IdkError> {
        val path = target.path ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "BlobInfo.path is required for storeBlob"))
        val metadata = target.toBlobMetadata()
        return try {
            val response =
                http.put(blobsUrl(target.storeId, path)) {
                    contentType(ContentType.Application.Json)
                    setBody(
                        BlobApiPutBody(
                            tenantId = target.tenantId ?: "default",
                            path = path,
                            dataBase64 = data.encodeToBase64(),
                            metadata = metadata,
                            options = options,
                        ),
                    )
                    applyAuth()
                }
            if (!response.status.isSuccess()) {
                return Err(mapHttpError(response, path))
            }
            Ok(response.body<BlobDescriptor>())
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "HTTP blob store failed for $path: ${expected.message}", exception = expected, code = "BLOB_HTTP_PUT_FAILED"))
        }
    }

    override suspend fun getBlob(info: BlobInfoType): IdkResult<ResolvedBlobInfo, IdkError> {
        if (info is ResolvedBlobInfo) {
            return Ok(info)
        }
        val blobInfo = info.toBlobInfo()
        val path = blobInfo.path ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "BlobInfo.path is required for getBlob"))
        return try {
            val response =
                http.get("${blobsUrl(blobInfo.storeId, path)}/content") {
                    applyAuth()
                }
            if (!response.status.isSuccess()) {
                return Err(mapHttpError(response, path))
            }
            val data = response.bodyAsBytes()
            val responseContentType = response.contentType()?.toString()
            val contentLength = response.contentLength() ?: data.size.toLong()
            val filename = extractFilename(response) ?: path.substringAfterLast('/')
            val descriptor =
                BlobDescriptor(
                    path = path,
                    storeId = resolveStoreId(blobInfo.storeId),
                    sizeBytes = contentLength,
                    contentType = responseContentType,
                    filename = filename,
                )
            Ok(ResolvedBlobInfo.fromContent(blobInfo, data, descriptor))
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "HTTP blob get failed for $path: ${expected.message}", exception = expected, code = "BLOB_HTTP_GET_FAILED"))
        }
    }

    override suspend fun getBlobInfo(info: BlobInfoType): IdkResult<BlobDescriptor, IdkError> {
        val blobInfo = info.toBlobInfo()
        val path = blobInfo.path ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "BlobInfo.path is required for getBlobInfo"))
        return try {
            val response =
                http.get("${blobsUrl(blobInfo.storeId, path)}/stat") {
                    applyAuth()
                }
            if (!response.status.isSuccess()) {
                return Err(mapHttpError(response, path))
            }
            Ok(response.body<BlobDescriptor>())
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "HTTP blob stat failed for $path: ${expected.message}", exception = expected, code = "BLOB_HTTP_STAT_FAILED"))
        }
    }

    override suspend fun deleteBlob(info: BlobInfoType): IdkResult<Boolean, IdkError> {
        val blobInfo = info.toBlobInfo()
        val path = blobInfo.path ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "BlobInfo.path is required for deleteBlob"))
        return try {
            val response =
                http.delete(blobsUrl(blobInfo.storeId, path)) {
                    applyAuth()
                }
            if (response.status == HttpStatusCode.NotFound) {
                return Ok(false)
            }
            if (!response.status.isSuccess()) {
                return Err(mapHttpError(response, path))
            }
            val output = response.body<BlobDeleteOutput>()
            Ok(output.deleted)
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "HTTP blob delete failed for $path: ${expected.message}", exception = expected, code = "BLOB_HTTP_DELETE_FAILED"))
        }
    }

    override suspend fun listBlobs(
        info: BlobInfo,
        options: ListOptions,
    ): IdkResult<ListResult, IdkError> {
        return try {
            val response =
                http.get(blobsUrl(info.storeId)) {
                    options.prefix?.let { parameter("prefix", it) }
                    options.pageToken?.let { parameter("pageToken", it) }
                    parameter("maxResults", options.maxResults)
                    if (options.recursive) {
                        parameter("recursive", true)
                    }
                    applyAuth()
                }
            if (!response.status.isSuccess()) {
                return Err(mapHttpError(response, "list"))
            }
            Ok(response.body<ListResult>())
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "HTTP blob list failed: ${expected.message}", exception = expected, code = "BLOB_HTTP_LIST_FAILED"))
        }
    }

    override suspend fun copyBlob(
        source: BlobInfoType,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> {
        val sourceInfo = source.toBlobInfo()
        val sourcePath = sourceInfo.path ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Source BlobInfo.path is required for copyBlob"))
        val destinationPath = destination.path ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Destination BlobInfo.path is required for copyBlob"))
        return try {
            val response =
                http.post("${blobsUrl(sourceInfo.storeId, sourcePath)}/copy") {
                    contentType(ContentType.Application.Json)
                    setBody(BlobCopyMoveBody(destination = destinationPath))
                    applyAuth()
                }
            if (!response.status.isSuccess()) {
                return Err(mapHttpError(response, sourcePath))
            }
            Ok(response.body<BlobDescriptor>())
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "HTTP blob copy failed for $sourcePath: ${expected.message}", exception = expected, code = "BLOB_HTTP_COPY_FAILED"))
        }
    }

    override suspend fun moveBlob(
        source: BlobInfoType,
        destination: BlobInfo,
    ): IdkResult<BlobDescriptor, IdkError> {
        val sourceInfo = source.toBlobInfo()
        val sourcePath = sourceInfo.path ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Source BlobInfo.path is required for moveBlob"))
        val destinationPath = destination.path ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Destination BlobInfo.path is required for moveBlob"))
        return try {
            val response =
                http.post("${blobsUrl(sourceInfo.storeId, sourcePath)}/move") {
                    contentType(ContentType.Application.Json)
                    setBody(BlobCopyMoveBody(destination = destinationPath))
                    applyAuth()
                }
            if (!response.status.isSuccess()) {
                return Err(mapHttpError(response, sourcePath))
            }
            Ok(response.body<BlobDescriptor>())
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "HTTP blob move failed for $sourcePath: ${expected.message}", exception = expected, code = "BLOB_HTTP_MOVE_FAILED"))
        }
    }

    // ── CAS operations ─────────────────────────────────────────────────

    override suspend fun casStore(
        info: BlobInfo,
        data: ByteArray,
        algorithm: DigestAlg,
    ): IdkResult<ContentAddressDescriptor, IdkError> {
        return try {
            val response =
                http.post("${config.baseUrl}/api/commands/blob.cas.store") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        CasStoreBody(
                            tenantId = info.tenantId ?: "default",
                            dataBase64 = data.encodeToBase64(),
                            algorithm = algorithm,
                            storeId = info.storeId,
                            metadata = info.toBlobMetadata(),
                        ),
                    )
                    applyAuth()
                }
            if (!response.status.isSuccess()) {
                return Err(mapHttpError(response, "cas.store"))
            }
            Ok(response.body<ContentAddressDescriptor>())
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "HTTP CAS store failed: ${expected.message}", exception = expected, code = "BLOB_HTTP_CAS_STORE_FAILED"))
        }
    }

    override suspend fun casGet(
        info: BlobInfo,
        address: ContentAddress,
    ): IdkResult<ResolvedBlobInfo, IdkError> {
        return try {
            val response =
                http.post("${config.baseUrl}/api/commands/blob.cas.get") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        CasGetBody(
                            tenantId = info.tenantId ?: "default",
                            addressMultibase = address.toMultibaseString(),
                            storeId = info.storeId,
                        ),
                    )
                    applyAuth()
                }
            if (!response.status.isSuccess()) {
                return Err(mapHttpError(response, "cas.get"))
            }
            Ok(response.body<ResolvedBlobInfo>())
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "HTTP CAS get failed: ${expected.message}", exception = expected, code = "BLOB_HTTP_CAS_GET_FAILED"))
        }
    }

    override suspend fun casVerify(
        info: BlobInfo,
        address: ContentAddress,
    ): IdkResult<Boolean, IdkError> {
        return try {
            val response =
                http.post("${config.baseUrl}/api/commands/blob.cas.verify") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        CasGetBody(
                            tenantId = info.tenantId ?: "default",
                            addressMultibase = address.toMultibaseString(),
                            storeId = info.storeId,
                        ),
                    )
                    applyAuth()
                }
            if (!response.status.isSuccess()) {
                return Err(mapHttpError(response, "cas.verify"))
            }
            val output = response.body<CasVerifyOutput>()
            Ok(output.valid)
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "HTTP CAS verify failed: ${expected.message}", exception = expected, code = "BLOB_HTTP_CAS_VERIFY_FAILED"))
        }
    }

    // ── Metadata search ────────────────────────────────────────────────

    override suspend fun findByMetadata(
        info: BlobInfo,
        query: MetadataSearchQuery,
    ): IdkResult<List<BlobDescriptor>, IdkError> {
        return try {
            val response =
                http.post("${config.baseUrl}/api/commands/blob.metadata.search") {
                    contentType(ContentType.Application.Json)
                    setBody(MetadataSearchBody(tenantId = info.tenantId ?: "default", query = query))
                    applyAuth()
                }
            if (!response.status.isSuccess()) {
                return Err(mapHttpError(response, "metadata.search"))
            }
            Ok(response.body<List<BlobDescriptor>>())
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "HTTP metadata search failed: ${expected.message}", exception = expected, code = "BLOB_HTTP_SEARCH_FAILED"))
        }
    }

    // ── Temp URLs ──────────────────────────────────────────────────────

    override suspend fun createTempUrl(
        info: BlobInfoType,
        options: TempUrlOptions,
    ): IdkResult<TempUrlResult, IdkError> {
        val blobInfo = info.toBlobInfo()
        val path = blobInfo.path ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "BlobInfo.path is required for createTempUrl"))
        return try {
            val response =
                http.post("${blobsUrl(blobInfo.storeId, path)}/temp-url") {
                    contentType(ContentType.Application.Json)
                    setBody(TempUrlBody(tenantId = blobInfo.tenantId ?: "default", options = options))
                    applyAuth()
                }
            if (!response.status.isSuccess()) {
                return Err(mapHttpError(response, path))
            }
            Ok(response.body<TempUrlResult>())
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "HTTP createTempUrl failed for $path: ${expected.message}", exception = expected, code = "BLOB_HTTP_TEMP_URL_FAILED"))
        }
    }

    // ── Error mapping ──────────────────────────────────────────────────

    private suspend fun mapHttpError(
        response: HttpResponse,
        context: String,
    ): IdkError {
        val serverMessage = tryParseErrorBody(response)
        val detail = serverMessage ?: "HTTP ${response.status.value} for $context"
        return when (response.status) {
            HttpStatusCode.BadRequest -> {
                IdkError.ILLEGAL_ARGUMENT_ERROR(message = detail)
            }

            HttpStatusCode.Unauthorized -> {
                BlobStoreError.PermissionDenied("Authentication failed: $detail").toIdkError()
            }

            HttpStatusCode.Forbidden -> {
                BlobStoreError.PermissionDenied("Access denied: $detail").toIdkError()
            }

            HttpStatusCode.NotFound -> {
                BlobStoreError.NotFound(context).toIdkError()
            }

            HttpStatusCode.Conflict -> {
                BlobStoreError.AlreadyExists(context).toIdkError()
            }

            HttpStatusCode.PreconditionFailed -> {
                BlobStoreError.PreconditionFailed(detail).toIdkError()
            }

            HttpStatusCode.PayloadTooLarge -> {
                BlobStoreError.QuotaExceeded(detail).toIdkError()
            }

            HttpStatusCode.TooManyRequests -> {
                BlobStoreError.BackendError("Rate limited: $detail").toIdkError()
            }

            else -> {
                BlobStoreError.BackendError(detail).toIdkError()
            }
        }
    }

    private suspend fun tryParseErrorBody(response: HttpResponse): String? {
        return try {
            val body = response.bodyAsText()
            if (body.isBlank()) {
                return null
            }
            val errorObj = jsonParser.decodeFromString<ErrorBody>(body)
            errorObj.message ?: errorObj.error
        } catch (_: Exception) {
            // Ignored: error response body could not be parsed
            null
        }
    }

    private fun extractFilename(response: HttpResponse): String? {
        val disposition = response.headers["Content-Disposition"] ?: return null
        val match = Regex("""filename="?([^";\s]+)"?""").find(disposition)
        return match?.groupValues?.get(1)
    }
}

// ── Internal request/response DTOs ─────────────────────────────────────

@Serializable
internal data class BlobApiPutBody(
    val tenantId: String,
    val path: String,
    val dataBase64: String,
    val metadata: BlobMetadata = BlobMetadata.EMPTY,
    val options: PutOptions = PutOptions.DEFAULT,
)

@Serializable
internal data class BlobCopyMoveBody(
    val destination: String,
)

@Serializable
internal data class CasStoreBody(
    val tenantId: String,
    val dataBase64: String,
    val algorithm: DigestAlg = DigestAlg.SHA256,
    val storeId: String? = null,
    val metadata: BlobMetadata = BlobMetadata.EMPTY,
)

@Serializable
internal data class CasGetBody(
    val tenantId: String,
    val addressMultibase: String,
    val storeId: String? = null,
)

@Serializable
internal data class MetadataSearchBody(
    val tenantId: String,
    val query: MetadataSearchQuery,
)

@Serializable
internal data class TempUrlBody(
    val tenantId: String,
    val options: TempUrlOptions = TempUrlOptions.DEFAULT,
)

@Serializable
internal data class ErrorBody(
    val message: String? = null,
    val error: String? = null,
)
