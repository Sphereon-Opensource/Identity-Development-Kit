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

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.store.schema.registry.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.PutOptions
import com.sphereon.data.store.schema.registry.CreateSchemaInput
import com.sphereon.data.store.schema.registry.ImportExternalInput
import com.sphereon.data.store.schema.registry.ResolvedSchemaContent
import com.sphereon.data.store.schema.registry.SchemaHostingMode
import com.sphereon.data.store.schema.registry.SchemaRecord
import com.sphereon.data.store.schema.registry.SchemaRecordFilter
import com.sphereon.data.store.schema.registry.SchemaRecordOrigin
import com.sphereon.data.store.schema.registry.SchemaRecordProvenance
import com.sphereon.data.store.schema.registry.SchemaRegistryService
import com.sphereon.data.store.schema.registry.UpdateSchemaInput
import com.sphereon.data.store.schema.registry.persistence.SchemaRecordRepository
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Default IDK implementation of [SchemaRegistryService].
 *
 * Coordinates [SchemaRecordRepository] (metadata) and [BlobService] (content storage).
 * No versioning — each schema has one content blob. EDK replaces this with a versioned implementation.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SchemaRegistryService>())
class DefaultSchemaRegistryService(
    private val repository: SchemaRecordRepository,
    private val blobService: BlobService,
    private val externalFetcher: SchemaExternalFetcher,
) : SchemaRegistryService {
    private val putOptionsWithHash = PutOptions(digestAlgorithm = DigestAlg.SHA256)

    override suspend fun createSchema(
        tenantId: String,
        input: CreateSchemaInput,
    ): IdkResult<SchemaRecord, IdkError> {
        val existing = repository.findByNamespaceName(tenantId, input.namespace, input.name)
        if (existing != null) {
            return Err(IdkError.ALREADY_EXISTS_ERROR(message = "Schema '${input.namespace}/${input.name}' already exists"))
        }

        val contentBytes =
            resolveContentBytes(input.contentBase64, input.contentText)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Either contentBase64 or contentText must be provided"))

        val contentType = input.contentType ?: input.schemaType.defaultContentType
        val schemaId = Uuid.random()
        val now = Clock.System.now()

        val blobPath = contentBlobPath(input.namespace, input.name, input.schemaType.fileExtension)
        val storeResult =
            blobService.storeBlob(
                target = BlobInfo(path = blobPath, tenantId = tenantId, contentType = contentType),
                data = contentBytes,
                options = putOptionsWithHash,
            )
        if (storeResult.isErr) {
            return Err(storeResult.error)
        }

        val descriptor = storeResult.value
        val record =
            SchemaRecord(
                id = schemaId,
                tenantId = tenantId,
                namespace = input.namespace,
                name = input.name,
                schemaType = input.schemaType,
                hostingMode = input.hostingMode,
                description = input.description,
                contentType = contentType,
                contentHash = descriptor.contentHash,
                sizeBytes = descriptor.sizeBytes,
                createdAt = now,
                updatedAt = now,
                provenance = input.provenance,
            )
        repository.create(record)
        return Ok(record)
    }

    override suspend fun getSchema(
        tenantId: String,
        schemaId: Uuid,
    ): IdkResult<SchemaRecord, IdkError> {
        val record =
            repository.findById(tenantId, schemaId)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Schema not found: $schemaId"))
        return Ok(record)
    }

    override suspend fun findSchemaByName(
        tenantId: String,
        namespace: String,
        name: String,
    ): IdkResult<SchemaRecord, IdkError> {
        val record =
            repository.findByNamespaceName(tenantId, namespace, name)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Schema not found: $namespace/$name"))
        return Ok(record)
    }

    override suspend fun listSchemas(
        tenantId: String,
        filter: SchemaRecordFilter,
    ): IdkResult<List<SchemaRecord>, IdkError> = Ok(repository.findAll(tenantId, filter))

    override suspend fun updateSchema(
        tenantId: String,
        schemaId: Uuid,
        input: UpdateSchemaInput,
    ): IdkResult<SchemaRecord, IdkError> {
        val existing =
            repository.findById(tenantId, schemaId)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Schema not found: $schemaId"))

        val contentBytes = resolveContentBytes(input.contentBase64, input.contentText)
        val now = Clock.System.now()

        var updated =
            existing.copy(
                description = input.description ?: existing.description,
                hostingMode = input.hostingMode ?: existing.hostingMode,
                updatedAt = now,
            )

        if (contentBytes != null) {
            val blobPath = contentBlobPath(existing.namespace, existing.name, existing.schemaType.fileExtension)
            val storeResult =
                blobService.storeBlob(
                    target = BlobInfo(path = blobPath, tenantId = tenantId, contentType = existing.contentType),
                    data = contentBytes,
                    options = putOptionsWithHash,
                )
            if (storeResult.isErr) {
                return Err(storeResult.error)
            }

            val descriptor = storeResult.value
            updated =
                updated.copy(
                    contentHash = descriptor.contentHash,
                    sizeBytes = descriptor.sizeBytes,
                )
        }

        repository.update(updated)
        return Ok(updated)
    }

    override suspend fun deleteSchema(
        tenantId: String,
        schemaId: Uuid,
    ): IdkResult<Boolean, IdkError> {
        val existing =
            repository.findById(tenantId, schemaId)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Schema not found: $schemaId"))

        val blobPath = contentBlobPath(existing.namespace, existing.name, existing.schemaType.fileExtension)
        blobService.deleteBlob(BlobInfo(path = blobPath, tenantId = tenantId))
        repository.delete(tenantId, schemaId)
        return Ok(true)
    }

    override suspend fun getContent(
        tenantId: String,
        schemaId: Uuid,
    ): IdkResult<ResolvedSchemaContent, IdkError> {
        val record =
            repository.findById(tenantId, schemaId)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Schema not found: $schemaId"))
        return resolveContent(tenantId, record)
    }

    override suspend fun resolveByPath(
        tenantId: String,
        namespace: String,
        name: String,
    ): IdkResult<ResolvedSchemaContent, IdkError> {
        val record =
            repository.findByNamespaceName(tenantId, namespace, name)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Schema not found: $namespace/$name"))
        return resolveContent(tenantId, record)
    }

    override suspend fun importExternal(
        tenantId: String,
        input: ImportExternalInput,
    ): IdkResult<SchemaRecord, IdkError> {
        val existing = repository.findByNamespaceName(tenantId, input.namespace, input.name)
        if (existing != null) {
            return Err(IdkError.ALREADY_EXISTS_ERROR(message = "Schema '${input.namespace}/${input.name}' already exists"))
        }

        val fetchResult = externalFetcher.fetch(input.sourceUrl)
        if (fetchResult.isErr) {
            return Err(fetchResult.error)
        }
        val fetched = fetchResult.value

        val contentType = fetched.contentType ?: input.schemaType.defaultContentType
        val schemaId = Uuid.random()
        val now = Clock.System.now()

        val blobPath = contentBlobPath(input.namespace, input.name, input.schemaType.fileExtension)
        val storeResult =
            blobService.storeBlob(
                target = BlobInfo(path = blobPath, tenantId = tenantId, contentType = contentType),
                data = fetched.data,
                options = putOptionsWithHash,
            )
        if (storeResult.isErr) {
            return Err(storeResult.error)
        }

        val descriptor = storeResult.value
        val resolvedProvenance =
            input.provenance
                ?: SchemaRecordProvenance(
                    origin = SchemaRecordOrigin.EXTERNAL,
                    sourceUrl = input.sourceUrl,
                )
        val record =
            SchemaRecord(
                id = schemaId,
                tenantId = tenantId,
                namespace = input.namespace,
                name = input.name,
                schemaType = input.schemaType,
                hostingMode = SchemaHostingMode.CACHED_EXTERNAL,
                sourceUrl = input.sourceUrl,
                description = input.description,
                contentType = contentType,
                contentHash = descriptor.contentHash,
                sizeBytes = descriptor.sizeBytes,
                sourceEtag = fetched.etag,
                createdAt = now,
                updatedAt = now,
                provenance = resolvedProvenance,
            )
        repository.create(record)
        return Ok(record)
    }

    override suspend fun refreshCached(
        tenantId: String,
        schemaId: Uuid,
    ): IdkResult<SchemaRecord, IdkError> {
        val existing =
            repository.findById(tenantId, schemaId)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Schema not found: $schemaId"))

        if (existing.hostingMode != SchemaHostingMode.CACHED_EXTERNAL) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Schema is not a cached external schema"))
        }
        val sourceUrl =
            existing.sourceUrl
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Schema has no source URL"))

        // Use upstream ETag for conditional refresh, not our computed contentHash
        val fetchResult = externalFetcher.fetch(sourceUrl, ifNoneMatch = existing.sourceEtag)
        if (fetchResult.isErr) {
            return Err(fetchResult.error)
        }
        val fetched = fetchResult.value

        if (fetched.notModified) {
            return Ok(existing) // no change
        }

        val blobPath = contentBlobPath(existing.namespace, existing.name, existing.schemaType.fileExtension)
        val storeResult =
            blobService.storeBlob(
                target = BlobInfo(path = blobPath, tenantId = tenantId, contentType = existing.contentType),
                data = fetched.data,
                options = putOptionsWithHash,
            )
        if (storeResult.isErr) {
            return Err(storeResult.error)
        }

        val descriptor = storeResult.value
        val now = Clock.System.now()
        val updated =
            existing.copy(
                contentHash = descriptor.contentHash,
                sizeBytes = descriptor.sizeBytes,
                sourceEtag = fetched.etag ?: existing.sourceEtag,
                updatedAt = now,
            )
        repository.update(updated)
        return Ok(updated)
    }

    private suspend fun resolveContent(
        tenantId: String,
        record: SchemaRecord,
    ): IdkResult<ResolvedSchemaContent, IdkError> {
        val blobPath = contentBlobPath(record.namespace, record.name, record.schemaType.fileExtension)
        val blobResult = blobService.getBlob(BlobInfo(path = blobPath, tenantId = tenantId))
        if (blobResult.isErr) {
            return Err(blobResult.error)
        }

        return Ok(
            ResolvedSchemaContent(
                data = blobResult.value.data,
                contentType = record.contentType,
                schemaRecord = record,
            ),
        )
    }

    private fun contentBlobPath(
        namespace: String,
        name: String,
        extension: String,
    ): String = "schemas/${namespace.ifEmpty { "_default" }}/$name/content$extension"

    private fun resolveContentBytes(
        contentBase64: String?,
        contentText: String?,
    ): ByteArray? =
        when {
            contentBase64 != null -> contentBase64.decodeFromBase64()
            contentText != null -> contentText.encodeToByteArray()
            else -> null
        }
}
