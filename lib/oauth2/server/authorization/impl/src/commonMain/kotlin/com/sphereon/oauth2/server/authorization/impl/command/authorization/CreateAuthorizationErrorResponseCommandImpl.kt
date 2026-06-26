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

package com.sphereon.oauth2.server.authorization.impl.command.authorization

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.jose.JoseKeyOperations
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.resolution.managed.ManagedOptsJwk
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.common.jarm.CreateJarmResponseArgs
import com.sphereon.oauth2.common.jarm.CreateJarmResponseCommand
import com.sphereon.oauth2.common.jarm.JarmConfig
import com.sphereon.oauth2.common.jarm.JarmMode
import com.sphereon.oauth2.common.model.OAuth2ResponseMode
import com.sphereon.oauth2.server.authorization.command.AuthorizationErrorResponseData
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationErrorResponseCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
import com.sphereon.oauth2.server.authorization.signing.AsServerSigningIdentifierResolver
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of [CreateAuthorizationErrorResponseCommand] per RFC 6749 §4.1.2.1 +
 * OIDF JARM (https://openid.net/specs/oauth-v2-jarm.html).
 *
 * Error response shaping per requested [OAuth2ResponseMode] (mirrors
 * [CreateAuthorizationResponseCommandImpl]):
 *
 *  - [OAuth2ResponseMode.QUERY]     → `Location: {redirect_uri}?error=…&error_description=…&state=…`
 *  - [OAuth2ResponseMode.FRAGMENT]  → `Location: {redirect_uri}#error=…&error_description=…&state=…`
 *  - [OAuth2ResponseMode.FORM_POST] → 200 OK with an auto-submitting HTML form posting the error
 *    parameters to [redirectUri] (adapter emits HTML from [AuthorizationErrorResponseData.formPostHtml]).
 *  - JARM `*.jwt` modes → the error parameters become claims inside a signed JARM JWT, delivered
 *    via the underlying carrier (query / fragment / form post).
 *
 * IMPORTANT (RFC 6749 §4.1.2.1): error responses MUST NOT be sent to an unvalidated redirect URI.
 * The caller (the HTTP adapter / handlers) is responsible for ensuring the redirect URI has been
 * verified against the client registration before invoking this command; when the redirect URI
 * cannot be trusted, the caller serves a JSON error response instead.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateAuthorizationErrorResponseCommandImpl", exact = true)
class CreateAuthorizationErrorResponseCommandImpl(
    execution: SessionExecution,
    private val configProvider: OAuth2ServersConfigProvider,
    private val clientRegistry: ClientRegistry,
    private val createJarmResponse: CreateJarmResponseCommand,
    private val signingIdentifierResolver: AsServerSigningIdentifierResolver,
) : TypedServiceCommandAdapter<CreateAuthorizationErrorResponseArgs, AuthorizationErrorResponseData, IdkError>(
        commandId = CreateAuthorizationErrorResponseCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateAuthorizationErrorResponseArgs>(),
        outputTypeToken = typeToken<AuthorizationErrorResponseData>(),
    ),
    CreateAuthorizationErrorResponseCommand {
    override val commandId: String get() = CreateAuthorizationErrorResponseCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateAuthorizationErrorResponseArgs

    override suspend fun doExecute(
        args: CreateAuthorizationErrorResponseArgs,
        applyDuring: (CreateAuthorizationErrorResponseArgs) -> CreateAuthorizationErrorResponseArgs,
    ): IdkResult<AuthorizationErrorResponseData, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(args: CreateAuthorizationErrorResponseArgs): IdkResult<AuthorizationErrorResponseData, AuthorizationServerError> {
        // Preserve insertion order so the wire representation is stable and test-friendly.
        val parameters = linkedMapOf("error" to args.error)
        args.errorDescription?.let { parameters["error_description"] = it }
        args.errorUri?.let { parameters["error_uri"] = it }
        args.state?.let { parameters["state"] = it }

        // RFC 9207 OAuth 2.0 Authorization Server Issuer Identification: include `iss` in the
        // authorization response (success AND error) so the client can detect mix-up attacks.
        // FAPI2-SP §5.3.2.2-7 and HAIP both require this.
        val configuredIssuer = args.baseUrlOverride?.takeIf { it.isNotBlank() } ?: configProvider.serverConfig.issuer
        if (configuredIssuer != null) {
            parameters["iss"] = configuredIssuer
        }

        if (args.responseMode.isJarm) {
            return shapeJarmErrorResponse(args, parameters)
        }

        val (finalRedirectLocation: String, formPostHtml: String?) =
            when (args.responseMode.carrier) {
                OAuth2ResponseMode.Carrier.QUERY -> buildQueryRedirect(args.redirectUri, parameters) to null
                OAuth2ResponseMode.Carrier.FRAGMENT -> buildFragmentRedirect(args.redirectUri, parameters) to null
                OAuth2ResponseMode.Carrier.FORM_POST -> args.redirectUri to buildFormPostHtml(args.redirectUri, parameters)
            }

        return Ok(
            AuthorizationErrorResponseData(
                error = args.error,
                errorDescription = args.errorDescription,
                errorUri = args.errorUri,
                state = args.state,
                redirectUri = finalRedirectLocation,
                responseMode = args.responseMode,
                formPostHtml = formPostHtml,
            ),
        )
    }

    private suspend fun shapeJarmErrorResponse(
        args: CreateAuthorizationErrorResponseArgs,
        parameters: Map<String, String>,
    ): IdkResult<AuthorizationErrorResponseData, AuthorizationServerError> {
        val serverIdentifier = signingIdentifierResolver.resolveSigningIdentifier()
        val config = configProvider.serverConfig
        // For error responses, downgrade to the underlying carrier without JARM packaging when
        // JARM cannot be honored (server feature off, client signing alg missing). Fail-open here
        // is intentional: the AS would otherwise be unable to deliver the error to the redirect
        // URI, which the OIDF JARM spec discourages.
        val downgrade = downgradedJarmMode(args.responseMode)
        if (!config.jarm.isEnabled) {
            return shapeBareError(args, parameters, downgrade)
        }
        val clientId = args.clientId
        if (clientId == null) {
            return shapeBareError(args, parameters, downgrade)
        }
        val client =
            clientRegistry.getClient(clientId).getOrElse { error ->
                return Err(error)
            } ?: return shapeBareError(args, parameters, downgrade)
        // OIDF JARM §6.1: signed alg OR encrypted alg = client opted into JARM. Both unset =
        // bare-mode downgrade (the client doesn't have JARM configured at all).
        val signingAlg = client.authorizationSignedResponseAlg
        val encryptedAlg = client.authorizationEncryptedResponseAlg
        if (signingAlg == null && encryptedAlg == null) {
            return shapeBareError(args, parameters, downgrade)
        }
        if (signingAlg != null && serverIdentifier == null) {
            return shapeBareError(args, parameters, downgrade)
        }
        val issuerUrl =
            args.baseUrlOverride?.takeIf { it.isNotBlank() }
                ?: config.issuer
                ?: return shapeBareError(args, parameters, downgrade)

        val jarmConfig =
            JarmConfig.fromClientMetadata(
                signedAlg = signingAlg,
                encryptedAlg = encryptedAlg,
                encryptedEnc = client.authorizationEncryptedResponseEnc,
            ) ?: return shapeBareError(args, parameters, downgrade)

        // OIDF JARM §6.1: when encryption is configured, look up the recipient JWK from the
        // client registration. For error responses, falling back to bare-mode is acceptable per
        // the existing fail-open strategy here: an unrecoverable error must reach the validated
        // redirect URI rather than vanish.
        val encryptionRecipient =
            when (jarmConfig.mode) {
                JarmMode.SIGNED -> {
                    null
                }

                JarmMode.ENCRYPTED, JarmMode.SIGNED_ENCRYPTED -> {
                    val keyEncryptionAlg = jarmConfig.encryptionAlgorithm ?: ""
                    val recipientJwk =
                        selectEncryptionJwk(client, keyEncryptionAlg)
                            ?: return shapeBareError(args, parameters, downgrade)
                    ManagedOptsJwk(identifier = recipientJwk)
                }
            }

        val responseParameters: JsonObject =
            buildJsonObject {
                parameters.forEach { (key, value) ->
                    if (key != "state") put(key, JsonPrimitive(value))
                }
            }

        val jarmResult =
            createJarmResponse
                .execute(
                    CreateJarmResponseArgs(
                        responseParameters = responseParameters,
                        state = args.state,
                        issuer = issuerUrl,
                        audience = clientId,
                        signingKey = if (jarmConfig.mode == JarmMode.ENCRYPTED) null else serverIdentifier,
                        encryptionRecipient = encryptionRecipient,
                        jarmConfig = jarmConfig,
                        expirationSeconds = config.jarmExpirationSeconds,
                    ),
                ).getOrElse { error ->
                    return Err(
                        AuthorizationServerError.ServerError(
                            details = "Failed to mint JARM error response: ${error.message.defaultMessage}",
                        ),
                    )
                }

        val jarmParameters = linkedMapOf("response" to jarmResult.jarmJwt)
        val (finalRedirectLocation: String, formPostHtml: String?) =
            when (args.responseMode.carrier) {
                OAuth2ResponseMode.Carrier.QUERY -> buildQueryRedirect(args.redirectUri, jarmParameters) to null
                OAuth2ResponseMode.Carrier.FRAGMENT -> buildFragmentRedirect(args.redirectUri, jarmParameters) to null
                OAuth2ResponseMode.Carrier.FORM_POST -> args.redirectUri to buildFormPostHtml(args.redirectUri, jarmParameters)
            }

        return Ok(
            AuthorizationErrorResponseData(
                error = args.error,
                errorDescription = args.errorDescription,
                errorUri = args.errorUri,
                state = args.state,
                redirectUri = finalRedirectLocation,
                responseMode = args.responseMode,
                formPostHtml = formPostHtml,
            ),
        )
    }

    /**
     * Build a non-JARM error response over the carrier the JARM mode resolves to. Used when the
     * AS cannot honor a JARM error response (server feature disabled or per-client signing alg
     * missing) so the error still reaches the validated redirect URI.
     */
    private fun shapeBareError(
        args: CreateAuthorizationErrorResponseArgs,
        parameters: Map<String, String>,
        downgrade: OAuth2ResponseMode,
    ): IdkResult<AuthorizationErrorResponseData, AuthorizationServerError> {
        val (finalRedirectLocation: String, formPostHtml: String?) =
            when (downgrade.carrier) {
                OAuth2ResponseMode.Carrier.QUERY -> buildQueryRedirect(args.redirectUri, parameters) to null
                OAuth2ResponseMode.Carrier.FRAGMENT -> buildFragmentRedirect(args.redirectUri, parameters) to null
                OAuth2ResponseMode.Carrier.FORM_POST -> args.redirectUri to buildFormPostHtml(args.redirectUri, parameters)
            }
        return Ok(
            AuthorizationErrorResponseData(
                error = args.error,
                errorDescription = args.errorDescription,
                errorUri = args.errorUri,
                state = args.state,
                redirectUri = finalRedirectLocation,
                responseMode = downgrade,
                formPostHtml = formPostHtml,
            ),
        )
    }

    /**
     * Resolve the underlying non-JARM carrier mode for a JARM mode. Used as the fallback target
     * when JARM cannot be honored: a `query.jwt` request lands its error on `query`, and so on.
     */
    private fun downgradedJarmMode(mode: OAuth2ResponseMode): OAuth2ResponseMode =
        when (mode.carrier) {
            OAuth2ResponseMode.Carrier.QUERY -> OAuth2ResponseMode.QUERY
            OAuth2ResponseMode.Carrier.FRAGMENT -> OAuth2ResponseMode.FRAGMENT
            OAuth2ResponseMode.Carrier.FORM_POST -> OAuth2ResponseMode.FORM_POST
        }

    /**
     * Selects an encryption-capable JWK from the client's inline `jwks` list whose key type matches
     * the JWE `alg` family. Same selection rules as [CreateAuthorizationResponseCommandImpl]: filter
     * on `use=enc` or `key_ops` containing encrypt/wrapKey, then match `kty` to the alg family
     * (RSA-* → RSA, ECDH-ES* → EC or OKP).
     */
    private fun selectEncryptionJwk(
        client: ClientRegistration,
        keyEncryptionAlg: String,
    ): Jwk? {
        val keys = client.jwks ?: return null
        val expectedKty = expectedKtyForAlg(keyEncryptionAlg) ?: return null
        return keys
            .filter { jwk -> isEncryptionKey(jwk) }
            .firstOrNull { jwk -> jwk.kty in expectedKty }
    }

    private fun isEncryptionKey(jwk: Jwk): Boolean {
        if (jwk.use == "enc") return true
        val ops = jwk.key_ops ?: return false
        return ops.any { it == JoseKeyOperations.ENCRYPT || it == JoseKeyOperations.WRAP_KEY }
    }

    private fun expectedKtyForAlg(alg: String): Set<JwaKeyType>? =
        when {
            alg.startsWith("RSA") -> setOf(JwaKeyType.RSA)
            alg.startsWith("ECDH-ES") -> setOf(JwaKeyType.EC, JwaKeyType.OKP)
            else -> null
        }
}
