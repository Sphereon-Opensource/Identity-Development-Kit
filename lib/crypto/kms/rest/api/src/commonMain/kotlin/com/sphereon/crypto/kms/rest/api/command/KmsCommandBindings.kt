package com.sphereon.crypto.kms.rest.api.command

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface KmsCommandBindings {
    @Provides
    fun kmsGetKey(registry: SessionScopedCommandRegistry): GetKeyServiceCommand =
        registry.get(GetKeyServiceCommand.COMMAND_ID) as? GetKeyServiceCommand
            ?: error("No binding for ${GetKeyServiceCommand.COMMAND_ID}")

    @Provides
    fun kmsListKeys(registry: SessionScopedCommandRegistry): ListKeysServiceCommand =
        registry.get(ListKeysServiceCommand.COMMAND_ID) as? ListKeysServiceCommand
            ?: error("No binding for ${ListKeysServiceCommand.COMMAND_ID}")

    @Provides
    fun kmsStoreKey(registry: SessionScopedCommandRegistry): StoreKeyServiceCommand =
        registry.get(StoreKeyServiceCommand.COMMAND_ID) as? StoreKeyServiceCommand
            ?: error("No binding for ${StoreKeyServiceCommand.COMMAND_ID}")

    @Provides
    fun kmsGenerateKey(registry: SessionScopedCommandRegistry): GenerateKeyServiceCommand =
        registry.get(GenerateKeyServiceCommand.COMMAND_ID) as? GenerateKeyServiceCommand
            ?: error("No binding for ${GenerateKeyServiceCommand.COMMAND_ID}")

    @Provides
    fun kmsDeleteKey(registry: SessionScopedCommandRegistry): DeleteKeyServiceCommand =
        registry.get(DeleteKeyServiceCommand.COMMAND_ID) as? DeleteKeyServiceCommand
            ?: error("No binding for ${DeleteKeyServiceCommand.COMMAND_ID}")
}
