/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.data.store.party.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The origin of a party record, indicating whether it was created internally
 * or synced from an external source.
 */
@Serializable
enum class PartyOrigin {
    /** Party was synced from an outside source (IdP, external system, import, auto-discovery) */
    @SerialName("external")
    EXTERNAL,

    /** Party was created and is managed natively within this system */
    @SerialName("managed")
    MANAGED
}
