package com.sphereon.oauth2.server.authorization.impl.command.oidc

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.StringResult
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.jose.jws.CreateJwsArgs
import com.sphereon.crypto.jose.jws.CreateJwsOpts
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenArgs
import com.sphereon.oauth2.server.authorization.command.CreateIdTokenCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import kotlinx.datetime.Clock
import com.sphereon.oauth2.server.authorization.impl.command.putClaims
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of CreateIdTokenCommand
 *
 * Builds and signs an OpenID Connect ID Token JWT.
 *
 * Computes at_hash: SHA-256 of access token ASCII bytes, take left 128 bits, base64url encode.
 * Computes c_hash: same algorithm but for authorization code.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("CreateIdTokenCommandImpl", exact = true)
class CreateIdTokenCommandImpl(
    execution: SessionExecution,
    private val jwtService: JwtService,
    private val configProvider: OAuth2ServersConfigProvider,
    @Named("oauth2.issuerUrl") private val issuerUrl: String,
    @Named("oauth2.serverIdentifier") private val serverIdentifier: ManagedIdentifierOptsOrResult?
) : TypedServiceCommandAdapter<CreateIdTokenArgs, StringResult>(
    commandId = CreateIdTokenCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<CreateIdTokenArgs>(),
    outputTypeToken = typeToken<StringResult>(),
), CreateIdTokenCommand {

    override val commandId: String get() = CreateIdTokenCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateIdTokenArgs

    override suspend fun doExecute(
        args: CreateIdTokenArgs,
        applyDuring: (CreateIdTokenArgs) -> CreateIdTokenArgs
    ): IdkResult<StringResult, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied).map { StringResult(it) }.mapError { IdkError.fromDTO(it) }
    }

    private suspend fun executeInternal(
        args: CreateIdTokenArgs
    ): IdkResult<String, AuthorizationServerError> {
        if (serverIdentifier == null) {
            return Err(
                AuthorizationServerError.ServerError(
                    details = "Cannot create ID token: server signing key not configured",
                    exception = null
                )
            )
        }

        val now = Clock.System.now()
        val config = configProvider.serverConfig
        val expiresAt = now.epochSeconds + config.idTokenLifetimeSeconds

        val payload = buildJsonObject {
            put("iss", issuerUrl)
            put("sub", args.subject)
            put("aud", args.clientId)
            put("exp", expiresAt)
            put("iat", now.epochSeconds)

            args.nonce?.let { put("nonce", it) }
            args.authTime?.let { put("auth_time", it) }
            args.acr?.let { put("acr", it) }
            args.amr?.let { amrList ->
                put("amr", buildJsonArray { amrList.forEach { add(JsonPrimitive(it)) } })
            }

            // at_hash: left half of SHA-256 of access token, base64url encoded
            args.accessToken?.let { token ->
                computeTokenHash(token)?.let { put("at_hash", it) }
            }

            // c_hash: left half of SHA-256 of authorization code, base64url encoded
            args.authorizationCode?.let { code ->
                computeTokenHash(code)?.let { put("c_hash", it) }
            }

            // User claims (identity claims from federation)
            putClaims(args.userClaims)

            // Additional claims
            putClaims(args.additionalClaims)
        }

        val header = buildJsonObject {
            put("typ", "JWT")
        }

        return try {
            val jwsArgs = CreateJwsArgs(
                issuer = serverIdentifier,
                payload = payload.toString(),
                opts = CreateJwsOpts(
                    protectedHeader = header,
                    noIssPayloadUpdate = true
                )
            )

            jwtService.createJwsCompact(jwsArgs)
                .map { it.jwt }
                .mapError { error ->
                    AuthorizationServerError.ServerError(
                        details = "Failed to sign ID token: ${error.message.defaultMessage}",
                        exception = error.exception
                    )
                }
        } catch (e: Exception) {
            Err(
                AuthorizationServerError.ServerError(
                    details = "ID token creation failed: ${e.message}",
                    exception = e
                )
            )
        }
    }

    /**
     * Compute hash for at_hash/c_hash per OpenID Connect Core Section 3.1.3.3:
     * SHA-256 of ASCII bytes, take left 128 bits (16 bytes), base64url encode.
     */
    private fun computeTokenHash(input: String): String? {
        return try {
            val bytes = input.encodeToByteArray()
            val hashBytes = hash(bytes, DigestAlg.SHA256)
            val leftHalf = hashBytes.copyOfRange(0, hashBytes.size / 2)
            leftHalf.encodeToBase64Url()
        } catch (_: Exception) {
            null
        }
    }
}
