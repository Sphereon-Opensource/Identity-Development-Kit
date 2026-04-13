package com.sphereon.did.resolver

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface DidResolverCommandBindings {

    @Provides
    fun resolveDidCommand(registry: SessionScopedCommandRegistry): ResolveDidCommand =
        registry.get(ResolveDidCommand.COMMAND_ID) as? ResolveDidCommand
            ?: error("No binding for ${ResolveDidCommand.COMMAND_ID}")

    @Provides
    fun dereferenceDidCommand(registry: SessionScopedCommandRegistry): DereferenceDidCommand =
        registry.get(DereferenceDidCommand.COMMAND_ID) as? DereferenceDidCommand
            ?: error("No binding for ${DereferenceDidCommand.COMMAND_ID}")

    @Provides
    fun resolveVerificationMethodCommand(registry: SessionScopedCommandRegistry): ResolveVerificationMethodCommand =
        registry.get(ResolveVerificationMethodCommand.COMMAND_ID) as? ResolveVerificationMethodCommand
            ?: error("No binding for ${ResolveVerificationMethodCommand.COMMAND_ID}")

    @Provides
    fun resolveVerificationMethodsByPurposeCommand(registry: SessionScopedCommandRegistry): ResolveVerificationMethodsByPurposeCommand =
        registry.get(ResolveVerificationMethodsByPurposeCommand.COMMAND_ID) as? ResolveVerificationMethodsByPurposeCommand
            ?: error("No binding for ${ResolveVerificationMethodsByPurposeCommand.COMMAND_ID}")
}
