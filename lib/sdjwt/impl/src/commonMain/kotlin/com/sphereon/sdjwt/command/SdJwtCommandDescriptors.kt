package com.sphereon.sdjwt.command

import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.di.session.SessionScope
import com.sphereon.sdjwt.vc.command.ResolveIssuerMetadataCommand
import com.sphereon.sdjwt.vc.command.ResolveIssuerMetadataCommandImpl
import com.sphereon.sdjwt.vc.command.ResolveTypeMetadataCommand
import com.sphereon.sdjwt.vc.command.ResolveTypeMetadataCommandImpl
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcCommand
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcCommandImpl
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcPresentationCommand
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcPresentationCommandImpl
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface SdJwtCommandDescriptors {

    @Provides @IntoSet
    fun verifySdJwtVc(impl: Lazy<VerifySdJwtVcCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifySdJwtVcCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun verifySdJwtVcPresentation(impl: Lazy<VerifySdJwtVcPresentationCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifySdJwtVcPresentationCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun resolveTypeMetadata(impl: Lazy<ResolveTypeMetadataCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ResolveTypeMetadataCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun resolveIssuerMetadata(impl: Lazy<ResolveIssuerMetadataCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ResolveIssuerMetadataCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun issueSdJwt(impl: Lazy<IssueSdJwtCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(IssueSdJwtCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun presentSdJwt(impl: Lazy<PresentSdJwtCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(PresentSdJwtCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun verifySdJwt(impl: Lazy<VerifySdJwtCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifySdJwtCommand.COMMAND_ID) { impl.value }
}
