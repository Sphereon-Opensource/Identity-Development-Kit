package com.sphereon.oauth2.client.service

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.oauth2.common.command.CreateDpopProofCommand
import com.sphereon.oauth2.common.command.VerifyDpopProofCommand
import com.sphereon.oauth2.common.model.CreateDpopProofOptions
import com.sphereon.oauth2.common.model.DpopProofResult
import com.sphereon.oauth2.common.model.VerifyDpopProofOptions
import com.sphereon.oauth2.common.model.VerifyDpopProofResult
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Service for DPoP (Demonstrating Proof-of-Possession) operations (RFC 9449)
 *
 * DPoP binds tokens to a specific client by requiring cryptographic proof of possession
 * of a private key when using the token.
 *
 * This service follows the Command/Service pattern:
 * - Service methods provide convenient access for common operations
 * - Commands are exposed via the `commands` property for advanced usage
 * - Commands can also be injected directly for fine-grained control
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DpopService", exact = true)
@JsExportCompat
interface DpopService {

    /**
     * Provides access to underlying commands
     */
    val commands: Commands

    /**
     * Container for DPoP commands
     */
    @JsExportIgnoreCompat
    interface Commands {
        val createDpopProof: CreateDpopProofCommand
        val verifyDpopProof: VerifyDpopProofCommand
    }

    /**
     * Creates a DPoP proof JWT
     *
     * The DPoP proof is a JWT signed with the client's private key that includes:
     * - jti: Unique identifier for replay prevention
     * - htm: HTTP method of the request
     * - htu: HTTP URL of the request (without query and fragment)
     * - iat: Issued at timestamp
     * - ath: Hash of the access token (when using with access token)
     * - nonce: Server-provided nonce (when required by server)
     *
     * @param options Options for creating the DPoP proof (includes issuer/key for signing)
     * @param publicJwk The public key (JWK) to embed in the DPoP proof header
     * @return IdkResult containing the DPoP proof JWT and JWK thumbprint, or an IdkError
     *
     * Example usage:
     * ```kotlin
     * val result = dpopService.createDpopProof(
     *     options = CreateDpopProofOptions(
     *         issuer = managedIdentifier,
     *         httpMethod = "POST",
     *         httpUrl = "https://as.example.com/token",
     *         accessToken = accessToken, // Optional
     *         nonce = dpopNonce // Optional, from server
     *     ),
     *     publicJwk = keyPair.publicKey
     * )
     * ```
     */
    suspend fun createDpopProof(
        options: CreateDpopProofOptions,
        publicJwk: Jwk
    ): IdkResult<DpopProofResult, IdkError>

    /**
     * Verifies a DPoP proof JWT
     *
     * Validates that the DPoP proof:
     * - Has a valid JWT signature using the embedded public key
     * - Has typ header set to "dpop+jwt"
     * - Contains all required claims (jti, htm, htu, iat)
     * - htm and htu match the request
     * - iat is not too old or in the future
     * - ath matches the access token hash (if access token provided)
     * - nonce matches the expected value (if expected nonce provided)
     * - JWK thumbprint matches (if expected thumbprint provided)
     * - Algorithm is in allowed list (if allowed algorithms specified)
     *
     * @param options Options for verifying the DPoP proof
     * @return IdkResult containing the verified header, payload, and JWK thumbprint, or an IdkError
     *
     * Example usage:
     * ```kotlin
     * val result = dpopService.verifyDpopProof(
     *     options = VerifyDpopProofOptions(
     *         dpopProof = dpopProofJwt,
     *         httpMethod = "GET",
     *         httpUrl = "https://rs.example.com/resource",
     *         accessToken = accessToken, // Optional
     *         expectedJwkThumbprint = cnfClaim.jkt, // Optional, from access token
     *         expectedNonce = serverNonce // Optional
     *     )
     * )
     * ```
     */
    suspend fun verifyDpopProof(
        options: VerifyDpopProofOptions
    ): IdkResult<VerifyDpopProofResult, IdkError>
}
