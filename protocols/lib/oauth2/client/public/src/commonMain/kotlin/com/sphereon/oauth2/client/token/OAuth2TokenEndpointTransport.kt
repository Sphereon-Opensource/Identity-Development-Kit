/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.oauth2.client.token

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.ktor.http.client.provider.HttpClientRequestContext
import com.sphereon.oauth2.common.error.Oauth2Error
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.common.model.TokenRequest
import com.sphereon.oauth2.common.model.TokenResponse

/** Inputs whose trust and credential references were resolved before token transport begins. */
data class OAuth2TokenEndpointTransportContext(
    val clientAuthentication: ClientAuthenticationConfig? = null,
    val authorizationServerMetadata: AuthorizationServerMetadata? = null,
    val callerAuthorizedTokenEndpoints: Set<String> = emptySet(),
    val httpClientRequestContext: HttpClientRequestContext? = null,
) {
    override fun toString(): String =
        "OAuth2TokenEndpointTransportContext(" +
            "clientAuthentication=${clientAuthentication.redactedPresence()}, " +
            "authorizationServerMetadata=${authorizationServerMetadata.redactedPresence()}, " +
            "callerAuthorizedTokenEndpoints=<redacted:${callerAuthorizedTokenEndpoints.size}>, " +
            "httpClientRequestContext=${httpClientRequestContext.redactedPresence()})"
}

/** One execution-leaf token endpoint exchange. */
data class OAuth2TokenEndpointTransportArgs(
    val configuredTokenEndpoint: String,
    val tokenRequest: TokenRequest,
    val clientAuthentication: ClientAuthenticationConfig? = null,
    val authorizationServerMetadata: AuthorizationServerMetadata? = null,
    val callerAuthorizedTokenEndpoints: Set<String> = emptySet(),
    val httpClientRequestContext: HttpClientRequestContext? = null,
) {
    override fun toString(): String =
        "OAuth2TokenEndpointTransportArgs(" +
            "configuredTokenEndpoint=<redacted>, tokenRequest=<redacted>, " +
            "clientAuthentication=${clientAuthentication.redactedPresence()}, " +
            "authorizationServerMetadata=${authorizationServerMetadata.redactedPresence()}, " +
            "callerAuthorizedTokenEndpoints=<redacted:${callerAuthorizedTokenEndpoints.size}>, " +
            "httpClientRequestContext=${httpClientRequestContext.redactedPresence()})"
}

interface OAuth2TokenEndpointTransport {
    suspend fun exchange(args: OAuth2TokenEndpointTransportArgs): IdkResult<TokenResponse, Oauth2Error>
}

enum class OAuth2TokenEndpointSource {
    CONFIGURED,
    MTLS_ALIAS,
}

data class EffectiveOAuth2TokenEndpoint(
    val uri: String,
    val source: OAuth2TokenEndpointSource,
)

data class OAuth2TokenEndpointSelection(
    val configuredTokenEndpoint: String,
    val authenticationMethod: ClientAuthenticationMethod,
    val authorizationServerMetadata: AuthorizationServerMetadata? = null,
    val callerAuthorizedTokenEndpoints: Set<String> = emptySet(),
)

interface OAuth2TokenEndpointSelector {
    fun select(selection: OAuth2TokenEndpointSelection): IdkResult<EffectiveOAuth2TokenEndpoint, Oauth2Error>
}

/**
 * RFC 8705 endpoint selection with an explicit authorization boundary. Discovery can suggest an
 * alias, but only caller configuration can authorize that exact URI.
 */
object DefaultOAuth2TokenEndpointSelector : OAuth2TokenEndpointSelector {
    override fun select(selection: OAuth2TokenEndpointSelection): IdkResult<EffectiveOAuth2TokenEndpoint, Oauth2Error> {
        val usesMutualTls = selection.authenticationMethod == ClientAuthenticationMethod.TLS_CLIENT_AUTH ||
            selection.authenticationMethod == ClientAuthenticationMethod.SELF_SIGNED_TLS_CLIENT_AUTH
        val alias = selection.authorizationServerMetadata?.mtlsEndpointAliases?.get(TOKEN_ENDPOINT_ALIAS)

        if (!usesMutualTls || alias == null) {
            return Ok(EffectiveOAuth2TokenEndpoint(selection.configuredTokenEndpoint, OAuth2TokenEndpointSource.CONFIGURED))
        }

        val authorized = alias == selection.configuredTokenEndpoint || alias in selection.callerAuthorizedTokenEndpoints
        if (!authorized) {
            return Err(
                Oauth2Error.ValidationFailed(
                    failureMessage = "The discovered mTLS token endpoint is not caller-authorized",
                    validationErrors = listOf("mtls_endpoint_aliases.token_endpoint must exactly match an authorized token endpoint URI"),
                ),
            )
        }
        return Ok(EffectiveOAuth2TokenEndpoint(alias, OAuth2TokenEndpointSource.MTLS_ALIAS))
    }

    private const val TOKEN_ENDPOINT_ALIAS = "token_endpoint"
}

private fun Any?.redactedPresence(): String = if (this == null) "null" else "<redacted>"


