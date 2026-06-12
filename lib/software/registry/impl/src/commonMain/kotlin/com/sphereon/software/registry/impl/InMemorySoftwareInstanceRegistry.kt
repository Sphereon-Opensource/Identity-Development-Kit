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
 *
 */

package com.sphereon.software.registry.impl

import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.di.session.SessionScope
import com.sphereon.software.registry.SoftwareInstanceRegistry
import com.sphereon.software.registry.model.SoftwareCapabilityType
import com.sphereon.software.registry.model.SoftwareInstance
import com.sphereon.software.registry.model.SoftwareLifecycleStatus
import com.sphereon.software.registry.model.SoftwareManagementMode
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Open-core DEFAULT [SoftwareInstanceRegistry] that DERIVES software instances purely from
 * configuration — no database, no persistence.
 *
 * A config-only IDK deployment "has" instances simply by having declared their per-instance
 * config namespaces. Each capability owns one map-style namespace under which an operator
 * declares one or more instances keyed by id:
 *
 * ```yaml
 * oid4vci:
 *   issuers:
 *     acme:
 *       displayName: Acme Issuer
 *     beta: { ... }
 * oid4vp:
 *   verifiers:
 *     gamma: { ... }
 * oauth2:
 *   servers:
 *     default: { ... }
 * ```
 *
 * Instance ids are discovered by scanning every sub-property under a namespace and taking the
 * first dotted segment of each stripped key — the same enumeration idiom the OAuth2 server
 * config binder and the OID4VCI issuer config provider use ([PrincipalConfigService.getSubProperties]
 * with `stripPrefix = true`).
 *
 * EDK / VDX contribute richer registries (durable rows, public-endpoint routing) that REPLACE this
 * binding; endpoint enrichment is out of scope here, so every derived instance carries an empty
 * endpoint list.
 *
 * The [SoftwareCapabilityType.ATTRIBUTE_SOURCE] capability has no config-driven namespace in the
 * open core, so it is never derived here ([list] returns empty for it).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SoftwareInstanceRegistry>())
class InMemorySoftwareInstanceRegistry(
    private val configService: PrincipalConfigService,
) : SoftwareInstanceRegistry {
    override suspend fun list(
        tenantId: String,
        capabilityType: SoftwareCapabilityType,
    ): List<SoftwareInstance> {
        val namespace = namespaceFor(capabilityType) ?: return emptyList()
        return discoverInstanceIds(namespace)
            .map { id -> buildInstance(tenantId, id, capabilityType, namespace) }
    }

    override suspend fun get(
        tenantId: String,
        instanceId: String,
    ): SoftwareInstance? {
        for (capabilityType in SoftwareCapabilityType.ROUTED) {
            val namespace = namespaceFor(capabilityType) ?: continue
            if (instanceId in discoverInstanceIds(namespace)) {
                return buildInstance(tenantId, instanceId, capabilityType, namespace)
            }
        }
        return null
    }

    /**
     * Discovers configured instance ids by scanning every property under [namespace] and collecting
     * the first path segment of each stripped key. Mirrors `OAuth2ServersConfigBinder.discoverServerIds`.
     */
    private fun discoverInstanceIds(namespace: String): Set<String> =
        configService
            .getSubProperties(prefixes = setOf(namespace), stripPrefix = true)
            .keys
            .asSequence()
            .map { it.substringBefore('.') }
            .filter { it.isNotEmpty() }
            .toSet()

    private fun buildInstance(
        tenantId: String,
        instanceId: String,
        capabilityType: SoftwareCapabilityType,
        namespace: String,
    ): SoftwareInstance {
        val prefix = "$namespace.$instanceId"
        val displayName =
            configService.getPropertyAsString("$prefix.displayName")?.takeIf { it.isNotBlank() }
                ?: configService.getPropertyAsString("$prefix.name")?.takeIf { it.isNotBlank() }
                ?: instanceId
        return SoftwareInstance(
            instanceId = instanceId,
            tenantId = tenantId,
            capabilityType = capabilityType,
            displayName = displayName,
            lifecycleStatus = SoftwareLifecycleStatus.ACTIVE,
            managementMode = SoftwareManagementMode.MANAGED,
            runtimeMode = null,
            configKeyPrefix = prefix,
            endpoints = emptyList(),
        )
    }

    private fun namespaceFor(capabilityType: SoftwareCapabilityType): String? = SoftwareCapabilityType.instancesNamespaceOrNull(capabilityType)
}
