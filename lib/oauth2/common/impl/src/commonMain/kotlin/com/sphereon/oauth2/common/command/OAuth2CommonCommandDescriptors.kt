package com.sphereon.oauth2.common.command

import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.jarm.CreateJarmResponseCommand
import com.sphereon.oauth2.common.jarm.CreateJarmResponseCommandImpl
import com.sphereon.oauth2.common.jarm.VerifyJarmResponseCommand
import com.sphereon.oauth2.common.jarm.VerifyJarmResponseCommandImpl
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface OAuth2CommonCommandDescriptors {

    @Provides @IntoSet
    fun validateIdToken(impl: Lazy<ValidateIdTokenCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ValidateIdTokenCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun verifyJarmResponse(impl: Lazy<VerifyJarmResponseCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyJarmResponseCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createJarmResponse(impl: Lazy<CreateJarmResponseCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateJarmResponseCommand.COMMAND_ID) { impl.value }
}
