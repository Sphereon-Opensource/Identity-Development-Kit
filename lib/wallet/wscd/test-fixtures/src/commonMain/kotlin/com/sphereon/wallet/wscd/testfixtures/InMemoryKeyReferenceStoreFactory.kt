/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wscd.testfixtures

import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.crypto.key.persistence.KeyReferenceStoreFactory
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntoMap
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.StringKey

/**
 * Test-only dialect adapter that exposes one AppScope-owned in-memory store to every session.
 *
 * The KMS key-reference index is session-scoped in production. The fixture's app-owned store is
 * deliberately shared here so a test can reconstruct a WSCD instance and still observe the same
 * durable owner metadata through the session KMS graph.
 */
@Inject
@SingleIn(AppScope::class)
class InMemoryKeyReferenceStoreFactory : KeyReferenceStoreFactory {
    val store: InMemoryKeyReferenceStore = InMemoryKeyReferenceStore()

    override val type: String = TYPE

    override fun createStore(): KeyReferenceStore = store

    companion object {
        const val TYPE = "in-memory-test"
    }
}

@ContributesTo(AppScope::class)
interface InMemoryKeyReferencePersistenceModule {
    @Provides
    @IntoMap
    @StringKey(InMemoryKeyReferenceStoreFactory.TYPE)
    @SingleIn(AppScope::class)
    fun provideInMemoryKeyReferenceStoreFactory(
        impl: InMemoryKeyReferenceStoreFactory,
    ): KeyReferenceStoreFactory = impl
}
