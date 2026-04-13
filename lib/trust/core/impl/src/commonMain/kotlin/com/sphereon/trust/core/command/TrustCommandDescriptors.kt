/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.command

import com.sphereon.core.api.service.RegistrableServiceCommandDescriptor
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

@ContributesTo(SessionScope::class)
interface TrustCommandDescriptors {

    @Provides @IntoSet
    fun validateTrust(impl: Lazy<ValidateTrustCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(ValidateTrustCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun getTrustAnchors(impl: Lazy<GetTrustAnchorsCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(GetTrustAnchorsCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun refreshTrustAnchors(impl: Lazy<RefreshTrustAnchorsCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(RefreshTrustAnchorsCommand.COMMAND_ID) { impl.value }

    @Provides @IntoSet
    fun checkRevocation(impl: Lazy<CheckRevocationCommandImpl>): RegistrableServiceCommandDescriptor =
        RegistrableServiceCommandDescriptor.of(CheckRevocationCommand.COMMAND_ID) { impl.value }
}
