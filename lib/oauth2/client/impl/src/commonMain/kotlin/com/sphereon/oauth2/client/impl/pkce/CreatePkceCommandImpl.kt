package com.sphereon.oauth2.client.impl.pkce

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.command.CreatePkceCommand
import com.sphereon.oauth2.common.error.PkceError
import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.oauth2.common.model.PkceMethod
import com.sphereon.oauth2.client.validation.validatePkceData
import com.sphereon.oauth2.common.validation.toIdkResult
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.random.Random

/**
 * Implementation of CreatePkceCommand for generating PKCE challenge/verifier pairs
 */
@Inject
@SingleIn(SessionScope::class)
class CreatePkceCommandImpl(
    execution: SessionExecution,
) : TypedServiceCommandAdapter<CreatePkceArgs, PkceData>(
    commandId = CreatePkceCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<CreatePkceArgs>(),
    outputTypeToken = typeToken<PkceData>(),
), CreatePkceCommand {

    override val commandId: String get() = CreatePkceCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreatePkceArgs

    override suspend fun doExecute(
        args: CreatePkceArgs,
        applyDuring: (CreatePkceArgs) -> CreatePkceArgs
    ): IdkResult<PkceData, IdkError> {
        val applied = applyDuring(args)
        return createPkceInternal(applied.codeVerifier, applied.allowedMethods).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun createPkceInternal(
        codeVerifier: String?,
        allowedMethods: List<PkceMethod>
    ): IdkResult<PkceData, PkceError> {
        if (allowedMethods.isEmpty()) {
            return Err(PkceError.NoMethodsAllowed).asResult()
        }

        return try {
            val verifier = codeVerifier ?: generateCodeVerifier()

            // Prefer S256 over PLAIN for security
            val method = allowedMethods.firstOrNull { it == PkceMethod.S256 }
                ?: allowedMethods.first()

            val challenge = calculateCodeChallenge(verifier, method)

            val pkceData = PkceData(
                codeVerifier = verifier,
                codeChallenge = challenge,
                codeChallengeMethod = method
            )

            // Validate using Konform
            validatePkceData(pkceData).toIdkResult { errors ->
                PkceError.GenerationFailed(
                    reason = "PKCE validation failed: ${errors.joinToString("; ") { it.message }}"
                )
            }
        } catch (e: Exception) {
            Err(
                PkceError.GenerationFailed(
                    reason = e.message ?: "Unknown error during PKCE generation"
                )
            ).asResult()
        }
    }

    /**
     * Generates a cryptographically secure random code verifier
     *
     * RFC 7636 requires:
     * - Length: 43-128 characters
     * - Character set: [A-Z] / [a-z] / [0-9] / "-" / "." / "_" / "~" (unreserved)
     *
     * We generate 64 random bytes (512 bits) and encode as base64url,
     * which produces 86 characters (well within the 43-128 range).
     */
    private fun generateCodeVerifier(): String {
        val randomBytes = Random.Default.nextBytes(64)
        return randomBytes.encodeToBase64Url()
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
