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

package com.sphereon.crypto.core

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Controls whether the platform may mutate the provider resource during its lifecycle.
 * This is distinct from [com.sphereon.core.api.model.Origin], which records provenance.
 * Deleting an externally managed resource removes only EDK's local reference and never
 * deletes the provider resource.
 */
@Serializable
@JsExportCompat
enum class ResourceControlMode {
    @SerialName("platform_managed")
    PLATFORM_MANAGED,

    @SerialName("externally_managed")
    EXTERNALLY_MANAGED,
}
