/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl.client

import com.sphereon.catalog.client.IssuerBindingLookup
import com.sphereon.catalog.client.IssuerBindingQuery
import com.sphereon.catalog.client.IssuerBindingSnapshot
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class NoOpIssuerBindingLookup : IssuerBindingLookup {
    override suspend fun lookup(query: IssuerBindingQuery): IssuerBindingSnapshot = IssuerBindingSnapshot()
}
