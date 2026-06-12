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

package com.sphereon.software.registry.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The protocol capability a [SoftwareInstance] exposes.
 *
 * This is the unified, open-core projection of the richer (and extensible)
 * VDX `CapabilityType` value class: only the capability kinds the runtime
 * routes against are modelled here. EDK and VDX map their own capability
 * representations onto these four values.
 */
@Serializable
enum class SoftwareCapabilityType {
    @SerialName("OID4VCI_ISSUER")
    OID4VCI_ISSUER,

    @SerialName("OID4VP_VERIFIER")
    OID4VP_VERIFIER,

    @SerialName("OAUTH2_AUTHORIZATION_SERVER")
    OAUTH2_AUTHORIZATION_SERVER,

    @SerialName("ATTRIBUTE_SOURCE")
    ATTRIBUTE_SOURCE,
    ;

    companion object {
        /**
         * The config/routing-backed capability types — the ones that own a per-instance
         * configuration namespace and are projected by the config-derived registries. Stable
         * order. Excludes [ATTRIBUTE_SOURCE], which has no config-driven namespace in the open
         * core. This is the single shared list the `get` / `configKeyPrefix` scans iterate.
         */
        val ROUTED: List<SoftwareCapabilityType> =
            listOf(OID4VCI_ISSUER, OID4VP_VERIFIER, OAUTH2_AUTHORIZATION_SERVER)

        /**
         * The per-instance ("plural") configuration namespace that holds a config-backed
         * instance's typed config, keyed by instance id underneath (`<namespace>.<id>`). This is
         * the single writer-side source of truth shared by the config-derived registries and the
         * VDX create/get/update commands. Returns null for [ATTRIBUTE_SOURCE] (no config namespace).
         *
         * The values mirror the canonical reader-side constants declared in the OID4VCI/OID4VP
         * issuer-public and oauth2-common-public config modules (`INSTANCES_NAMESPACE` /
         * `OAuth2ServerInstanceConfig.CONFIG_PREFIX`); those modules are not reachable from the
         * low-level registry model without inverting the dependency arrow.
         */
        fun instancesNamespaceOrNull(capabilityType: SoftwareCapabilityType): String? =
            when (capabilityType) {
                OID4VCI_ISSUER -> INSTANCES_NAMESPACE_OID4VCI_ISSUERS
                OID4VP_VERIFIER -> INSTANCES_NAMESPACE_OID4VP_VERIFIERS
                OAUTH2_AUTHORIZATION_SERVER -> INSTANCES_NAMESPACE_OAUTH2_SERVERS
                ATTRIBUTE_SOURCE -> null
            }

        /** Per-instance config namespace for OID4VCI issuers (`oid4vci.issuers.<id>`). */
        const val INSTANCES_NAMESPACE_OID4VCI_ISSUERS = "oid4vci.issuers"

        /** Per-instance config namespace for OID4VP verifiers (`oid4vp.verifiers.<id>`). */
        const val INSTANCES_NAMESPACE_OID4VP_VERIFIERS = "oid4vp.verifiers"

        /** Per-instance config namespace for OAuth2 authorization servers (`oauth2.servers.<id>`). */
        const val INSTANCES_NAMESPACE_OAUTH2_SERVERS = "oauth2.servers"
    }
}

/**
 * Lifecycle state of a [SoftwareInstance]. Mirrors the full VDX `LifecycleStatus`
 * set verbatim, so every state a VDX instance can hold is representable in this
 * unified contract (the VDX durable store maps into this model).
 */
@Serializable
enum class SoftwareLifecycleStatus {
    @SerialName("PROVISIONING")
    PROVISIONING,

    @SerialName("ACTIVE")
    ACTIVE,

    @SerialName("SUSPENDED")
    SUSPENDED,

    @SerialName("DECOMMISSIONED")
    DECOMMISSIONED,

    @SerialName("ERROR")
    ERROR,
}

/**
 * Whether the platform manages the instance or it is operated externally.
 * Mirrors the full VDX `ManagementMode` set verbatim (`MANAGED` / `EXTERNAL` /
 * `HYBRID`), so every mode a VDX instance can hold is representable in this
 * unified contract (the VDX durable store maps into this model).
 */
@Serializable
enum class SoftwareManagementMode {
    @SerialName("MANAGED")
    MANAGED,

    @SerialName("EXTERNAL")
    EXTERNAL,

    @SerialName("HYBRID")
    HYBRID,
}

/**
 * Where a [SoftwareManagementMode.MANAGED] instance actually runs. Mirrors the
 * VDX `RuntimeMode` enum variants verbatim. Null when the runtime location is
 * unknown or not applicable (typically for [SoftwareManagementMode.EXTERNAL]
 * instances).
 */
@Serializable
enum class SoftwareRuntimeMode {
    @SerialName("VDX_MANAGED")
    VDX_MANAGED,

    @SerialName("REMOTE_EDK")
    REMOTE_EDK,

    @SerialName("THIRD_PARTY")
    THIRD_PARTY,
}

/**
 * One externally reachable protocol surface of a [SoftwareInstance].
 *
 * This is the unified projection of the EDK `TenantPublicEndpoint` routing
 * row (host / pathPrefix / wellKnownPath / enabled / primary) plus the VDX
 * `SoftwareEndpoint`. EDK/VDX map their own endpoint rows onto this shape.
 *
 * @property serviceType the protocol surface this endpoint serves; usually equal
 *   to the owning instance's [SoftwareInstance.capabilityType] but modelled per
 *   endpoint so a single instance can expose more than one surface.
 * @property host lowercased host without scheme or port. Null means use the
 *   runtime host / default base.
 * @property pathPrefix protocol route prefix, for example `/acme/oid4vci`.
 * @property wellKnownPath well-known route, for example
 *   `/.well-known/openid-credential-issuer/acme`.
 * @property primary whether this is the primary endpoint for its [serviceType].
 * @property enabled whether the endpoint is currently active.
 */
@Serializable
data class SoftwareInstanceEndpoint(
    val serviceType: SoftwareCapabilityType,
    val host: String? = null,
    val pathPrefix: String? = null,
    val wellKnownPath: String? = null,
    val primary: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * Unified, open-core read model for a single software instance — an issuer,
 * verifier, authorization server, or attribute source that a tenant owns or
 * references.
 *
 * This is the single source of truth for the "software instance" concept across
 * the stack. The richer VDX `SoftwareParty` / `ServerParty` / `SoftwareCapability`
 * rows and the EDK `TenantPublicEndpoint` rows are superset/parallel
 * representations that map onto this model; the runtime consumes only this.
 *
 * @property instanceId stable identifier of the instance within its tenant.
 * @property tenantId owning tenant.
 * @property capabilityType the primary protocol capability the instance exposes.
 * @property displayName human-readable name.
 * @property lifecycleStatus current lifecycle state.
 * @property managementMode whether the platform manages the instance or it is
 *   operated externally.
 * @property runtimeMode where a managed instance runs; null when unknown or not
 *   applicable.
 * @property configKeyPrefix the configuration key prefix that holds this
 *   instance's typed config (for example `oid4vci.issuers.<id>`); null when the
 *   instance is not config-backed.
 * @property endpoints the externally reachable protocol surfaces of this
 *   instance.
 */
@Serializable
data class SoftwareInstance(
    val instanceId: String,
    val tenantId: String,
    val capabilityType: SoftwareCapabilityType,
    val displayName: String,
    val lifecycleStatus: SoftwareLifecycleStatus,
    val managementMode: SoftwareManagementMode,
    val runtimeMode: SoftwareRuntimeMode? = null,
    val configKeyPrefix: String? = null,
    val endpoints: List<SoftwareInstanceEndpoint> = emptyList(),
)
