/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.key.persistence

import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<ManagedKeyAuthorityRegistration>(),
)
class NoOpManagedKeyAuthorityRegistration : ManagedKeyAuthorityRegistration {
    override suspend fun register(providerId: String, keyResourceId: String): Boolean = true
}
