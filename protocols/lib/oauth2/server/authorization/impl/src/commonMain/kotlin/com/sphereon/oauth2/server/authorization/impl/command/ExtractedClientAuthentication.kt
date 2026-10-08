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

package com.sphereon.oauth2.server.authorization.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64
import io.ktor.http.decodeURLQueryComponent
import com.sphereon.core.api.http.command.headerIgnoreCase
import com.sphereon.core.defaults.context.JwtClaimsParser
import com.sphereon.oauth2.common.model.ClientAssertion
import com.sphereon.oauth2.common.model.ClientAttestation
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientCredentials
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extracted client authentication bound for [com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationCommand].
 *
 * Used by the `/token`, `/introspect`, and `/revoke` handlers so all three endpoints use an
 * identical client-auth extraction path: none of them may act on an unauthenticated `client_id`
 * taken from the request body.
 */
public data class ExtractedClientAuthentication(
    val clientAuthentication: ClientAuthenticationConfig,
    /** Final resolved client id (post-attestation-jwt-sub extraction where applicable). May be `null` for the anonymous case. */
    val clientId: String?,
)

/**
 * Extract client authentication from a form-encoded request body + request headers per OAuth2 /
 * OIDC conventions. Precedence, highest first:
 *
 *   1. `OAuth-Client-Attestation` + `OAuth-Client-Attestation-PoP` headers (attestation-based
 *      client auth draft)
 *   2. `client_assertion_type` + `client_assertion` body params (RFC 7523 — `private_key_jwt` /
 *      `client_secret_jwt`)
 *   3. HTTP Basic `Authorization` header (`client_secret_basic`)
 *   4. `client_id` + `client_secret` body params (`client_secret_post`)
 *   5. `client_id` alone (auth method `none` for public clients)
 *   6. Anonymous (no credentials supplied)
 *
 * The `tokenEndpointUrl` arg passed to [VerifyClientAuthenticationCommand] controls DPoP `htu`
 * binding and should be the endpoint the request landed on (`/token`, `/introspect`, `/revoke`).
 */
public fun extractClientAuthentication(
    requestBody: Map<String, List<String>>,
    requestHeaders: Map<String, String>,
    /**
     * Leaf TLS client certificate (DER bytes) presented at the AS edge during the TLS handshake.
     * `null` when the request did not arrive over mTLS or when no peer cert was supplied.
     *
     * When non-null AND the conventional credential carriers (Basic header, body secret, JWT
     * assertion, attestation headers) are absent, the extractor selects RFC 8705
     * [ClientAuthenticationConfig.MutualTls] from the request body's `client_id`. The exact
     * mTLS mode (PKI vs self-signed) is dispatched downstream by the verifier from the client's
     * registered `token_endpoint_auth_method`.
     */
    clientCertificateDer: ByteArray? = null,
): IdkResult<ExtractedClientAuthentication, AuthorizationServerError> {
    val attestationHeader = requestHeaders.headerIgnoreCase("OAuth-Client-Attestation")
    val attestationPopHeader = requestHeaders.headerIgnoreCase("OAuth-Client-Attestation-PoP")

    val assertionType = requestBody["client_assertion_type"]?.firstOrNull()
    val assertion = requestBody["client_assertion"]?.firstOrNull()

    val authHeader = requestHeaders.headerIgnoreCase("Authorization")
    val (clientIdFromAuth, clientSecretFromAuth) = parseBasicAuthHeader(authHeader)

    val clientIdFromBody = requestBody["client_id"]?.firstOrNull()
    val clientSecretFromBody = requestBody["client_secret"]?.firstOrNull()

    // Detect method ambiguity per OIDC Core §9 / RFC 6749 §2.3: a client MUST use exactly one
    // authentication method per token request. Counting credential-bearing sources (plain
    // `client_id` alone is not a method — it only identifies a public client).
    val methodsPresent =
        buildList {
            if (attestationHeader != null && attestationPopHeader != null) add("attestation_jwt")
            if (assertionType != null && assertion != null) add("client_assertion")
            if (clientIdFromAuth != null && clientSecretFromAuth != null) add("basic")
            if (clientSecretFromBody != null) add("post")
        }
    if (methodsPresent.size > 1) {
        return Err(
            AuthorizationServerError.InvalidRequest(
                details = "Multiple client authentication methods present: ${methodsPresent.joinToString()}. A client MUST use exactly one method per request (RFC 6749 §2.3).",
            ),
        )
    }

    // Prefer header over body (OAuth2 convention — Basic Auth wins when both are sent).
    val clientId = clientIdFromAuth ?: clientIdFromBody
    val clientSecret = clientSecretFromAuth ?: clientSecretFromBody

    val clientAuthentication: ClientAuthenticationConfig =
        when {
            attestationHeader != null && attestationPopHeader != null -> {
                ClientAuthenticationConfig.AttestationJwt(ClientAttestation(attestationHeader, attestationPopHeader))
            }

            assertionType != null && assertion != null -> {
                // RFC 7523 client authentication carries the client identity in the signed
                // assertion's `sub` claim. A form `client_id` is therefore optional. Decode the
                // unverified claim only to select the registered client; the verifier still pins
                // the signature to that registration and requires iss == sub == client_id.
                val assertionClientId = clientId ?: extractClientIdFromJwtSubject(assertion).orEmpty()
                when (assertionType) {
                    "urn:ietf:params:oauth:client-assertion-type:jwt-bearer" -> {
                        ClientAuthenticationConfig.PrivateKeyJwt(ClientAssertion(assertionClientId, assertionType, assertion))
                    }

                    else -> {
                        ClientAuthenticationConfig.SecretJwt(ClientAssertion(assertionClientId, assertionType, assertion))
                    }
                }
            }

            clientIdFromAuth != null && clientSecretFromAuth != null -> {
                ClientAuthenticationConfig.Basic(ClientCredentials(clientIdFromAuth, clientSecretFromAuth))
            }

            clientId != null && clientSecret != null -> {
                ClientAuthenticationConfig.Post(ClientCredentials(clientId, clientSecret))
            }

            clientId != null && clientCertificateDer != null -> {
                ClientAuthenticationConfig.MutualTls(clientId = clientId, clientCertificateDer = clientCertificateDer)
            }

            clientId != null -> {
                ClientAuthenticationConfig.None(clientId)
            }

            else -> {
                ClientAuthenticationConfig.Anonymous
            }
        }

    // Attestation-JWT client auth carries the client_id inside the attestation's `sub` claim;
    // when the body didn't supply one, extract it for downstream lookups.
    val resolvedClientId =
        when {
            clientId != null -> clientId
            clientAuthentication is ClientAuthenticationConfig.AttestationJwt -> extractClientIdFromAttestationJwt(attestationHeader)
            clientAuthentication is ClientAuthenticationConfig.PrivateKeyJwt -> clientAuthentication.assertion.clientId.takeIf(String::isNotBlank)
            clientAuthentication is ClientAuthenticationConfig.SecretJwt -> clientAuthentication.assertion.clientId.takeIf(String::isNotBlank)
            else -> null
        }

    return Ok(ExtractedClientAuthentication(clientAuthentication, resolvedClientId))
}

/** Parse a `Basic base64(user:pass)` header into `(clientId, clientSecret)` or `(null, null)` on any failure. */
private fun parseBasicAuthHeader(authHeader: String?): Pair<String?, String?> {
    if (authHeader == null) return Pair(null, null)
    val parts = authHeader.trim().split(" ", limit = 2)
    if (parts.size != 2 || !parts[0].equals("Basic", ignoreCase = true)) return Pair(null, null)
    return try {
        val decoded = parts[1].decodeFromBase64().decodeToString()
        val credentials = decoded.split(":", limit = 2)
        if (credentials.size == 2) {
            Pair(
                credentials[0].decodeURLQueryComponent(plusIsSpace = true),
                credentials[1].decodeURLQueryComponent(plusIsSpace = true),
            )
        } else {
            Pair(null, null)
        }
    } catch (_: Exception) {
        Pair(null, null)
    }
}

/** Lightweight decode of the attestation JWT's `sub` claim (no signature verification — that happens in VerifyClientAuthentication). */
private fun extractClientIdFromAttestationJwt(attestationJwt: String?): String? {
    if (attestationJwt == null) return null
    val claims = JwtClaimsParser.parseClaimsOrNull(attestationJwt) ?: return null
    return claims["sub"]?.jsonPrimitive?.content
}

/**
 * Decode the RFC 7523 assertion subject for registered-client selection. This is not an
 * authentication decision: signature and iss/sub/aud/time/replay validation happen downstream in
 * VerifyClientAuthenticationCommandImpl before the identity is accepted.
 */
private fun extractClientIdFromJwtSubject(assertionJwt: String): String? {
    val claims = JwtClaimsParser.parseClaimsOrNull(assertionJwt) ?: return null
    return claims["sub"]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
}
