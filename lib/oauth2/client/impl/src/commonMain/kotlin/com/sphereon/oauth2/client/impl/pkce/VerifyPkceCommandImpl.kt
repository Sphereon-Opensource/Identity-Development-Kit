package com.sphereon.oauth2.client.impl.pkce

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.EmptyResult
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.VerifyPkceArgs
import com.sphereon.oauth2.client.command.VerifyPkceCommand
import com.sphereon.oauth2.common.error.PkceError
import com.sphereon.oauth2.common.model.PkceMethod
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of VerifyPkceCommand for verifying PKCE challenge/verifier pairs
 */
@Inject
@SingleIn(SessionScope::class)
class VerifyPkceCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<VerifyPkceArgs, EmptyResult>(
    commandId = VerifyPkceCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<VerifyPkceArgs>(),
    outputTypeToken = typeToken<EmptyResult>(),
), VerifyPkceCommand {

    override val commandId: String get() = VerifyPkceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyPkceArgs

    override suspend fun doExecute(
        args: VerifyPkceArgs,
        applyDuring: (VerifyPkceArgs) -> VerifyPkceArgs
    ): IdkResult<EmptyResult, IdkError> {
        val applied = applyDuring(args)
        return verifyPkceInternal(applied.codeVerifier, applied.codeChallenge, applied.method).map { EmptyResult }.mapError { IdkError.fromDTO(it) }
    }

    private suspend fun verifyPkceInternal(
        codeVerifier: String,
        codeChallenge: String,
        method: PkceMethod
    ): IdkResult<Unit, PkceError> {
        return try {
            val calculatedChallenge = calculateCodeChallenge(codeVerifier, method)

            if (calculatedChallenge == codeChallenge) {
                Ok(Unit).asResult()
            } else {
                Err(
                    PkceError.VerificationFailed(
                        reason = "Code challenge mismatch: expected=$codeChallenge, calculated=$calculatedChallenge"
                    )
                ).asResult()
            }
        } catch (e: Exception) {
            Err(
                PkceError.VerificationFailed(
                    reason = e.message ?: "Unknown error during PKCE verification"
                )
            ).asResult()
        }
    }

    /**
     * Calculates the code challenge from the code verifier
     *
     * @param verifier The code verifier
     * @param method The code challenge method (plain or S256)
     * @return The code challenge
     */
    private fun calculateCodeChallenge(
        verifier: String,
        method: PkceMethod
    ): String {
        return when (method) {
            PkceMethod.PLAIN -> verifier
            PkceMethod.S256 -> {
                // Use IDK's crypto infrastructure for SHA256
                val hashBytes = hash(verifier.encodeToByteArray(), DigestAlg.SHA256)
                hashBytes.encodeToBase64Url()
            }
        }
    }
}
