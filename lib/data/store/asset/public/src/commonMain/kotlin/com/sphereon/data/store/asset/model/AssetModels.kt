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

package com.sphereon.data.store.asset.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.store.blob.BlobInfo
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.time.Instant

/**
 * A pointer to an asset, either external (by [uri]) or stored locally in the platform blob store
 * (by [localBlob]). For locally stored assets the [uri] is the stable public hosting path and
 * [integrity] carries the subresource-integrity digest (`sha256-<base64>`).
 */
@JsExportCompat
@Serializable
data class AssetReference
    @JvmOverloads
    constructor(
        val uri: String,
        val integrity: String? = null,
        val altText: String? = null,
        val contentType: String? = null,
        val localBlob: BlobInfo? = null,
    )

/**
 * Lightweight descriptor for a CONTENT-ADDRESSED, tenant-scoped asset blob, returned by the
 * asset library listing surface ([com.sphereon.data.store.asset.TenantAssetService.listAssets]).
 *
 * The [hash] is the lowercase-hex SHA-256 of the asset bytes and the [uri] is the stable relative
 * public path (the per-tenant absolute host is applied at serve time, see
 * [com.sphereon.data.store.asset.PublicAssetPaths.toAbsolute]). [sizeBytes]/[createdAt] are
 * populated from the underlying blob descriptor when the store reports them and are otherwise null.
 */
@JsExportCompat
@Serializable
data class AssetInfo
    @JvmOverloads
    constructor(
        val uri: String,
        val contentType: String,
        val hash: String,
        val sizeBytes: Long? = null,
        val createdAt: Instant? = null,
    )
