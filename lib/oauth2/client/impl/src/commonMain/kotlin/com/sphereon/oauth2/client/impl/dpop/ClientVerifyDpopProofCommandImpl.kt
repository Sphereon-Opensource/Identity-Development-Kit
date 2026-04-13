package com.sphereon.oauth2.client.impl.dpop

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.jose.generateJwkThumbprint
import com.sphereon.crypto.jose.jws.Jws
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.VerifyJwsArgs
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.command.VerifyDpopProofCommand
import com.sphereon.oauth2.common.error.DpopError
import com.sphereon.oauth2.common.model.DpopJwtHeader
import com.sphereon.oauth2.common.model.DpopJwtPayload
import com.sphereon.oauth2.common.model.VerifyDpopProofOptions
import com.sphereon.oauth2.common.model.VerifyDpopProofResult
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of VerifyDpopProofCommand
 *
 * Verifies DPoP proof JWTs as defined in RFC 9449.
 */
@Inject
@SingleIn(SessionScope::class)
class ClientVerifyDpopProofCommandImpl(
    execution: SessionExecution,
    private val jwtService: JwtService
) : TypedServiceCommandAdapter<VerifyDpopProofOptions, VerifyDpopProofResult>(
    commandId = VerifyDpopProofCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<VerifyDpopProofOptions>(),
    outputTypeToken = typeToken<VerifyDpopProofResult>(),
), VerifyDpopProofCommand {

    override val commandId: String get() = VerifyDpopProofCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is VerifyDpopProofOptions

    override suspend fun doExecute(
        args: VerifyDpopProofOptions,
        applyDuring: (VerifyDpopProofOptions) -> VerifyDpopProofOptions
    ): IdkResult<VerifyDpopProofResult, IdkError> {
        val applied = applyDuring(args)
        return verifyDpopProofInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun verifyDpopProofInternal(
        options: VerifyDpopProofOptions
    ): IdkResult<VerifyDpopProofResult, DpopError> {
        return try {
            // Parse JWT parts
            val parsed = parseJwt(options.dpopProof).getOrElse { return Err(it) }

            // Validate typ header
            validateTypHeader(parsed.header).getOrElse { return Err(it) }

            // Verify JWT signature using embedded JWK
            verifySignature(options.dpopProof, parsed.header).getOrElse { return Err(it) }

            // Validate claims
            val now = options.now ?: Clock.System.now().epochSeconds
            validateClaims(parsed.payload, options, now).getOrElse { return Err(it) }

            // Validate JWK thumbprint if expected
            val jwkThumbprint = generateJwkThumbprint(parsed.header.jwk)
            val expectedThumbprint = options.expectedJwkThumbprint
            if (expectedThumbprint != null && jwkThumbprint != expectedThumbprint) {
                return Err(DpopError.ClaimMismatch(
                    claimName = "jwk thumbprint",
                    expected = expectedThumbprint,
                    actual = jwkThumbprint
                ))
            }

            // Validate allowed signing algorithms
            val allowedAlgs = options.allowedSigningAlgs
            if (allowedAlgs != null && !allowedAlgs.contains(parsed.header.alg)) {
                return Err(DpopError.VerificationFailed(
                    reason = "Algorithm '${parsed.header.alg}' not in allowed list: $allowedAlgs"
                ))
            }

            Ok(VerifyDpopProofResult(
                header = parsed.header,
                payload = parsed.payload,
                jwkThumbprint = jwkThumbprint
            ))

        } catch (e: Exception) {
            Err(DpopError.VerificationFailed(
                reason = "DPoP proof verification failed: ${e.message}",
                exception = e
            ))
        }
    }

    /**
     * Parsed JWT structure
     */
    private data class ParsedJwt(
        val header: DpopJwtHeader,
        val payload: DpopJwtPayload
    )

    /**
     * Parses a JWT into header and payload
     */
    private fun parseJwt(dpopProof: String): IdkResult<ParsedJwt, DpopError> {
        return try {
            // Split into header.payload.signature
            val parts = dpopProof.split(".")
            if (parts.size != 3) {
                return Err(DpopError.InvalidFormat(
                    reason = "JWT must have 3 parts (header.payload.signature), got ${parts.size}"
                ))
            }

            // Decode header and payload
            val headerJson = parts[0].decodeFromBase64Url().decodeToString()
            val payloadJson = parts[1].decodeFromBase64Url().decodeToString()

            val header = Json.decodeFromString<DpopJwtHeader>(headerJson)
            val payload = Json.decodeFromString<DpopJwtPayload>(payloadJson)

            Ok(ParsedJwt(header, payload))

        } catch (e: Exception) {
            Err(DpopError.InvalidFormat(
                reason = "Failed to parse JWT: ${e.message}"
            ))
        }
    }

    /**
     * Validates that the typ header is "dpop+jwt"
     */
    private fun validateTypHeader(header: DpopJwtHeader): IdkResult<Unit, DpopError> {
        if (header.typ != "dpop+jwt") {
            return Err(DpopError.InvalidFormat(
                reason = "typ header must be 'dpop+jwt', got '${header.typ}'"
            ))
        }
        return Ok(Unit)
    }

    /**
     * Verifies the JWT signature using the embedded public key
     */
    private suspend fun verifySignature(
        dpopProof: String,
        header: DpopJwtHeader
    ): IdkResult<Unit, DpopError> {
        // For DPoP, we verify using the embedded JWK in the header
        // The JwtService will extract the key from the JWT header automatically
        val verifyArgs = VerifyJwsArgs(
            jws = JwsCompact(dpopProof) as Jws
            // identifier is null - JwtService will use the embedded jwk from the header
        )

        val validationResult = jwtService.verifyJws(verifyArgs).getOrElse { error ->
            return Err(DpopError.VerificationFailed(
                reason = "JWT verification error: ${error.message.defaultMessage}",
                exception = error.exception
            ))
        }

        return if (validationResult.isValid) {
            Ok(Unit)
        } else {
            Err(DpopError.VerificationFailed(
                reason = "JWT signature verification failed: ${validationResult.errorMessages.joinToString()}"
            ))
        }
    }

    /**
     * Validates all required DPoP claims
     */
    private fun validateClaims(
        payload: DpopJwtPayload,
        options: VerifyDpopProofOptions,
        now: Long
    ): IdkResult<Unit, DpopError> {
        // Validate htm (HTTP method)
        if (payload.htm.uppercase() != options.httpMethod.uppercase()) {
            return Err(DpopError.ClaimMismatch(
                claimName = "htm",
                expected = options.httpMethod.uppercase(),
                actual = payload.htm.uppercase()
            ))
        }

        // Validate htu (HTTP URL - normalize both for comparison)
        val normalizedPayloadHtu = normalizeUrl(payload.htu)
        val normalizedExpectedHtu = normalizeUrl(options.httpUrl)
        if (normalizedPayloadHtu != normalizedExpectedHtu) {
            return Err(DpopError.ClaimMismatch(
                claimName = "htu",
                expected = normalizedExpectedHtu,
                actual = normalizedPayloadHtu
            ))
        }

        // Validate iat (issued at timestamp)
        // RFC 9449: The server SHOULD reject proofs that are too old or in the future
        val maxAgeSeconds = 60L // Maximum age: 60 seconds
        val clockSkewSeconds = 5L // Allow 5 seconds of clock skew

        if (payload.iat > now + clockSkewSeconds) {
            return Err(DpopError.ClaimMismatch(
                claimName = "iat",
                expected = "not in future (now=$now, skew=$clockSkewSeconds)",
                actual = payload.iat.toString()
            ))
        }

        if (payload.iat < now - maxAgeSeconds) {
            return Err(DpopError.ClaimMismatch(
                claimName = "iat",
                expected = "not older than $maxAgeSeconds seconds (now=$now)",
                actual = payload.iat.toString()
            ))
        }

        // Validate nonce if expected
        val expectedNonce = options.expectedNonce
        if (expectedNonce != null && payload.nonce != expectedNonce) {
            return Err(DpopError.ClaimMismatch(
                claimName = "nonce",
                expected = expectedNonce,
                actual = payload.nonce ?: "null"
            ))
        }

        // Validate ath (access token hash) if access token provided
        val accessToken = options.accessToken
        if (accessToken != null) {
            val ath = payload.ath
            if (ath == null) {
                return Err(DpopError.MissingClaim(
                    claimName = "ath"
                ))
            }

            val expectedAth = calculateAccessTokenHash(accessToken)
            if (ath != expectedAth) {
                return Err(DpopError.ClaimMismatch(
                    claimName = "ath",
                    expected = expectedAth,
                    actual = ath
                ))
            }
        }

        return Ok(Unit)
    }

    /**
     * Normalizes a URL by removing query parameters and fragment
     */
    private fun normalizeUrl(url: String): String {
        val queryStart = url.indexOf('?')
        val fragmentStart = url.indexOf('#')

        val cutPosition = when {
            queryStart != -1 && fragmentStart != -1 -> minOf(queryStart, fragmentStart)
            queryStart != -1 -> queryStart
            fragmentStart != -1 -> fragmentStart
            else -> url.length
        }

        return url.substring(0, cutPosition)
    }

    /**
     * Calculates the SHA-256 hash of an access token (RFC 9449 Section 4.2)
     */
    private fun calculateAccessTokenHash(accessToken: String): String {
        val tokenBytes = accessToken.encodeToByteArray()
        val hashBytes = hash(tokenBytes, DigestAlg.SHA256)
        return hashBytes.encodeToBase64Url()
    }
}
