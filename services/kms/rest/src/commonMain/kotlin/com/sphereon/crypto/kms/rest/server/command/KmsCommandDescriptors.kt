package com.sphereon.crypto.kms.rest.server.command

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.kms.rest.api.command.DeleteKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GenerateKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.GetKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ImportKeyServiceCommand
import com.sphereon.crypto.kms.rest.api.command.ListKeysServiceCommand
import com.sphereon.crypto.kms.rest.api.command.RegisterKeyReferenceServiceCommand
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface KmsCommandDescriptors {
    @Provides @IntoMap
    @StringKey(GetKeyServiceCommand.COMMAND_ID)
    fun getKeyService(impl: GetKeyServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ListKeysServiceCommand.COMMAND_ID)
    fun listKeysService(impl: ListKeysServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(ImportKeyServiceCommand.COMMAND_ID)
    fun importKeyService(impl: ImportKeyServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(GenerateKeyServiceCommand.COMMAND_ID)
    fun generateKeyService(impl: GenerateKeyServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(DeleteKeyServiceCommand.COMMAND_ID)
    fun deleteKeyService(impl: DeleteKeyServiceCommandImpl): ServiceCommand<*, *, *> = impl

    @Provides @IntoMap
    @StringKey(RegisterKeyReferenceServiceCommand.COMMAND_ID)
    fun registerKeyReferenceService(impl: RegisterKeyReferenceServiceCommandImpl): ServiceCommand<*, *, *> = impl
}
