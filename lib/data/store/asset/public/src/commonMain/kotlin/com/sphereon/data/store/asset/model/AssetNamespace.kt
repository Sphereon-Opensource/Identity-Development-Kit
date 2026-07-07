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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Logical namespace an asset belongs to. Serializes to the lowercase wire [value]
 * (`brand` / `design`), which is also the namespace segment in public asset URLs.
 */
@Serializable
@JsExportCompat
enum class AssetNamespace(
    val value: String,
) {
    /** Theming and branding assets: logos, favicons, backgrounds. */
    @SerialName("brand")
    BRAND("brand"),

    /** Credential design assets: card artwork, issuer logos, templates. */
    @SerialName("design")
    DESIGN("design"),
    ;

    companion object {
        /** Resolves the wire value (or enum name), case-insensitively; `null` for unknown namespaces. */
        fun fromValue(value: String): AssetNamespace? =
            entries.firstOrNull { namespace ->
                namespace.value.equals(value, ignoreCase = true) || namespace.name.equals(value, ignoreCase = true)
            }
    }
}
