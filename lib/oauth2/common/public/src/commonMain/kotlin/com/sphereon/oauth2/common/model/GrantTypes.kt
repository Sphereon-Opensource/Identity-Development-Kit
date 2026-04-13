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

package com.sphereon.oauth2.common.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OAuth 2.0 grant types (RFC 6749)
 */
@Serializable
enum class GrantType(
    val value: String,
) {
    @SerialName("authorization_code")
    AUTHORIZATION_CODE("authorization_code"),

    @SerialName("refresh_token")
    REFRESH_TOKEN("refresh_token"),

    @SerialName("client_credentials")
    CLIENT_CREDENTIALS("client_credentials"),

    @SerialName("password")
    PASSWORD("password"),

    @SerialName("urn:ietf:params:oauth:grant-type:pre-authorized_code")
    PRE_AUTHORIZED_CODE("urn:ietf:params:oauth:grant-type:pre-authorized_code"),

    @SerialName("urn:ietf:params:oauth:grant-type:token-exchange")
    TOKEN_EXCHANGE("urn:ietf:params:oauth:grant-type:token-exchange"),
}

/**
 * OAuth 2.0 response types
 */
@Serializable
enum class ResponseType(
    val value: String,
) {
    @SerialName("code")
    CODE("code"),

    @SerialName("token")
    TOKEN("token"),

    @SerialName("id_token")
    ID_TOKEN("id_token"),
}
