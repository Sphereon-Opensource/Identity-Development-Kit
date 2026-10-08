/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl.command

import com.sphereon.catalog.command.CreateCatalogCommand
import com.sphereon.catalog.command.CreateSchemaCommand
import com.sphereon.catalog.command.DeleteSchemaCommand
import com.sphereon.catalog.command.DisableCatalogCommand
import com.sphereon.catalog.command.EvaluateCatalogVerificationCommand
import com.sphereon.catalog.command.GetCatalogCommand
import com.sphereon.catalog.command.GetCatalogTypeViewCommand
import com.sphereon.catalog.command.GetSchemaCommand
import com.sphereon.catalog.command.GetSchemaFormatCommand
import com.sphereon.catalog.command.GetSchemaRulebookCommand
import com.sphereon.catalog.command.ImportRemoteCatalogCommand
import com.sphereon.catalog.command.ImportRulebooksCommand
import com.sphereon.catalog.command.LinkSchemaCommand
import com.sphereon.catalog.command.ListCatalogsCommand
import com.sphereon.catalog.command.ListSchemasCommand
import com.sphereon.catalog.command.PublishCatalogCommand
import com.sphereon.catalog.command.ResolveAttestationTypeCommand
import com.sphereon.catalog.command.UpdateCatalogCommand
import com.sphereon.catalog.command.UpdateSchemaCommand
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides

@ContributesTo(SessionScope::class)
interface CatalogCommandBindings {
    @Provides
    fun listCatalogs(registry: SessionScopedCommandRegistry): ListCatalogsCommand = registry.get(ListCatalogsCommand.COMMAND_ID) as ListCatalogsCommand

    @Provides
    fun createCatalog(registry: SessionScopedCommandRegistry): CreateCatalogCommand = registry.get(CreateCatalogCommand.COMMAND_ID) as CreateCatalogCommand

    @Provides
    fun getCatalog(registry: SessionScopedCommandRegistry): GetCatalogCommand = registry.get(GetCatalogCommand.COMMAND_ID) as GetCatalogCommand

    @Provides
    fun updateCatalog(registry: SessionScopedCommandRegistry): UpdateCatalogCommand = registry.get(UpdateCatalogCommand.COMMAND_ID) as UpdateCatalogCommand

    @Provides
    fun publishCatalog(registry: SessionScopedCommandRegistry): PublishCatalogCommand = registry.get(PublishCatalogCommand.COMMAND_ID) as PublishCatalogCommand

    @Provides
    fun disableCatalog(registry: SessionScopedCommandRegistry): DisableCatalogCommand = registry.get(DisableCatalogCommand.COMMAND_ID) as DisableCatalogCommand

    @Provides
    fun listSchemas(registry: SessionScopedCommandRegistry): ListSchemasCommand = registry.get(ListSchemasCommand.COMMAND_ID) as ListSchemasCommand

    @Provides
    fun getSchema(registry: SessionScopedCommandRegistry): GetSchemaCommand = registry.get(GetSchemaCommand.COMMAND_ID) as GetSchemaCommand

    @Provides
    fun viewType(registry: SessionScopedCommandRegistry): GetCatalogTypeViewCommand = registry.get(GetCatalogTypeViewCommand.COMMAND_ID) as GetCatalogTypeViewCommand

    @Provides
    fun createSchema(registry: SessionScopedCommandRegistry): CreateSchemaCommand = registry.get(CreateSchemaCommand.COMMAND_ID) as CreateSchemaCommand

    @Provides
    fun updateSchema(registry: SessionScopedCommandRegistry): UpdateSchemaCommand = registry.get(UpdateSchemaCommand.COMMAND_ID) as UpdateSchemaCommand

    @Provides
    fun deleteSchema(registry: SessionScopedCommandRegistry): DeleteSchemaCommand = registry.get(DeleteSchemaCommand.COMMAND_ID) as DeleteSchemaCommand

    @Provides
    fun linkSchema(registry: SessionScopedCommandRegistry): LinkSchemaCommand = registry.get(LinkSchemaCommand.COMMAND_ID) as LinkSchemaCommand

    @Provides
    fun getSchemaFormat(registry: SessionScopedCommandRegistry): GetSchemaFormatCommand = registry.get(GetSchemaFormatCommand.COMMAND_ID) as GetSchemaFormatCommand

    @Provides
    fun getSchemaRulebook(registry: SessionScopedCommandRegistry): GetSchemaRulebookCommand = registry.get(GetSchemaRulebookCommand.COMMAND_ID) as GetSchemaRulebookCommand

    @Provides
    fun importRemote(registry: SessionScopedCommandRegistry): ImportRemoteCatalogCommand = registry.get(ImportRemoteCatalogCommand.COMMAND_ID) as ImportRemoteCatalogCommand

    @Provides
    fun importRulebooks(registry: SessionScopedCommandRegistry): ImportRulebooksCommand = registry.get(ImportRulebooksCommand.COMMAND_ID) as ImportRulebooksCommand

    @Provides
    fun resolveType(registry: SessionScopedCommandRegistry): ResolveAttestationTypeCommand = registry.get(ResolveAttestationTypeCommand.COMMAND_ID) as ResolveAttestationTypeCommand

    @Provides
    fun evaluate(registry: SessionScopedCommandRegistry): EvaluateCatalogVerificationCommand = registry.get(EvaluateCatalogVerificationCommand.COMMAND_ID) as EvaluateCatalogVerificationCommand
}
