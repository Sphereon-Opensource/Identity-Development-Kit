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

@Serializable
enum class CredentialFormat(
    val value: String,
) {
    @SerialName("dc+sd-jwt")
    SD_JWT_VC("dc+sd-jwt"),

    @SerialName("vc+sd-jwt")
    W3C_VC_SD_JWT("vc+sd-jwt"),

    @SerialName("mso_mdoc")
    MSO_MDOC("mso_mdoc"),

    @SerialName("jwt_vc_json")
    JWT_VC_JSON("jwt_vc_json"),

    @SerialName("jwt_vp_json")
    JWT_VP_JSON("jwt_vp_json"),

    @SerialName("vc+ld+json+jwt")
    VC_LD_JSON_JWT("vc+ld+json+jwt"),
    ;

    val isSdJwt: Boolean
        get() = this == SD_JWT_VC || this == W3C_VC_SD_JWT

    val isJwt: Boolean
        get() = this == JWT_VC_JSON || this == JWT_VP_JSON

    val isMdoc: Boolean
        get() = this == MSO_MDOC

    companion object {
        fun fromValue(value: String): CredentialFormat? = entries.find { it.value == value }

        fun fromValueLenient(value: String): CredentialFormat? {
            fromValue(value)?.let { return it }

            val lowerValue = value.lowercase()
            return when {
                lowerValue == "application/dc+sd-jwt" -> SD_JWT_VC
                lowerValue == "application/vc+sd-jwt" -> W3C_VC_SD_JWT
                lowerValue == "mso_mdoc" || lowerValue.contains("mdoc") -> MSO_MDOC
                lowerValue.contains("jwt_vc") || lowerValue == "jwt_vc_json" -> JWT_VC_JSON
                lowerValue.contains("jwt_vp") || lowerValue == "jwt_vp_json" -> JWT_VP_JSON
                else -> null
            }
        }
    }
}

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
