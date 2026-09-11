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
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface CatalogCommandDescriptors {
    @Provides @IntoMap
    @StringKey(ListCatalogsCommand.COMMAND_ID)
    fun listCatalogs(impl: ListCatalogsCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CreateCatalogCommand.COMMAND_ID)
    fun createCatalog(impl: CreateCatalogCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetCatalogCommand.COMMAND_ID)
    fun getCatalog(impl: GetCatalogCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(UpdateCatalogCommand.COMMAND_ID)
    fun updateCatalog(impl: UpdateCatalogCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(PublishCatalogCommand.COMMAND_ID)
    fun publishCatalog(impl: PublishCatalogCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(DisableCatalogCommand.COMMAND_ID)
    fun disableCatalog(impl: DisableCatalogCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ListSchemasCommand.COMMAND_ID)
    fun listSchemas(impl: ListSchemasCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetSchemaCommand.COMMAND_ID)
    fun getSchema(impl: GetSchemaCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetCatalogTypeViewCommand.COMMAND_ID)
    fun viewType(impl: GetCatalogTypeViewCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CreateSchemaCommand.COMMAND_ID)
    fun createSchema(impl: CreateSchemaCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(UpdateSchemaCommand.COMMAND_ID)
    fun updateSchema(impl: UpdateSchemaCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(DeleteSchemaCommand.COMMAND_ID)
    fun deleteSchema(impl: DeleteSchemaCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(LinkSchemaCommand.COMMAND_ID)
    fun linkSchema(impl: LinkSchemaCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetSchemaFormatCommand.COMMAND_ID)
    fun getSchemaFormat(impl: GetSchemaFormatCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GetSchemaRulebookCommand.COMMAND_ID)
    fun getSchemaRulebook(impl: GetSchemaRulebookCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ImportRemoteCatalogCommand.COMMAND_ID)
    fun importRemote(impl: ImportRemoteCatalogCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ImportRulebooksCommand.COMMAND_ID)
    fun importRulebooks(impl: ImportRulebooksCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ResolveAttestationTypeCommand.COMMAND_ID)
    fun resolveType(impl: ResolveAttestationTypeCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(EvaluateCatalogVerificationCommand.COMMAND_ID)
    fun evaluate(impl: EvaluateCatalogVerificationCommandImpl): ServiceCommand<*, *, *> = impl
}
