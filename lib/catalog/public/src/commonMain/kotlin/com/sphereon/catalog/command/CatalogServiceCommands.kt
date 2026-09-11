/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.command

import com.sphereon.catalog.model.AttestationCatalog
import com.sphereon.catalog.model.AttestationCatalogList
import com.sphereon.catalog.model.CatalogDocument
import com.sphereon.catalog.model.CatalogImportReport
import com.sphereon.catalog.model.CatalogTypeView
import com.sphereon.catalog.model.CatalogVerificationDecision
import com.sphereon.catalog.model.PaginatedSchemaList
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.service.ActionType
import com.sphereon.core.api.service.PublicApiCommand
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.compat.JsExportCompat

@JsExportCompat
interface ListCatalogsCommand :
    ServiceCommand<ListCatalogsArgs, AttestationCatalogList, IdkError>,
    PublicApiCommand {
    override val actionType: ActionType get() = ActionType.LIST
    override val commandId: String get() = COMMAND_ID
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "catalog.catalogs.list"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/catalogs",
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "listCatalogs",
                tags = setOf("Catalogs"),
            )
    }
}

@JsExportCompat
interface CreateCatalogCommand :
    ServiceCommand<CreateCatalogArgs, AttestationCatalog, IdkError>,
    PublicApiCommand {
    override val actionType: ActionType get() = ActionType.CREATE
    override val commandId: String get() = COMMAND_ID
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "catalog.catalogs.create"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/catalogs",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "createCatalog",
                tags = setOf("Catalogs"),
            )
    }
}

@JsExportCompat
interface GetCatalogCommand :
    ServiceCommand<CatalogIdArgs, AttestationCatalog, IdkError>,
    PublicApiCommand {
    override val actionType: ActionType get() = ActionType.READ
    override val commandId: String get() = COMMAND_ID
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "catalog.catalogs.get"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/catalogs/{catalogId}",
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "getCatalog",
                tags = setOf("Catalogs"),
            )
    }
}

@JsExportCompat
interface UpdateCatalogCommand :
    ServiceCommand<UpdateCatalogArgs, AttestationCatalog, IdkError>,
    PublicApiCommand {
    override val actionType: ActionType get() = ActionType.UPDATE
    override val commandId: String get() = COMMAND_ID
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "catalog.catalogs.update"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.PUT,
                pathPattern = "/catalogs/{catalogId}",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "updateCatalog",
                tags = setOf("Catalogs"),
            )
    }
}

@JsExportCompat
interface PublishCatalogCommand :
    ServiceCommand<CatalogIdArgs, AttestationCatalog, IdkError>,
    PublicApiCommand {
    override val actionType: ActionType get() = ActionType.UPDATE
    override val commandId: String get() = COMMAND_ID
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "catalog.catalogs.publish"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/catalogs/{catalogId}/publish",
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "publishCatalog",
                tags = setOf("Catalogs"),
            )
    }
}

@JsExportCompat
interface DisableCatalogCommand :
    ServiceCommand<CatalogIdArgs, AttestationCatalog, IdkError>,
    PublicApiCommand {
    override val actionType: ActionType get() = ActionType.UPDATE
    override val commandId: String get() = COMMAND_ID
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "catalog.catalogs.disable"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/catalogs/{catalogId}/disable",
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "disableCatalog",
                tags = setOf("Catalogs"),
            )
    }
}

@JsExportCompat
interface ListSchemasCommand :
    ServiceCommand<ListSchemasArgs, PaginatedSchemaList, IdkError>,
    PublicApiCommand {
    override val actionType: ActionType get() = ActionType.LIST
    override val commandId: String get() = COMMAND_ID
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "catalog.schemas.list"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/schemas",
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "listSchemas",
                tags = setOf("Schemas"),
            )
    }
}

@JsExportCompat
interface GetCatalogTypeViewCommand :
    ServiceCommand<SchemaIdArgs, CatalogTypeView, IdkError>,
    PublicApiCommand {
    override val actionType: ActionType get() = ActionType.READ
    override val commandId: String get() = COMMAND_ID
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "catalog.types.view"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/catalogs/{catalogId}/schemas/{schemaId}/view",
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "getCatalogTypeView",
                tags = setOf("Schemas"),
            )
    }
}

@JsExportCompat
interface GetSchemaCommand :
    ServiceCommand<SchemaIdArgs, SchemaMeta, IdkError>,
    PublicApiCommand {
    override val actionType: ActionType get() = ActionType.READ
    override val commandId: String get() = COMMAND_ID
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "catalog.schemas.get"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/schemas/{schemaId}",
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "getSchema",
                tags = setOf("Schemas"),
            )
    }
}

@JsExportCompat
interface CreateSchemaCommand :
    ServiceCommand<CreateSchemaArgs, SchemaMeta, IdkError>,
    PublicApiCommand {
    override val actionType: ActionType get() = ActionType.CREATE
    override val commandId: String get() = COMMAND_ID
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "catalog.schemas.create"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/catalogs/{catalogId}/schemas",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "createSchema",
                tags = setOf("Schemas"),
            )
    }
}

@JsExportCompat
interface UpdateSchemaCommand :
    ServiceCommand<UpdateSchemaArgs, SchemaMeta, IdkError>,
    PublicApiCommand {
    override val actionType: ActionType get() = ActionType.UPDATE
    override val commandId: String get() = COMMAND_ID
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "catalog.schemas.update"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.PUT,
                pathPattern = "/catalogs/{catalogId}/schemas/{schemaId}",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "updateSchema",
                tags = setOf("Schemas"),
            )
    }
}

@JsExportCompat
interface DeleteSchemaCommand :
    ServiceCommand<SchemaIdArgs, Unit, IdkError>,
    PublicApiCommand {
    override val actionType: ActionType get() = ActionType.DELETE
    override val commandId: String get() = COMMAND_ID
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "catalog.schemas.delete"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.DELETE,
                pathPattern = "/catalogs/{catalogId}/schemas/{schemaId}",
                commandId = COMMAND_ID,
                operationId = "deleteSchema",
                tags = setOf("Schemas"),
            )
    }
}

@JsExportCompat
interface LinkSchemaCommand :
    ServiceCommand<LinkSchemaArgs, SchemaMeta, IdkError>,
    PublicApiCommand {
    override val actionType: ActionType get() = ActionType.CREATE
    override val commandId: String get() = COMMAND_ID
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "catalog.schemas.link"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/catalogs/{catalogId}/schemas/link",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "linkSchema",
                tags = setOf("Schemas"),
            )
    }
}

@JsExportCompat
interface GetSchemaFormatCommand :
    ServiceCommand<SchemaFormatArgs, CatalogDocument, IdkError>,
    PublicApiCommand {
    override val actionType: ActionType get() = ActionType.READ
    override val commandId: String get() = COMMAND_ID
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "catalog.schemas.format"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/schemas/{schemaId}/formats/{format}",
                commandId = COMMAND_ID,
                operationId = "getSchemaFormat",
                tags = setOf("Schemas"),
            )
    }
}

@JsExportCompat
interface GetSchemaRulebookCommand :
    ServiceCommand<SchemaRulebookArgs, CatalogDocument, IdkError>,
    PublicApiCommand {
    override val actionType: ActionType get() = ActionType.READ
    override val commandId: String get() = COMMAND_ID
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "catalog.schemas.rulebook"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/schemas/{schemaId}/rulebook",
                commandId = COMMAND_ID,
                operationId = "getSchemaRulebook",
                tags = setOf("Schemas"),
            )
    }
}

@JsExportCompat
interface ImportRemoteCatalogCommand :
    ServiceCommand<ImportRemoteCatalogArgs, CatalogImportReport, IdkError>,
    PublicApiCommand {
    override val actionType: ActionType get() = ActionType.CREATE
    override val commandId: String get() = COMMAND_ID
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "catalog.imports.remote"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/catalogs/{catalogId}/imports/remote",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "importRemoteCatalog",
                tags = setOf("Imports"),
            )
    }
}

@JsExportCompat
interface ImportRulebooksCommand :
    ServiceCommand<ImportRulebooksArgs, CatalogImportReport, IdkError>,
    PublicApiCommand {
    override val actionType: ActionType get() = ActionType.CREATE
    override val commandId: String get() = COMMAND_ID
    override val httpEndpoint: HttpEndpointDescriptor get() = ENDPOINT

    companion object {
        const val COMMAND_ID = "catalog.imports.rulebooks"
        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/catalogs/{catalogId}/imports/rulebooks",
                consumes = setOf(MediaType.ApplicationJson),
                produces = setOf(MediaType.ApplicationJson),
                commandId = COMMAND_ID,
                operationId = "importRulebooks",
                tags = setOf("Imports"),
            )
    }
}

@JsExportCompat
interface ResolveAttestationTypeCommand : ServiceCommand<ResolveAttestationTypeArgs, PaginatedSchemaList, IdkError> {
    override val actionType: ActionType get() = ActionType.READ
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "catalog.resolution.resolve"
    }
}

@JsExportCompat
interface EvaluateCatalogVerificationCommand : ServiceCommand<EvaluateCatalogVerificationArgs, CatalogVerificationDecision, IdkError> {
    override val actionType: ActionType get() = ActionType.READ
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "catalog.verification.evaluate"
    }
}
