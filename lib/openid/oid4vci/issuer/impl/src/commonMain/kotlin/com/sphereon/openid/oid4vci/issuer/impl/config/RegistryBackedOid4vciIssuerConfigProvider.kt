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

package com.sphereon.openid.oid4vci.issuer.impl.config

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.config.INSTANCES_NAMESPACE
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerInstanceIdProvider
import com.sphereon.openid.oid4vci.issuer.config.VctTypeMetadataProvider
import com.sphereon.statuslist.StatusListDefinitionsProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Per-instance [Oid4vciIssuerConfigProvider] / [VctTypeMetadataProvider] backed by IDK's
 * ConfigService, selected at request time by [Oid4vciIssuerInstanceIdProvider].
 *
 * Computes the active config namespace from the resolved instance id:
 *   `oid4vci.issuers.<instanceId>` when an id is set, else the singular
 *   [ConfigDrivenOid4vciIssuerConfigProvider.NAMESPACE] (`oid4vci.issuer`).
 *
 * This is the runtime half of the per-issuer story: VDX's `CreateOid4vciIssuerCommandImpl` already
 * writes the plural prefix `oid4vci.issuers.<partyId>.*` into tenant config; this provider makes it
 * READABLE once an upstream resolver populates the instance-id holder for the request. With the
 * holder empty (no resolver, or resolver returned null) it transparently reads the singular
 * namespace, so a pure-IDK config-only deploy keeps working unchanged.
 *
 * Replaces the [ConfigDrivenOid4vciIssuerConfigProvider] binding when on the classpath, mirroring
 * how the OAuth2 AS instance pattern (`oauth2.servers.<id>.*`) supersedes its singular default.
 *
 * ## Namespace shape (no brackets)
 * The instance id is appended as a PLAIN dotted segment (`$INSTANCES_NAMESPACE.$instanceId`), NOT
 * bracket-quoted. This matches the OAuth2 AS precedent (`oauth2.servers.$id`) and, decisively, the
 * keys VDX actually writes: `oid4vci.issuers.<partyId>.<key>` is written through the same
 * `PropertyKeyNormalizer` with the id un-bracketed, so its hyphens are normalised to dots. Reading
 * with a bracket-quoted id (`[<id>]`) would preserve the hyphens verbatim and fail to match the
 * persisted, hyphen-to-dot-normalised keys. Plain concatenation round-trips with the writer.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<Oid4vciIssuerConfigProvider>(),
    replaces = [ConfigDrivenOid4vciIssuerConfigProvider::class],
)
@ContributesBinding(
    SessionScope::class,
    binding = binding<VctTypeMetadataProvider>(),
    replaces = [ConfigDrivenOid4vciIssuerConfigProvider::class],
)
class RegistryBackedOid4vciIssuerConfigProvider(
    execution: SessionExecution,
    private val instanceIdProvider: Oid4vciIssuerInstanceIdProvider,
    statusListDefinitionsProvider: Provider<StatusListDefinitionsProvider>? = null,
) : AbstractConfigOid4vciIssuerConfigProvider(
        execution = execution,
        statusListDefinitionsProvider = statusListDefinitionsProvider,
        namespaceProvider = {
            instanceIdProvider
                .currentInstanceId()
                ?.takeIf { it.isNotBlank() }
                ?.let { "$INSTANCES_NAMESPACE.$it" }
                ?: ConfigDrivenOid4vciIssuerConfigProvider.NAMESPACE
        },
    )
