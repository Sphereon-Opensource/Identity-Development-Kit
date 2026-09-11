/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.facade

import com.sphereon.catalog.command.CatalogIdArgs
import com.sphereon.catalog.command.CreateCatalogArgs
import com.sphereon.catalog.command.CreateSchemaArgs
import com.sphereon.catalog.command.EvaluateCatalogVerificationArgs
import com.sphereon.catalog.command.ImportRemoteCatalogArgs
import com.sphereon.catalog.command.ImportRulebooksArgs
import com.sphereon.catalog.command.LinkSchemaArgs
import com.sphereon.catalog.command.ListCatalogsArgs
import com.sphereon.catalog.command.ListSchemasArgs
import com.sphereon.catalog.command.ResolveAttestationTypeArgs
import com.sphereon.catalog.command.SchemaFormatArgs
import com.sphereon.catalog.command.SchemaIdArgs
import com.sphereon.catalog.command.SchemaRulebookArgs
import com.sphereon.catalog.command.UpdateCatalogArgs
import com.sphereon.catalog.command.UpdateSchemaArgs
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
import com.sphereon.core.api.service.ServiceFacade
import com.sphereon.core.compat.JsExportCompat

@JsExportCompat
interface CatalogQueryFacade : ServiceFacade {
    override val serviceId: String get() = "catalog.query"

    suspend fun listCatalogs(args: ListCatalogsArgs): IdkResult<AttestationCatalogList, IdkError>

    suspend fun getCatalog(args: CatalogIdArgs): IdkResult<AttestationCatalog, IdkError>

    suspend fun listSchemas(args: ListSchemasArgs): IdkResult<PaginatedSchemaList, IdkError>

    suspend fun getSchema(args: SchemaIdArgs): IdkResult<SchemaMeta, IdkError>

    suspend fun viewType(args: SchemaIdArgs): IdkResult<CatalogTypeView, IdkError>

    suspend fun getSchemaFormat(args: SchemaFormatArgs): IdkResult<CatalogDocument, IdkError>

    suspend fun getSchemaRulebook(args: SchemaRulebookArgs): IdkResult<CatalogDocument, IdkError>
}

@JsExportCompat
interface CatalogManagementFacade : ServiceFacade {
    override val serviceId: String get() = "catalog.management"

    suspend fun createCatalog(args: CreateCatalogArgs): IdkResult<AttestationCatalog, IdkError>

    suspend fun updateCatalog(args: UpdateCatalogArgs): IdkResult<AttestationCatalog, IdkError>

    suspend fun publishCatalog(args: CatalogIdArgs): IdkResult<AttestationCatalog, IdkError>

    suspend fun disableCatalog(args: CatalogIdArgs): IdkResult<AttestationCatalog, IdkError>

    suspend fun createSchema(args: CreateSchemaArgs): IdkResult<SchemaMeta, IdkError>

    suspend fun updateSchema(args: UpdateSchemaArgs): IdkResult<SchemaMeta, IdkError>

    suspend fun deleteSchema(args: SchemaIdArgs): IdkResult<Unit, IdkError>

    suspend fun linkSchema(args: LinkSchemaArgs): IdkResult<SchemaMeta, IdkError>

    suspend fun importRemote(args: ImportRemoteCatalogArgs): IdkResult<CatalogImportReport, IdkError>

    suspend fun importRulebooks(args: ImportRulebooksArgs): IdkResult<CatalogImportReport, IdkError>
}

@JsExportCompat
interface CatalogResolutionFacade : ServiceFacade {
    override val serviceId: String get() = "catalog.resolution"

    suspend fun resolve(args: ResolveAttestationTypeArgs): IdkResult<PaginatedSchemaList, IdkError>

    suspend fun evaluate(args: EvaluateCatalogVerificationArgs): IdkResult<CatalogVerificationDecision, IdkError>
}
