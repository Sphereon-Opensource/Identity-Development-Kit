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

package com.sphereon.data.store.blob.impl.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

/**
 * DI descriptor registration for all blob store commands.
 *
 * Uses Metro @IntoMap multibinding to register commands by their COMMAND_ID.
 */
@ContributesTo(SessionScope::class)
interface BlobCommandDescriptors {
    @Provides @IntoMap
    @StringKey(BlobStorePutCommand.COMMAND_ID)
    fun blobStorePut(impl: BlobStorePutCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(BlobStoreGetCommand.COMMAND_ID)
    fun blobStoreGet(impl: BlobStoreGetCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(BlobStoreDeleteCommand.COMMAND_ID)
    fun blobStoreDelete(impl: BlobStoreDeleteCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(BlobStoreStatCommand.COMMAND_ID)
    fun blobStoreStat(impl: BlobStoreStatCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(BlobStoreListCommand.COMMAND_ID)
    fun blobStoreList(impl: BlobStoreListCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(BlobStoreCopyCommand.COMMAND_ID)
    fun blobStoreCopy(impl: BlobStoreCopyCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(BlobStoreMoveCommand.COMMAND_ID)
    fun blobStoreMove(impl: BlobStoreMoveCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CasStoreCommand.COMMAND_ID)
    fun casStore(impl: CasStoreCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CasGetCommand.COMMAND_ID)
    fun casGet(impl: CasGetCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(CasVerifyCommand.COMMAND_ID)
    fun casVerify(impl: CasVerifyCommand): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(MetadataSearchCommand.COMMAND_ID)
    fun metadataSearch(impl: MetadataSearchCommand): ServiceCommand<*, *, *> = impl
}
