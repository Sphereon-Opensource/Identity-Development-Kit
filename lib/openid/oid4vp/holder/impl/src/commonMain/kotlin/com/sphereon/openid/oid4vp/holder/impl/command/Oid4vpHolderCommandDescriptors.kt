package com.sphereon.openid.oid4vp.holder.impl.command

import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.holder.CreateAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.holder.ParseAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.holder.ResolveAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.holder.SubmitAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.holder.command.ResolveClientMetadataCommand
import com.sphereon.openid.oid4vp.holder.impl.CreateAuthorizationResponseCommandImpl
import com.sphereon.openid.oid4vp.holder.impl.ParseAuthorizationRequestCommandImpl
import com.sphereon.openid.oid4vp.holder.impl.ResolveAuthorizationRequestCommandImpl
import com.sphereon.openid.oid4vp.holder.impl.SubmitAuthorizationResponseCommandImpl
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface Oid4vpHolderCommandDescriptors {

    @Provides @IntoSet
    fun resolveClientMetadata(impl: Lazy<ResolveClientMetadataCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ResolveClientMetadataCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun resolveAuthorizationRequest(impl: Lazy<ResolveAuthorizationRequestCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ResolveAuthorizationRequestCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun submitAuthorizationResponse(impl: Lazy<SubmitAuthorizationResponseCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(SubmitAuthorizationResponseCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createAuthorizationResponse(impl: Lazy<CreateAuthorizationResponseCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateAuthorizationResponseCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun parseAuthorizationRequest(impl: Lazy<ParseAuthorizationRequestCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ParseAuthorizationRequestCommand.COMMAND_ID) { impl.value }
}
