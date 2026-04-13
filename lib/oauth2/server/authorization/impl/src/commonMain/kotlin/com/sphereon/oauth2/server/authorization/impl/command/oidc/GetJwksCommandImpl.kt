package com.sphereon.oauth2.server.authorization.impl.command.oidc

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOpts
import com.sphereon.crypto.resolution.managed.ManagedIdentifierResult
import com.sphereon.crypto.resolution.managed.MultiManagedIdentifierService
import com.sphereon.crypto.resolution.tryManagedIdentifierToJwk
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.command.GetJwksArgs
import com.sphereon.oauth2.server.authorization.command.GetJwksCommand
import com.sphereon.oauth2.server.authorization.command.JwksResult
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of GetJwksCommand
 *
 * Returns the server's public signing key(s) for ID token and access token verification.
 * Always available (needed for JWT access token verification regardless of OIDC mode).
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("GetJwksCommandImpl", exact = true)
class GetJwksCommandImpl(
    execution: SessionExecution,
    @Named("oauth2.serverIdentifier") private val serverIdentifier: ManagedIdentifierOptsOrResult?,
    private val multiManagedIdentifierService: MultiManagedIdentifierService
) : TypedServiceCommandAdapter<GetJwksArgs, JwksResult>(
    commandId = GetJwksCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<GetJwksArgs>(),
    outputTypeToken = typeToken<JwksResult>(),
), GetJwksCommand {

    override val commandId: String get() = GetJwksCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is GetJwksArgs

    override suspend fun doExecute(
        args: GetJwksArgs,
        applyDuring: (GetJwksArgs) -> GetJwksArgs
    ): IdkResult<JwksResult, IdkError> {
        applyDuring(args)
        return executeInternal()
    }

    private suspend fun executeInternal(): IdkResult<JwksResult, IdkError> {
        if (serverIdentifier == null) {
            return Ok(JwksResult(keys = emptyList()))
        }

        // If the identifier is an unresolved opts (e.g., ManagedOptsAlias), resolve it first
        val resolvedIdentifier: ManagedIdentifierOptsOrResult = if (serverIdentifier is ManagedIdentifierOpts && serverIdentifier !is ManagedIdentifierResult<*>) {
            multiManagedIdentifierService.resolve(serverIdentifier)
                .getOrElse { return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to resolve server signing key: ${it.message}")) }
        } else {
            serverIdentifier
        }

        val jwkResult = tryManagedIdentifierToJwk(resolvedIdentifier)
            .getOrElse { return Err(it) }

        val publicJwk = (jwkResult.identifier.toPublicKey() as? Jwk)
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to convert server key to public JWK"))

        return Ok(JwksResult(keys = listOf(publicJwk)))
    }
}
