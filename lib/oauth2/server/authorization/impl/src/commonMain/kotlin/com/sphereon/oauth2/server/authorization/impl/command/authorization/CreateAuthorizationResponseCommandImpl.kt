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
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedOptsJwk
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.common.jarm.CreateJarmResponseArgs
import com.sphereon.oauth2.common.jarm.CreateJarmResponseCommand
import com.sphereon.oauth2.common.jarm.JarmConfig
import com.sphereon.oauth2.common.jarm.JarmMode
import com.sphereon.oauth2.common.model.OAuth2ResponseMode
import com.sphereon.oauth2.server.authorization.command.AuthorizationResponseData
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseArgs
import com.sphereon.oauth2.server.authorization.command.CreateAuthorizationResponseCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.model.ClientRegistration
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
 * Implementation of [CreateAuthorizationResponseCommand] per RFC 6749 §4.1.2 / OAuth 2.0
 * Form Post Response Mode / OIDF JARM (https://openid.net/specs/oauth-v2-jarm.html).
 *
 * Response shaping per resolved [OAuth2ResponseMode]:
 *
 *  - [OAuth2ResponseMode.QUERY]      → `Location: {redirect_uri}?code=…&state=…` (302)
 *  - [OAuth2ResponseMode.FRAGMENT]   → `Location: {redirect_uri}#code=…&state=…` (302)
 *  - [OAuth2ResponseMode.FORM_POST]  → `200 OK` with an auto-submitting HTML form POSTing to
 *    `redirect_uri` (the adapter emits the HTML from [AuthorizationResponseData.formPostHtml]).
 *  - [OAuth2ResponseMode.QUERY_JWT]    → `Location: {redirect_uri}?response=<jwt>` (302)
 *  - [OAuth2ResponseMode.FRAGMENT_JWT] → `Location: {redirect_uri}#response=<jwt>` (302)
 *  - [OAuth2ResponseMode.FORM_POST_JWT] → 200 OK with an auto-submitting HTML form carrying
 *    `response=<jwt>` to `redirect_uri`.
 *
 * For FORM_POST, [AuthorizationResponseData.redirectUri] is left as the bare registered URI; all
 * response parameters live inside the generated HTML form instead.
 *
 * The JARM `*.jwt` paths bundle the response parameters (`code` + `state`) plus standard JWT
 * claims (`iss`, `aud=client_id`, `exp`) into a JWT minted by [CreateJarmResponseCommand]. The
 * JARM mode is derived from the per-client metadata:
 *   - `authorization_signed_response_alg` only -> JWS (signed) using the server signing key
 *   - `authorization_encrypted_response_alg` only -> JWE (encrypted) using the client's
 *     registered encryption JWK (selected from `client.jwks`, filtered by `use=enc` /
 *     encrypt-capable `key_ops` and matching `kty` to the alg family)
 *   - both -> nested JWT (sign-then-encrypt) per OIDF JARM §6.1.
 * JARM is gated server-wide by [com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig.jarm].
 * A JARM request from a client with neither alg configured is rejected as `invalid_request`. A
 * client that requested encryption but registered no matching encryption JWK is rejected as
 * `invalid_client` rather than silently downgraded to bare-mode.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateAuthorizationResponseCommandImpl", exact = true)
class CreateAuthorizationResponseCommandImpl(
    execution: SessionExecution,
    private val configProvider: OAuth2ServersConfigProvider,
    private val clientRegistry: ClientRegistry,
    private val createJarmResponse: CreateJarmResponseCommand,
    @Named("oauth2.serverIdentifier") private val serverIdentifier: ManagedIdentifierOptsOrResult?,
) : TypedServiceCommandAdapter<CreateAuthorizationResponseArgs, AuthorizationResponseData, IdkError>(
        commandId = CreateAuthorizationResponseCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateAuthorizationResponseArgs>(),
        outputTypeToken = typeToken<AuthorizationResponseData>(),
    ),
    CreateAuthorizationResponseCommand {
    override val commandId: String get() = CreateAuthorizationResponseCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateAuthorizationResponseArgs

    override suspend fun doExecute(
        args: CreateAuthorizationResponseArgs,
        applyDuring: (CreateAuthorizationResponseArgs) -> CreateAuthorizationResponseArgs,
    ): IdkResult<AuthorizationResponseData, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(args: CreateAuthorizationResponseArgs): IdkResult<AuthorizationResponseData, AuthorizationServerError> {
        val parameters = linkedMapOf("code" to args.code)
        args.state?.let { parameters["state"] = it }
        // OIDC Core §3.3 Hybrid Flow — front-channel id_token / access_token ride alongside
        // the code in the same response (URL fragment for response_mode=fragment, hidden
        // form fields for form_post). RFC 6749 §4.2.2 mandates `token_type` and
        // `expires_in` (when known) accompany an access token in the front channel.
        args.idToken?.let { parameters["id_token"] = it }
        args.accessToken?.let { parameters["access_token"] = it }
        args.tokenType?.let { parameters["token_type"] = it }
        args.accessTokenExpiresIn?.let { parameters["expires_in"] = it.toString() }

        // RFC 9207 OAuth 2.0 Authorization Server Issuer Identification: include `iss` in the
        // authorization response so the client can detect mix-up attacks where an auth code
        // from one AS is replayed at another. FAPI2-SP §5.3.2.2-7 and HAIP both require this.
        val configuredIssuer = configProvider.serverConfig.issuer ?: args.baseUrlOverride
        if (configuredIssuer != null) {
            parameters["iss"] = configuredIssuer
        }

        if (args.responseMode.isJarm) {
            return shapeJarmResponse(args, parameters)
        }

        val (finalRedirectLocation: String, formPostHtml: String?) =
            when (args.responseMode.carrier) {
                OAuth2ResponseMode.Carrier.QUERY -> buildQueryRedirect(args.redirectUri, parameters) to null
                OAuth2ResponseMode.Carrier.FRAGMENT -> buildFragmentRedirect(args.redirectUri, parameters) to null
                OAuth2ResponseMode.Carrier.FORM_POST -> args.redirectUri to buildFormPostHtml(args.redirectUri, parameters)
            }

        return Ok(
            AuthorizationResponseData(
                code = args.code,
                state = args.state,
                redirectUri = finalRedirectLocation,
                responseMode = args.responseMode,
                formPostHtml = formPostHtml,
            ),
        )
    }

    private suspend fun shapeJarmResponse(
        args: CreateAuthorizationResponseArgs,
        parameters: Map<String, String>,
    ): IdkResult<AuthorizationResponseData, AuthorizationServerError> {
        val config = configProvider.serverConfig
        if (!config.jarm.isEnabled) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Authorization response mode '${args.responseMode.value}' requires JARM, but JARM is not enabled on this server",
                ),
            )
        }
        val clientId =
            args.clientId
                ?: return Err(
                    AuthorizationServerError.ServerError(
                        details = "JARM response requires clientId on CreateAuthorizationResponseArgs",
                    ),
                )
        val client =
            clientRegistry.getClient(clientId).getOrElse { error ->
                return Err(error)
            } ?: return Err(AuthorizationServerError.ClientNotFound(clientId = clientId))

        // OIDF JARM §6.1: a client opts into JARM by registering one of `authorization_signed_response_alg`
        // and / or `authorization_encrypted_response_alg`. Both unset = client did not opt into JARM,
        // so a JARM request mode is `invalid_request`. Either one set = derive the JARM mode below.
        val signingAlg = client.authorizationSignedResponseAlg
        val encryptedAlg = client.authorizationEncryptedResponseAlg
        if (signingAlg == null && encryptedAlg == null) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details =
                        "Client '$clientId' requested JARM response_mode='${args.responseMode.value}' but has no " +
                            "authorization_signed_response_alg or authorization_encrypted_response_alg configured",
                ),
            )
        }
        // The server signing key is only required when the JARM mode actually signs.
        if (signingAlg != null && serverIdentifier == null) {
            return Err(
                AuthorizationServerError.ServerError(
                    details = "Cannot mint JARM response: server signing key is not wired",
                ),
            )
        }
        val issuerUrl =
            config.issuer
                ?: args.baseUrlOverride
                ?: return Err(
                    AuthorizationServerError.ServerError(
                        details = "JARM requires a configured issuer or a request-time baseUrlOverride",
                    ),
                )

        val jarmConfig =
            JarmConfig.fromClientMetadata(
                signedAlg = signingAlg,
                encryptedAlg = encryptedAlg,
                encryptedEnc = client.authorizationEncryptedResponseEnc,
            ) ?: return Err(
                AuthorizationServerError.ServerError(
                    details = "JARM mode could not be derived from client metadata for client '$clientId'",
                ),
            )

        // OIDF JARM §6.1: when authorization_encrypted_response_alg is configured, the AS MUST
        // encrypt to the client's registered encryption key. Resolve a use=enc / wrap-capable JWK
        // from the client's registered jwks whose kty matches the JWE key-encryption alg family
        // (RSA family for RSA-OAEP*, EC for ECDH-ES*). Reject the request as `invalid_client` when
        // no suitable key can be resolved: silently downgrading to bare-mode would erase the
        // confidentiality guarantee the client requested.
        val encryptionRecipient =
            when (jarmConfig.mode) {
                JarmMode.SIGNED -> {
                    null
                }

                JarmMode.ENCRYPTED, JarmMode.SIGNED_ENCRYPTED -> {
                    val keyEncryptionAlg = jarmConfig.encryptionAlgorithm ?: ""
                    val recipientJwk =
                        selectEncryptionJwk(client, keyEncryptionAlg)
                            ?: return Err(
                                AuthorizationServerError.InvalidClient(
                                    details =
                                        "Client '$clientId' requested JARM with " +
                                            "authorization_encrypted_response_alg='$keyEncryptionAlg' but no " +
                                            "matching encryption JWK (use=enc or key_ops including encrypt/wrapKey, " +
                                            "kty matching the alg) was found in client.jwks",
                                ),
                            )
                    ManagedOptsJwk(identifier = recipientJwk)
                }
            }

        // OIDF JARM §4.1: build the response payload from the authorization-response parameters,
        // excluding `state` since the JARM lib lifts it onto its own claim slot.
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
                        // SIGNED + SIGNED_ENCRYPTED need the server's identifier; ENCRYPTED-only
                        // does not (the JARM lib enforces this in its own validation).
                        signingKey = if (jarmConfig.mode == JarmMode.ENCRYPTED) null else serverIdentifier,
                        encryptionRecipient = encryptionRecipient,
                        jarmConfig = jarmConfig,
                        expirationSeconds = config.jarmExpirationSeconds,
                    ),
                ).getOrElse { error ->
                    return Err(
                        AuthorizationServerError.ServerError(
                            details = "Failed to mint JARM response: ${error.message.defaultMessage}",
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
            AuthorizationResponseData(
                code = args.code,
                state = args.state,
                redirectUri = finalRedirectLocation,
                responseMode = args.responseMode,
                formPostHtml = formPostHtml,
            ),
        )
    }

    /**
     * Selects an encryption-capable JWK from the client's inline `jwks` list whose key type matches
     * the JWE `alg` family. Returns `null` when no inline `jwks` are registered or none satisfy the
     * encryption-use + kty filters.
     *
     * Encryption-use filter (JWA RFC 7517 §4.2 / §4.3):
     *  - `use == "enc"`, OR
     *  - `key_ops` contains `encrypt` or `wrap key`.
     *
     * Key-type matching follows JWA RFC 7518 §4: RSA-* / RSA-OAEP* alg names need an RSA key;
     * ECDH-ES* alg names need an EC or OKP key; A*KW / dir use `oct` (not exposed via the inline
     * binder yet, so unreachable here).
     *
     * Resolution intentionally does not cross the network: a registered `jwks_uri` is not fetched
     * here, since synchronous fetch on the authorize hot path would block the redirect. Operators
     * who only have a `jwks_uri` registered must additionally inline the encryption JWK.
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

    /**
     * Maps a JWE key-encryption alg family to the set of JWK `kty` values that can serve as the
     * recipient. RFC 7518 §4 fixes which kty backs which alg family.
     */
    private fun expectedKtyForAlg(alg: String): Set<JwaKeyType>? =
        when {
            alg.startsWith("RSA") -> setOf(JwaKeyType.RSA)
            alg.startsWith("ECDH-ES") -> setOf(JwaKeyType.EC, JwaKeyType.OKP)
            else -> null
        }
}
