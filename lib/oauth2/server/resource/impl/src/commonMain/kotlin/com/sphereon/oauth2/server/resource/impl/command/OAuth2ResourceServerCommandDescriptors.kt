package com.sphereon.oauth2.server.resource.impl.command

import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.resource.command.IntrospectTokenCommand
import com.sphereon.oauth2.server.resource.command.ValidateAccessTokenCommand
import com.sphereon.oauth2.server.resource.command.VerifyDpopProofCommand
import com.sphereon.oauth2.server.resource.command.VerifyJwtCommand
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface OAuth2ResourceServerCommandDescriptors {

    @Provides @IntoSet
    fun resourceServerIntrospectToken(impl: Lazy<ResourceServerIntrospectTokenCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(IntrospectTokenCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun validateAccessToken(impl: Lazy<ValidateAccessTokenCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ValidateAccessTokenCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun resourceServerVerifyDpopProof(impl: Lazy<ResourceServerVerifyDpopProofCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyDpopProofCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun verifyJwt(impl: Lazy<VerifyJwtCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyJwtCommand.COMMAND_ID) { impl.value }
}
