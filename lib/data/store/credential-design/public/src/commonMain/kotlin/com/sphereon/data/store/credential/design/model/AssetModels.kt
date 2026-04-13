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

import com.sphereon.data.store.blob.BlobInfo
import kotlinx.serialization.Serializable

@Serializable
data class AssetReference(
    val uri: String,
    val integrity: String? = null,
    val altText: String? = null,
    val contentType: String? = null,
    val localBlob: BlobInfo? = null,
)

@Serializable
data class SvgTemplate(
    val uri: String,
    val integrity: String? = null,
    val orientation: SvgOrientation? = null,
    val colorScheme: SvgColorScheme? = null,
    val contrast: SvgContrast? = null,
    val localBlob: BlobInfo? = null,
)

@Serializable
data class W3cRenderMethodReference(
    val type: String,
    val renderSuite: String? = null,
    val uri: String,
    val mediaType: String? = null,
    val name: String? = null,
    val description: String? = null,
    val digestMultibase: String? = null,
    val renderProperties: List<String>? = null,
)
