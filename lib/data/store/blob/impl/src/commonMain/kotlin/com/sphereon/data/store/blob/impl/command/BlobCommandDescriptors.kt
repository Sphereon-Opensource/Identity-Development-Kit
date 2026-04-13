package com.sphereon.data.store.blob.impl.command

import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

/**
 * DI descriptor registration for all blob store commands.
 *
 * Uses lazy instantiation — commands are only created when first requested.
 */
@ContributesTo(SessionScope::class)
interface BlobCommandDescriptors {

    @Provides @IntoSet
    fun blobStorePut(impl: Lazy<BlobStorePutCommand>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(BlobStorePutCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun blobStoreGet(impl: Lazy<BlobStoreGetCommand>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(BlobStoreGetCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun blobStoreDelete(impl: Lazy<BlobStoreDeleteCommand>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(BlobStoreDeleteCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun blobStoreStat(impl: Lazy<BlobStoreStatCommand>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(BlobStoreStatCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun blobStoreList(impl: Lazy<BlobStoreListCommand>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(BlobStoreListCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun blobStoreCopy(impl: Lazy<BlobStoreCopyCommand>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(BlobStoreCopyCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun blobStoreMove(impl: Lazy<BlobStoreMoveCommand>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(BlobStoreMoveCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun casStore(impl: Lazy<CasStoreCommand>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CasStoreCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun casGet(impl: Lazy<CasGetCommand>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CasGetCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun casVerify(impl: Lazy<CasVerifyCommand>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CasVerifyCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun metadataSearch(impl: Lazy<MetadataSearchCommand>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(MetadataSearchCommand.COMMAND_ID) { impl.value }
}
