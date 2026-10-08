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

package com.sphereon.data.store.asset

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.asset.model.AssetInfo
import com.sphereon.data.store.asset.model.AssetNamespace
import com.sphereon.data.store.asset.model.AssetReference
import com.sphereon.data.store.asset.model.ResolvedAsset
import com.sphereon.data.store.asset.model.UploadAssetInput

/**
 * Tenant asset library: a thin, content-addressed layer over the blob store.
 *
 * Assets are addressed by the SHA-256 hash of their bytes and deduplicated per tenant and
 * [AssetNamespace]: uploading identical bytes twice returns the existing asset and stores one
 * blob. Public URIs follow [PublicAssetPaths] (`/public/assets/{tenantId}/{namespace}/{hash}.{ext}`)
 * and are stored RELATIVE; the per-tenant absolute host is applied at serve time
 * ([PublicAssetPaths.toAbsolute]).
 *
 * The tenant is always an explicit parameter supplied by the caller (REST adapters take it from
 * the authenticated context); there is never a default tenant.
 */
interface TenantAssetService {
    /**
     * Stores raw bytes as a content-addressed asset in the tenant's library.
     *
     * Dedup has putIfAbsent semantics: re-uploading identical bytes within the same tenant and
     * namespace returns a reference to the already-stored asset without writing a second blob.
     *
     * @return an [AssetReference] whose `uri` is the stable relative public hosting path and
     *   whose `integrity` is the `sha256-<base64>` subresource-integrity digest
     */
    suspend fun uploadAsset(
        tenantId: String,
        input: UploadAssetInput,
    ): IdkResult<AssetReference, IdkError>

    /**
     * Lists the tenant's assets in [namespace]. The optional [contentType] filter restricts to
     * assets whose content type starts with the given value (for example `image/`).
     */
    suspend fun listAssets(
        tenantId: String,
        namespace: AssetNamespace,
        contentType: String? = null,
    ): IdkResult<List<AssetInfo>, IdkError>

    /**
     * Returns the descriptor of one stored asset by its lowercase-hex SHA-256 content [hash].
     * The asset bytes themselves are served from the public hosting path in the descriptor's
     * `uri` (or fetched via [getAssetContent]).
     */
    suspend fun getAsset(
        tenantId: String,
        namespace: AssetNamespace,
        hash: String,
    ): IdkResult<AssetInfo, IdkError>

    /**
     * Resolves one stored asset to its raw bytes by content [hash]. Used by the public hosting
     * surface that serves `/public/assets/{tenantId}/{namespace}/{hash}.{ext}`.
     */
    suspend fun getAssetContent(
        tenantId: String,
        namespace: AssetNamespace,
        hash: String,
    ): IdkResult<ResolvedAsset, IdkError>

    /**
     * Deletes a stored asset by content [hash]. The public hosting URL stops resolving for this
     * tenant and namespace. Errs with a not-found error when no such asset exists.
     */
    suspend fun deleteAsset(
        tenantId: String,
        namespace: AssetNamespace,
        hash: String,
    ): IdkResult<Unit, IdkError>
}
