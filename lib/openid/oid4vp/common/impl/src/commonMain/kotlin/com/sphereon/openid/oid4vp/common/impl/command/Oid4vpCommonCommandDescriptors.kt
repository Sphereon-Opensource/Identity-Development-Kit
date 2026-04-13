package com.sphereon.openid.oid4vp.common.impl.command

import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.common.ParseClientIdCommand
import com.sphereon.openid.oid4vp.common.ParseTransactionDataCommand
import com.sphereon.openid.oid4vp.common.ResolveScopeCommand
import com.sphereon.openid.oid4vp.common.ValidateClientIdCommand
import com.sphereon.openid.oid4vp.common.VerifyTransactionDataCommand
import com.sphereon.openid.oid4vp.common.VerifyVerifierAttestationCommand
import com.sphereon.openid.oid4vp.common.impl.ParseClientIdCommandImpl
import com.sphereon.openid.oid4vp.common.impl.ParseTransactionDataCommandImpl
import com.sphereon.openid.oid4vp.common.impl.ResolveScopeCommandImpl
import com.sphereon.openid.oid4vp.common.impl.ValidateClientIdCommandImpl
import com.sphereon.openid.oid4vp.common.impl.VerifyTransactionDataCommandImpl
import com.sphereon.openid.oid4vp.common.impl.VerifyVerifierAttestationCommandImpl
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

@ContributesTo(SessionScope::class)
interface Oid4vpCommonCommandDescriptors {

    @Provides @IntoSet
    fun parseClientId(impl: Lazy<ParseClientIdCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ParseClientIdCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun verifyTransactionData(impl: Lazy<VerifyTransactionDataCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyTransactionDataCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun parseTransactionData(impl: Lazy<ParseTransactionDataCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ParseTransactionDataCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun resolveScope(impl: Lazy<ResolveScopeCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ResolveScopeCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun verifyVerifierAttestation(impl: Lazy<VerifyVerifierAttestationCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(VerifyVerifierAttestationCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun validateClientId(impl: Lazy<ValidateClientIdCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ValidateClientIdCommand.COMMAND_ID) { impl.value }
}
