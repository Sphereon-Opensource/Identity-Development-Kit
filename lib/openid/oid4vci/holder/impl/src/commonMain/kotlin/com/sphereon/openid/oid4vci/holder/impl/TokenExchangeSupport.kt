/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vci.holder.impl

import com.sphereon.openid.oid4vci.holder.TokenResponseWithContext
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.TokenResponse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

internal fun ClientAuthenticationConfig.tokenEndpointAuthMethod(): ClientAuthenticationMethod =
    when (this) {
        is ClientAuthenticationConfig.Basic -> ClientAuthenticationMethod.CLIENT_SECRET_BASIC
        is ClientAuthenticationConfig.Post -> ClientAuthenticationMethod.CLIENT_SECRET_POST
        is ClientAuthenticationConfig.SecretJwt -> ClientAuthenticationMethod.CLIENT_SECRET_JWT
        is ClientAuthenticationConfig.PrivateKeyJwt -> ClientAuthenticationMethod.PRIVATE_KEY_JWT
        is ClientAuthenticationConfig.None -> ClientAuthenticationMethod.NONE
        is ClientAuthenticationConfig.AttestationJwt -> ClientAuthenticationMethod.ATTEST_JWT_CLIENT_AUTH
        is ClientAuthenticationConfig.MutualTls -> ClientAuthenticationMethod.TLS_CLIENT_AUTH
        ClientAuthenticationConfig.Anonymous -> ClientAuthenticationMethod.NONE
    }

internal fun tokenResponseWithContext(response: TokenResponse): TokenResponseWithContext =
    TokenResponseWithContext(
        accessToken = response.accessToken,
        tokenType = response.tokenType,
        expiresIn = response.expiresIn,
        authorizationDetails =
            when (val details = response.authorizationDetails) {
                is JsonArray -> details.toList()
                null -> null
                else -> listOf(details)
            },
        cNonce = response.cNonce,
        cNonceExpiresIn = response.cNonceExpiresIn,
        refreshToken = response.refreshToken,
        additionalParameters = response.additionalParameters,
    )

internal fun additionalAuthParameters(bodyParameters: Map<String, String>): Map<String, JsonElement> =
    bodyParameters
        .filterKeys { it !in TOKEN_REQUEST_KNOWN_AUTH_BODY_PARAMETERS }
        .mapValues { (_, value) -> JsonPrimitive(value) }

private val TOKEN_REQUEST_KNOWN_AUTH_BODY_PARAMETERS =
    setOf(
        "client_id",
        "client_secret",
        "client_assertion_type",
        "client_assertion",
    )
