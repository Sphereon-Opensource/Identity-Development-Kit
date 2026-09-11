/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl.facade

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
import com.sphereon.catalog.facade.CatalogManagementFacade
import com.sphereon.catalog.facade.CatalogQueryFacade
import com.sphereon.catalog.facade.CatalogResolutionFacade
import com.sphereon.catalog.model.AttestationCatalog
import com.sphereon.catalog.model.AttestationCatalogList
import com.sphereon.catalog.model.CatalogDocument
import com.sphereon.catalog.model.CatalogImportReport
import com.sphereon.catalog.model.CatalogTypeView
import com.sphereon.catalog.model.CatalogVerificationDecision
import com.sphereon.catalog.model.PaginatedSchemaList
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class CatalogQueryFacadeImpl(
    private val listCatalogsCommand: ListCatalogsCommand,
    private val getCatalogCommand: GetCatalogCommand,
    private val listSchemasCommand: ListSchemasCommand,
    private val getSchemaCommand: GetSchemaCommand,
    private val getCatalogTypeViewCommand: GetCatalogTypeViewCommand,
    private val getSchemaFormatCommand: GetSchemaFormatCommand,
    private val getSchemaRulebookCommand: GetSchemaRulebookCommand,
) : CatalogQueryFacade {
    override suspend fun listCatalogs(args: ListCatalogsArgs): IdkResult<AttestationCatalogList, IdkError> = listCatalogsCommand.execute(args)

    override suspend fun getCatalog(args: CatalogIdArgs): IdkResult<AttestationCatalog, IdkError> = getCatalogCommand.execute(args)

    override suspend fun listSchemas(args: ListSchemasArgs): IdkResult<PaginatedSchemaList, IdkError> = listSchemasCommand.execute(args)

    override suspend fun getSchema(args: SchemaIdArgs): IdkResult<SchemaMeta, IdkError> = getSchemaCommand.execute(args)

    override suspend fun viewType(args: SchemaIdArgs): IdkResult<CatalogTypeView, IdkError> = getCatalogTypeViewCommand.execute(args)

    override suspend fun getSchemaFormat(args: SchemaFormatArgs): IdkResult<CatalogDocument, IdkError> = getSchemaFormatCommand.execute(args)

    override suspend fun getSchemaRulebook(args: SchemaRulebookArgs): IdkResult<CatalogDocument, IdkError> = getSchemaRulebookCommand.execute(args)
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class CatalogManagementFacadeImpl(
    private val createCatalogCommand: CreateCatalogCommand,
    private val updateCatalogCommand: UpdateCatalogCommand,
    private val publishCatalogCommand: PublishCatalogCommand,
    private val disableCatalogCommand: DisableCatalogCommand,
    private val createSchemaCommand: CreateSchemaCommand,
    private val updateSchemaCommand: UpdateSchemaCommand,
    private val deleteSchemaCommand: DeleteSchemaCommand,
    private val linkSchemaCommand: LinkSchemaCommand,
    private val importRemoteCommand: ImportRemoteCatalogCommand,
    private val importRulebooksCommand: ImportRulebooksCommand,
) : CatalogManagementFacade {
    override suspend fun createCatalog(args: CreateCatalogArgs): IdkResult<AttestationCatalog, IdkError> = createCatalogCommand.execute(args)

    override suspend fun updateCatalog(args: UpdateCatalogArgs): IdkResult<AttestationCatalog, IdkError> = updateCatalogCommand.execute(args)

    override suspend fun publishCatalog(args: CatalogIdArgs): IdkResult<AttestationCatalog, IdkError> = publishCatalogCommand.execute(args)

    override suspend fun disableCatalog(args: CatalogIdArgs): IdkResult<AttestationCatalog, IdkError> = disableCatalogCommand.execute(args)

    override suspend fun createSchema(args: CreateSchemaArgs): IdkResult<SchemaMeta, IdkError> = createSchemaCommand.execute(args)

    override suspend fun updateSchema(args: UpdateSchemaArgs): IdkResult<SchemaMeta, IdkError> = updateSchemaCommand.execute(args)

    override suspend fun deleteSchema(args: SchemaIdArgs): IdkResult<Unit, IdkError> = deleteSchemaCommand.execute(args)

    override suspend fun linkSchema(args: LinkSchemaArgs): IdkResult<SchemaMeta, IdkError> = linkSchemaCommand.execute(args)

    override suspend fun importRemote(args: ImportRemoteCatalogArgs): IdkResult<CatalogImportReport, IdkError> = importRemoteCommand.execute(args)

    override suspend fun importRulebooks(args: ImportRulebooksArgs): IdkResult<CatalogImportReport, IdkError> = importRulebooksCommand.execute(args)
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class CatalogResolutionFacadeImpl(
    private val resolveCommand: ResolveAttestationTypeCommand,
    private val evaluateCommand: EvaluateCatalogVerificationCommand,
) : CatalogResolutionFacade {
    override suspend fun resolve(args: ResolveAttestationTypeArgs): IdkResult<PaginatedSchemaList, IdkError> = resolveCommand.execute(args)

    override suspend fun evaluate(args: EvaluateCatalogVerificationArgs): IdkResult<CatalogVerificationDecision, IdkError> = evaluateCommand.execute(args)
}
