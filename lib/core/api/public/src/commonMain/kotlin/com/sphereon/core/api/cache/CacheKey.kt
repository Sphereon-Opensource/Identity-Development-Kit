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

package com.sphereon.core.api.cache

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * Cache scope levels for multi-tenant caching.
 *
 * Determines the isolation level of cached data:
 * - APP: Application-wide, shared across all tenants
 * - TENANT: Isolated per tenant
 * - PRINCIPAL: Isolated per principal (user) within a tenant
 */
@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheScope", exact = true)
enum class CacheScope {
    /** Application-wide scope, shared across all tenants */
    APP,

    /** Tenant-specific scope, isolated per tenant */
    TENANT,

    /** Principal-specific scope, isolated per user within a tenant */
    PRINCIPAL,
}

/**
 * A scoped cache key with namespace partitioning.
 *
 * Keys are partitioned by:
 * - Namespace: Module/subsystem identifier (e.g., "config", "did-resolver")
 * - Scope: Isolation level (APP, TENANT, PRINCIPAL)
 * - TenantId: Tenant identifier (for TENANT and PRINCIPAL scopes)
 * - PrincipalId: Principal/user identifier (for PRINCIPAL scope)
 * - Key: The actual cache key within the namespace
 *
 * String representation format:
 * `{namespace}::{scope}::{tenantId}::{principalId}::{key}`
 *
 * Examples:
 * - `config::APP::::db.pool.size`
 * - `config::TENANT::tenant-a::::kms.provider.type`
 * - `oauth-tokens::PRINCIPAL::tenant-a::user-123::access-token`
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScopedKey", exact = true)
data class ScopedKey<K : Any>(
    val namespace: String,
    val scope: CacheScope,
    val tenantId: String?,
    val principalId: String?,
    val key: K,
) {
    /**
     * Convert to a string key for use in cache backends.
     */
    fun toStringKey(keySerializer: CacheSerializer<K>? = null): String =
        buildString {
            append(namespace)
            append(SEPARATOR)
            append(scope.name)
            append(SEPARATOR)
            tenantId?.let { append(it) }
            append(SEPARATOR)
            principalId?.let { append(it) }
            append(SEPARATOR)
            append(keySerializer?.serializeToString(key) ?: key.toString())
        }

    /**
     * Check if this key matches a pattern.
     * Supports wildcard (*) matching for each graph.
     */
    fun matches(pattern: ScopedKeyPattern): Boolean {
        if (pattern.namespace != null && pattern.namespace != "*" && pattern.namespace != namespace) {
            return false
        }
        if (pattern.scope != null && pattern.scope != scope) {
            return false
        }
        if (pattern.tenantId != null && pattern.tenantId != "*" && pattern.tenantId != tenantId) {
            return false
        }
        if (pattern.principalId != null && pattern.principalId != "*" && pattern.principalId != principalId) {
            return false
        }
        return true
    }

    companion object {
        const val SEPARATOR = "::"
        private const val SCOPED_KEY_PART_COUNT = 5
        private const val PART_INDEX_TENANT_ID = 2
        private const val PART_INDEX_PRINCIPAL_ID = 3
        private const val PART_INDEX_KEY = 4

        /**
         * Create an app-scoped key.
         */
        @JvmStatic
        fun <K : Any> app(
            namespace: String,
            key: K,
        ) = ScopedKey(
            namespace = namespace,
            scope = CacheScope.APP,
            tenantId = null,
            principalId = null,
            key = key,
        )

        /**
         * Create a tenant-scoped key.
         */
        @JvmStatic
        fun <K : Any> tenant(
            namespace: String,
            tenantId: String,
            key: K,
        ) = ScopedKey(
            namespace = namespace,
            scope = CacheScope.TENANT,
            tenantId = tenantId,
            principalId = null,
            key = key,
        )

        /**
         * Create a principal-scoped key.
         */
        @JvmStatic
        fun <K : Any> principal(
            namespace: String,
            tenantId: String,
            principalId: String,
            key: K,
        ) = ScopedKey(
            namespace = namespace,
            scope = CacheScope.PRINCIPAL,
            tenantId = tenantId,
            principalId = principalId,
            key = key,
        )

        /**
         * Parse a string key back to a ScopedKey (for String keys only).
         */
        @JvmStatic
        fun parseString(stringKey: String): ScopedKey<String>? {
            val parts = stringKey.split(SEPARATOR)
            if (parts.size != SCOPED_KEY_PART_COUNT) {
                return null
            }

            val namespace = parts[0]
            val scope =
                try {
                    CacheScope.valueOf(parts[1])
                } catch (_: Exception) {
                    return null
                }
            val tenantId = parts[PART_INDEX_TENANT_ID].ifEmpty { null }
            val principalId = parts[PART_INDEX_PRINCIPAL_ID].ifEmpty { null }
            val key = parts[PART_INDEX_KEY]

            return ScopedKey(namespace, scope, tenantId, principalId, key)
        }
    }
}

/**
 * Pattern for matching scoped keys.
 * Used for bulk invalidation and key filtering.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ScopedKeyPattern", exact = true)
data class ScopedKeyPattern(
    val namespace: String? = null,
    val scope: CacheScope? = null,
    val tenantId: String? = null,
    val principalId: String? = null,
    val keyPattern: String? = null,
) {
    /**
     * Convert to a regex pattern for matching string keys.
     */
    fun toRegexPattern(): Regex {
        val pattern =
            buildString {
                append(namespace ?: ".*")
                append(ScopedKey.SEPARATOR)
                append(scope?.name ?: ".*")
                append(ScopedKey.SEPARATOR)
                append(
                    if (tenantId != null && tenantId != "*") {
                        Regex.escape(tenantId)
                    } else {
                        ".*"
                    }
                )
                append(ScopedKey.SEPARATOR)
                append(
                    if (principalId != null && principalId != "*") {
                        Regex.escape(principalId)
                    } else {
                        ".*"
                    }
                )
                append(ScopedKey.SEPARATOR)
                append(keyPattern?.replace("*", ".*") ?: ".*")
            }
        return Regex(pattern)
    }

    companion object {
        /** Match all keys */
        val ALL = ScopedKeyPattern()

        /** Match all keys in a namespace */
        @JvmStatic
        fun namespace(namespace: String) = ScopedKeyPattern(namespace = namespace)

        /** Match all keys for a tenant */
        @JvmStatic
        fun tenant(tenantId: String) = ScopedKeyPattern(tenantId = tenantId)

        /** Match all keys for a principal */
        @JvmStatic
        fun principal(
            tenantId: String,
            principalId: String,
        ) = ScopedKeyPattern(
            tenantId = tenantId,
            principalId = principalId,
        )
    }
}
