/*
 * Copyright 2025 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.core.command

import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo

/**
 * DI bindings that resolve trust commands from the SessionScopedCommandRegistry.
 */
@ContributesTo(SessionScope::class)
interface TrustCommandBindings {

    @Provides
    fun validateTrustCommand(registry: SessionScopedCommandRegistry): ValidateTrustCommand =
        registry.get(ValidateTrustCommand.COMMAND_ID) as? ValidateTrustCommand
            ?: error("No binding for ${ValidateTrustCommand.COMMAND_ID}")

    @Provides
    fun getTrustAnchorsCommand(registry: SessionScopedCommandRegistry): GetTrustAnchorsCommand =
        registry.get(GetTrustAnchorsCommand.COMMAND_ID) as? GetTrustAnchorsCommand
            ?: error("No binding for ${GetTrustAnchorsCommand.COMMAND_ID}")

    @Provides
    fun refreshTrustAnchorsCommand(registry: SessionScopedCommandRegistry): RefreshTrustAnchorsCommand =
        registry.get(RefreshTrustAnchorsCommand.COMMAND_ID) as? RefreshTrustAnchorsCommand
            ?: error("No binding for ${RefreshTrustAnchorsCommand.COMMAND_ID}")

    @Provides
    fun checkRevocationCommand(registry: SessionScopedCommandRegistry): CheckRevocationCommand =
        registry.get(CheckRevocationCommand.COMMAND_ID) as? CheckRevocationCommand
            ?: error("No binding for ${CheckRevocationCommand.COMMAND_ID}")
}
