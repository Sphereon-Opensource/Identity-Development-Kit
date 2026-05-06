/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.ktor.test

import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.impl.provider.UserAuthenticationProviderDelegate
import com.sphereon.oauth2.server.authorization.provider.UserAuthenticationProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Test-only DI binding that activates [TestUserAuthenticationProvider] as the resolved
 * [UserAuthenticationProvider]. Replaces [UserAuthenticationProviderDelegate] so the test
 * harness sees the fixture directly without going through the config-driven map lookup.
 *
 * Lives in `jvmTest` so production assemblies pulling the `jvmMain` artifact of
 * `services-oauth2-as-rest` do not get this binding.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<UserAuthenticationProvider>(),
    replaces = [UserAuthenticationProviderDelegate::class],
)
class TestUserAuthenticationProviderBinding(
    private val impl: TestUserAuthenticationProvider,
) : UserAuthenticationProvider by impl
