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

package com.sphereon.data.store.schema.registry.impl.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.data.store.schema.registry.command.CreateSchemaServiceCommand
import com.sphereon.data.store.schema.registry.command.DeleteSchemaServiceCommand
import com.sphereon.data.store.schema.registry.command.FindSchemaByNameServiceCommand
import com.sphereon.data.store.schema.registry.command.GetContentServiceCommand
import com.sphereon.data.store.schema.registry.command.GetSchemaServiceCommand
import com.sphereon.data.store.schema.registry.command.ImportExternalServiceCommand
import com.sphereon.data.store.schema.registry.command.ListSchemasServiceCommand
import com.sphereon.data.store.schema.registry.command.RefreshCachedServiceCommand
import com.sphereon.data.store.schema.registry.command.ResolveByPathServiceCommand
import com.sphereon.data.store.schema.registry.command.UpdateSchemaServiceCommand
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface SchemaRegistryCommandDescriptors {
    @Provides @IntoMap
    @StringKey(CreateSchemaServiceCommand.COMMAND_ID)
    fun createSchema(impl: CreateSchemaServiceCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(GetSchemaServiceCommand.COMMAND_ID)
    fun getSchema(impl: GetSchemaServiceCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(FindSchemaByNameServiceCommand.COMMAND_ID)
    fun findSchemaByName(impl: FindSchemaByNameServiceCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(ListSchemasServiceCommand.COMMAND_ID)
    fun listSchemas(impl: ListSchemasServiceCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(UpdateSchemaServiceCommand.COMMAND_ID)
    fun updateSchema(impl: UpdateSchemaServiceCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(DeleteSchemaServiceCommand.COMMAND_ID)
    fun deleteSchema(impl: DeleteSchemaServiceCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(GetContentServiceCommand.COMMAND_ID)
    fun getContent(impl: GetContentServiceCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(ResolveByPathServiceCommand.COMMAND_ID)
    fun resolveByPath(impl: ResolveByPathServiceCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(ImportExternalServiceCommand.COMMAND_ID)
    fun importExternal(impl: ImportExternalServiceCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(RefreshCachedServiceCommand.COMMAND_ID)
    fun refreshCached(impl: RefreshCachedServiceCommandImpl): ServiceCommand<*, *> = impl
}
