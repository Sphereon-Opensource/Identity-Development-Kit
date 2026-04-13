package com.sphereon.openid.oid4vp.verifier.impl.command

import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriCommand
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.verifier.CreateSignedAuthorizationRequestCommand
import com.sphereon.openid.oid4vp.verifier.HandleDirectPostResponseCommand
import com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.RetrieveAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.VerifyHolderBindingCommand
import com.sphereon.openid.oid4vp.verifier.impl.BuildAuthorizationRequestUriCommandImpl
import com.sphereon.openid.oid4vp.verifier.impl.CreateAuthorizationRequestCommandImpl
import com.sphereon.openid.oid4vp.verifier.impl.CreateSignedAuthorizationRequestCommandImpl
import com.sphereon.openid.oid4vp.verifier.impl.HandleDirectPostResponseCommandImpl
import com.sphereon.openid.oid4vp.verifier.impl.ParseAuthorizationResponseCommandImpl
import com.sphereon.openid.oid4vp.verifier.impl.RetrieveAuthorizationResponseCommandImpl
import com.sphereon.openid.oid4vp.verifier.impl.ValidateAuthorizationResponseCommandImpl
import com.sphereon.openid.oid4vp.verifier.impl.VerifyHolderBindingCommandImpl
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface Oid4vpVerifierCommandDescriptors {

    @Provides @IntoSet
    fun createSignedAuthorizationRequest(impl: Lazy<CreateSignedAuthorizationRequestCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateSignedAuthorizationRequestCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun buildAuthorizationRequestUri(impl: Lazy<BuildAuthorizationRequestUriCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(BuildAuthorizationRequestUriCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun retrieveAuthorizationResponse(impl: Lazy<RetrieveAuthorizationResponseCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(RetrieveAuthorizationResponseCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun handleDirectPostResponse(impl: Lazy<HandleDirectPostResponseCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(HandleDirectPostResponseCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun verifyHolderBinding(impl: Lazy<VerifyHolderBindingCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyHolderBindingCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun validateAuthorizationResponse(impl: Lazy<ValidateAuthorizationResponseCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ValidateAuthorizationResponseCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun oid4vpParseAuthorizationResponse(impl: Lazy<ParseAuthorizationResponseCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ParseAuthorizationResponseCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun createAuthorizationRequest(impl: Lazy<CreateAuthorizationRequestCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CreateAuthorizationRequestCommand.COMMAND_ID) { impl.value }
}
