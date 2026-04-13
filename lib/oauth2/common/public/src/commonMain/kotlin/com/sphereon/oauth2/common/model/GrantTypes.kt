package com.sphereon.oauth2.common.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * OAuth 2.0 grant types (RFC 6749)
 */
@Serializable
enum class GrantType(val value: String) {
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
    TOKEN_EXCHANGE("urn:ietf:params:oauth:grant-type:token-exchange")
}

/**
 * OAuth 2.0 response types
 */
@Serializable
enum class ResponseType(val value: String) {
    @SerialName("code")
    CODE("code"),

    @SerialName("token")
    TOKEN("token"),

    @SerialName("id_token")
    ID_TOKEN("id_token")
}
