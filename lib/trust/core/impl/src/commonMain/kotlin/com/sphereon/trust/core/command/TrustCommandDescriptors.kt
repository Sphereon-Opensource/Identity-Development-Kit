/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.StringKey

@ContributesTo(SessionScope::class)
interface TrustCommandDescriptors {
    @Provides @IntoMap
    @StringKey(ValidateTrustCommand.COMMAND_ID)
    fun validateTrust(impl: ValidateTrustCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(GetTrustAnchorsCommand.COMMAND_ID)
    fun getTrustAnchors(impl: GetTrustAnchorsCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(RefreshTrustAnchorsCommand.COMMAND_ID)
    fun refreshTrustAnchors(impl: RefreshTrustAnchorsCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(CheckRevocationCommand.COMMAND_ID)
    fun checkRevocation(impl: CheckRevocationCommandImpl): ServiceCommand<*, *> = impl

    @Provides @IntoMap
    @StringKey(DiscoverEntityInfoCommand.COMMAND_ID)
    fun discoverEntityInfo(impl: DiscoverEntityInfoCommandImpl): ServiceCommand<*, *> = impl
}
