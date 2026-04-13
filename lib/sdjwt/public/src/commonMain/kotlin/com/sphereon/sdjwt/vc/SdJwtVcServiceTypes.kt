package com.sphereon.sdjwt.vc

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.sdjwt.KeyBindingJwt
import com.sphereon.sdjwt.SdJwtCompact
import com.sphereon.sdjwt.vc.command.ResolveIssuerMetadataCommand
import com.sphereon.sdjwt.vc.command.ResolveTypeMetadataCommand
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcCommand
import com.sphereon.sdjwt.vc.command.VerifySdJwtVcPresentationCommand

/**
 * Command service interface for SD-JWT-VC verification
 */
interface VerifySdJwtVcCommandService {
    suspend fun verifySdJwtVc(args: VerifySdJwtVcArgs): IdkResult<SdJwtVcVerificationResult, IdkError>
}

/**
 * Command service interface for SD-JWT-VC presentation verification
 */
interface VerifySdJwtVcPresentationCommandService {
    suspend fun verifySdJwtVcPresentation(args: VerifySdJwtVcPresentationArgs): IdkResult<SdJwtVcPresentationVerificationResult, IdkError>
}

/**
 * Command service interface for resolving type metadata
 */
interface ResolveTypeMetadataCommandService {
    suspend fun resolveTypeMetadata(args: ResolveTypeMetadataArgs): IdkResult<TypeMetadataResolutionResult, IdkError>
}

/**
 * Command service interface for resolving issuer metadata
 */
interface ResolveIssuerMetadataCommandService {
    suspend fun resolveIssuerMetadata(args: ResolveIssuerMetadataArgs): IdkResult<IssuerMetadataResolutionResult, IdkError>
}

/**
 * Service interface for SD-JWT-VC operations
 *
 * Provides high-level API for:
 * - Verifying SD-JWT-VC credentials
 * - Verifying SD-JWT-VC presentations (with KB-JWT)
 * - Resolving type and issuer metadata
 *
 * Follows the same pattern as SdJwtService, exposing both service methods
 * and underlying commands for advanced usage.
 */
interface SdJwtVcService : VerifySdJwtVcCommandService, VerifySdJwtVcPresentationCommandService, ResolveTypeMetadataCommandService, ResolveIssuerMetadataCommandService {

    /**
     * Provides access to the underlying commands for advanced usage scenarios.
     */
    val commands: Commands

    /**
     * Container for all SD-JWT-VC commands.
     */
    interface Commands {
        val verifySdJwtVc: VerifySdJwtVcCommand
        val verifySdJwtVcPresentation: VerifySdJwtVcPresentationCommand
        val resolveTypeMetadata: ResolveTypeMetadataCommand
        val resolveIssuerMetadata: ResolveIssuerMetadataCommand
    }

    /**
     * Verify SD-JWT-VC credential (without Key Binding)
     *
     * @param args Verification arguments
     * @return Verification result
     */
    suspend fun verify(args: VerifySdJwtVcArgs): IdkResult<SdJwtVcVerificationResult, IdkError>

    /**
     * Verify SD-JWT-VC presentation (with Key Binding)
     *
     * @param args Presentation verification arguments
     * @return Presentation verification result
     */
    suspend fun verifyPresentation(args: VerifySdJwtVcPresentationArgs): IdkResult<SdJwtVcPresentationVerificationResult, IdkError>
}

/**
 * Arguments for SD-JWT-VC verification
 *
 * @property sdJwt SD-JWT string in compact format
 * @property opts Verification options
 */
data class VerifySdJwtVcArgs(
    val sdJwt: String,
    val opts: SdJwtVcVerificationOpts = SdJwtVcVerificationOpts(),
)

/**
 * Arguments for SD-JWT-VC presentation verification
 *
 * @property sdJwt SD-JWT+KB string in compact format
 * @property expectedNonce Verifier's expected nonce (in KB-JWT)
 * @property opts Verification options
 */
data class VerifySdJwtVcPresentationArgs(
    val sdJwt: String,
    val expectedNonce: String?,
    val opts: SdJwtVcVerificationOpts = SdJwtVcVerificationOpts(),
)

/**
 * Arguments for type metadata resolution
 *
 * @property vct Verifiable Credential Type identifier
 * @property resolver Custom resolver (optional, uses default if null)
 */
data class ResolveTypeMetadataArgs(
    val vct: String,
    val resolver: TypeMetadataResolver? = null,
)

/**
 * Arguments for issuer metadata resolution
 *
 * @property issuer Issuer identifier (HTTPS URL)
 * @property resolver Custom resolver (optional, uses default if null)
 */
data class ResolveIssuerMetadataArgs(
    val issuer: String,
    val resolver: IssuerMetadataResolver? = null,
)



/**
 * SD-JWT-VC specific verifier
 *
 * Extends base SD-JWT verification with:
 * - VCT (Verifiable Credential Type) validation
 * - Issuer metadata resolution and verification
 * - Type metadata resolution and validation
 * - Status checking (optional)
 *
 * Based on draft-ietf-oauth-sd-jwt-vc-13
 */
interface SdJwtVcVerifier {
    /**
     * Verify SD-JWT-VC without Key Binding
     *
     * Used by:
     * - Holder verifying issued credential
     * - Verifier when KB-JWT must not be present
     *
     * @param sdJwtString SD-JWT in compact format
     * @param opts Verification options
     * @return Verification result
     */
    suspend fun verify(
        sdJwtString: String,
        opts: SdJwtVcVerificationOpts = SdJwtVcVerificationOpts(),
    ): IdkResult<SdJwtVcVerificationResult, SdJwtVcVerificationError>

    /**
     * Verify SD-JWT-VC with Key Binding
     *
     * Used by:
     * - Verifier validating holder presentation
     *
     * @param sdJwtString SD-JWT+KB in compact format
     * @param expectedNonce Verifier's expected nonce (in KB-JWT)
     * @param opts Verification options
     * @return Verification result
     */
    suspend fun verifyPresentation(
        sdJwtString: String,
        expectedNonce: String?,
        opts: SdJwtVcVerificationOpts = SdJwtVcVerificationOpts(),
    ): IdkResult<SdJwtVcPresentationVerificationResult, SdJwtVcVerificationError>
}

/**
 * SD-JWT-VC verification errors
 */
sealed interface SdJwtVcVerificationError {
    /** VCT claim is missing or invalid */
    data class InvalidVct(val message: String) : SdJwtVcVerificationError

    /** Type header (typ) is invalid or missing (draft-13 §4.1) */
    data class InvalidTypeHeader(val message: String) : SdJwtVcVerificationError

    /** Type metadata resolution failed */
    data class TypeMetadataResolutionFailed(val error: TypeMetadataResolutionError) : SdJwtVcVerificationError

    /** Type metadata validation failed */
    data class TypeMetadataValidationFailed(val errors: List<ClaimValidationError>) : SdJwtVcVerificationError

    /** Issuer metadata resolution failed */
    data class IssuerMetadataResolutionFailed(val error: IssuerMetadataResolutionError) : SdJwtVcVerificationError

    /** Status check failed */
    data class StatusCheckFailed(val message: String) : SdJwtVcVerificationError

    /** Base SD-JWT verification failed */
    data class SdJwtVerificationFailed(val message: String) : SdJwtVcVerificationError
}