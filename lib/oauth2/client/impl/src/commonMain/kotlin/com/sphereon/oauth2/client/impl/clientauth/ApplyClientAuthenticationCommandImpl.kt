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
) : TypedServiceCommandAdapter<ApplyClientAuthenticationArgs, ClientAuthenticationResult>(
    commandId = ApplyClientAuthenticationCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<ApplyClientAuthenticationArgs>(),
    outputTypeToken = typeToken<ClientAuthenticationResult>(),
), ApplyClientAuthenticationCommand {

    override val commandId: String get() = ApplyClientAuthenticationCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ApplyClientAuthenticationArgs

    override suspend fun doExecute(
        args: ApplyClientAuthenticationArgs,
        applyDuring: (ApplyClientAuthenticationArgs) -> ApplyClientAuthenticationArgs
    ): IdkResult<ClientAuthenticationResult, IdkError> {
        val applied = applyDuring(args)
        return applyClientAuthenticationInternal(applied.config, applied.tokenEndpoint).mapError { IdkError.fromDTO(it) }
    }

    /**
     * Applies client authentication to a request
     *
     * @param config The client authentication configuration
     * @param tokenEndpoint The token endpoint URL (used for JWT audience)
     * @return IdkResult containing authentication headers and body parameters
     */
    private suspend fun applyClientAuthenticationInternal(
        config: ClientAuthenticationConfig,
        tokenEndpoint: String
    ): IdkResult<ClientAuthenticationResult, Oauth2Error> {
        return try {
            val result = when (config) {
                is ClientAuthenticationConfig.Basic -> applyBasicAuth(config)
                is ClientAuthenticationConfig.Post -> applyPostAuth(config)
                is ClientAuthenticationConfig.SecretJwt -> applySecretJwtAuth(config)
                is ClientAuthenticationConfig.PrivateKeyJwt -> applyPrivateKeyJwtAuth(config)
                is ClientAuthenticationConfig.None -> applyNoneAuth(config)
                is ClientAuthenticationConfig.AttestationJwt -> applyAttestationJwtAuth(config)
                ClientAuthenticationConfig.Anonymous -> applyAnonymousAuth()
            }
            Ok(result)
        } catch (e: Exception) {
            Err(
                Oauth2Error.ValidationFailed(
                    failureMessage = "Failed to apply client authentication: ${e.message}",
                    validationErrors = emptyList()
                )
            )
        }
    }

    /**
     * Applies HTTP Basic authentication (RFC 6749 Section 2.3.1)
     *
     * Encodes credentials as: Authorization: Basic base64(clientId:clientSecret)
     */
    private fun applyBasicAuth(config: ClientAuthenticationConfig.Basic): ClientAuthenticationResult {
        val credentials = "${config.credentials.clientId}:${config.credentials.clientSecret}"
        val encodedBytes = credentials.encodeToByteArray()
        val base64Credentials = encodedBytes.encodeToBase64(urlSafe = false)

        return ClientAuthenticationResult(
            headers = mapOf("Authorization" to "Basic $base64Credentials"),
            bodyParameters = emptyMap()
        )
    }

    /**
     * Applies POST body authentication (RFC 6749 Section 2.3.1)
     *
     * Adds client_id and client_secret to request body
     */
    private fun applyPostAuth(config: ClientAuthenticationConfig.Post): ClientAuthenticationResult {
        return ClientAuthenticationResult(
            headers = emptyMap(),
            bodyParameters = mapOf(
                "client_id" to config.credentials.clientId,
                "client_secret" to config.credentials.clientSecret
            )
        )
    }

    /**
     * Applies JWT authentication with client secret (RFC 7523)
     *
     * Adds client_assertion_type and client_assertion to request body
     */
    private fun applySecretJwtAuth(config: ClientAuthenticationConfig.SecretJwt): ClientAuthenticationResult {
        return ClientAuthenticationResult(
            headers = emptyMap(),
            bodyParameters = mapOf(
                "client_assertion_type" to config.assertion.assertionType,
                "client_assertion" to config.assertion.assertion
            )
        )
    }

    /**
     * Applies JWT authentication with private key (RFC 7523)
     *
     * Adds client_assertion_type and client_assertion to request body
     */
    private fun applyPrivateKeyJwtAuth(config: ClientAuthenticationConfig.PrivateKeyJwt): ClientAuthenticationResult {
        return ClientAuthenticationResult(
            headers = emptyMap(),
            bodyParameters = mapOf(
                "client_assertion_type" to config.assertion.assertionType,
                "client_assertion" to config.assertion.assertion
            )
        )
    }

    /**
     * Applies no authentication (public client)
     *
     * Only adds client_id to request body
     */
    private fun applyNoneAuth(config: ClientAuthenticationConfig.None): ClientAuthenticationResult {
        return ClientAuthenticationResult(
            headers = emptyMap(),
            bodyParameters = mapOf("client_id" to config.clientId)
        )
    }

    /**
     * Applies client attestation JWT authentication
     *
     * Adds OAuth-Client-Attestation and OAuth-Client-Attestation-PoP headers
     */
    private fun applyAttestationJwtAuth(config: ClientAuthenticationConfig.AttestationJwt): ClientAuthenticationResult {
        return ClientAuthenticationResult(
            headers = mapOf(
                "OAuth-Client-Attestation" to config.attestation.clientAttestationJwt,
                "OAuth-Client-Attestation-PoP" to config.attestation.clientAttestationPopJwt
            ),
            bodyParameters = emptyMap()
        )
    }

    /**
     * Applies anonymous authentication
     *
     * No authentication - returns empty headers and body parameters
     */
    private fun applyAnonymousAuth(): ClientAuthenticationResult {
        return ClientAuthenticationResult(
            headers = emptyMap(),
            bodyParameters = emptyMap()
        )
    }
}
