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

package com.sphereon.openid.oid4vci.common.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JsExportCompat
@Serializable
data class CredentialOffer(
    @SerialName("credential_issuer") val credentialIssuer: String,
    @SerialName("credential_configuration_ids") val credentialConfigurationIds: List<String>,
    val grants: CredentialOfferGrants? = null,
)

@JsExportCompat
@Serializable
data class CredentialOfferGrants(
    @SerialName("authorization_code") val authorizationCode: AuthorizationCodeOfferGrant? = null,
    @SerialName("urn:ietf:params:oauth:grant-type:pre-authorized_code") val preAuthorizedCode: PreAuthorizedCodeOfferGrant? = null,
)

@JsExportCompat
@Serializable
data class AuthorizationCodeOfferGrant(
    @SerialName("issuer_state") val issuerState: String? = null,
    @SerialName("authorization_server") val authorizationServer: String? = null,
)

@JsExportCompat
@Serializable
data class PreAuthorizedCodeOfferGrant(
    @SerialName("pre-authorized_code") val preAuthorizedCode: String,
    @SerialName("tx_code") val txCode: TxCodeConfig? = null,
    val interval: Int? = null,
    @SerialName("authorization_server") val authorizationServer: String? = null,
)

@JsExportCompat
@Serializable
data class TxCodeConfig(
    @SerialName("input_mode") val inputMode: String? = "numeric",
    val length: Int? = null,
    val description: String? = null,
)
