package com.sphereon.oauth2.client.validation

import com.sphereon.core.api.conf.Env
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import io.konform.validation.Validation
import io.konform.validation.ValidationBuilder
import io.konform.validation.constraints.notBlank
import io.konform.validation.constraints.pattern

/**
 * Whether HTTP is allowed for non-localhost URLs.
 * Mirrors the flag in [com.sphereon.oauth2.client.util.isSecureUrl].
 */
private val allowInsecureHttp: Boolean by lazy {
    Env.get("OAUTH2_CLIENT_ALLOW_INSECURE_HTTP")?.toBoolean() == true
}

/**
 * URL pattern validator: HTTPS required, HTTP allowed for localhost/127.0.0.1/[::1].
 * When OAUTH2_CLIENT_ALLOW_INSECURE_HTTP=true, HTTP is allowed for all hosts.
 */
private val httpsOrLocalHttpUrlPattern: Regex
    get() = if (allowInsecureHttp) {
        "https?://.*".toRegex()
    } else {
        "https://.*|http://(localhost|127\\.0\\.0\\.1|\\[::1]).*".toRegex()
    }

/**
 * Validates that a string is a valid HTTPS URL (HTTP allowed for localhost in development,
 * or all hosts when OAUTH2_CLIENT_ALLOW_INSECURE_HTTP=true)
 */
private fun ValidationBuilder<String>.httpsUrl() {
    pattern(httpsOrLocalHttpUrlPattern) hint "Must be a valid HTTPS URL (HTTP only allowed for localhost)"
}

/**
 * Konform validator for Authorization Server Metadata (RFC 8414)
 *
 * Validates:
 * - Required fields (issuer, tokenEndpoint)
 * - HTTPS URLs
 * - Complex constraint: introspection endpoint auth signing algorithms must be present
 *   if auth methods include 'private_key_jwt' or 'client_secret_jwt'
 */
val validateAuthorizationServerMetadata = Validation<AuthorizationServerMetadata> {
    // Required fields
    AuthorizationServerMetadata::issuer {
        notBlank() hint "issuer is required"
        httpsUrl()
    }

    AuthorizationServerMetadata::tokenEndpoint {
        notBlank() hint "token_endpoint is required"
        httpsUrl()
    }

    // Optional HTTPS URLs
    AuthorizationServerMetadata::authorizationEndpoint ifPresent {
        httpsUrl()
    }

    AuthorizationServerMetadata::jwksUri ifPresent {
        httpsUrl()
    }

    AuthorizationServerMetadata::pushedAuthorizationRequestEndpoint ifPresent {
        httpsUrl()
    }

    AuthorizationServerMetadata::introspectionEndpoint ifPresent {
        httpsUrl()
    }

    AuthorizationServerMetadata::authorizationChallengeEndpoint ifPresent {
        httpsUrl()
    }

    // Complex validation: If introspection endpoint auth methods include JWT-based methods,
    // then signing algorithms must be specified
    constrain("introspection_endpoint_auth_signing_alg_values_supported must be defined if introspection_endpoint_auth_methods_supported contains 'private_key_jwt' or 'client_secret_jwt'") { metadata ->
        val authMethods = metadata.introspectionEndpointAuthMethodsSupported
        val algValues = metadata.introspectionEndpointAuthSigningAlgValuesSupported

        if (authMethods == null) {
            return@constrain true
        }

        val requiresAlgValues = authMethods.contains("private_key_jwt") || authMethods.contains("client_secret_jwt")

        if (requiresAlgValues) {
            algValues != null && algValues.isNotEmpty()
        } else {
            true
        }
    }

    // Validate algorithm values don't contain 'none'
    AuthorizationServerMetadata::introspectionEndpointAuthSigningAlgValuesSupported ifPresent {
        constrain("Algorithm values must not contain 'none'") { algValues ->
            !algValues.contains("none")
        }
    }
}
