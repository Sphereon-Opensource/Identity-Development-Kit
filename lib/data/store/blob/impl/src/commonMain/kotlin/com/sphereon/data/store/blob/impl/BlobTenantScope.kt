/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.blob.impl

import com.sphereon.data.store.blob.BlobInfo

/**
 * Single owner of the blob tenant-scoping convention.
 *
 * A blob's physical storage key is `"<tenantId>/<logicalPath>"`; callers only
 * ever see the logical (unscoped) path. Both [DefaultBlobService] — the
 * session-scoped service that scopes every operation — and
 * [TenantScopedBlobStore] — an app-scoped decorator for callers that have no
 * session — route through this object, so the convention lives in exactly one
 * place. (Duplicating it risks write/read drift, which surfaces as
 * `BLOB_NOT_FOUND`.)
 */
object BlobTenantScope {
    /** Tenant applied when a [BlobInfo] carries none. */
    const val DEFAULT_TENANT: String = "default"

    /** Effective tenant for [info], falling back to [DEFAULT_TENANT]. */
    fun tenantOf(info: BlobInfo): String = info.tenantId ?: DEFAULT_TENANT

    /**
     * Logical → physical: prepend `"<tenantId>/"` unless the path is already
     * scoped. Idempotent, so re-scoping a stored key never double-prefixes.
     */
    fun scopePath(logicalPath: String, tenantId: String): String =
        if (logicalPath == tenantId || logicalPath.startsWith("$tenantId/")) {
            logicalPath
        } else {
            "$tenantId/$logicalPath"
        }

    /** Physical → logical: strip the leading `"<tenantId>/"`. Idempotent. */
    fun unscopePath(scopedPath: String, tenantId: String): String =
        if (scopedPath.startsWith("$tenantId/")) scopedPath.removePrefix("$tenantId/") else scopedPath

    /** Scope a list/search path prefix: `"<tenantId>/<prefix-or-empty>"`. */
    fun scopePrefix(prefix: String?, tenantId: String): String = "$tenantId/${prefix ?: ""}"
}
