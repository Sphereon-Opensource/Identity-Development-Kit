/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.wallet.credential

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The wallet uses the canonical OID4VC credential-format vocabulary.
 *
 * This is a type alias, not a second enum: credential formats have one owner and one serializer
 * across the IDK. Presentation formats are represented by the separate PresentationFormat type.
 */
typealias CredentialFormat = com.sphereon.openid.oid4vc.common.CredentialFormat

@Serializable
data class CredentialDisplayProperties(
    val name: String,
    val locale: String? = null,
    val logo: CredentialLogoProperties? = null,
    val description: String? = null,
    @SerialName("background_color")
    val backgroundColor: String? = null,
    @SerialName("background_image")
    val backgroundImage: CredentialImageProperties? = null,
    @SerialName("text_color")
    val textColor: String? = null,
) {
    init {
        require(name.isNotBlank()) { "CredentialDisplayProperties.name must not be blank" }
    }
}

@Serializable
data class CredentialLogoProperties(
    val uri: String? = null,
    @SerialName("alt_text")
    val altText: String? = null,
)

@Serializable
data class CredentialImageProperties(
    val uri: String? = null,
)

@Serializable
data class CredentialClaimMetadata(
    val path: List<String>,
    val mandatory: Boolean? = null,
    @SerialName("value_type")
    val valueType: String? = null,
    val display: List<CredentialClaimDisplay> = emptyList(),
) {
    init {
        require(path.isNotEmpty()) { "CredentialClaimMetadata.path must not be empty" }
        require(path.all { it.isNotBlank() }) { "CredentialClaimMetadata.path entries must not be blank" }
    }
}

@Serializable
data class CredentialClaimDisplay(
    val name: String,
    val locale: String? = null,
) {
    init {
        require(name.isNotBlank()) { "CredentialClaimDisplay.name must not be blank" }
    }
}
