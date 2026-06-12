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

package com.sphereon.openid.oid4vp.verifier.impl.config

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.resolution.managed.ManagedIdentifierService
import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.DidProviderRegistry
import com.sphereon.openid.oid4vp.verifier.config.INSTANCES_NAMESPACE
import com.sphereon.openid.oid4vp.verifier.config.Oid4vpVerifierInstanceIdProvider
import com.sphereon.openid.oid4vp.verifier.impl.ConfigDrivenRequestObjectSigningConfig
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestObjectSigningConfig
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Per-instance [RequestObjectSigningConfig] backed by IDK's ConfigService, selected at request time
 * by [Oid4vpVerifierInstanceIdProvider].
 *
 * Computes the active verifier config root from the resolved instance id:
 *   `oid4vp.verifiers.<instanceId>` when an id is set, else the singular
 *   [ConfigDrivenRequestObjectSigningConfig.NAMESPACE] (`oid4vp.verifier`). The request-object
 *   signing keyspace then nests under `${root}.request-object.*`.
 *
 * This is the runtime half of the per-verifier story: VDX's `CreateOid4vpVerifierCommandImpl` already
 * writes the plural prefix `oid4vp.verifiers.<partyId>.*` into tenant config; this provider makes it
 * READABLE once an upstream resolver populates the instance-id holder for the request. With the
 * holder empty (no resolver, or resolver returned null) it transparently reads the singular
 * namespace, so a pure-IDK config-only deploy keeps working unchanged.
 *
 * Replaces the [ConfigDrivenRequestObjectSigningConfig] binding when on the classpath, mirroring the
 * OID4VCI issuer (`oid4vci.issuers.<id>.*`) and OAuth2 AS (`oauth2.servers.<id>.*`) instance
 * patterns superseding their singular defaults.
 *
 * ## Namespace shape (no brackets)
 * The instance id is appended as a PLAIN dotted segment (`$INSTANCES_NAMESPACE.$instanceId`), NOT
 * bracket-quoted. This matches the OID4VCI issuer precedent and, decisively, the keys VDX actually
 * writes: `oid4vp.verifiers.<partyId>.<key>` is persisted through the same `PropertyKeyNormalizer`
 * with the id un-bracketed, so its hyphens are normalised to dots. Reading with a bracket-quoted id
 * (`[<id>]`) would preserve the hyphens verbatim and fail to match the persisted, hyphen-to-dot
 * normalised keys. Plain concatenation round-trips with the writer.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<RequestObjectSigningConfig>(),
    replaces = [ConfigDrivenRequestObjectSigningConfig::class],
)
class RegistryBackedOid4vpVerifierConfigProvider(
    execution: SessionExecution,
    managedIdentifierService: ManagedIdentifierService,
    kms: KeyManagerService,
    didProviderRegistry: DidProviderRegistry,
    private val instanceIdProvider: Oid4vpVerifierInstanceIdProvider,
) : AbstractConfigOid4vpVerifierConfigProvider(
        execution = execution,
        managedIdentifierService = managedIdentifierService,
        kms = kms,
        didProviderRegistry = didProviderRegistry,
        namespaceProvider = {
            instanceIdProvider
                .currentInstanceId()
                ?.takeIf { it.isNotBlank() }
                ?.let { "$INSTANCES_NAMESPACE.$it" }
                ?: ConfigDrivenRequestObjectSigningConfig.NAMESPACE
        },
    )
