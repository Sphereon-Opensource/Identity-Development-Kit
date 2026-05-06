/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.defaults.context

import com.sphereon.di.context.MutableResolvedTenantIdProvider
import com.sphereon.di.context.ResolvedTenantIdProvider
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Session-scoped default. Holds at most one descended tenant id per request and
 * exposes both the read and write contracts via the same instance, the same way
 * `DefaultOAuth2ServerInstanceIdProvider` does for AS instance ids.
 *
 * Initial value is null (no override). The dispatcher's peel loop calls
 * [setCurrentTenantId] on a successful peel and [clearCurrentTenantId] in a
 * `finally` block.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ResolvedTenantIdProvider>())
@ContributesBinding(SessionScope::class, binding = binding<MutableResolvedTenantIdProvider>())
class DefaultResolvedTenantIdProvider : MutableResolvedTenantIdProvider {
    private var current: String? = null

    override fun currentTenantId(): String? = current

    override fun setCurrentTenantId(tenantId: String) {
        current = tenantId
    }

    override fun clearCurrentTenantId() {
        current = null
    }
}
