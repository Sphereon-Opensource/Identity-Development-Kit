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

package com.sphereon.data.store.credential.design.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.store.blob.BlobInfo
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.time.Instant

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
 * Lightweight descriptor for a CONTENT-ADDRESSED, tenant-scoped design asset blob, returned by the
 * design-agnostic asset listing surface ([com.sphereon.data.store.credential.design.CredentialDesignService.listDesignAssets]).
 *
 * The [hash] is the lowercase-hex SHA-256 of the asset bytes and the [uri] is the stable relative
 * public path under [com.sphereon.data.store.credential.design.PublicDesignAssetPaths.BASE_PATH]
 * (the per-tenant absolute host is applied at serve time). [sizeBytes]/[createdAt] are populated
 * from the underlying blob descriptor when the store reports them and are otherwise null.
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

@JsExportCompat
@Serializable
data class SvgTemplate
    @JvmOverloads
    constructor(
        val uri: String,
        val integrity: String? = null,
        val orientation: SvgOrientation? = null,
        val colorScheme: SvgColorScheme? = null,
        val contrast: SvgContrast? = null,
        val localBlob: BlobInfo? = null,
    )

@JsExportCompat
@Serializable
data class W3cRenderMethodReference
    @JvmOverloads
    constructor(
        val type: String,
        val renderSuite: String? = null,
        val uri: String,
        val mediaType: String? = null,
        val name: String? = null,
        val description: String? = null,
        val digestMultibase: String? = null,
        val renderProperties: List<String>? = null,
    )
