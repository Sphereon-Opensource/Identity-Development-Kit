/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.command.clientauth

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.defaults.context.JwtClaimsParser
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwsUtils
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.VerifyJwsArgs
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.common.model.ClientAuthenticationConfig
import com.sphereon.oauth2.common.model.ClientAuthenticationMethod
import com.sphereon.oauth2.server.authorization.command.VerifiedClientAuthentication
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationArgs
import com.sphereon.oauth2.server.authorization.command.VerifyClientAuthenticationCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.storage.AttestationChallengeStorage
import com.sphereon.oauth2.server.authorization.storage.ClientRegistry
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of VerifyClientAuthenticationCommand.
 *
 * Dispatches by ClientAuthenticationConfig variant to verify client identity.
 * For attestation-based auth, implements the full spec flow from
 * draft-ietf-oauth-attestation-based-client-auth-07.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyClientAuthenticationCommandImpl", exact = true)
class VerifyClientAuthenticationCommandImpl(
    execution: SessionExecution,
    private val clientRegistry: ClientRegistry,
    private val jwtService: JwtService,
    private val configProvider: OAuth2ServersConfigProvider,
    private val challengeStorage: AttestationChallengeStorage
) : TypedServiceCommandAdapter<VerifyClientAuthenticationArgs, VerifiedClientAuthentication>(
    commandId = VerifyClientAuthenticationCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<VerifyClientAuthenticationArgs>(),
    outputTypeToken = typeToken<VerifiedClientAuthentication>(),
), VerifyClientAuthenticationCommand {

    override val commandId: String get() = VerifyClientAuthenticationCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyClientAuthenticationArgs

    override suspend fun doExecute(
        args: VerifyClientAuthenticationArgs,
        applyDuring: (VerifyClientAuthenticationArgs) -> VerifyClientAuthenticationArgs
    ): IdkResult<VerifiedClientAuthentication, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        args: VerifyClientAuthenticationArgs
    ): IdkResult<VerifiedClientAuthentication, AuthorizationServerError> {
        return when (val auth = args.clientAuthentication) {
            is ClientAuthenticationConfig.Basic -> verifyBasicAuth(auth, args.clientId)
            is ClientAuthenticationConfig.Post -> verifyPostAuth(auth, args.clientId)
            is ClientAuthenticationConfig.SecretJwt -> verifyJwtAssertion(auth, args.clientId, args.tokenEndpointUrl)
            is ClientAuthenticationConfig.PrivateKeyJwt -> verifyJwtAssertion(auth, args.clientId, args.tokenEndpointUrl)
            is ClientAuthenticationConfig.AttestationJwt -> verifyAttestationAuth(auth, args.clientId, args.tokenEndpointUrl)
            is ClientAuthenticationConfig.None -> Ok(VerifiedClientAuthentication(
                clientId = args.clientId,
                method = ClientAuthenticationMethod.NONE
            ))
            ClientAuthenticationConfig.Anonymous -> Ok(VerifiedClientAuthentication(
                clientId = args.clientId,
                method = ClientAuthenticationMethod.NONE
            ))
        }
    }

    private suspend fun verifyBasicAuth(
        auth: ClientAuthenticationConfig.Basic,
        clientId: String
    ): IdkResult<VerifiedClientAuthentication, AuthorizationServerError> {
        val valid = clientRegistry.verifyClientCredentials(
            auth.credentials.clientId,
            auth.credentials.clientSecret
        ).getOrElse { return Err(it) }

        if (!valid) {
            return Err(AuthorizationServerError.InvalidClient(details = "Invalid client credentials"))
        }

        return Ok(VerifiedClientAuthentication(
            clientId = auth.credentials.clientId,
            method = ClientAuthenticationMethod.CLIENT_SECRET_BASIC
        ))
    }

    private suspend fun verifyPostAuth(
        auth: ClientAuthenticationConfig.Post,
        clientId: String
    ): IdkResult<VerifiedClientAuthentication, AuthorizationServerError> {
        val valid = clientRegistry.verifyClientCredentials(
            auth.credentials.clientId,
            auth.credentials.clientSecret
        ).getOrElse { return Err(it) }

        if (!valid) {
            return Err(AuthorizationServerError.InvalidClient(details = "Invalid client credentials"))
        }

        return Ok(VerifiedClientAuthentication(
            clientId = auth.credentials.clientId,
            method = ClientAuthenticationMethod.CLIENT_SECRET_POST
        ))
    }

    private suspend fun verifyJwtAssertion(
        auth: ClientAuthenticationConfig,
        clientId: String,
        tokenEndpointUrl: String
    ): IdkResult<VerifiedClientAuthentication, AuthorizationServerError> {
        val assertion = when (auth) {
            is ClientAuthenticationConfig.SecretJwt -> auth.assertion
            is ClientAuthenticationConfig.PrivateKeyJwt -> auth.assertion
            else -> return Err(AuthorizationServerError.InvalidClient(details = "Unexpected auth type"))
        }

        // Verify JWT signature
        val verifyResult = jwtService.verifyJws(VerifyJwsArgs(jws = JwsCompact(assertion.assertion)))
            .getOrElse {
                return Err(AuthorizationServerError.InvalidClient(
                    details = "JWT assertion signature verification failed: ${it.message.defaultMessage}"
                ))
            }

        if (!verifyResult.isValid) {
            return Err(AuthorizationServerError.InvalidClient(
                details = "JWT assertion signature invalid: ${verifyResult.errorMessages.joinToString()}"
            ))
        }

        // Validate claims
        val claims = JwtClaimsParser.parseClaimsOrNull(assertion.assertion)
            ?: return Err(AuthorizationServerError.InvalidClient(details = "Cannot parse JWT assertion claims"))

        val iss = claims["iss"]?.jsonPrimitive?.content
        val sub = claims["sub"]?.jsonPrimitive?.content
        val aud = claims["aud"]?.jsonPrimitive?.content

        if (sub != clientId && iss != clientId) {
            return Err(AuthorizationServerError.InvalidClient(details = "JWT assertion sub/iss does not match client_id"))
        }

        if (aud != tokenEndpointUrl) {
            return Err(AuthorizationServerError.InvalidClient(details = "JWT assertion aud does not match token endpoint"))
        }

        val method = when (auth) {
            is ClientAuthenticationConfig.SecretJwt -> ClientAuthenticationMethod.CLIENT_SECRET_JWT
            is ClientAuthenticationConfig.PrivateKeyJwt -> ClientAuthenticationMethod.PRIVATE_KEY_JWT
            else -> ClientAuthenticationMethod.NONE
        }

        return Ok(VerifiedClientAuthentication(clientId = clientId, method = method))
    }

    /**
     * Verify attestation-based client authentication.
     *
     * Implements draft-ietf-oauth-attestation-based-client-auth-07 Section 4.
     */
    private suspend fun verifyAttestationAuth(
        auth: ClientAuthenticationConfig.AttestationJwt,
        clientId: String,
        tokenEndpointUrl: String
    ): IdkResult<VerifiedClientAuthentication, AuthorizationServerError> {
        val config = configProvider.serverConfig
        if (!config.attestation.isEnabled) {
            return Err(AuthorizationServerError.InvalidClient(
                details = "Attestation-based client authentication is not enabled"
            ))
        }

        val attestationJwt = auth.attestation.clientAttestationJwt
        val popJwt = auth.attestation.clientAttestationPopJwt

        // 1. Parse and validate attestation JWT header
        val attestationHeader = parseJwtHeader(attestationJwt)
            ?: return Err(AuthorizationServerError.InvalidClientAttestation(
                details = "Cannot parse attestation JWT header"
            ))

        val attestationTyp = attestationHeader["typ"]?.jsonPrimitive?.content
        if (attestationTyp != "oauth-client-attestation+jwt") {
            return Err(AuthorizationServerError.InvalidClientAttestation(
                details = "Attestation JWT typ must be 'oauth-client-attestation+jwt', got: $attestationTyp"
            ))
        }

        // 2. Parse attestation JWT claims (lightweight decode)
        val attestationClaims = JwtClaimsParser.parseClaimsOrNull(attestationJwt)
            ?: return Err(AuthorizationServerError.InvalidClientAttestation(
                details = "Cannot parse attestation JWT claims"
            ))

        val attIss = attestationClaims["iss"]?.jsonPrimitive?.content
            ?: return Err(AuthorizationServerError.InvalidClientAttestation(details = "Missing iss in attestation JWT"))
        val attSub = attestationClaims["sub"]?.jsonPrimitive?.content
            ?: return Err(AuthorizationServerError.InvalidClientAttestation(details = "Missing sub in attestation JWT"))
        val attExp = attestationClaims["exp"]?.jsonPrimitive?.long
            ?: return Err(AuthorizationServerError.InvalidClientAttestation(details = "Missing exp in attestation JWT"))

        // Use sub as client_id
        val resolvedClientId = if (clientId.isNotEmpty()) clientId else attSub

        // 3. Look up client registration
        val client = clientRegistry.getClient(resolvedClientId)
            .getOrElse { return Err(it) }
            ?: return Err(AuthorizationServerError.ClientNotFound(clientId = resolvedClientId))

        if (client.tokenEndpointAuthMethod != ClientAuthenticationMethod.ATTEST_JWT_CLIENT_AUTH) {
            return Err(AuthorizationServerError.InvalidClient(
                details = "Client is not configured for attestation-based authentication"
            ))
        }

        // 4. Verify attester issuer is trusted
        val trustedIssuers = client.trustedAttesterIssuers
        if (trustedIssuers != null && attIss !in trustedIssuers) {
            return Err(AuthorizationServerError.InvalidClientAttestation(
                details = "Attestation issuer '$attIss' is not trusted for client '$resolvedClientId'"
            ))
        }

        // 5. Verify attestation JWT signature using attester's key
        val attestationVerifyResult = jwtService.verifyJws(VerifyJwsArgs(jws = JwsCompact(attestationJwt)))
            .getOrElse {
                return Err(AuthorizationServerError.InvalidClientAttestation(
                    details = "Attestation JWT signature verification failed: ${it.message.defaultMessage}"
                ))
            }

        if (!attestationVerifyResult.isValid) {
            return Err(AuthorizationServerError.InvalidClientAttestation(
                details = "Attestation JWT signature invalid: ${attestationVerifyResult.errorMessages.joinToString()}"
            ))
        }

        // 6. Check expiration and freshness
        val now = Clock.System.now().epochSeconds
        if (attExp < now) {
            return Err(AuthorizationServerError.UseFreshAttestation(
                details = "Attestation JWT has expired"
            ))
        }

        val attIat = attestationClaims["iat"]?.jsonPrimitive?.long
        if (attIat != null && (now - attIat) > config.attestationMaxLifetimeSeconds) {
            return Err(AuthorizationServerError.UseFreshAttestation(
                details = "Attestation JWT is too old (issued ${now - attIat}s ago, max ${config.attestationMaxLifetimeSeconds}s)"
            ))
        }

        // 7. Extract client instance public key from cnf.jwk
        val cnf = attestationClaims["cnf"]?.jsonObject
            ?: return Err(AuthorizationServerError.InvalidClientAttestation(
                details = "Missing cnf claim in attestation JWT"
            ))

        val cnfJwkJson = cnf["jwk"]?.jsonObject
            ?: return Err(AuthorizationServerError.InvalidClientAttestation(
                details = "Missing jwk in cnf claim"
            ))

        val clientInstanceKey = try {
            Json.decodeFromJsonElement(Jwk.serializer(), cnfJwkJson)
        } catch (e: Exception) {
            return Err(AuthorizationServerError.InvalidClientAttestation(
                details = "Invalid JWK in cnf claim: ${e.message}"
            ))
        }

        // 8. Parse and validate PoP JWT header
        val popHeader = parseJwtHeader(popJwt)
            ?: return Err(AuthorizationServerError.InvalidClientAttestation(
                details = "Cannot parse attestation PoP JWT header"
            ))

        val popTyp = popHeader["typ"]?.jsonPrimitive?.content
        if (popTyp != "oauth-client-attestation-pop+jwt") {
            return Err(AuthorizationServerError.InvalidClientAttestation(
                details = "PoP JWT typ must be 'oauth-client-attestation-pop+jwt', got: $popTyp"
            ))
        }

        // 9. Verify PoP JWT signature with client instance key
        val popVerifyResult = jwtService.verifyJws(VerifyJwsArgs(jws = JwsCompact(popJwt)))
            .getOrElse {
                return Err(AuthorizationServerError.InvalidClientAttestation(
                    details = "PoP JWT signature verification failed: ${it.message.defaultMessage}"
                ))
            }

        if (!popVerifyResult.isValid) {
            return Err(AuthorizationServerError.InvalidClientAttestation(
                details = "PoP JWT signature invalid: ${popVerifyResult.errorMessages.joinToString()}"
            ))
        }

        // 10. Validate PoP JWT claims
        val popClaims = JwtClaimsParser.parseClaimsOrNull(popJwt)
            ?: return Err(AuthorizationServerError.InvalidClientAttestation(
                details = "Cannot parse PoP JWT claims"
            ))

        val popIss = popClaims["iss"]?.jsonPrimitive?.content
        if (popIss != attSub) {
            return Err(AuthorizationServerError.InvalidClientAttestation(
                details = "PoP JWT iss ('$popIss') must match attestation sub ('$attSub')"
            ))
        }

        val popAud = popClaims["aud"]?.jsonPrimitive?.content
        val configIssuer = config.issuer ?: config.baseUrl
        if (popAud != tokenEndpointUrl && popAud != configIssuer) {
            return Err(AuthorizationServerError.InvalidClientAttestation(
                details = "PoP JWT aud does not match AS issuer or token endpoint"
            ))
        }

        val popIat = popClaims["iat"]?.jsonPrimitive?.long
        if (popIat != null && (now - popIat) > config.attestationPopMaxAgeSeconds) {
            return Err(AuthorizationServerError.InvalidClientAttestation(
                details = "PoP JWT is too old (issued ${now - popIat}s ago, max ${config.attestationPopMaxAgeSeconds}s)"
            ))
        }

        // 11. If challenge required, verify challenge claim
        if (config.attestationChallengeRequired) {
            val challengeClaim = popClaims["nonce"]?.jsonPrimitive?.content
            if (challengeClaim == null) {
                // Generate a challenge and return it
                val newChallenge = challengeStorage.generateChallenge()
                    .getOrElse { return Err(it) }
                return Err(AuthorizationServerError.UseAttestationChallenge(challenge = newChallenge))
            }

            challengeStorage.verifyAndConsumeChallenge(challengeClaim)
                .getOrElse { error -> return Err(error) }
        }

        // 12. All checks passed
        return Ok(VerifiedClientAuthentication(
            clientId = resolvedClientId,
            method = ClientAuthenticationMethod.ATTEST_JWT_CLIENT_AUTH,
            clientInstanceKey = clientInstanceKey
        ))
    }

    /**
     * Parse the header portion of a JWT without full verification.
     */
    private fun parseJwtHeader(jwt: String): JsonObject? {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return null
            JwsUtils.decodeBase64UrlToJson(parts[0])
        } catch (e: Exception) {
            null
        }
    }
}
