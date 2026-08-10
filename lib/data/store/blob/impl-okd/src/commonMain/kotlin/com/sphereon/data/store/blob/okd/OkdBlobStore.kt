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

package com.sphereon.data.store.blob.okd

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.OpaqueSecretResolver
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobStore
import com.sphereon.data.store.blob.BlobStoreCapabilities
import com.sphereon.data.store.blob.BlobStoreError
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.ListResult
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.blob.ResolvedBlobInfo
import com.sphereon.data.store.blob.TempUrlOptions
import com.sphereon.data.store.blob.TempUrlResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Blob store implementation that delegates to an external OKD-compliant DMS via HTTP.
 *
 * Maps blob store operations to OKD API endpoints:
 * - `put(info, data)` -> `PATCH /documents/{documentId}` (update binary content)
 * - `put(data)` -> server-assigned upload (generates UUID path)
 * - `get(info)` -> `GET /documents/{documentId}` (download binary)
 * - `delete(info)` -> `DELETE /documents/{documentId}`
 * - `stat(info)` -> `GET /documents/{documentId}/metadata`
 * - `createTempUrl(info)` -> returns download URL (OKD `documentTempDownloadUrl` from metadata)
 *
 * The `info.path` is the OKD `dmsDocumentId` (UUID).
 *
 * Authentication is derived from the authenticated session or resolved from an opaque secret
 * handle immediately before use. Serialized configuration never contains credential plaintext.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("OkdBlobStore", exact = true)
class OkdBlobStore(
    private val config: OkdBlobStoreConfig,
    private val http: HttpClient,
    private val execution: SessionExecution?,
    private val opaqueSecretResolver: OpaqueSecretResolver,
) : BlobStore {
    override val schemeId: String = OkdBlobStoreConfig.BACKEND_ID

    override val capabilities: BlobStoreCapabilities =
        BlobStoreCapabilities(
            supportsTempUrls = true,
        )

    private fun documentUrl(documentId: String): String = "${config.baseUrl}/documents/$documentId"

    /**
     * Apply authentication headers to outbound OKD requests.
     *
     * Follows the RestKmsProvider pattern:
     * - BEARER: bearer token from the authenticated session
     * - STATIC_TOKEN: opaque secret resolved immediately before the request
     * - CLIENT_CREDENTIALS: Ktor Auth plugin handles it (installed by factory)
     */
    private suspend fun resolveAuthorizationToken(): IdkResult<String?, IdkError> =
        when (config.auth.mode) {
            OkdAuthMode.BEARER -> {
                val jwt =
                    try {
                        execution
                            ?.sessionContext
                            ?.context
                            ?.secureDetails
                            ?.jwt
                    } catch (_: Exception) {
                        null
                    }
                if (jwt.isNullOrBlank()) {
                    Err(IdkError.FORBIDDEN_ERROR(message = "Authenticated session token is unavailable"))
                } else {
                    Ok(jwt)
                }
            }

            OkdAuthMode.STATIC_TOKEN -> {
                val secretId = config.auth.tokenSecretId
                if (secretId == null) {
                    Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "OKD tokenSecretId is required"))
                } else {
                    secretId.requireOkdOpaqueSecretId("tokenSecretId")
                    val result = opaqueSecretResolver.resolve(secretId)
                    if (result.isErr || result.value.isBlank()) {
                        Err(IdkError.SERVICE_UNAVAILABLE_ERROR(message = "OKD credential is unavailable"))
                    } else {
                        Ok(result.value)
                    }
                }
            }

            OkdAuthMode.CLIENT_CREDENTIALS -> Ok(null)
        }

    private fun io.ktor.client.request.HttpRequestBuilder.applyAuth(authorizationToken: String?) {
        if (authorizationToken != null) {
            header(HttpHeaders.Authorization, "Bearer $authorizationToken")
        }
    }

    // -- BlobStore operations --

    override suspend fun put(
        target: BlobInfo,
        data: ByteArray,
        options: PutOptions,
    ): IdkResult<BlobDescriptor, IdkError> {
        val path =
            target.path ?: kotlin.uuid.Uuid
                .random()
                .toString()
        val authorization = resolveAuthorizationToken()
        if (authorization.isErr) return Err(authorization.error)
        return try {
            val response =
                http.patch(documentUrl(path)) {
                    contentType(ContentType.Application.OctetStream)
                    setBody(data)
                    applyAuth(authorization.value)
                }
            if (!response.status.isSuccess()) {
                return Err(mapHttpError(response.status, path))
            }
            // OKD PATCH returns 204 — stat to get the descriptor
            stat(target.copy(path = path))
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "OKD put failed for $path: ${expected.message}", exception = expected, code = "BLOB_OKD_PUT_FAILED"))
        }
    }

    override suspend fun get(info: BlobInfo): IdkResult<ResolvedBlobInfo, IdkError> {
        val path = info.path!!
        val authorization = resolveAuthorizationToken()
        if (authorization.isErr) return Err(authorization.error)
        return try {
            val response =
                http.get(documentUrl(path)) {
                    applyAuth(authorization.value)
                }
            if (!response.status.isSuccess()) {
                return Err(mapHttpError(response.status, path))
            }
            val data = response.bodyAsBytes()
            val responseContentType = response.contentType()?.toString()
            val descriptor =
                BlobDescriptor(
                    path = path,
                    storeId = schemeId,
                    sizeBytes = data.size.toLong(),
                    contentType = responseContentType,
                    filename = path,
                )
            Ok(ResolvedBlobInfo.fromContent(info, data, descriptor))
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "OKD get failed for $path: ${expected.message}", exception = expected, code = "BLOB_OKD_GET_FAILED"))
        }
    }

    override suspend fun delete(info: BlobInfo): IdkResult<Boolean, IdkError> {
        val path = info.path!!
        val authorization = resolveAuthorizationToken()
        if (authorization.isErr) return Err(authorization.error)
        return try {
            val response =
                http.delete(documentUrl(path)) {
                    applyAuth(authorization.value)
                }
            if (response.status == HttpStatusCode.NotFound) {
                return Ok(false)
            }
            if (!response.status.isSuccess()) {
                return Err(mapHttpError(response.status, path))
            }
            Ok(true)
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "OKD delete failed for $path: ${expected.message}", exception = expected, code = "BLOB_OKD_DELETE_FAILED"))
        }
    }

    override suspend fun exists(info: BlobInfo): IdkResult<Boolean, IdkError> {
        val statResult = stat(info)
        if (statResult.isErr) {
            if (statResult.error.code == "BLOB_NOT_FOUND") {
                return Ok(false)
            }
            return Err(statResult.error)
        }
        return Ok(true)
    }

    override suspend fun stat(info: BlobInfo): IdkResult<BlobDescriptor, IdkError> {
        val path = info.path!!
        val authorization = resolveAuthorizationToken()
        if (authorization.isErr) return Err(authorization.error)
        return try {
            val response =
                http.get("${documentUrl(path)}/metadata") {
                    applyAuth(authorization.value)
                }
            if (response.status == HttpStatusCode.NotFound) {
                return Err(BlobStoreError.NotFound(path).toIdkError())
            }
            if (!response.status.isSuccess()) {
                return Err(mapHttpError(response.status, path))
            }
            val okdMeta = response.body<com.sphereon.data.store.okd.generated.models.DocumentMetadata>()
            Ok(OkdBlobMapping.fromOkdMetadata(okdMeta, schemeId))
        } catch (expected: Exception) {
            Err(IdkError.fromString(message = "OKD stat failed for $path: ${expected.message}", exception = expected, code = "BLOB_OKD_STAT_FAILED"))
        }
    }

    override suspend fun list(
        info: BlobInfo,
        options: ListOptions,
    ): IdkResult<ListResult, IdkError> {
        // OKD doesn't have a generic document listing endpoint.
        // Documents are listed via person or association queries (relational, out of blob scope).
        return Ok(ListResult(descriptors = emptyList()))
    }

    override suspend fun createTempUrl(
        info: BlobInfo,
        options: TempUrlOptions,
    ): IdkResult<TempUrlResult, IdkError> {
        val path = info.path!!
        val statResult = stat(info)
        if (statResult.isErr) {
            return Err(statResult.error)
        }

        // Use the actual OKD temp download URL if available from metadata
        val descriptor = statResult.value
        val tempUrl = descriptor.metadata.custom["documentTempDownloadUrl"] ?: documentUrl(path)

        val now = Clock.System.now()
        return Ok(
            TempUrlResult(
                url = tempUrl,
                expiresAt = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + options.expiresIn.inWholeMilliseconds),
                method = options.method,
                isPublic = tempUrl != documentUrl(path), // Public if DMS provided a signed URL
            ),
        )
    }

    private fun mapHttpError(
        status: HttpStatusCode,
        path: String,
    ): IdkError =
        when (status) {
            HttpStatusCode.NotFound -> BlobStoreError.NotFound(path).toIdkError()
            HttpStatusCode.Forbidden -> BlobStoreError.PermissionDenied("OKD access denied for $path").toIdkError()
            HttpStatusCode.Unauthorized -> BlobStoreError.PermissionDenied("OKD authentication failed for $path").toIdkError()
            HttpStatusCode.PayloadTooLarge -> BlobStoreError.QuotaExceeded("Document too large: $path").toIdkError()
            HttpStatusCode.TooManyRequests -> BlobStoreError.BackendError("Rate limited by OKD DMS", null).toIdkError()
            else -> BlobStoreError.BackendError("OKD API error ${status.value} for $path", null).toIdkError()
        }
}
