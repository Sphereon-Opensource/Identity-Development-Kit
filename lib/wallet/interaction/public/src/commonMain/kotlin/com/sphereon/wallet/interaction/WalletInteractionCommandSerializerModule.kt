/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.interaction

import com.sphereon.core.api.codec.CommandSerializerEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides

@ContributesTo(AppScope::class)
interface WalletInteractionCommandSerializerModule {
    @Provides
    @IntoSet
    fun provideWalletInteractionInputSerializerEntry(): CommandSerializerEntry = CommandSerializerEntry(WalletInteractionInput::class, WalletInteractionInput.serializer())

    @Provides
    @IntoSet
    fun provideWalletInteractionSessionSerializerEntry(): CommandSerializerEntry = CommandSerializerEntry(WalletInteractionSession::class, WalletInteractionSession.serializer())

    @Provides
    @IntoSet
    fun provideResumeWalletInteractionArgsSerializerEntry(): CommandSerializerEntry = CommandSerializerEntry(ResumeWalletInteractionArgs::class, ResumeWalletInteractionArgs.serializer())

    @Provides
    @IntoSet
    fun provideSubmitWalletInteractionActionArgsSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(SubmitWalletInteractionActionArgs::class, SubmitWalletInteractionActionArgs.serializer())

    @Provides
    @IntoSet
    fun provideCancelWalletInteractionArgsSerializerEntry(): CommandSerializerEntry = CommandSerializerEntry(CancelWalletInteractionArgs::class, CancelWalletInteractionArgs.serializer())

    @Provides
    @IntoSet
    fun provideCancelWalletInteractionResultSerializerEntry(): CommandSerializerEntry = CommandSerializerEntry(CancelWalletInteractionResult::class, CancelWalletInteractionResult.serializer())

    @Provides
    @IntoSet
    fun provideGetWalletInteractionStateArgsSerializerEntry(): CommandSerializerEntry = CommandSerializerEntry(GetWalletInteractionStateArgs::class, GetWalletInteractionStateArgs.serializer())

    @Provides
    @IntoSet
    fun provideGetWalletInteractionEventsArgsSerializerEntry(): CommandSerializerEntry = CommandSerializerEntry(GetWalletInteractionEventsArgs::class, GetWalletInteractionEventsArgs.serializer())

    @Provides
    @IntoSet
    fun provideGetWalletInteractionEventsResultSerializerEntry(): CommandSerializerEntry =
        CommandSerializerEntry(GetWalletInteractionEventsResult::class, GetWalletInteractionEventsResult.serializer())

    @Provides
    @IntoSet
    fun provideWalletInteractionStateSerializerEntry(): CommandSerializerEntry = CommandSerializerEntry(WalletInteractionState::class, WalletInteractionState.serializer())

    @Provides
    @IntoSet
    fun provideWalletInteractionStateEventSerializerEntry(): CommandSerializerEntry = CommandSerializerEntry(WalletInteractionStateEvent::class, WalletInteractionStateEvent.serializer())
}
