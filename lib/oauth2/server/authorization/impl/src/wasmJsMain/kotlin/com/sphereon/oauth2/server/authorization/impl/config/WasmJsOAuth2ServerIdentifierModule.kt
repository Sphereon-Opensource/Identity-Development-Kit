/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.oauth2.server.authorization.impl.config

import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * wasmJs platform: server identifier is not available (key resolution uses runBlocking on JVM/native).
 * OAuth2 authorization server is a server-side graph; JS/wasmJs targets provide a null stub
 * so the DI graph is complete for test compilation.
 */
@ContributesTo(SessionScope::class)
interface WasmJsOAuth2ServerIdentifierModule {
    @Provides
    @SingleIn(SessionScope::class)
    @Named("oauth2.serverIdentifier")
    fun provideServerIdentifier(): ManagedIdentifierOptsOrResult? = null
}
