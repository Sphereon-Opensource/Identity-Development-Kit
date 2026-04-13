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

package com.sphereon.openid.oid4vc.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Display properties for a credential configuration.
 *
 * Per OID4VCI Section 11.2.3, each entry in the "display" array
 * describes how to render a credential for a given locale.
 */
@Serializable
data class DisplayProperties(
    val name: String,
    val locale: String? = null,
    val logo: LogoProperties? = null,
    val description: String? = null,
    @SerialName("background_color")
    val backgroundColor: String? = null,
    @SerialName("background_image")
    val backgroundImage: ImageProperties? = null,
    @SerialName("text_color")
    val textColor: String? = null,
)

/**
 * Logo display properties.
 */
@Serializable
data class LogoProperties(
    val uri: String? = null,
    @SerialName("alt_text")
    val altText: String? = null,
)

/**
 * Image properties (used for background_image, etc.).
 */
@Serializable
data class ImageProperties(
    val uri: String? = null,
)
