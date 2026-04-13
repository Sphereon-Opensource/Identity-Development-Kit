/*
 * Copyright 2025 Sphereon International B.V.
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

import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Duration

/**
 * Arguments for cache get operation.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheGetArgs", exact = true)
data class CacheGetArgs(
    val namespace: String,
    val scope: CacheScope,
    val tenantId: String? = null,
    val principalId: String? = null,
    val key: String
) {
    init {
        when (scope) {
            CacheScope.TENANT -> require(!tenantId.isNullOrBlank()) { "tenantId required for TENANT scope" }
            CacheScope.PRINCIPAL -> {
                require(!tenantId.isNullOrBlank()) { "tenantId required for PRINCIPAL scope" }
                require(!principalId.isNullOrBlank()) { "principalId required for PRINCIPAL scope" }
            }
            else -> {}
        }
    }

    companion object {
        fun app(namespace: String, key: String) = CacheGetArgs(namespace, CacheScope.APP, null, null, key)
        fun tenant(namespace: String, tenantId: String, key: String) = CacheGetArgs(namespace, CacheScope.TENANT, tenantId, null, key)
        fun principal(namespace: String, tenantId: String, principalId: String, key: String) =
            CacheGetArgs(namespace, CacheScope.PRINCIPAL, tenantId, principalId, key)
    }
}

/**
 * Result of cache get operation.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheGetResult", exact = true)
data class CacheGetResult(
    val value: String?,
    val hit: Boolean,
    val fromBackend: String
) {
    companion object {
        fun hit(value: String, backend: String) = CacheGetResult(value, true, backend)
        fun miss(backend: String) = CacheGetResult(null, false, backend)
    }
}

/**
 * Arguments for cache put operation.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CachePutArgs", exact = true)
data class CachePutArgs(
    val namespace: String,
    val scope: CacheScope,
    val tenantId: String? = null,
    val principalId: String? = null,
    val key: String,
    val value: String,
    val ttlMs: Long? = null
) {
    companion object {
        fun app(namespace: String, key: String, value: String, ttl: Duration? = null) =
            CachePutArgs(namespace, CacheScope.APP, null, null, key, value, ttl?.inWholeMilliseconds)

        fun tenant(namespace: String, tenantId: String, key: String, value: String, ttl: Duration? = null) =
            CachePutArgs(namespace, CacheScope.TENANT, tenantId, null, key, value, ttl?.inWholeMilliseconds)

        fun principal(namespace: String, tenantId: String, principalId: String, key: String, value: String, ttl: Duration? = null) =
            CachePutArgs(namespace, CacheScope.PRINCIPAL, tenantId, principalId, key, value, ttl?.inWholeMilliseconds)
    }
}

/**
 * Result of cache put operation.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CachePutResult", exact = true)
data class CachePutResult(
    val stored: Boolean,
    val toBackend: String
)

/**
 * Arguments for cache remove operation.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheRemoveArgs", exact = true)
data class CacheRemoveArgs(
    val namespace: String,
    val scope: CacheScope,
    val tenantId: String? = null,
    val principalId: String? = null,
    val key: String
) {
    companion object {
        fun app(namespace: String, key: String) = CacheRemoveArgs(namespace, CacheScope.APP, null, null, key)
        fun tenant(namespace: String, tenantId: String, key: String) = CacheRemoveArgs(namespace, CacheScope.TENANT, tenantId, null, key)
        fun principal(namespace: String, tenantId: String, principalId: String, key: String) =
            CacheRemoveArgs(namespace, CacheScope.PRINCIPAL, tenantId, principalId, key)
    }
}

/**
 * Result of cache remove operation.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheRemoveResult", exact = true)
data class CacheRemoveResult(
    val removed: Boolean,
    val fromBackend: String
)

/**
 * Arguments for cache invalidation.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheInvalidateArgs", exact = true)
data class CacheInvalidateArgs(
    /** Namespace to invalidate, or null for all namespaces */
    val namespace: String? = null,
    /** Tenant to invalidate, or null for all */
    val tenantId: String? = null,
    /** Principal to invalidate, or null for all within tenant */
    val principalId: String? = null,
    /** Key pattern to match, or null for all keys */
    val keyPattern: String? = null
) {
    companion object {
        fun all() = CacheInvalidateArgs()
        fun namespace(namespace: String) = CacheInvalidateArgs(namespace = namespace)
        fun tenant(tenantId: String) = CacheInvalidateArgs(tenantId = tenantId)
        fun principal(tenantId: String, principalId: String) = CacheInvalidateArgs(tenantId = tenantId, principalId = principalId)
        fun pattern(namespace: String, pattern: String) = CacheInvalidateArgs(namespace = namespace, keyPattern = pattern)
    }
}

/**
 * Result of cache invalidation.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("CacheInvalidateResult", exact = true)
data class CacheInvalidateResult(
    val entriesRemoved: Long,
    val namespacesAffected: List<String>
)
