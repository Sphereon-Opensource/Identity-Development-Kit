package com.sphereon.crypto.kms.rest.server.command

import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GenerateKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GetKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ListKeysServiceCommand
import com.sphereon.crypto.kms.rest.api.command.StoreKeyServiceCommand
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface KmsCommandDescriptors {

    @Provides @IntoSet
    fun getKeyService(impl: Lazy<GetKeyServiceCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(GetKeyServiceCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun listKeysService(impl: Lazy<ListKeysServiceCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ListKeysServiceCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun storeKeyService(impl: Lazy<StoreKeyServiceCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(StoreKeyServiceCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun generateKeyService(impl: Lazy<GenerateKeyServiceCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(GenerateKeyServiceCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun deleteKeyService(impl: Lazy<DeleteKeyServiceCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(DeleteKeyServiceCommand.COMMAND_ID) { impl.value }
}
