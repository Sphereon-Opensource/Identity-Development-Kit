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

package com.sphereon.oauth2.client.impl.clientauth

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.command.ApplyClientAuthenticationArgs
import com.sphereon.oauth2.common.command.ApplyClientAuthenticationCommand
import com.sphereon.oauth2.common.error.Oauth2Error
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationResult
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.http.encodeURLParameter
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of ApplyClientAuthenticationCommand
 *
 * Applies OAuth 2.0 client authentication methods to HTTP requests:
 * - client_secret_basic: HTTP Basic authentication (RFC 6749 Section 2.3.1)
 * - client_secret_post: Client credentials in request body (RFC 6749 Section 2.3.1)
 * - client_secret_jwt: JWT signed with client secret (RFC 7523)
 * - private_key_jwt: JWT signed with private key (RFC 7523)
 * - none: Public client (no authentication)
 * - attest_jwt_client_auth: Client attestation JWT
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("ApplyClientAuthenticationCommandImpl", exact = true)
class ApplyClientAuthenticationCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<ApplyClientAuthenticationArgs, ClientAuthenticationResult, IdkError>(
        commandId = ApplyClientAuthenticationCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ApplyClientAuthenticationArgs>(),
        outputTypeToken = typeToken<ClientAuthenticationResult>(),
    ),
    ApplyClientAuthenticationCommand {
    override val commandId: String get() = ApplyClientAuthenticationCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ApplyClientAuthenticationArgs

    override suspend fun doExecute(
        args: ApplyClientAuthenticationArgs,
        applyDuring: (ApplyClientAuthenticationArgs) -> ApplyClientAuthenticationArgs,
    ): IdkResult<ClientAuthenticationResult, IdkError> {
        val applied = applyDuring(args)
        return applyClientAuthenticationInternal(applied.config).mapError { IdkError.fromDTO(it) }
    }

    /**
     * Applies client authentication to a request
     *
     * @param config The client authentication configuration
     * @return IdkResult containing authentication headers and body parameters
     */
    private suspend fun applyClientAuthenticationInternal(config: ClientAuthenticationConfig,): IdkResult<ClientAuthenticationResult, Oauth2Error> =
        try {
            val result =
                when (config) {
                    is ClientAuthenticationConfig.Basic -> applyBasicAuth(config)
                    is ClientAuthenticationConfig.Post -> applyPostAuth(config)
                    is ClientAuthenticationConfig.SecretJwt -> applySecretJwtAuth(config)
                    is ClientAuthenticationConfig.PrivateKeyJwt -> applyPrivateKeyJwtAuth(config)
                    is ClientAuthenticationConfig.None -> applyNoneAuth(config)
                    is ClientAuthenticationConfig.AttestationJwt -> applyAttestationJwtAuth(config)
                    is ClientAuthenticationConfig.MutualTls -> applyMutualTlsAuth(config)
                    ClientAuthenticationConfig.Anonymous -> applyAnonymousAuth()
                }
            Ok(result)
        } catch (expected: Exception) {
            Err(
                Oauth2Error.ValidationFailed(
                    failureMessage = "Failed to apply client authentication: ${expected.message}",
                    validationErrors = emptyList(),
                ),
            )
        }

    /**
     * Applies HTTP Basic authentication (RFC 6749 Section 2.3.1)
     *
     * Encodes credentials as required by RFC 6749 section 2.3.1: each component
     * is form-encoded before joining them with the Basic-auth colon separator.
     */
    private fun applyBasicAuth(config: ClientAuthenticationConfig.Basic): ClientAuthenticationResult {
        val credentials =
            "${config.credentials.clientId.encodeURLParameter()}:" +
                config.credentials.clientSecret.encodeURLParameter()
        val encodedBytes = credentials.encodeToByteArray()
        val base64Credentials = encodedBytes.encodeToBase64(urlSafe = false)

        return ClientAuthenticationResult(
            headers = mapOf("Authorization" to "Basic $base64Credentials"),
            bodyParameters = emptyMap(),
        )
    }

    /**
     * Applies POST body authentication (RFC 6749 Section 2.3.1)
     *
     * Adds client_id and client_secret to request body
     */
    private fun applyPostAuth(config: ClientAuthenticationConfig.Post): ClientAuthenticationResult =
        ClientAuthenticationResult(
            headers = emptyMap(),
            bodyParameters =
                mapOf(
                    "client_id" to config.credentials.clientId,
                    "client_secret" to config.credentials.clientSecret,
                ),
        )

    /**
     * Applies JWT authentication with client secret (RFC 7523)
     *
     * Adds client_assertion_type and client_assertion to request body
     */
    private fun applySecretJwtAuth(config: ClientAuthenticationConfig.SecretJwt): ClientAuthenticationResult =
        ClientAuthenticationResult(
            headers = emptyMap(),
            bodyParameters =
                mapOf(
                    "client_assertion_type" to config.assertion.assertionType,
                    "client_assertion" to config.assertion.assertion,
                ),
        )

    /**
     * Applies JWT authentication with private key (RFC 7523)
     *
     * Adds client_assertion_type and client_assertion to request body
     */
    private fun applyPrivateKeyJwtAuth(config: ClientAuthenticationConfig.PrivateKeyJwt): ClientAuthenticationResult =
        ClientAuthenticationResult(
            headers = emptyMap(),
            bodyParameters =
                mapOf(
                    "client_assertion_type" to config.assertion.assertionType,
                    "client_assertion" to config.assertion.assertion,
                ),
        )

    /**
     * Applies no authentication (public client)
     *
     * Only adds client_id to request body
     */
    private fun applyNoneAuth(config: ClientAuthenticationConfig.None): ClientAuthenticationResult =
        ClientAuthenticationResult(
            headers = emptyMap(),
            bodyParameters = mapOf("client_id" to config.clientId),
        )

    /**
     * Applies client attestation JWT authentication
     *
     * Adds OAuth-Client-Attestation and OAuth-Client-Attestation-PoP headers
     */
    private fun applyAttestationJwtAuth(config: ClientAuthenticationConfig.AttestationJwt): ClientAuthenticationResult =
        ClientAuthenticationResult(
            headers =
                mapOf(
                    "OAuth-Client-Attestation" to config.attestation.clientAttestationJwt,
                    "OAuth-Client-Attestation-PoP" to config.attestation.clientAttestationPopJwt,
                ),
            bodyParameters = emptyMap(),
        )

    /**
     * Applies anonymous authentication
     *
     * No authentication - returns empty headers and body parameters
     */
    private fun applyAnonymousAuth(): ClientAuthenticationResult =
        ClientAuthenticationResult(
            headers = emptyMap(),
            bodyParameters = emptyMap(),
        )

    /**
     * Mutual-TLS client authentication (RFC 8705 §2). The client presents the cert at the TLS
     * handshake, configured upstream on the [com.sphereon.ktor.http.client.config.ClientSslConfig]
     * passed to [com.sphereon.ktor.http.client.provider.HttpClientFactory]. At the application
     * layer the only requirement is to identify the client; per RFC 8705 §2.3 the AS extracts the
     * `client_id` from the form body when no shared secret is sent.
     */
    private fun applyMutualTlsAuth(config: ClientAuthenticationConfig.MutualTls): ClientAuthenticationResult =
        ClientAuthenticationResult(
            headers = emptyMap(),
            bodyParameters = mapOf("client_id" to config.clientId),
        )
}
