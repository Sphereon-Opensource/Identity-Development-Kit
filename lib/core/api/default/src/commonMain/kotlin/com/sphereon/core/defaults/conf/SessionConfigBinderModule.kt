/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.core.defaults.conf

import com.sphereon.core.api.conf.ConfigBinder
import com.sphereon.core.api.conf.DefaultConfigBinder
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Default session-scoped [ConfigBinder] binding, shared by every EDK / VDX
 * module that needs to bind typed config from [PrincipalConfigService]
 * (App → Tenant → Principal layering).
 *
 * Lives here because the binding is purely mechanical — `DefaultConfigBinder(
 * resolver = principalConfigService)` — and copying it into every domain
 * module (auth, email, blob-store, crypto, etc.) created a duplicate-binding
 * fight that every assembly had to resolve with hand-picked
 * `replaces = [FooConfigBinderModule::class]` exclusions. Providing it once
 * from the default module means any assembly that pulls in
 * `lib-core-api-default` (which every service does transitively through the
 * transport-server layer) gets one authoritative binding.
 *
 * Assemblies that need a *different* shape (e.g. a polymorphic binder for
 * blob-store, or a custom resolver) still register their own
 * `@ContributesTo(SessionScope::class)` interface with
 * `replaces = [SessionConfigBinderModule::class]` and provide the alternative.
 */
@ContributesTo(SessionScope::class)
interface SessionConfigBinderModule {
    @Provides
    @SingleIn(SessionScope::class)
    fun provideSessionConfigBinder(principalConfigService: PrincipalConfigService): ConfigBinder = DefaultConfigBinder(resolver = principalConfigService)
}
