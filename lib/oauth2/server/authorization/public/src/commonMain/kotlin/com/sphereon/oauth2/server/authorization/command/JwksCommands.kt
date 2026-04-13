package com.sphereon.oauth2.server.authorization.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.crypto.core.jose.Jwk
import kotlinx.serialization.Serializable

/**
 * Arguments for the JWKS endpoint (empty — returns all public keys)
 */
class GetJwksArgs

/**
 * JWKS endpoint result
 */
@Serializable
data class JwksResult(
    val keys: List<Jwk>
)

/**
 * Get JWKS command
 *
 * Returns the server's public signing key(s) for ID token and access token verification.
 * Always available (needed for JWT access token verification regardless of OIDC mode).
 */
interface GetJwksCommand : ServiceCommand<GetJwksArgs, JwksResult> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.jwks.get"
    }
}
