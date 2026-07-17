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

package com.sphereon.conf.theme.core.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads

/**
 * A named, overridable design element a feature declares, such as a sign-in logo,
 * background, or tagline.
 *
 * @property elementId Identifier of the element within its feature
 * @property kind Kind of value the element takes (ASSET or TEXT)
 * @property required Whether resolution must produce a value for this element
 * @property description Human-readable description
 * @property acceptedContentTypes Accepted MIME types for ASSET elements
 * @property maxSizeBytes Maximum asset size for ASSET elements
 * @property maxLength Maximum text length for TEXT elements
 * @property fallbackTokenKey Token key resolution falls back to when no binding or default exists, for example `branding.logoUrl`
 * @property defaultAsset Default asset value for ASSET elements
 * @property defaultText Default text value for TEXT elements
 */
@JsExportCompat
@Serializable
data class DesignElement
    @JvmOverloads
    constructor(
        val elementId: String,
        val kind: ElementKind,
        val required: Boolean = false,
        val description: String? = null,
        val acceptedContentTypes: List<String>? = null,
        val maxSizeBytes: Long? = null,
        val maxLength: Int? = null,
        val fallbackTokenKey: String? = null,
        val defaultAsset: ThemeAssetReference? = null,
        val defaultText: String? = null,
    )
