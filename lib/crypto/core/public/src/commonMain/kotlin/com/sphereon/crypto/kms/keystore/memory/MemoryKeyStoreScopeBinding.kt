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
 *
 */

package com.sphereon.crypto.kms.keystore.memory

import kotlinx.serialization.Serializable
import com.sphereon.core.compat.JsExportCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Defines the scope binding level for MemoryKeyStore instances.
 *
 * This determines how keys and certificates are partitioned and isolated:
 * - APP: Single storage shared across entire application (no isolation)
 * - TENANT: Storage partitioned by tenant (suitable for multi-tenant REST APIs)
 * - PRINCIPAL_TENANT: Storage partitioned by principal + tenant combination
 * - SESSION: Storage partitioned by session + principal + tenant (default, suitable for stateful apps)
 */
@Serializable
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("MemoryKeyStoreScopeBinding", exact = true)
enum class MemoryKeyStoreScopeBinding(val value: String) {
    /**
     * App-scoped: Single storage for the entire application.
     * Use when keys should be shared across all tenants and users (rare case).
     */
    APP("app"),

    /**
     * Tenant-scoped: Storage partitioned by tenant only.
     * Use for REST APIs where keys should be tenant-specific but survive multiple requests.
     * Suitable for multi-tenant applications without session state.
     */
    TENANT("tenant"),

    /**
     * Principal+Tenant scoped: Storage partitioned by principal and tenant.
     * Use when keys should be user-specific within a tenant context.
     */
    PRINCIPAL_TENANT("principal_tenant"),

    /**
     * Session-scoped: Storage partitioned by session, principal, and tenant (default).
     * Use for mobile apps or stateful web applications with sessions.
     * Provides the highest level of isolation.
     */
    SESSION("session");

    companion object {
        fun fromValue(value: String): MemoryKeyStoreScopeBinding {
            return MemoryKeyStoreScopeBinding.entries.find { it.value.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown scope binding: $value")
        }
    }
}
