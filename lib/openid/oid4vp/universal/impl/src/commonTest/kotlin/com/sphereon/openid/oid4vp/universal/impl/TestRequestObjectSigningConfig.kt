/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.universal.impl

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.verifier.impl.ConfigDrivenRequestObjectSigningConfig
import com.sphereon.openid.oid4vp.verifier.impl.config.RegistryBackedOid4vpVerifierConfigProvider
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestObjectSigningConfig
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Test-only binding for request_uri JAR signing configuration.
 *
 * The Universal OID4VP module depends on OID4VP verifier which requires RequestObjectSigningConfig.
 * This binding is only to satisfy the DI graph for KSP in tests.
 */
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<RequestObjectSigningConfig>(),
    replaces = [ConfigDrivenRequestObjectSigningConfig::class, RegistryBackedOid4vpVerifierConfigProvider::class],
)
class TestRequestObjectSigningConfig
    @Inject
    constructor() : RequestObjectSigningConfig {
        // Tests do not exercise JAR signing; disable to avoid triggering the
        // request-object signing code path.
        override val enabled: Boolean = false

        override suspend fun resolveSigningKey() = KeyInfo<Nothing>(kid = "test-request-uri-signing-key")

        override val audience: String = "https://wallet.example.com"
        override val expirationSeconds: Long = 60
    }
