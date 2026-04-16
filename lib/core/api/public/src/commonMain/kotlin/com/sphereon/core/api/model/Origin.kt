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
 *
 */

package com.sphereon.core.api.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Generic origin indicator for records — whether a resource was created and managed
 * natively within this system, or synced/imported from an external source.
 *
 * Mirrors the semantics of [com.sphereon.data.store.party.model.PartyOrigin] but
 * lives in the core module so it can be reused across domains (keys, DIDs, parties, etc.)
 * without pulling in domain-specific dependencies.
 */
@JsExportCompat
@Serializable
enum class Origin {
    /** Resource was synced from an outside source (IdP, external system, import, auto-discovery) */
    @SerialName("external")
    EXTERNAL,

    /** Resource was created and is managed natively within this system */
    @SerialName("managed")
    MANAGED,
    ;

    companion object {
        fun fromValue(value: String): Origin =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
                ?: throw IllegalArgumentException("Unknown origin: $value")
    }
}
