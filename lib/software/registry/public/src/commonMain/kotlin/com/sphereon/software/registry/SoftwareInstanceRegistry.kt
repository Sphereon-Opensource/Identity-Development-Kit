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

package com.sphereon.software.registry

import com.sphereon.software.registry.model.SoftwareCapabilityType
import com.sphereon.software.registry.model.SoftwareInstance

/**
 * Read SPI over the unified software-instance model, consumed by the runtime.
 *
 * Implementations live in EDK / VDX and project their own persistence
 * (VDX `SoftwareParty` rows, EDK `TenantPublicEndpoint` rows, config-backed
 * instances) onto [SoftwareInstance]. This is a pure read contract: reads never
 * fail in a domain sense — an unknown instance returns `null` and an empty
 * result returns an empty list.
 */
interface SoftwareInstanceRegistry {
    /**
     * Lists every [SoftwareInstance] of [capabilityType] owned by [tenantId],
     * or an empty list when there are none.
     */
    suspend fun list(
        tenantId: String,
        capabilityType: SoftwareCapabilityType,
    ): List<SoftwareInstance>

    /**
     * Resolves the [SoftwareInstance] identified by [instanceId] within
     * [tenantId], or `null` when no such instance exists for that tenant.
     */
    suspend fun get(
        tenantId: String,
        instanceId: String,
    ): SoftwareInstance?
}
