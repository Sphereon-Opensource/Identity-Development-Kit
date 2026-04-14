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
 */

package com.sphereon.oauth2.common.config

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * Controls whether a feature is available and whether clients must use it.
 */
@JsExportCompat
@Serializable
enum class FeaturePolicy {
    DISABLED,
    SUPPORTED,
    REQUIRED,
}

val FeaturePolicy.isEnabled: Boolean get() = this != FeaturePolicy.DISABLED
val FeaturePolicy.isRequired: Boolean get() = this == FeaturePolicy.REQUIRED

/**
 * Whether this AS config represents a hosted server or an external server.
 */
@JsExportCompat
@Serializable
enum class AuthorizationServerMode {
    HOSTED,
    EXTERNAL,
}

@JsExportCompat
@Serializable
enum class TokenFormat {
    JWT,
    OPAQUE,
}
