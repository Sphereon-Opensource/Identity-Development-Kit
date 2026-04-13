/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestUriSigningConfig
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Test-only binding for request_uri JAR signing configuration.
 *
 * The RP impl module contributes HTTP adapters that depend on RequestUriHandlerImpl, which requires this config.
 * The unit tests in this module construct RequestUriHandlerImpl explicitly, so this binding is only to satisfy
 * the DI graph for KSP.
 */
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RequestUriSigningConfig>())
class TestRequestUriSigningConfig @Inject constructor() : RequestUriSigningConfig {
    // Use alias for key lookup - the alias must match what the test generates
    override val signingKey = KeyInfo<Nothing>(alias = "test-request-uri-signing-key")
    override val audience: String = "https://wallet.example.com"
    override val expirationSeconds: Long = 60
}
