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

package com.sphereon.data.store.party.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The lifecycle status of a binding between an identity and an application's login surface.
 */
@JsExportCompat
@Serializable
enum class BindingStatus {
    /** The binding is active and may be used to authenticate. */
    @SerialName("active")
    ACTIVE,

    /** The binding is temporarily suspended and may not be used to authenticate. */
    @SerialName("suspended")
    SUSPENDED,

    /** The binding is permanently revoked. */
    @SerialName("revoked")
    REVOKED,
}
