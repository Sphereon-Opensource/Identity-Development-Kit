/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

@file:OptIn(kotlin.time.ExperimentalTime::class, kotlin.uuid.ExperimentalUuidApi::class)

package com.sphereon.catalog.impl.command

import com.sphereon.catalog.client.CatalogLinkedTypeSource
import com.sphereon.catalog.client.CatalogRemoteClient
import com.sphereon.catalog.client.CatalogSessionHydrator
import com.sphereon.catalog.client.IssuerBindingLookup
import com.sphereon.catalog.command.CatalogIdArgs
import com.sphereon.catalog.command.CreateCatalogArgs
import com.sphereon.catalog.command.CreateCatalogCommand
import com.sphereon.catalog.command.CreateSchemaArgs
import com.sphereon.catalog.command.CreateSchemaCommand
import com.sphereon.catalog.command.DeleteSchemaCommand
import com.sphereon.catalog.command.DisableCatalogCommand
import com.sphereon.catalog.command.EvaluateCatalogVerificationArgs
import com.sphereon.catalog.command.EvaluateCatalogVerificationCommand
import com.sphereon.catalog.command.GetCatalogCommand
import com.sphereon.catalog.command.GetCatalogTypeViewCommand
import com.sphereon.catalog.command.GetSchemaCommand
import com.sphereon.catalog.command.GetSchemaFormatCommand
import com.sphereon.catalog.command.GetSchemaRulebookCommand
import com.sphereon.catalog.command.ImportRemoteCatalogArgs
import com.sphereon.catalog.command.ImportRemoteCatalogCommand
import com.sphereon.catalog.command.ImportRulebooksArgs
import com.sphereon.catalog.command.ImportRulebooksCommand
import com.sphereon.catalog.command.LinkSchemaArgs
import com.sphereon.catalog.command.LinkSchemaCommand
import com.sphereon.catalog.command.ListCatalogsArgs
import com.sphereon.catalog.command.ListCatalogsCommand
import com.sphereon.catalog.command.ListSchemasArgs
import com.sphereon.catalog.command.ListSchemasCommand
import com.sphereon.catalog.command.PublishCatalogCommand
import com.sphereon.catalog.command.ResolveAttestationTypeArgs
import com.sphereon.catalog.command.ResolveAttestationTypeCommand
import com.sphereon.catalog.command.SchemaFormatArgs
import com.sphereon.catalog.command.SchemaIdArgs
import com.sphereon.catalog.command.SchemaRulebookArgs
import com.sphereon.catalog.command.UpdateCatalogArgs
import com.sphereon.catalog.command.UpdateCatalogCommand
import com.sphereon.catalog.command.UpdateSchemaArgs
import com.sphereon.catalog.command.UpdateSchemaCommand
import com.sphereon.catalog.impl.CatalogTypeViewAssembler
import com.sphereon.catalog.impl.CatalogVerificationEvaluator
import com.sphereon.catalog.impl.FormatDocumentValidator
import com.sphereon.catalog.impl.RulebookCatalogMapper
import com.sphereon.catalog.impl.SchemaMetaValidator
import com.sphereon.catalog.impl.client.NoOpCatalogSessionHydrator
import com.sphereon.catalog.model.AttestationCatalog
import com.sphereon.catalog.model.AttestationCatalogList
import com.sphereon.catalog.model.AttestationCatalogStatus
import com.sphereon.catalog.model.AttestationSchemaRecord
import com.sphereon.catalog.model.CatalogDocument
import com.sphereon.catalog.model.CatalogDocumentKind
import com.sphereon.catalog.model.CatalogImportReport
import com.sphereon.catalog.model.CatalogListingWindow
import com.sphereon.catalog.model.CatalogSchemaProvenance
import com.sphereon.catalog.model.CatalogTypeKeys
import com.sphereon.catalog.model.CatalogTypeView
import com.sphereon.catalog.model.CatalogVerificationDecision
import com.sphereon.catalog.model.PaginatedSchemaList
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.model.SchemaUriRef
import com.sphereon.catalog.store.AttestationCatalogStore
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock
import kotlin.uuid.Uuid

private fun newId(): String = Uuid.random().toString()

private fun now() = Clock.System.now()

private fun matchImported(
    existing: List<AttestationSchemaRecord>,
    incoming: SchemaMeta,
    documents: List<com.sphereon.catalog.model.AttestationSchemaDocument>,
): AttestationSchemaRecord? {
    incoming.id?.takeIf { it.isNotBlank() }?.let { remoteId ->
        existing.firstOrNull { it.schema.id == remoteId }?.let { return it }
    }
    val incomingKey =
        CatalogTypeKeys.of(
            AttestationSchemaRecord(
                catalogId = "",
                schema = incoming,
                provenance = CatalogSchemaProvenance.IMPORTED,
                documents = documents,
            ),
        )
    if (incomingKey.value.isBlank()) return null
    return existing.firstOrNull { CatalogTypeKeys.of(it) == incomingKey }
}

private fun publicOrigin(execution: SessionExecution): String {
    val candidates =
        listOf(
            runCatching { execution.conf.tenant.getPropertyAsString("catalog.public.iss") }.getOrNull(),
            runCatching { execution.conf.tenant.getPropertyAsString("tenant.public-base-url") }.getOrNull(),
            runCatching { execution.conf.app.getPropertyAsString("tenant.public-base-url") }.getOrNull(),
        )
    return candidates
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
        ?.trimEnd('/')
        .orEmpty()
}

private fun hostedPath(
    origin: String,
    path: String
): String = if (origin.isEmpty()) path else "$origin$path"

private fun hostedRulebookUri(
    origin: String,
    slug: String,
    schemaId: String
): String = hostedPath(origin, "/public/catalogs/$slug/api/v1/schemas/$schemaId/rulebook")

private fun hostedFormatUri(
    origin: String,
    slug: String,
    schemaId: String,
    format: String
): String = hostedPath(origin, "/public/catalogs/$slug/api/v1/schemas/$schemaId/formats/$format")

@Inject
@SingleIn(SessionScope::class)
class ListCatalogsCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
    private val hydrator: CatalogSessionHydrator = NoOpCatalogSessionHydrator(),
) : TypedServiceCommandAdapter<ListCatalogsArgs, AttestationCatalogList, IdkError>(
        commandId = ListCatalogsCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ListCatalogsArgs>(),
        outputTypeToken = typeToken<AttestationCatalogList>(),
    ),
    ListCatalogsCommand {
    override val commandId: String get() = ListCatalogsCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ListCatalogsArgs

    override suspend fun doExecute(
        args: ListCatalogsArgs,
        applyDuring: (ListCatalogsArgs) -> ListCatalogsArgs,
    ): IdkResult<AttestationCatalogList, IdkError> {
        val input = applyDuring(args)
        hydrator.hydrate(store, execution.tenantId)
        val items =
            store
                .listCatalogs(execution.tenantId, input.status, input.verificationEnabled)
                .getOrElse { return Err(it) }
        return Ok(AttestationCatalogList(items))
    }
}

@Inject
@SingleIn(SessionScope::class)
class CreateCatalogCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
) : TypedServiceCommandAdapter<CreateCatalogArgs, AttestationCatalog, IdkError>(
        commandId = CreateCatalogCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateCatalogArgs>(),
        outputTypeToken = typeToken<AttestationCatalog>(),
    ),
    CreateCatalogCommand {
    override val commandId: String get() = CreateCatalogCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateCatalogArgs

    override suspend fun doExecute(
        args: CreateCatalogArgs,
        applyDuring: (CreateCatalogArgs) -> CreateCatalogArgs,
    ): IdkResult<AttestationCatalog, IdkError> {
        val input = applyDuring(args)
        SchemaMetaValidator.validateSlug(input.slug).getOrElse { return Err(it) }
        if (input.displayName.isBlank()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "displayName is required"))
        }
        val existing = store.findCatalogBySlug(execution.tenantId, input.slug).getOrElse { return Err(it) }
        if (existing != null) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(arg = input.slug, message = "Catalog slug already exists"))
        }
        val instant = now()
        val catalog =
            AttestationCatalog(
                id = newId(),
                slug = input.slug,
                displayName = input.displayName,
                description = input.description,
                verificationEnabled = input.verificationEnabled,
                createdAt = instant,
                updatedAt = instant,
            )
        return store.saveCatalog(execution.tenantId, catalog)
    }
}

@Inject
@SingleIn(SessionScope::class)
class GetCatalogCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
) : TypedServiceCommandAdapter<CatalogIdArgs, AttestationCatalog, IdkError>(
        commandId = GetCatalogCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CatalogIdArgs>(),
        outputTypeToken = typeToken<AttestationCatalog>(),
    ),
    GetCatalogCommand {
    override val commandId: String get() = GetCatalogCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CatalogIdArgs

    override suspend fun doExecute(
        args: CatalogIdArgs,
        applyDuring: (CatalogIdArgs) -> CatalogIdArgs,
    ): IdkResult<AttestationCatalog, IdkError> {
        val input = applyDuring(args)
        return store.requireCatalog(execution.tenantId, input.catalogId, null, publishedOnly = false)
    }
}

@Inject
@SingleIn(SessionScope::class)
class UpdateCatalogCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
) : TypedServiceCommandAdapter<UpdateCatalogArgs, AttestationCatalog, IdkError>(
        commandId = UpdateCatalogCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<UpdateCatalogArgs>(),
        outputTypeToken = typeToken<AttestationCatalog>(),
    ),
    UpdateCatalogCommand {
    override val commandId: String get() = UpdateCatalogCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is UpdateCatalogArgs

    override suspend fun doExecute(
        args: UpdateCatalogArgs,
        applyDuring: (UpdateCatalogArgs) -> UpdateCatalogArgs,
    ): IdkResult<AttestationCatalog, IdkError> {
        val input = applyDuring(args)
        val current =
            store
                .requireCatalog(execution.tenantId, input.catalogId, null, publishedOnly = false)
                .getOrElse { return Err(it) }
        val nextSlug = input.slug?.trim()?.takeIf { it.isNotEmpty() }
        if (nextSlug != null && nextSlug != current.slug) {
            if (current.status != AttestationCatalogStatus.DRAFT) {
                return Err(illegalState("Catalog slug is immutable after first publish"))
            }
            SchemaMetaValidator.validateSlug(nextSlug).getOrElse { return Err(it) }
            val existing = store.findCatalogBySlug(execution.tenantId, nextSlug).getOrElse { return Err(it) }
            if (existing != null && existing.id != current.id) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(arg = nextSlug, message = "Catalog slug already exists"))
            }
        }
        val updated =
            current.copy(
                slug = nextSlug ?: current.slug,
                displayName = input.displayName,
                description = input.description,
                verificationEnabled = input.verificationEnabled,
                version = current.version + 1,
                updatedAt = now(),
            )
        return store.saveCatalog(execution.tenantId, updated)
    }
}

@Inject
@SingleIn(SessionScope::class)
class PublishCatalogCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
) : TypedServiceCommandAdapter<CatalogIdArgs, AttestationCatalog, IdkError>(
        commandId = PublishCatalogCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CatalogIdArgs>(),
        outputTypeToken = typeToken<AttestationCatalog>(),
    ),
    PublishCatalogCommand {
    override val commandId: String get() = PublishCatalogCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CatalogIdArgs

    override suspend fun doExecute(
        args: CatalogIdArgs,
        applyDuring: (CatalogIdArgs) -> CatalogIdArgs,
    ): IdkResult<AttestationCatalog, IdkError> {
        val input = applyDuring(args)
        val current =
            store
                .requireCatalog(execution.tenantId, input.catalogId, null, publishedOnly = false)
                .getOrElse { return Err(it) }
        if (current.status != AttestationCatalogStatus.DRAFT) {
            return Err(illegalState("Only DRAFT catalogs can be published"))
        }
        val schemas = store.listSchemas(execution.tenantId, current.id).getOrElse { return Err(it) }
        val origin = publicOrigin(execution)
        schemas.filter { !it.listing.isEmpty() }.forEach { record ->
            SchemaMetaValidator.validate(record.schema, requireId = true).getOrElse { return Err(it) }
            requireListedDocuments(record.schema, record.documents, record.listing).getOrElse { return Err(it) }
            val schemaId = record.schema.id!!
            val hosted =
                record.schema.copy(
                    rulebookURI = hostedRulebookUri(origin, current.slug, schemaId),
                    schemaURIs =
                        record.schema.schemaURIs.map { ref ->
                            if (ref.uri.startsWith("http://") || ref.uri.startsWith("https://")) {
                                ref
                            } else {
                                SchemaUriRef(ref.formatIdentifier, hostedFormatUri(origin, current.slug, schemaId, ref.formatIdentifier))
                            }
                        },
                )
            store
                .saveSchema(execution.tenantId, record.copy(schema = hosted, updatedAt = now()))
                .getOrElse { return Err(it) }
        }
        return store.saveCatalog(
            execution.tenantId,
            current.copy(
                status = AttestationCatalogStatus.PUBLISHED,
                version = current.version + 1,
                updatedAt = now(),
            ),
        )
    }
}

@Inject
@SingleIn(SessionScope::class)
class DisableCatalogCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
) : TypedServiceCommandAdapter<CatalogIdArgs, AttestationCatalog, IdkError>(
        commandId = DisableCatalogCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CatalogIdArgs>(),
        outputTypeToken = typeToken<AttestationCatalog>(),
    ),
    DisableCatalogCommand {
    override val commandId: String get() = DisableCatalogCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CatalogIdArgs

    override suspend fun doExecute(
        args: CatalogIdArgs,
        applyDuring: (CatalogIdArgs) -> CatalogIdArgs,
    ): IdkResult<AttestationCatalog, IdkError> {
        val input = applyDuring(args)
        val current =
            store
                .requireCatalog(execution.tenantId, input.catalogId, null, publishedOnly = false)
                .getOrElse { return Err(it) }
        if (current.status != AttestationCatalogStatus.PUBLISHED) {
            return Err(illegalState("Only PUBLISHED catalogs can be disabled"))
        }
        return store.saveCatalog(
            execution.tenantId,
            current.copy(
                status = AttestationCatalogStatus.DISABLED,
                version = current.version + 1,
                updatedAt = now(),
            ),
        )
    }
}

@Inject
@SingleIn(SessionScope::class)
class ListSchemasCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
    private val hydrator: CatalogSessionHydrator = NoOpCatalogSessionHydrator(),
) : TypedServiceCommandAdapter<ListSchemasArgs, PaginatedSchemaList, IdkError>(
        commandId = ListSchemasCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ListSchemasArgs>(),
        outputTypeToken = typeToken<PaginatedSchemaList>(),
    ),
    ListSchemasCommand {
    override val commandId: String get() = ListSchemasCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ListSchemasArgs

    override suspend fun doExecute(
        args: ListSchemasArgs,
        applyDuring: (ListSchemasArgs) -> ListSchemasArgs,
    ): IdkResult<PaginatedSchemaList, IdkError> {
        val input = applyDuring(args)
        hydrator.hydrate(store, execution.tenantId)
        val catalog =
            store
                .requireCatalog(execution.tenantId, input.catalogId, input.slug, input.publishedOnly)
                .getOrElse { return Err(it) }
        val all = store.listSchemas(execution.tenantId, catalog.id).getOrElse { return Err(it) }
        val filtered =
            all.filter { record ->
                (!input.listedOnly || record.listing.includes(now())) &&
                    matchesLinkedType(record, input.linkedDesignId, input.linkedVctId) &&
                    matchesSchemaFilters(
                        schema = record.schema,
                        id = input.id,
                        supportedFormats = input.supportedFormats,
                        attestationLoS = input.attestationLoS,
                        bindingType = input.bindingType,
                        trustedAuthoritiesFrameworkType = input.trustedAuthoritiesFrameworkType,
                        trustedAuthoritiesValue = input.trustedAuthoritiesValue,
                        schemaUri = input.schemaUri,
                        rulebookUri = input.rulebookUri,
                    )
            }
        val limit = input.limit.coerceIn(1, 200)
        val offset = input.offset.coerceAtLeast(0)
        return Ok(
            PaginatedSchemaList(
                total = filtered.size,
                limit = limit,
                offset = offset,
                data = filtered.drop(offset).take(limit).map { it.schema },
            ),
        )
    }
}

@Inject
@SingleIn(SessionScope::class)
class GetSchemaCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
) : TypedServiceCommandAdapter<SchemaIdArgs, SchemaMeta, IdkError>(
        commandId = GetSchemaCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<SchemaIdArgs>(),
        outputTypeToken = typeToken<SchemaMeta>(),
    ),
    GetSchemaCommand {
    override val commandId: String get() = GetSchemaCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is SchemaIdArgs

    override suspend fun doExecute(
        args: SchemaIdArgs,
        applyDuring: (SchemaIdArgs) -> SchemaIdArgs,
    ): IdkResult<SchemaMeta, IdkError> {
        val input = applyDuring(args)
        val catalog =
            store
                .requireCatalog(execution.tenantId, input.catalogId, input.slug, input.publishedOnly)
                .getOrElse { return Err(it) }
        val record = store.requireSchema(execution.tenantId, catalog, input.schemaId).getOrElse { return Err(it) }
        if (input.publishedOnly && !record.listing.includes(now())) {
            return Err(IdkError.NOT_FOUND_ERROR(resource = input.schemaId))
        }
        return Ok(record.schema)
    }
}

@Inject
@SingleIn(SessionScope::class)
class GetCatalogTypeViewCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
    issuerBindingLookup: IssuerBindingLookup,
    private val hydrator: CatalogSessionHydrator = NoOpCatalogSessionHydrator(),
) : TypedServiceCommandAdapter<SchemaIdArgs, CatalogTypeView, IdkError>(
        commandId = GetCatalogTypeViewCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<SchemaIdArgs>(),
        outputTypeToken = typeToken<CatalogTypeView>(),
    ),
    GetCatalogTypeViewCommand {
    override val commandId: String get() = GetCatalogTypeViewCommand.COMMAND_ID

    private val assembler = CatalogTypeViewAssembler(issuerBindingLookup)

    override suspend fun supports(args: Any): Boolean = args is SchemaIdArgs

    override suspend fun doExecute(
        args: SchemaIdArgs,
        applyDuring: (SchemaIdArgs) -> SchemaIdArgs,
    ): IdkResult<CatalogTypeView, IdkError> {
        val input = applyDuring(args)
        hydrator.hydrate(store, execution.tenantId)
        val catalog =
            store
                .requireCatalog(execution.tenantId, input.catalogId, input.slug, input.publishedOnly)
                .getOrElse { return Err(it) }
        val record = store.requireSchema(execution.tenantId, catalog, input.schemaId).getOrElse { return Err(it) }
        if (input.publishedOnly && !record.listing.includes(now())) {
            return Err(IdkError.NOT_FOUND_ERROR(resource = input.schemaId))
        }
        return Ok(assembler.assemble(catalog, record, locale = input.locale?.trim()?.takeIf { it.isNotEmpty() } ?: "en"))
    }
}

@Inject
@SingleIn(SessionScope::class)
class CreateSchemaCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
) : TypedServiceCommandAdapter<CreateSchemaArgs, SchemaMeta, IdkError>(
        commandId = CreateSchemaCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateSchemaArgs>(),
        outputTypeToken = typeToken<SchemaMeta>(),
    ),
    CreateSchemaCommand {
    override val commandId: String get() = CreateSchemaCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateSchemaArgs

    override suspend fun doExecute(
        args: CreateSchemaArgs,
        applyDuring: (CreateSchemaArgs) -> CreateSchemaArgs,
    ): IdkResult<SchemaMeta, IdkError> {
        val input = applyDuring(args)
        val catalog =
            store
                .requireCatalog(execution.tenantId, input.catalogId, null, publishedOnly = false)
                .getOrElse { return Err(it) }
        SchemaMetaValidator.validate(input.schema, requireId = false).getOrElse { return Err(it) }
        val instant = now()
        val listing = input.listing ?: CatalogListingWindow.open(instant)
        requireListedDocuments(input.schema, input.documents, listing).getOrElse { return Err(it) }
        val schema = input.schema.copy(id = input.schema.id ?: newId())
        store
            .saveSchema(
                execution.tenantId,
                AttestationSchemaRecord(
                    catalogId = catalog.id,
                    schema = schema,
                    provenance = CatalogSchemaProvenance.AUTHORED,
                    listing = listing,
                    documents = input.documents,
                    createdAt = instant,
                    updatedAt = instant,
                ),
            ).getOrElse { return Err(it) }
        return Ok(schema)
    }
}

@Inject
@SingleIn(SessionScope::class)
class UpdateSchemaCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
) : TypedServiceCommandAdapter<UpdateSchemaArgs, SchemaMeta, IdkError>(
        commandId = UpdateSchemaCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<UpdateSchemaArgs>(),
        outputTypeToken = typeToken<SchemaMeta>(),
    ),
    UpdateSchemaCommand {
    override val commandId: String get() = UpdateSchemaCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is UpdateSchemaArgs

    override suspend fun doExecute(
        args: UpdateSchemaArgs,
        applyDuring: (UpdateSchemaArgs) -> UpdateSchemaArgs,
    ): IdkResult<SchemaMeta, IdkError> {
        val input = applyDuring(args)
        val catalog =
            store
                .requireCatalog(execution.tenantId, input.catalogId, null, publishedOnly = false)
                .getOrElse { return Err(it) }
        val current = store.requireSchema(execution.tenantId, catalog, input.schemaId).getOrElse { return Err(it) }
        SchemaMetaValidator.validate(input.schema, requireId = false).getOrElse { return Err(it) }
        val listing = input.listing ?: current.listing
        val documents = input.documents ?: current.documents
        requireListedDocuments(input.schema, documents, listing).getOrElse { return Err(it) }
        val schema = input.schema.copy(id = input.schemaId)
        store
            .saveSchema(
                execution.tenantId,
                current.copy(schema = schema, documents = documents, listing = listing, updatedAt = now()),
            ).getOrElse { return Err(it) }
        return Ok(schema)
    }
}

@Inject
@SingleIn(SessionScope::class)
class DeleteSchemaCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
) : TypedServiceCommandAdapter<SchemaIdArgs, Unit, IdkError>(
        commandId = DeleteSchemaCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<SchemaIdArgs>(),
        outputTypeToken = typeToken<Unit>(),
    ),
    DeleteSchemaCommand {
    override val commandId: String get() = DeleteSchemaCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is SchemaIdArgs

    override suspend fun doExecute(
        args: SchemaIdArgs,
        applyDuring: (SchemaIdArgs) -> SchemaIdArgs,
    ): IdkResult<Unit, IdkError> {
        val input = applyDuring(args)
        val catalog =
            store
                .requireCatalog(execution.tenantId, input.catalogId, input.slug, publishedOnly = false)
                .getOrElse { return Err(it) }
        val deleted = store.deleteSchema(execution.tenantId, catalog.id, input.schemaId).getOrElse { return Err(it) }
        if (!deleted) return Err(IdkError.NOT_FOUND_ERROR(resource = input.schemaId))
        return Ok(Unit)
    }
}

@Inject
@SingleIn(SessionScope::class)
class LinkSchemaCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
    private val linkedTypeSource: CatalogLinkedTypeSource,
) : TypedServiceCommandAdapter<LinkSchemaArgs, SchemaMeta, IdkError>(
        commandId = LinkSchemaCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<LinkSchemaArgs>(),
        outputTypeToken = typeToken<SchemaMeta>(),
    ),
    LinkSchemaCommand {
    override val commandId: String get() = LinkSchemaCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is LinkSchemaArgs

    override suspend fun doExecute(
        args: LinkSchemaArgs,
        applyDuring: (LinkSchemaArgs) -> LinkSchemaArgs,
    ): IdkResult<SchemaMeta, IdkError> {
        val input = applyDuring(args)
        val catalog =
            store
                .requireCatalog(execution.tenantId, input.catalogId, null, publishedOnly = false)
                .getOrElse { return Err(it) }
        val existing =
            store
                .listSchemas(execution.tenantId, catalog.id)
                .getOrElse { return Err(it) }
                .firstOrNull { record -> existingLinkMatches(record, input) }
        if (existing != null) return Ok(existing.schema)
        val snapshot =
            linkedTypeSource.resolve(input.designId, input.vctId).getOrElse { return Err(it) }
        val schemaUris =
            input.schemaURIs.ifEmpty {
                buildList {
                    snapshot?.schemaURIs?.let { addAll(it) }
                    if (isEmpty()) {
                        (input.vct ?: snapshot?.vct)?.let { add(SchemaUriRef("dc+sd-jwt", it)) }
                        (input.doctype ?: snapshot?.doctype)?.let { add(SchemaUriRef("mso_mdoc", it)) }
                    }
                }
            }
        if (schemaUris.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "schemaURIs, vct, doctype, vctId, or designId is required"))
        }
        val formats = input.supportedFormats.ifEmpty { schemaUris.map { it.formatIdentifier }.distinct() }
        val documents = input.documents.ifEmpty { snapshot?.documents.orEmpty() }
        val schema =
            SchemaMeta(
                id = newId(),
                version = input.version,
                rulebookURI = input.rulebookURI ?: "about:blank",
                trustedAuthorities = input.trustedAuthorities,
                attestationLoS = input.attestationLoS,
                bindingType = input.bindingType,
                supportedFormats = formats,
                schemaURIs = schemaUris,
            )
        SchemaMetaValidator.validate(schema).getOrElse { return Err(it) }
        val instant = now()
        // Membership only until a listing window is set. Credential include sends no
        // rulebook; serving still requires one via requireListedDocuments.
        val listing = input.listing ?: CatalogListingWindow.never(instant)
        requireListedDocuments(schema, documents, listing).getOrElse { return Err(it) }
        store
            .saveSchema(
                execution.tenantId,
                AttestationSchemaRecord(
                    catalogId = catalog.id,
                    schema = schema,
                    provenance = CatalogSchemaProvenance.LINKED_DESIGN,
                    listing = listing,
                    linkedDesignId = input.designId,
                    linkedVctId = input.vctId,
                    documents = documents,
                    createdAt = instant,
                    updatedAt = instant,
                ),
            ).getOrElse { return Err(it) }
        return Ok(schema)
    }
}

@Inject
@SingleIn(SessionScope::class)
class GetSchemaFormatCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
) : TypedServiceCommandAdapter<SchemaFormatArgs, CatalogDocument, IdkError>(
        commandId = GetSchemaFormatCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<SchemaFormatArgs>(),
        outputTypeToken = typeToken<CatalogDocument>(),
    ),
    GetSchemaFormatCommand {
    override val commandId: String get() = GetSchemaFormatCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is SchemaFormatArgs

    override suspend fun doExecute(
        args: SchemaFormatArgs,
        applyDuring: (SchemaFormatArgs) -> SchemaFormatArgs,
    ): IdkResult<CatalogDocument, IdkError> {
        val input = applyDuring(args)
        val catalog =
            store
                .requireCatalog(execution.tenantId, input.catalogId, input.slug, input.publishedOnly)
                .getOrElse { return Err(it) }
        val record = store.requireSchema(execution.tenantId, catalog, input.schemaId).getOrElse { return Err(it) }
        if (input.publishedOnly && !record.listing.includes(now())) {
            return Err(IdkError.NOT_FOUND_ERROR(resource = input.schemaId))
        }
        val document =
            record.documents.firstOrNull {
                it.kind == CatalogDocumentKind.FORMAT && it.formatIdentifier == input.formatIdentifier
            } ?: return Err(IdkError.NOT_FOUND_ERROR(resource = input.formatIdentifier))
        return Ok(CatalogDocument(document.mediaType, document.bytes, document.integrity))
    }
}

@Inject
@SingleIn(SessionScope::class)
class GetSchemaRulebookCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
) : TypedServiceCommandAdapter<SchemaRulebookArgs, CatalogDocument, IdkError>(
        commandId = GetSchemaRulebookCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<SchemaRulebookArgs>(),
        outputTypeToken = typeToken<CatalogDocument>(),
    ),
    GetSchemaRulebookCommand {
    override val commandId: String get() = GetSchemaRulebookCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is SchemaRulebookArgs

    override suspend fun doExecute(
        args: SchemaRulebookArgs,
        applyDuring: (SchemaRulebookArgs) -> SchemaRulebookArgs,
    ): IdkResult<CatalogDocument, IdkError> {
        val input = applyDuring(args)
        val catalog =
            store
                .requireCatalog(execution.tenantId, input.catalogId, input.slug, input.publishedOnly)
                .getOrElse { return Err(it) }
        val record = store.requireSchema(execution.tenantId, catalog, input.schemaId).getOrElse { return Err(it) }
        if (input.publishedOnly && !record.listing.includes(now())) {
            return Err(IdkError.NOT_FOUND_ERROR(resource = input.schemaId))
        }
        val document =
            record.documents.firstOrNull { it.kind == CatalogDocumentKind.RULEBOOK }
                ?: return Err(IdkError.NOT_FOUND_ERROR(resource = "rulebook"))
        return Ok(CatalogDocument(document.mediaType, document.bytes, document.integrity))
    }
}

@Inject
@SingleIn(SessionScope::class)
class ImportRemoteCatalogCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
    private val remoteClient: CatalogRemoteClient,
) : TypedServiceCommandAdapter<ImportRemoteCatalogArgs, CatalogImportReport, IdkError>(
        commandId = ImportRemoteCatalogCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ImportRemoteCatalogArgs>(),
        outputTypeToken = typeToken<CatalogImportReport>(),
    ),
    ImportRemoteCatalogCommand {
    override val commandId: String get() = ImportRemoteCatalogCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ImportRemoteCatalogArgs

    override suspend fun doExecute(
        args: ImportRemoteCatalogArgs,
        applyDuring: (ImportRemoteCatalogArgs) -> ImportRemoteCatalogArgs,
    ): IdkResult<CatalogImportReport, IdkError> {
        val input = applyDuring(args)
        val catalog =
            store
                .requireCatalog(execution.tenantId, input.catalogId, null, publishedOnly = false)
                .getOrElse { return Err(it) }
        val incoming = mutableListOf<SchemaMeta>()
        var offset = 0
        val pageSize = 100
        do {
            val page = remoteClient.listSchemas(input.baseUrl, pageSize, offset).getOrElse { return Err(it) }
            incoming += page.data
            offset += page.data.size
            if (page.data.isEmpty() || offset >= page.total) break
        } while (true)
        val errors = mutableListOf<String>()
        val ids = mutableListOf<String>()
        var imported = 0
        var skipped = 0
        val existing = store.listSchemas(execution.tenantId, catalog.id).getOrElse { return Err(it) }
        val keptIds = mutableSetOf<String>()
        incoming.forEach { listed ->
            val fetched =
                listed.id?.let { remoteClient.getSchema(input.baseUrl, it) }?.getOrElse {
                    errors += it.message.defaultMessage
                    skipped += 1
                    return@forEach
                } ?: listed
            val structuralOk =
                fetched.version.isNotBlank() &&
                    fetched.rulebookURI.isNotBlank() &&
                    fetched.supportedFormats.isNotEmpty() &&
                    fetched.schemaURIs.isNotEmpty()
            if (!structuralOk) {
                skipped += 1
                errors += "Skipping schema ${fetched.id ?: fetched.version}: missing required SchemaMeta fields"
                return@forEach
            }
            val documents = mutableListOf<com.sphereon.catalog.model.AttestationSchemaDocument>()
            fetched.schemaURIs.forEach { ref ->
                val doc = remoteClient.fetchDocument(ref.uri)
                if (doc.isErr) {
                    errors += doc.error.message.defaultMessage
                } else {
                    documents +=
                        com.sphereon.catalog.model.AttestationSchemaDocument(
                            kind = CatalogDocumentKind.FORMAT,
                            formatIdentifier = ref.formatIdentifier,
                            mediaType = doc.value.mediaType,
                            bytes = doc.value.bytes,
                            integrity = doc.value.integrity,
                        )
                }
            }
            val rulebook = remoteClient.fetchDocument(fetched.rulebookURI)
            if (rulebook.isErr) {
                errors += rulebook.error.message.defaultMessage
            } else {
                documents +=
                    com.sphereon.catalog.model.AttestationSchemaDocument(
                        kind = CatalogDocumentKind.RULEBOOK,
                        mediaType = rulebook.value.mediaType,
                        bytes = rulebook.value.bytes,
                        integrity = rulebook.value.integrity,
                    )
            }
            val prior = matchImported(existing, fetched, documents)
            val schema = fetched.copy(id = prior?.schema?.id ?: fetched.id ?: newId())
            val listedOk =
                SchemaMetaValidator.hasValidLoS(fetched.attestationLoS) &&
                    SchemaMetaValidator.hasValidBinding(fetched.bindingType) &&
                    SchemaMetaValidator.validate(fetched, requireId = false).isOk &&
                    FormatDocumentValidator.validateListedFormats(fetched.supportedFormats, documents).isOk
            val instant = now()
            store
                .saveSchema(
                    execution.tenantId,
                    AttestationSchemaRecord(
                        catalogId = catalog.id,
                        schema = schema,
                        provenance = CatalogSchemaProvenance.IMPORTED,
                        listing = if (listedOk) CatalogListingWindow.open(instant) else CatalogListingWindow.never(instant),
                        documents = documents,
                        createdAt = prior?.createdAt ?: instant,
                        updatedAt = instant,
                    ),
                ).getOrElse {
                    errors += it.message.defaultMessage
                    skipped += 1
                    return@forEach
                }
            imported += 1
            schema.id?.let {
                ids += it
                keptIds += it
            }
        }
        val instant = now()
        existing
            .filter { it.provenance == CatalogSchemaProvenance.IMPORTED }
            .filter { record -> record.schema.id !in keptIds }
            .forEach { record ->
                store
                    .saveSchema(
                        execution.tenantId,
                        record.copy(listing = CatalogListingWindow.never(instant), updatedAt = instant),
                    ).getOrElse { return Err(it) }
            }
        return Ok(
            CatalogImportReport(
                catalogId = catalog.id,
                imported = imported,
                skipped = skipped,
                errors = errors,
                schemaIds = ids,
            ),
        )
    }
}

@Inject
@SingleIn(SessionScope::class)
class ImportRulebooksCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
) : TypedServiceCommandAdapter<ImportRulebooksArgs, CatalogImportReport, IdkError>(
        commandId = ImportRulebooksCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ImportRulebooksArgs>(),
        outputTypeToken = typeToken<CatalogImportReport>(),
    ),
    ImportRulebooksCommand {
    override val commandId: String get() = ImportRulebooksCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ImportRulebooksArgs

    override suspend fun doExecute(
        args: ImportRulebooksArgs,
        applyDuring: (ImportRulebooksArgs) -> ImportRulebooksArgs,
    ): IdkResult<CatalogImportReport, IdkError> {
        val input = applyDuring(args)
        val catalog =
            store
                .requireCatalog(execution.tenantId, input.catalogId, null, publishedOnly = false)
                .getOrElse { return Err(it) }
        if (!input.sourceUrl.isNullOrBlank()) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "v1 rulebook import is upload-only; git URLs are not supported",
                ),
            )
        }
        val mapped =
            RulebookCatalogMapper.map(input.files, input.defaultAttestationLoS, input.defaultBindingType)
        val ids = mutableListOf<String>()
        mapped.forEach { item ->
            val schema = item.schema.copy(id = newId())
            val instant = now()
            store
                .saveSchema(
                    execution.tenantId,
                    AttestationSchemaRecord(
                        catalogId = catalog.id,
                        schema = schema,
                        provenance = CatalogSchemaProvenance.IMPORTED,
                        listing = if (item.complete) CatalogListingWindow.open(instant) else CatalogListingWindow.never(instant),
                        documents = item.documents,
                        createdAt = instant,
                        updatedAt = instant,
                    ),
                ).getOrElse { return Err(it) }
            schema.id?.let { ids += it }
        }
        return Ok(
            CatalogImportReport(
                catalogId = catalog.id,
                imported = mapped.size,
                skipped = 0,
                schemaIds = ids,
            ),
        )
    }
}

@Inject
@SingleIn(SessionScope::class)
class ResolveAttestationTypeCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
) : TypedServiceCommandAdapter<ResolveAttestationTypeArgs, PaginatedSchemaList, IdkError>(
        commandId = ResolveAttestationTypeCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ResolveAttestationTypeArgs>(),
        outputTypeToken = typeToken<PaginatedSchemaList>(),
    ),
    ResolveAttestationTypeCommand {
    override val commandId: String get() = ResolveAttestationTypeCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ResolveAttestationTypeArgs

    override suspend fun doExecute(
        args: ResolveAttestationTypeArgs,
        applyDuring: (ResolveAttestationTypeArgs) -> ResolveAttestationTypeArgs,
    ): IdkResult<PaginatedSchemaList, IdkError> {
        val input = applyDuring(args)
        val records =
            if (input.admin && input.catalogIds.isNotEmpty()) {
                input.catalogIds.flatMap { id ->
                    store.listSchemas(execution.tenantId, id).getOrElse { return Err(it) }
                }
            } else if (input.includeDisabled) {
                val catalogs =
                    store
                        .listCatalogs(execution.tenantId, status = null, verificationEnabled = true)
                        .getOrElse { return Err(it) }
                        .filter {
                            it.status == AttestationCatalogStatus.PUBLISHED ||
                                it.status == AttestationCatalogStatus.DISABLED
                        }.filter { input.catalogIds.isEmpty() || it.id in input.catalogIds }
                catalogs.flatMap { catalog ->
                    store
                        .listSchemas(execution.tenantId, catalog.id)
                        .getOrElse { return Err(it) }
                        .filter { it.listing.includes(now()) }
                }
            } else {
                store
                    .listPublishedVerificationSchemas(execution.tenantId)
                    .getOrElse { return Err(it) }
                    .filter { input.catalogIds.isEmpty() || it.catalogId in input.catalogIds }
            }
        val matches = records.map { it.schema }.filter { CatalogVerificationEvaluator.matches(it, input.type) }
        return Ok(PaginatedSchemaList(total = matches.size, limit = matches.size, offset = 0, data = matches))
    }
}

@Inject
@SingleIn(SessionScope::class)
class EvaluateCatalogVerificationCommandImpl(
    execution: SessionExecution,
    private val store: AttestationCatalogStore,
) : TypedServiceCommandAdapter<EvaluateCatalogVerificationArgs, CatalogVerificationDecision, IdkError>(
        commandId = EvaluateCatalogVerificationCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<EvaluateCatalogVerificationArgs>(),
        outputTypeToken = typeToken<CatalogVerificationDecision>(),
    ),
    EvaluateCatalogVerificationCommand {
    override val commandId: String get() = EvaluateCatalogVerificationCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is EvaluateCatalogVerificationArgs

    override suspend fun doExecute(
        args: EvaluateCatalogVerificationArgs,
        applyDuring: (EvaluateCatalogVerificationArgs) -> EvaluateCatalogVerificationArgs,
    ): IdkResult<CatalogVerificationDecision, IdkError> {
        val input = applyDuring(args)
        val records =
            store
                .listPublishedVerificationSchemas(execution.tenantId)
                .getOrElse { return Err(it) }
                .filter { input.catalogIds.isEmpty() || it.catalogId in input.catalogIds }
        val matches = records.map { it.schema }.filter { CatalogVerificationEvaluator.matches(it, input.type) }
        return Ok(CatalogVerificationEvaluator.evaluate(input.mode, input.type, matches))
    }
}
