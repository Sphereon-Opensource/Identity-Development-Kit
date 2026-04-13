package com.sphereon.oauth2.client.command

import com.sphereon.core.api.service.EmptyResult
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.client.model.PkceData
import com.sphereon.oauth2.common.model.PkceMethod

/**
 * Arguments for creating a PKCE challenge/verifier pair
 *
 * @property codeVerifier Optional code verifier; if not provided, one will be generated
 * @property allowedMethods Allowed PKCE methods (defaults to S256 and PLAIN)
 */
data class CreatePkceArgs(
    val codeVerifier: String? = null,
    val allowedMethods: List<PkceMethod> = listOf(PkceMethod.S256, PkceMethod.PLAIN)
)

/**
 * Arguments for verifying a PKCE challenge/verifier pair
 *
 * @property codeVerifier The code verifier
 * @property codeChallenge The code challenge to verify against
 * @property method The PKCE method used
 */
data class VerifyPkceArgs(
    val codeVerifier: String,
    val codeChallenge: String,
    val method: PkceMethod
)

/**
 * Command for creating PKCE challenge/verifier pairs (RFC 7636)
 */
interface CreatePkceCommand : ServiceCommand<CreatePkceArgs, PkceData> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.pkce.create"
    }
}

/**
 * Command for verifying PKCE challenge/verifier pairs (RFC 7636)
 */
interface VerifyPkceCommand : ServiceCommand<VerifyPkceArgs, EmptyResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.pkce.verify"
    }
}
