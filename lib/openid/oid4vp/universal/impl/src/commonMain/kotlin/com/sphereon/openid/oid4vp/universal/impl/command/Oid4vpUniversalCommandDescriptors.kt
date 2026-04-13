package com.sphereon.openid.oid4vp.universal.impl.command

import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.universal.CreateAuthRequestServiceCommand
import com.sphereon.openid.oid4vp.universal.DeleteAuthRequestServiceCommand
import com.sphereon.openid.oid4vp.universal.GetAuthRequestStatusServiceCommand
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface Oid4vpUniversalCommandDescriptors {

    @Provides @IntoSet
    fun createAuthRequest(impl: Lazy<CreateAuthRequestServiceCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateAuthRequestServiceCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun getAuthRequestStatus(impl: Lazy<GetAuthRequestStatusServiceCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(GetAuthRequestStatusServiceCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun deleteAuthRequest(impl: Lazy<DeleteAuthRequestServiceCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(DeleteAuthRequestServiceCommand.COMMAND_ID) { impl.value }
}
