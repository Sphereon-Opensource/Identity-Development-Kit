/*
 * Copyright 2026 Sphereon International B.V.
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

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.resolution.managed.ManagedIdentifierService
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.openid.oid4vp.verifier.impl.config.AbstractConfigOid4vpVerifierConfigProvider
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestObjectSigningConfig
import com.sphereon.openid.oid4vp.verifier.spi.VerifierSigningKeyNameResolver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Singular, single-verifier [RequestObjectSigningConfig] backed by IDK's ConfigService.
 *
 * Pins the verifier config root to the fixed [NAMESPACE] (`oid4vp.verifier`), preserving the
 * pure-IDK config-only single-verifier deploy. The request-object signing keyspace nests under
 * `oid4vp.verifier.request-object.*`. All read logic lives in
 * [AbstractConfigOid4vpVerifierConfigProvider]; this subclass only fixes the namespace.
 *
 * Per-instance multi-verifier routing (`oid4vp.verifiers.<id>.*` selected at request time) is
 * provided by
 * [com.sphereon.openid.oid4vp.verifier.impl.config.RegistryBackedOid4vpVerifierConfigProvider],
 * which replaces this binding when on the classpath.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RequestObjectSigningConfig>())
class ConfigDrivenRequestObjectSigningConfig(
    execution: SessionExecution,
    managedIdentifierService: ManagedIdentifierService,
    kms: KeyManagerService,
    didProviderRegistry: DidProviderRegistry,
    signingKeyNameResolver: Provider<VerifierSigningKeyNameResolver>? = null,
) : AbstractConfigOid4vpVerifierConfigProvider(
        execution = execution,
        managedIdentifierService = managedIdentifierService,
        kms = kms,
        didProviderRegistry = didProviderRegistry,
        signingKeyNameResolver = signingKeyNameResolver,
        namespaceProvider = { NAMESPACE },
    ) {
    companion object {
        /** Root config namespace for the singular-verifier keyspace (`oid4vp.verifier`). */
        const val NAMESPACE = "oid4vp.verifier"
    }
}
