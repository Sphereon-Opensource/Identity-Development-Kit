package com.sphereon.did.resolver.impl

import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.did.resolver.DereferenceDidCommand
import com.sphereon.did.resolver.ResolveDidCommand
import com.sphereon.did.resolver.ResolveVerificationMethodCommand
import com.sphereon.did.resolver.ResolveVerificationMethodsByPurposeCommand
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface DidResolverCommandDescriptors {

    @Provides @IntoSet
    fun resolveDid(impl: Lazy<ResolveDidCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ResolveDidCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun dereferenceDid(impl: Lazy<DereferenceDidCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(DereferenceDidCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun resolveVerificationMethod(impl: Lazy<ResolveVerificationMethodCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ResolveVerificationMethodCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun resolveVerificationMethodsByPurpose(impl: Lazy<ResolveVerificationMethodsByPurposeCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ResolveVerificationMethodsByPurposeCommand.COMMAND_ID) { impl.value }
}
