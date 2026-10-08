/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Names the provider ids under which the tenant's key references for a REST provider id are
 * recorded.
 *
 * A caller addresses a provider by the id the API publishes. A provider that has exactly one id
 * records its key references under it. A deployment whose providers also carry a separate runtime
 * id, which is what external key registration records, replaces this binding so that a linked key
 * is found under whichever of the provider's ids it was recorded with.
 */
interface KeyReferenceProviderIds {
    suspend fun recordedUnder(providerId: String): List<String>
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<KeyReferenceProviderIds>())
class SingleKeyReferenceProviderId : KeyReferenceProviderIds {
    override suspend fun recordedUnder(providerId: String): List<String> = listOf(providerId)
}
