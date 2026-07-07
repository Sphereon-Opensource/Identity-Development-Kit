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

package com.sphereon.data.store.credential.design.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.encodeToHex
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobService
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.credential.design.CredentialDesignService
import com.sphereon.data.store.credential.design.PublicDesignAssetPaths
import com.sphereon.data.store.credential.design.config.CredentialDesignConfigProvider
import com.sphereon.data.store.credential.design.impl.mapper.SdJwtVctDesignMapper
import com.sphereon.data.store.credential.design.impl.resolution.CredentialTemplateDesignProvider
import com.sphereon.data.store.credential.design.impl.resolution.DesignResolutionEngine
import com.sphereon.data.store.credential.design.impl.resolution.JsonLdContextDesignProvider
import com.sphereon.data.store.credential.design.impl.resolution.LocalOverrideDesignProvider
import com.sphereon.data.store.credential.design.impl.resolution.Oid4vciCredentialConfigDesignProvider
import com.sphereon.data.store.credential.design.impl.resolution.Oid4vciIssuerMetadataDesignProvider
import com.sphereon.data.store.credential.design.impl.resolution.PartyStoreDesignProvider
import com.sphereon.data.store.credential.design.impl.resolution.SchemaInferenceDesignProvider
import com.sphereon.data.store.credential.design.impl.resolution.SdJwtVctDesignProvider
import com.sphereon.data.store.credential.design.impl.resolution.W3cRenderMethodDesignProvider
import com.sphereon.data.store.credential.design.model.AssetFilter
import com.sphereon.data.store.asset.model.AssetInfo
import com.sphereon.data.store.asset.model.AssetReference
import com.sphereon.data.store.credential.design.model.CreateCredentialDesignInput
import com.sphereon.data.store.credential.design.model.CreateIssuerDesignInput
import com.sphereon.data.store.credential.design.model.CreateRenderVariantInput
import com.sphereon.data.store.credential.design.model.CreateVerifierDesignInput
import com.sphereon.data.store.credential.design.model.CredentialDesignRecord
import com.sphereon.data.store.credential.design.model.CredentialTypeDescriptor
import com.sphereon.data.store.credential.design.model.CredentialTypeFormat
import com.sphereon.data.store.credential.design.model.DesignAssetType
import com.sphereon.data.store.credential.design.model.DesignBinding
import com.sphereon.data.store.credential.design.model.DesignBindingKey
import com.sphereon.data.store.credential.design.model.DesignFilter
import com.sphereon.data.store.credential.design.model.DesignHostingMode
import com.sphereon.data.store.credential.design.model.DesignSourceType
import com.sphereon.data.store.credential.design.model.EntityLocaleDesign
import com.sphereon.data.store.credential.design.model.GetDesignAssetInput
import com.sphereon.data.store.credential.design.model.ImportExternalDesignInput
import com.sphereon.data.store.credential.design.model.IssuerDesignRecord
import com.sphereon.data.store.credential.design.model.LocalizedCredentialDisplay
import com.sphereon.data.store.credential.design.model.RenderVariantRecord
import com.sphereon.data.store.credential.design.model.ResolveCredentialDesignInput
import com.sphereon.data.store.credential.design.model.ResolveEntityDesignInput
import com.sphereon.data.store.credential.design.model.ResolvedCredentialDesign
import com.sphereon.data.store.credential.design.model.ResolvedDesignAsset
import com.sphereon.data.store.credential.design.model.ResolvedIssuerDesign
import com.sphereon.data.store.credential.design.model.ResolvedVerifierDesign
import com.sphereon.data.store.credential.design.model.SourceSnapshotRecord
import com.sphereon.data.store.credential.design.model.UpdateCredentialDesignInput
import com.sphereon.data.store.credential.design.model.UpdateIssuerDesignInput
import com.sphereon.data.store.credential.design.model.UpdateVerifierDesignInput
import com.sphereon.data.store.credential.design.model.UploadDesignAssetInput
import com.sphereon.data.store.credential.design.model.UploadTenantAssetInput
import com.sphereon.data.store.credential.design.model.VerifierDesignRecord
import com.sphereon.data.store.credential.design.persistence.CredentialDesignRepository
import com.sphereon.data.store.credential.design.persistence.DerivedRenderHintsRepository
import com.sphereon.data.store.credential.design.persistence.IssuerDesignRepository
import com.sphereon.data.store.credential.design.persistence.RenderVariantRepository
import com.sphereon.data.store.credential.design.persistence.SourceSnapshotRepository
import com.sphereon.data.store.credential.design.persistence.VerifierDesignRepository
import com.sphereon.data.store.credential.design.validation.credentialDesignRecordValidator
import com.sphereon.di.session.SessionScope
import com.sphereon.sdjwt.vc.SdJwtVcTypeMetadata
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.konform.validation.Invalid
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Default IDK implementation of [CredentialDesignService].
 *
 * Coordinates repositories (metadata) and [BlobService] (content/asset storage).
 * EDK replaces this with a richer implementation as needed.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CredentialDesignService>())
@ContributesBinding(SessionScope::class, binding = binding<CredentialDesignService?>())
class DefaultCredentialDesignService(
    private val credentialDesignRepository: CredentialDesignRepository,
    private val issuerDesignRepository: IssuerDesignRepository,
    private val verifierDesignRepository: VerifierDesignRepository,
    private val renderVariantRepository: RenderVariantRepository,
    private val sourceSnapshotRepository: SourceSnapshotRepository,
    private val derivedRenderHintsRepository: DerivedRenderHintsRepository,
    private val blobService: BlobService,
    private val externalFetcher: DesignExternalFetcher,
    private val configProvider: CredentialDesignConfigProvider,
) : CredentialDesignService {
    private val importJson = Json { ignoreUnknownKeys = true }
    private val mapper = SdJwtVctDesignMapper()

    /** IDK credential resolution provider chain, ordered by priority (lower = runs first). */
    private val credentialProviders by lazy {
        listOf(
            SchemaInferenceDesignProvider(), // priority 1
            JsonLdContextDesignProvider(), // priority 2
            CredentialTemplateDesignProvider(), // priority 3
            Oid4vciCredentialConfigDesignProvider(), // priority 4
            SdJwtVctDesignProvider(), // priority 5
            W3cRenderMethodDesignProvider(), // priority 6
            LocalOverrideDesignProvider(credentialDesignRepository), // priority 7
        )
    }
    private val credentialEngine by lazy { DesignResolutionEngine(credentialProviders) }

    /** IDK issuer resolution provider chain. */
    private val issuerProviders by lazy {
        listOf(
            Oid4vciIssuerMetadataDesignProvider(), // priority 1
            PartyStoreDesignProvider(), // priority 2
            LocalOverrideDesignProvider(credentialDesignRepository), // priority 3
        )
    }
    private val issuerEngine by lazy { DesignResolutionEngine(issuerProviders) }

    /** IDK verifier resolution provider chain. */
    private val verifierProviders by lazy {
        listOf(
            PartyStoreDesignProvider(), // priority 3
            LocalOverrideDesignProvider(credentialDesignRepository), // priority 6
        )
    }
    private val verifierEngine by lazy { DesignResolutionEngine(verifierProviders) }

    // ---- Credential designs ----

    override suspend fun createCredentialDesign(
        tenantId: String,
        input: CreateCredentialDesignInput,
    ): IdkResult<CredentialDesignRecord, IdkError> {
        val now = Clock.System.now()
        val record =
            CredentialDesignRecord(
                id = Uuid.random(),
                tenantId = tenantId,
                alias = input.alias,
                hostingMode = input.hostingMode,
                credentialType = input.credentialType,
                bindings = input.bindings,
                credentialTemplateId = input.credentialTemplateId,
                issuerDesignId = input.issuerDesignId,
                displays = input.displays,
                claims = input.claims,
                renderVariantIds = input.renderVariantIds,
                createdAt = now,
                updatedAt = now,
            )
        validateCredentialDesignRecord(record)?.let { return it }
        credentialDesignRepository.create(record)
        return Ok(record)
    }

    override suspend fun getCredentialDesign(
        tenantId: String,
        id: Uuid,
    ): IdkResult<CredentialDesignRecord, IdkError> {
        val record =
            credentialDesignRepository.findById(tenantId, id)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Credential design not found: $id"))
        return Ok(record)
    }

    override suspend fun findCredentialDesignByBinding(
        tenantId: String,
        binding: DesignBinding,
    ): IdkResult<List<CredentialDesignRecord>, IdkError> = Ok(credentialDesignRepository.findByBinding(tenantId, binding))

    override suspend fun findCredentialDesignByBindingKey(
        tenantId: String,
        bindingKey: DesignBindingKey,
        bindingValue: String,
    ): IdkResult<List<CredentialDesignRecord>, IdkError> = Ok(credentialDesignRepository.findByBindingKey(tenantId, bindingKey, bindingValue))

    override suspend fun listCredentialDesigns(
        tenantId: String,
        filter: DesignFilter,
    ): IdkResult<List<CredentialDesignRecord>, IdkError> = Ok(credentialDesignRepository.findAll(tenantId, filter))

    override suspend fun updateCredentialDesign(
        tenantId: String,
        id: Uuid,
        input: UpdateCredentialDesignInput,
    ): IdkResult<CredentialDesignRecord, IdkError> {
        val existing =
            credentialDesignRepository.findById(tenantId, id)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Credential design not found: $id"))

        val now = Clock.System.now()
        val updated =
            existing.copy(
                alias = input.alias ?: existing.alias,
                credentialType = input.credentialType ?: existing.credentialType,
                bindings = input.bindings ?: existing.bindings,
                credentialTemplateId = input.credentialTemplateId ?: existing.credentialTemplateId,
                issuerDesignId = input.issuerDesignId ?: existing.issuerDesignId,
                displays = input.displays ?: existing.displays,
                claims = input.claims ?: existing.claims,
                renderVariantIds = input.renderVariantIds ?: existing.renderVariantIds,
                hostingMode = input.hostingMode ?: existing.hostingMode,
                updatedAt = now,
            )
        validateCredentialDesignRecord(updated)?.let { return it }
        credentialDesignRepository.update(updated)
        return Ok(updated)
    }

    override suspend fun deleteCredentialDesign(
        tenantId: String,
        id: Uuid,
    ): IdkResult<Boolean, IdkError> {
        val existing =
            credentialDesignRepository.findById(tenantId, id)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Credential design not found: $id"))
        credentialDesignRepository.delete(tenantId, existing.id)
        return Ok(true)
    }

    // ---- Issuer designs ----

    override suspend fun createIssuerDesign(
        tenantId: String,
        input: CreateIssuerDesignInput,
    ): IdkResult<IssuerDesignRecord, IdkError> {
        val now = Clock.System.now()
        val record =
            IssuerDesignRecord(
                id = Uuid.random(),
                tenantId = tenantId,
                alias = input.alias,
                hostingMode = input.hostingMode,
                bindings = input.bindings,
                partyId = input.partyId,
                displays = input.displays,
                renderVariantIds = input.renderVariantIds,
                createdAt = now,
                updatedAt = now,
            )
        issuerDesignRepository.create(record)
        return Ok(record)
    }

    override suspend fun getIssuerDesign(
        tenantId: String,
        id: Uuid,
    ): IdkResult<IssuerDesignRecord, IdkError> {
        val record =
            issuerDesignRepository.findById(tenantId, id)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Issuer design not found: $id"))
        return Ok(record)
    }

    override suspend fun findIssuerDesignByBinding(
        tenantId: String,
        binding: DesignBinding,
    ): IdkResult<List<IssuerDesignRecord>, IdkError> = Ok(issuerDesignRepository.findByBinding(tenantId, binding))

    override suspend fun findIssuerDesignByBindingKey(
        tenantId: String,
        bindingKey: DesignBindingKey,
        bindingValue: String,
    ): IdkResult<List<IssuerDesignRecord>, IdkError> = Ok(issuerDesignRepository.findByBindingKey(tenantId, bindingKey, bindingValue))

    override suspend fun listIssuerDesigns(
        tenantId: String,
        filter: DesignFilter,
    ): IdkResult<List<IssuerDesignRecord>, IdkError> = Ok(issuerDesignRepository.findAll(tenantId, filter))

    override suspend fun updateIssuerDesign(
        tenantId: String,
        id: Uuid,
        input: UpdateIssuerDesignInput,
    ): IdkResult<IssuerDesignRecord, IdkError> {
        val existing =
            issuerDesignRepository.findById(tenantId, id)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Issuer design not found: $id"))

        val now = Clock.System.now()
        val updated =
            existing.copy(
                alias = input.alias ?: existing.alias,
                bindings = input.bindings ?: existing.bindings,
                partyId = input.partyId ?: existing.partyId,
                displays = input.displays ?: existing.displays,
                renderVariantIds = input.renderVariantIds ?: existing.renderVariantIds,
                updatedAt = now,
            )
        issuerDesignRepository.update(updated)
        return Ok(updated)
    }

    override suspend fun deleteIssuerDesign(
        tenantId: String,
        id: Uuid,
    ): IdkResult<Boolean, IdkError> {
        val existing =
            issuerDesignRepository.findById(tenantId, id)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Issuer design not found: $id"))
        issuerDesignRepository.delete(tenantId, existing.id)
        return Ok(true)
    }

    // ---- Verifier designs ----

    override suspend fun createVerifierDesign(
        tenantId: String,
        input: CreateVerifierDesignInput,
    ): IdkResult<VerifierDesignRecord, IdkError> {
        val now = Clock.System.now()
        val record =
            VerifierDesignRecord(
                id = Uuid.random(),
                tenantId = tenantId,
                alias = input.alias,
                hostingMode = input.hostingMode,
                bindings = input.bindings,
                partyId = input.partyId,
                displays = input.displays,
                renderVariantIds = input.renderVariantIds,
                createdAt = now,
                updatedAt = now,
            )
        verifierDesignRepository.create(record)
        return Ok(record)
    }

    override suspend fun getVerifierDesign(
        tenantId: String,
        id: Uuid,
    ): IdkResult<VerifierDesignRecord, IdkError> {
        val record =
            verifierDesignRepository.findById(tenantId, id)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Verifier design not found: $id"))
        return Ok(record)
    }

    override suspend fun findVerifierDesignByBinding(
        tenantId: String,
        binding: DesignBinding,
    ): IdkResult<List<VerifierDesignRecord>, IdkError> = Ok(verifierDesignRepository.findByBinding(tenantId, binding))

    override suspend fun findVerifierDesignByBindingKey(
        tenantId: String,
        bindingKey: DesignBindingKey,
        bindingValue: String,
    ): IdkResult<List<VerifierDesignRecord>, IdkError> = Ok(verifierDesignRepository.findByBindingKey(tenantId, bindingKey, bindingValue))

    override suspend fun listVerifierDesigns(
        tenantId: String,
        filter: DesignFilter,
    ): IdkResult<List<VerifierDesignRecord>, IdkError> = Ok(verifierDesignRepository.findAll(tenantId, filter))

    override suspend fun updateVerifierDesign(
        tenantId: String,
        id: Uuid,
        input: UpdateVerifierDesignInput,
    ): IdkResult<VerifierDesignRecord, IdkError> {
        val existing =
            verifierDesignRepository.findById(tenantId, id)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Verifier design not found: $id"))

        val now = Clock.System.now()
        val updated =
            existing.copy(
                alias = input.alias ?: existing.alias,
                bindings = input.bindings ?: existing.bindings,
                partyId = input.partyId ?: existing.partyId,
                displays = input.displays ?: existing.displays,
                renderVariantIds = input.renderVariantIds ?: existing.renderVariantIds,
                updatedAt = now,
            )
        verifierDesignRepository.update(updated)
        return Ok(updated)
    }

    override suspend fun deleteVerifierDesign(
        tenantId: String,
        id: Uuid,
    ): IdkResult<Boolean, IdkError> {
        val existing =
            verifierDesignRepository.findById(tenantId, id)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Verifier design not found: $id"))
        verifierDesignRepository.delete(tenantId, existing.id)
        return Ok(true)
    }

    // ---- Render variants ----

    override suspend fun createRenderVariant(
        tenantId: String,
        input: CreateRenderVariantInput,
    ): IdkResult<RenderVariantRecord, IdkError> {
        val record =
            RenderVariantRecord(
                id = Uuid.random(),
                tenantId = tenantId,
                kind = input.kind,
                alias = input.alias,
                localeApplicability = input.localeApplicability,
                logo = input.logo,
                backgroundImage = input.backgroundImage,
                backgroundColor = input.backgroundColor,
                textColor = input.textColor,
                accentColor = input.accentColor,
                svgTemplate = input.svgTemplate,
                w3cRenderMethod = input.w3cRenderMethod,
            )
        renderVariantRepository.create(record)
        return Ok(record)
    }

    override suspend fun getRenderVariant(
        tenantId: String,
        id: Uuid,
    ): IdkResult<RenderVariantRecord, IdkError> {
        val record =
            renderVariantRepository.findById(tenantId, id)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Render variant not found: $id"))
        return Ok(record)
    }

    override suspend fun updateRenderVariant(
        tenantId: String,
        id: Uuid,
        input: CreateRenderVariantInput,
    ): IdkResult<RenderVariantRecord, IdkError> {
        val existing =
            renderVariantRepository.findById(tenantId, id)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Render variant not found: $id"))

        val updated =
            existing.copy(
                kind = input.kind,
                alias = input.alias,
                localeApplicability = input.localeApplicability,
                logo = input.logo,
                backgroundImage = input.backgroundImage,
                backgroundColor = input.backgroundColor,
                textColor = input.textColor,
                accentColor = input.accentColor,
                svgTemplate = input.svgTemplate,
                w3cRenderMethod = input.w3cRenderMethod,
            )
        renderVariantRepository.update(updated)
        return Ok(updated)
    }

    override suspend fun listRenderVariants(
        tenantId: String,
        filter: DesignFilter,
    ): IdkResult<List<RenderVariantRecord>, IdkError> = Ok(renderVariantRepository.findAll(tenantId, filter))

    override suspend fun deleteRenderVariant(
        tenantId: String,
        id: Uuid,
    ): IdkResult<Boolean, IdkError> {
        val existing =
            renderVariantRepository.findById(tenantId, id)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Render variant not found: $id"))
        renderVariantRepository.delete(tenantId, existing.id)
        return Ok(true)
    }

    // ---- Import and refresh ----

    override suspend fun importExternalDesign(
        tenantId: String,
        input: ImportExternalDesignInput,
    ): IdkResult<CredentialDesignRecord, IdkError> {
        val fetchResult = externalFetcher.fetch(input.sourceUrl)
        if (fetchResult.isErr) return Err(fetchResult.error)
        val fetched = fetchResult.value

        val snapshotResult = createSnapshotFromFetch(tenantId, input.sourceType, input.sourceUrl, fetched)
        if (snapshotResult.isErr) return Err(snapshotResult.error)
        val snapshot = snapshotResult.value

        // Normalize using mapper based on source type
        val normalized: CredentialDesignRecord? = normalizeCredentialDesign(fetched, input)

        val now = Clock.System.now()
        val designId = Uuid.random()
        val record =
            if (normalized != null) {
                normalized.copy(
                    id = designId,
                    tenantId = tenantId,
                    alias = input.alias,
                    hostingMode = DesignHostingMode.CACHED_EXTERNAL,
                    bindings = input.bindings,
                    credentialType = normalized.credentialType ?: credentialTypeFromBindings(input.bindings),
                    sourceSnapshotIds = listOf(snapshot.id),
                    createdAt = now,
                    updatedAt = now,
                )
            } else {
                CredentialDesignRecord(
                    id = designId,
                    tenantId = tenantId,
                    alias = input.alias,
                    hostingMode = DesignHostingMode.CACHED_EXTERNAL,
                    bindings = input.bindings,
                    displays = listOf(LocalizedCredentialDisplay(locale = "und", name = input.alias ?: "Imported")),
                    sourceSnapshotIds = listOf(snapshot.id),
                    createdAt = now,
                    updatedAt = now,
                    credentialType = credentialTypeFromBindings(input.bindings),
                )
            }
        validateCredentialDesignRecord(record)?.let { return it }
        credentialDesignRepository.create(record)
        return Ok(record)
    }

    override suspend fun importIssuerDesign(
        tenantId: String,
        input: ImportExternalDesignInput,
    ): IdkResult<IssuerDesignRecord, IdkError> {
        val fetchResult = externalFetcher.fetch(input.sourceUrl)
        if (fetchResult.isErr) return Err(fetchResult.error)
        val fetched = fetchResult.value

        val snapshotResult = createSnapshotFromFetch(tenantId, input.sourceType, input.sourceUrl, fetched)
        if (snapshotResult.isErr) return Err(snapshotResult.error)
        val snapshot = snapshotResult.value

        // Normalize using mapper -- extract entity displays from credential-level mapper output
        val normalizedCredential = normalizeCredentialDesign(fetched, input)
        val entityDisplays =
            normalizedCredential?.displays?.map { d ->
                EntityLocaleDesign(locale = d.locale, displayName = d.name, description = d.description)
            }

        val now = Clock.System.now()
        val designId = Uuid.random()
        val record =
            if (entityDisplays != null && entityDisplays.isNotEmpty()) {
                IssuerDesignRecord(
                    id = designId,
                    tenantId = tenantId,
                    alias = input.alias,
                    hostingMode = DesignHostingMode.CACHED_EXTERNAL,
                    bindings = input.bindings,
                    displays = entityDisplays,
                    sourceSnapshotIds = listOf(snapshot.id),
                    createdAt = now,
                    updatedAt = now,
                )
            } else {
                IssuerDesignRecord(
                    id = designId,
                    tenantId = tenantId,
                    alias = input.alias,
                    hostingMode = DesignHostingMode.CACHED_EXTERNAL,
                    bindings = input.bindings,
                    displays = listOf(EntityLocaleDesign(locale = "", displayName = input.alias ?: "Imported")),
                    sourceSnapshotIds = listOf(snapshot.id),
                    createdAt = now,
                    updatedAt = now,
                )
            }
        issuerDesignRepository.create(record)
        return Ok(record)
    }

    override suspend fun importVerifierDesign(
        tenantId: String,
        input: ImportExternalDesignInput,
    ): IdkResult<VerifierDesignRecord, IdkError> {
        val fetchResult = externalFetcher.fetch(input.sourceUrl)
        if (fetchResult.isErr) return Err(fetchResult.error)
        val fetched = fetchResult.value

        val snapshotResult = createSnapshotFromFetch(tenantId, input.sourceType, input.sourceUrl, fetched)
        if (snapshotResult.isErr) return Err(snapshotResult.error)
        val snapshot = snapshotResult.value

        // Normalize using mapper -- extract entity displays from credential-level mapper output
        val normalizedCredential = normalizeCredentialDesign(fetched, input)
        val entityDisplays =
            normalizedCredential?.displays?.map { d ->
                EntityLocaleDesign(locale = d.locale, displayName = d.name, description = d.description)
            }

        val now = Clock.System.now()
        val designId = Uuid.random()
        val record =
            if (entityDisplays != null && entityDisplays.isNotEmpty()) {
                VerifierDesignRecord(
                    id = designId,
                    tenantId = tenantId,
                    alias = input.alias,
                    hostingMode = DesignHostingMode.CACHED_EXTERNAL,
                    bindings = input.bindings,
                    displays = entityDisplays,
                    sourceSnapshotIds = listOf(snapshot.id),
                    createdAt = now,
                    updatedAt = now,
                )
            } else {
                VerifierDesignRecord(
                    id = designId,
                    tenantId = tenantId,
                    alias = input.alias,
                    hostingMode = DesignHostingMode.CACHED_EXTERNAL,
                    bindings = input.bindings,
                    displays = listOf(EntityLocaleDesign(locale = "", displayName = input.alias ?: "Imported")),
                    sourceSnapshotIds = listOf(snapshot.id),
                    createdAt = now,
                    updatedAt = now,
                )
            }
        verifierDesignRepository.create(record)
        return Ok(record)
    }

    override suspend fun refreshCredentialDesign(
        tenantId: String,
        designId: Uuid,
    ): IdkResult<CredentialDesignRecord, IdkError> {
        val existing =
            credentialDesignRepository.findById(tenantId, designId)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Credential design not found: $designId"))

        if (existing.hostingMode != DesignHostingMode.CACHED_EXTERNAL) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Design is not a cached external design"))
        }

        val latestSnapshot =
            resolveLatestSnapshot(tenantId, existing.sourceSnapshotIds)
                .getOrElse { return Err(it) }

        val sourceUrl =
            latestSnapshot.sourceUrl
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Source snapshot has no source URL"))

        val fetchResult = externalFetcher.fetch(sourceUrl, ifNoneMatch = latestSnapshot.etag)
        if (fetchResult.isErr) return Err(fetchResult.error)
        val fetched = fetchResult.value

        if (fetched.notModified) {
            return Ok(existing)
        }

        val snapshotResult =
            createSnapshotFromFetch(
                tenantId,
                latestSnapshot.sourceType,
                sourceUrl,
                fetched,
                fallbackEtag = latestSnapshot.etag,
            )
        if (snapshotResult.isErr) return Err(snapshotResult.error)
        val newSnapshot = snapshotResult.value

        val updated =
            existing.copy(
                sourceSnapshotIds = existing.sourceSnapshotIds + newSnapshot.id,
                updatedAt = Clock.System.now(),
            )
        validateCredentialDesignRecord(updated)?.let { return it }
        credentialDesignRepository.update(updated)
        return Ok(updated)
    }

    override suspend fun refreshIssuerDesign(
        tenantId: String,
        designId: Uuid,
    ): IdkResult<IssuerDesignRecord, IdkError> {
        val existing =
            issuerDesignRepository.findById(tenantId, designId)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Issuer design not found: $designId"))

        if (existing.hostingMode != DesignHostingMode.CACHED_EXTERNAL) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Design is not a cached external design"))
        }

        val latestSnapshot =
            resolveLatestSnapshot(tenantId, existing.sourceSnapshotIds)
                .getOrElse { return Err(it) }

        val sourceUrl =
            latestSnapshot.sourceUrl
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Source snapshot has no source URL"))

        val fetchResult = externalFetcher.fetch(sourceUrl, ifNoneMatch = latestSnapshot.etag)
        if (fetchResult.isErr) return Err(fetchResult.error)
        val fetched = fetchResult.value

        if (fetched.notModified) {
            return Ok(existing)
        }

        val snapshotResult =
            createSnapshotFromFetch(
                tenantId,
                latestSnapshot.sourceType,
                sourceUrl,
                fetched,
                fallbackEtag = latestSnapshot.etag,
            )
        if (snapshotResult.isErr) return Err(snapshotResult.error)
        val newSnapshot = snapshotResult.value

        val updated =
            existing.copy(
                sourceSnapshotIds = existing.sourceSnapshotIds + newSnapshot.id,
                updatedAt = Clock.System.now(),
            )
        issuerDesignRepository.update(updated)
        return Ok(updated)
    }

    override suspend fun refreshVerifierDesign(
        tenantId: String,
        designId: Uuid,
    ): IdkResult<VerifierDesignRecord, IdkError> {
        val existing =
            verifierDesignRepository.findById(tenantId, designId)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Verifier design not found: $designId"))

        if (existing.hostingMode != DesignHostingMode.CACHED_EXTERNAL) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Design is not a cached external design"))
        }

        val latestSnapshot =
            resolveLatestSnapshot(tenantId, existing.sourceSnapshotIds)
                .getOrElse { return Err(it) }

        val sourceUrl =
            latestSnapshot.sourceUrl
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Source snapshot has no source URL"))

        val fetchResult = externalFetcher.fetch(sourceUrl, ifNoneMatch = latestSnapshot.etag)
        if (fetchResult.isErr) return Err(fetchResult.error)
        val fetched = fetchResult.value

        if (fetched.notModified) {
            return Ok(existing)
        }

        val snapshotResult =
            createSnapshotFromFetch(
                tenantId,
                latestSnapshot.sourceType,
                sourceUrl,
                fetched,
                fallbackEtag = latestSnapshot.etag,
            )
        if (snapshotResult.isErr) return Err(snapshotResult.error)
        val newSnapshot = snapshotResult.value

        val updated =
            existing.copy(
                sourceSnapshotIds = existing.sourceSnapshotIds + newSnapshot.id,
                updatedAt = Clock.System.now(),
            )
        verifierDesignRepository.update(updated)
        return Ok(updated)
    }

    override suspend fun getSourceSnapshot(
        tenantId: String,
        snapshotId: Uuid,
    ): IdkResult<SourceSnapshotRecord, IdkError> {
        val record =
            sourceSnapshotRepository.findById(tenantId, snapshotId)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Source snapshot not found: $snapshotId"))
        return Ok(record)
    }

    override suspend fun refreshSourceSnapshot(
        tenantId: String,
        snapshotId: Uuid,
    ): IdkResult<SourceSnapshotRecord, IdkError> {
        val existing =
            sourceSnapshotRepository.findById(tenantId, snapshotId)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Source snapshot not found: $snapshotId"))

        val sourceUrl =
            existing.sourceUrl
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Source snapshot has no source URL"))

        val fetchResult = externalFetcher.fetch(sourceUrl, ifNoneMatch = existing.etag)
        if (fetchResult.isErr) return Err(fetchResult.error)
        val fetched = fetchResult.value

        if (fetched.notModified) {
            return Ok(existing)
        }

        return createSnapshotFromFetch(
            tenantId,
            existing.sourceType,
            sourceUrl,
            fetched,
            fallbackEtag = existing.etag,
        )
    }

    // ---- Resolution ----

    override suspend fun resolveCredentialDesign(
        tenantId: String,
        input: ResolveCredentialDesignInput,
    ): IdkResult<ResolvedCredentialDesign, IdkError> {
        val design =
            when {
                input.designId != null -> {
                    credentialDesignRepository.findById(tenantId, input.designId!!)
                        ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Credential design not found: ${input.designId}"))
                }

                input.binding != null -> {
                    credentialDesignRepository.findByBinding(tenantId, input.binding!!).firstOrNull()
                        ?: return Err(IdkError.NOT_FOUND_ERROR(message = "No credential design found for binding"))
                }

                input.bindingKey != null && input.bindingValue != null -> {
                    credentialDesignRepository.findByBindingKey(tenantId, input.bindingKey!!, input.bindingValue!!).firstOrNull()
                        ?: return Err(IdkError.NOT_FOUND_ERROR(message = "No credential design found for binding key"))
                }

                else -> {
                    return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Either designId, binding, or bindingKey+bindingValue must be provided"))
                }
            }

        // Use cached IDK-scope provider chain ordered by priority (later providers override earlier ones)
        val resolved = credentialEngine.resolveCredential(tenantId, input, design)
        if (resolved.isErr) return resolved

        // Load render variants from the base design's renderVariantIds and merge with engine output
        val baseVariants = design.renderVariantIds.mapNotNull { renderVariantRepository.findById(tenantId, it) }
        val allVariants = (resolved.value.renderVariants + baseVariants).distinctBy { it.id }

        // Load associated issuer/verifier designs
        val issuerDesign = design.issuerDesignId?.let { issuerDesignRepository.findById(tenantId, it) }

        return Ok(
            resolved.value.copy(
                issuerDesign = issuerDesign,
                renderVariants = allVariants,
            ),
        )
    }

    override suspend fun resolveIssuerDesign(
        tenantId: String,
        input: ResolveEntityDesignInput,
    ): IdkResult<ResolvedIssuerDesign, IdkError> {
        val design =
            when {
                input.designId != null -> {
                    issuerDesignRepository.findById(tenantId, input.designId!!)
                        ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Issuer design not found: ${input.designId}"))
                }

                input.binding != null -> {
                    issuerDesignRepository.findByBinding(tenantId, input.binding!!).firstOrNull()
                        ?: return Err(IdkError.NOT_FOUND_ERROR(message = "No issuer design found for binding"))
                }

                input.bindingKey != null && input.bindingValue != null -> {
                    issuerDesignRepository.findByBindingKey(tenantId, input.bindingKey!!, input.bindingValue!!).firstOrNull()
                        ?: return Err(IdkError.NOT_FOUND_ERROR(message = "No issuer design found for binding key"))
                }

                else -> {
                    return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Either designId, binding, or bindingKey+bindingValue must be provided"))
                }
            }

        // Use cached issuer-scope provider chain
        val resolved = issuerEngine.resolveIssuer(tenantId, input, design)
        if (resolved.isErr) return resolved

        // Load render variants from the base design and merge
        val baseVariants = design.renderVariantIds.mapNotNull { renderVariantRepository.findById(tenantId, it) }
        val allVariants = (resolved.value.renderVariants + baseVariants).distinctBy { it.id }

        return Ok(resolved.value.copy(renderVariants = allVariants))
    }

    override suspend fun resolveVerifierDesign(
        tenantId: String,
        input: ResolveEntityDesignInput,
    ): IdkResult<ResolvedVerifierDesign, IdkError> {
        val design =
            when {
                input.designId != null -> {
                    verifierDesignRepository.findById(tenantId, input.designId!!)
                        ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Verifier design not found: ${input.designId}"))
                }

                input.binding != null -> {
                    verifierDesignRepository.findByBinding(tenantId, input.binding!!).firstOrNull()
                        ?: return Err(IdkError.NOT_FOUND_ERROR(message = "No verifier design found for binding"))
                }

                input.bindingKey != null && input.bindingValue != null -> {
                    verifierDesignRepository.findByBindingKey(tenantId, input.bindingKey!!, input.bindingValue!!).firstOrNull()
                        ?: return Err(IdkError.NOT_FOUND_ERROR(message = "No verifier design found for binding key"))
                }

                else -> {
                    return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Either designId, binding, or bindingKey+bindingValue must be provided"))
                }
            }

        // Use cached verifier-scope provider chain
        val resolved = verifierEngine.resolveVerifier(tenantId, input, design)
        if (resolved.isErr) return resolved

        // Load render variants from the base design and merge
        val baseVariants = design.renderVariantIds.mapNotNull { renderVariantRepository.findById(tenantId, it) }
        val allVariants = (resolved.value.renderVariants + baseVariants).distinctBy { it.id }

        return Ok(resolved.value.copy(renderVariants = allVariants))
    }

    // ---- Assets ----

    override suspend fun uploadDesignAsset(
        tenantId: String,
        input: UploadDesignAssetInput,
    ): IdkResult<AssetReference, IdkError> {
        // Content-address the asset: identical bytes collapse to ONE hash (and ONE public URL),
        // so a wallet that pre-fetches assets downloads byte-identical images exactly once.
        // The locale stays in the DISPLAY metadata, not in the image URL.
        val digest = hash(input.data, DigestAlg.SHA256)
        val hexHash = digest.encodeToHex() // lowercase 64-char hex; URL-safe + snapshot-stable
        val b64 = digest.encodeToBase64() // standard padded base64 for the SRI integrity string

        val blobPath = assetBlobPath(tenantId, hexHash)

        // Dedup: only write when this content is not already stored for the tenant. The content
        // type is persisted with the blob so it can be served back later by hash.
        val existing = blobService.getBlobInfo(BlobInfo(path = blobPath, tenantId = tenantId))
        if (existing.isErr) {
            val storeResult =
                blobService.storeBlob(
                    target = BlobInfo(path = blobPath, tenantId = tenantId, contentType = input.contentType),
                    data = input.data,
                )
            if (storeResult.isErr) return Err(storeResult.error)
        }

        // Store the asset URI RELATIVE (content-addressed under PublicDesignAssetPaths.BASE_PATH).
        // The absolute host is applied at SERVE time from the SAME per-tenant external base the
        // issuer advertises for `credential_issuer` / `vct` (see PublicDesignAssetPaths.toAbsolute),
        // so in multi-tenant gateway mode each tenant's logo URI carries that tenant's host rather
        // than one static configured host. Content-addressing is unchanged — only the host differs.
        val publicUri = PublicDesignAssetPaths.assetPath(hexHash, input.contentType)

        val reference =
            AssetReference(
                uri = publicUri,
                integrity = "sha256-$b64",
                contentType = input.contentType,
                localBlob = BlobInfo(path = blobPath, tenantId = tenantId),
            )
        return Ok(reference)
    }

    override suspend fun listDesignAssets(
        tenantId: String,
        filter: AssetFilter,
    ): IdkResult<List<AssetInfo>, IdkError> {
        // The blob list is filtered by PREFIX (the per-tenant content-addressed asset directory).
        // DefaultBlobService scopes the prefix under the tenant and unscopes the returned paths, so
        // each descriptor.path comes back as "vc-designs/$tenantId/assets/by-hash/<hash>".
        val byHashPrefix = "${assetBlobPath(tenantId, "")}"
        val listResult =
            blobService.listBlobs(
                info = BlobInfo(tenantId = tenantId),
                options = ListOptions(prefix = byHashPrefix, recursive = true),
            )
        if (listResult.isErr) return Err(listResult.error)

        val assets =
            listResult.value.descriptors
                .mapNotNull { descriptor -> descriptor.toAssetInfo() }
                .filter { asset -> assetMatchesFilter(asset, filter) }
        return Ok(assets)
    }

    override suspend fun uploadTenantAsset(
        tenantId: String,
        input: UploadTenantAssetInput,
    ): IdkResult<AssetReference, IdkError> {
        // Same content-addressing/dedup logic as [uploadDesignAsset], minus designId/locale: the
        // asset is shared tenant-wide and keyed solely by the SHA-256 of its bytes.
        val digest = hash(input.data, DigestAlg.SHA256)
        val hexHash = digest.encodeToHex()
        val b64 = digest.encodeToBase64()

        val blobPath = assetBlobPath(tenantId, hexHash)

        val existing = blobService.getBlobInfo(BlobInfo(path = blobPath, tenantId = tenantId))
        if (existing.isErr) {
            val storeResult =
                blobService.storeBlob(
                    target = BlobInfo(path = blobPath, tenantId = tenantId, contentType = input.contentType),
                    data = input.data,
                )
            if (storeResult.isErr) return Err(storeResult.error)
        }

        val publicUri = PublicDesignAssetPaths.assetPath(hexHash, input.contentType)
        return Ok(
            AssetReference(
                uri = publicUri,
                integrity = "sha256-$b64",
                contentType = input.contentType,
                localBlob = BlobInfo(path = blobPath, tenantId = tenantId),
            ),
        )
    }

    override suspend fun getDesignAsset(
        tenantId: String,
        input: GetDesignAssetInput,
    ): IdkResult<ResolvedDesignAsset, IdkError> {
        // Legacy per-(designId,locale,assetType) authenticated download API. Retained for the
        // management surface; the PUBLIC hosting surface uses [getDesignAssetByHash].
        val blobPath = legacyAssetBlobPath(tenantId, input.designId, input.locale, input.assetType.name)
        val blobResult = blobService.getBlob(BlobInfo(path = blobPath, tenantId = tenantId))
        if (blobResult.isErr) {
            return Err(IdkError.NOT_FOUND_ERROR(message = "Design asset not found: ${input.assetType} for design ${input.designId}"))
        }

        val resolved = blobResult.value
        val contentType = resolved.contentType ?: "application/octet-stream"
        return Ok(
            ResolvedDesignAsset(
                data = resolved.data,
                contentType = contentType,
                reference =
                    AssetReference(
                        uri = blobPath,
                        contentType = contentType,
                        localBlob = BlobInfo(path = blobPath, tenantId = tenantId),
                    ),
            ),
        )
    }

    override suspend fun getDesignAssetByHash(
        tenantId: String,
        hash: String,
    ): IdkResult<ResolvedDesignAsset, IdkError> {
        if (!hash.matches(HASH_PATTERN)) {
            return Err(IdkError.NOT_FOUND_ERROR(message = "Design asset not found"))
        }
        val blobPath = assetBlobPath(tenantId, hash)
        val blobResult = blobService.getBlob(BlobInfo(path = blobPath, tenantId = tenantId))
        if (blobResult.isErr) {
            return Err(IdkError.NOT_FOUND_ERROR(message = "Design asset not found for hash: $hash"))
        }

        val resolved = blobResult.value
        val contentType = resolved.contentType ?: "application/octet-stream"
        return Ok(
            ResolvedDesignAsset(
                data = resolved.data,
                contentType = contentType,
                reference =
                    AssetReference(
                        uri = PublicDesignAssetPaths.assetPath(hash),
                        contentType = contentType,
                        localBlob = BlobInfo(path = blobPath, tenantId = tenantId),
                    ),
            ),
        )
    }

    /**
     * Content-addressed, tenant-scoped blob path. Identical bytes dedup within a tenant; tenants
     * stay isolated by the `$tenantId` segment.
     */
    private fun assetBlobPath(
        tenantId: String,
        hash: String,
    ): String = "vc-designs/$tenantId/assets/by-hash/$hash"

    /**
     * Maps a content-addressed asset blob descriptor to an [AssetInfo], or `null` when the blob's
     * leaf is not a valid SHA-256 hash (defensive: the by-hash directory only holds hash-named blobs).
     */
    private fun BlobDescriptor.toAssetInfo(): AssetInfo? {
        val hashLeaf = PublicDesignAssetPaths.hashFromLeaf(path.substringAfterLast('/'))
        if (!hashLeaf.matches(HASH_PATTERN)) return null
        val contentType = contentType ?: "application/octet-stream"
        return AssetInfo(
            // Build the PUBLIC relative uri exactly like uploadDesignAsset (host applied at serve time).
            uri = PublicDesignAssetPaths.assetPath(hashLeaf, this.contentType),
            contentType = contentType,
            hash = hashLeaf,
            sizeBytes = sizeBytes,
            createdAt = createdAt,
        )
    }

    /**
     * Applies the [AssetFilter] to an [AssetInfo]. NOTE: a content-addressed blob keeps no asset-type
     * segment, so [AssetFilter.assetType] is matched LOOSELY by the type's conventional content-type
     * family (LOGO / BACKGROUND_IMAGE are not distinguishable and both match any `image/` type).
     * [AssetFilter.contentType] is matched as a case-insensitive content-type prefix.
     */
    private fun assetMatchesFilter(
        asset: AssetInfo,
        filter: AssetFilter,
    ): Boolean {
        val mediaType =
            asset.contentType
                .substringBefore(';')
                .trim()
                .lowercase()
        val contentTypeOk =
            filter.contentType?.let { mediaType.startsWith(it.substringBefore(';').trim().lowercase()) } ?: true
        val assetTypeOk =
            when (filter.assetType) {
                null -> true
                DesignAssetType.PDF_TEMPLATE -> mediaType == "application/pdf"
                DesignAssetType.SVG_TEMPLATE -> mediaType == "image/svg+xml"
                DesignAssetType.LOGO, DesignAssetType.BACKGROUND_IMAGE -> mediaType.startsWith("image/")
            }
        return contentTypeOk && assetTypeOk
    }

    /** Path scheme used by the legacy authenticated [getDesignAsset] download API. */
    private fun legacyAssetBlobPath(
        tenantId: String,
        designId: Uuid,
        locale: String,
        assetType: String,
    ): String = "vc-designs/$tenantId/assets/$designId/$locale/$assetType"

    /**
     * Creates a [SourceSnapshotRecord] from fetched content, storing the data in the blob store.
     */
    private suspend fun createSnapshotFromFetch(
        tenantId: String,
        sourceType: DesignSourceType,
        sourceUrl: String,
        fetched: DesignFetchResult,
        fallbackEtag: String? = null,
    ): IdkResult<SourceSnapshotRecord, IdkError> {
        val now = Clock.System.now()
        val snapshotId = Uuid.random()
        val blobPath = "vc-designs/$tenantId/snapshots/$snapshotId/content"

        val storeResult =
            blobService.storeBlob(
                target = BlobInfo(path = blobPath, tenantId = tenantId, contentType = fetched.contentType),
                data = fetched.data,
            )
        if (storeResult.isErr) return Err(storeResult.error)

        val snapshot =
            SourceSnapshotRecord(
                id = snapshotId,
                tenantId = tenantId,
                sourceType = sourceType,
                sourceUrl = sourceUrl,
                etag = fetched.etag ?: fallbackEtag,
                fetchedAt = now,
                contentBlob = BlobInfo(path = blobPath, tenantId = tenantId),
            )
        sourceSnapshotRepository.create(snapshot)
        return Ok(snapshot)
    }

    /**
     * Resolves the latest snapshot from a list of snapshot IDs, with validation.
     */
    private suspend fun resolveLatestSnapshot(
        tenantId: String,
        snapshotIds: List<Uuid>,
    ): IdkResult<SourceSnapshotRecord, IdkError> {
        if (snapshotIds.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Design has no source snapshots to refresh"))
        }
        val latestSnapshotId = snapshotIds.last()
        val snapshot =
            sourceSnapshotRepository.findById(tenantId, latestSnapshotId)
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Source snapshot not found: $latestSnapshotId"))
        return Ok(snapshot)
    }

    /**
     * Attempts to normalize fetched external content into a [CredentialDesignRecord] using the
     * appropriate mapper based on the declared [DesignSourceType].
     * Returns null if the source type is not supported for normalization.
     */
    private fun normalizeCredentialDesign(
        fetched: DesignFetchResult,
        input: ImportExternalDesignInput,
    ): CredentialDesignRecord? =
        when (input.sourceType) {
            DesignSourceType.SD_JWT_VCT_METADATA -> {
                try {
                    val sourceJson = fetched.data.decodeToString()
                    val metadata = importJson.decodeFromString<SdJwtVcTypeMetadata>(sourceJson)
                    mapper.toCanonical(metadata, input.bindings, input.sourceUrl)
                } catch (e: Exception) {
                    // Ignored: failed to parse SD-JWT VCT metadata, fall back to shell record
                    null
                }
            }

            DesignSourceType.W3C_VC_RENDER_METHOD -> {
                // W3C render methods produce render variants, not full designs — normalization is
                // handled at the render variant level, so return null for a shell record.
                null
            }

            else -> {
                null
            } // Unknown or unsupported source type — create shell record
        }

    private fun validateCredentialDesignRecord(record: CredentialDesignRecord): IdkResult<CredentialDesignRecord, IdkError>? {
        val validation = credentialDesignRecordValidator(record)
        if (validation !is Invalid) return null
        return Err(
            IdkError.ILLEGAL_ARGUMENT_ERROR(
                message = "Validation failed: ${validation.errors.joinToString { "${it.dataPath}: ${it.message}" }}",
            ),
        )
    }

    private fun credentialTypeFromBindings(bindings: List<DesignBinding>): CredentialTypeDescriptor? {
        val credentialTypes = bindings.mapNotNull(::credentialTypeFromBinding).distinct()
        return credentialTypes.singleOrNull()
    }

    private fun credentialTypeFromBinding(binding: DesignBinding): CredentialTypeDescriptor? {
        binding.credentialType?.let { return it }
        val discriminatorCount = listOf(binding.vct, binding.docType, binding.type).count { !it.isNullOrBlank() }
        if (discriminatorCount != 1) return null
        return when {
            !binding.vct.isNullOrBlank() -> {
                CredentialTypeDescriptor(format = CredentialTypeFormat.SD_JWT_VC, vct = binding.vct)
            }

            !binding.docType.isNullOrBlank() -> {
                CredentialTypeDescriptor(format = CredentialTypeFormat.MSO_MDOC, docType = binding.docType)
            }

            !binding.type.isNullOrBlank() -> {
                CredentialTypeDescriptor(format = CredentialTypeFormat.W3C_VC, type = binding.type, context = binding.context)
            }

            else -> {
                null
            }
        }
    }

    private companion object {
        val HASH_PATTERN = Regex("^[0-9a-f]{64}$")
    }
}
