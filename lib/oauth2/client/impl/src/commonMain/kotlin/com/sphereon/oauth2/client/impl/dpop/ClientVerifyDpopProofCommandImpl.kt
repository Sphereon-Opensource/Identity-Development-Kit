/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.oauth2.client.impl.dpop

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.security.ConstantTime
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.generateJwkThumbprint
import com.sphereon.crypto.jose.jws.Jws
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.command.VerifyDpopProofCommand
import com.sphereon.oauth2.common.error.DpopError
import com.sphereon.oauth2.common.model.DpopJwtHeader
import com.sphereon.oauth2.common.model.DpopJwtPayload
import com.sphereon.oauth2.common.model.VerifyDpopProofOptions
import com.sphereon.oauth2.common.model.VerifyDpopProofResult
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/**
 * Implementation of VerifyDpopProofCommand
 *
 * Verifies DPoP proof JWTs as defined in RFC 9449.
 */
@Inject
@SingleIn(SessionScope::class)
class ClientVerifyDpopProofCommandImpl(
    execution: SessionExecution,
    private val jwtService: JwtService,
    private val appConfigService: com.sphereon.core.api.conf.AppConfigService,
) : TypedServiceCommandAdapter<VerifyDpopProofOptions, VerifyDpopProofResult, IdkError>(
        commandId = VerifyDpopProofCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<VerifyDpopProofOptions>(),
        outputTypeToken = typeToken<VerifyDpopProofResult>(),
    ),
    VerifyDpopProofCommand {
    override val commandId: String get() = VerifyDpopProofCommand.COMMAND_ID

    /**
     * RFC 9449 §4.2 lists `jti` / `htm` / `htu` / `iat` / `nonce` / `ath` as the defined DPoP
     * proof claims, but does not forbid extras. Real-world clients (FAPI2 conformance, AS-Init
     * Web wallet) routinely add `nbf` / `exp` for additional time-validity hints. Decode with
     * `ignoreUnknownKeys = true` so we don't reject otherwise-valid proofs over forward-compat
     * claims; the AS-side time / nonce / jti checks still apply on the parsed claims.
     */
    private val lenientJson = Json { ignoreUnknownKeys = true }

    override suspend fun supports(args: Any): Boolean = args is VerifyDpopProofOptions

    override suspend fun doExecute(
        args: VerifyDpopProofOptions,
        applyDuring: (VerifyDpopProofOptions) -> VerifyDpopProofOptions,
    ): IdkResult<VerifyDpopProofResult, IdkError> {
        val applied = applyDuring(args)
        return verifyDpopProofInternal(applied).mapError { IdkError.fromDTO(it) }
    }

    private suspend fun verifyDpopProofInternal(options: VerifyDpopProofOptions): IdkResult<VerifyDpopProofResult, DpopError> {
        return try {
            // RFC 9449 §4.1: exactly one `DPoP` HTTP header is REQUIRED. Detect duplicates by
            // checking for `,` in the proof string — RFC 7230 §3.2.2 mandates that a recipient
            // joins multi-value headers with `,`, and a base64url JWT contains only the
            // `[A-Za-z0-9._~-]` charset (RFC 4648 §5), so a comma is unambiguously the
            // multi-header join. The transport adapter ALSO surfaces this via
            // `GenericHttpRequest.multiValueHeaders`, but binary boundaries below the HTTP
            // layer can collapse multi-value to the joined scalar; checking here ensures the
            // refusal fires regardless of the path the proof took to reach us.
            if (options.dpopProof.contains(',')) {
                return Err(
                    DpopError.InvalidFormat(
                        reason = "Multiple DPoP HTTP headers presented; RFC 9449 §4.1 requires exactly one",
                    ),
                )
            }

            // Parse JWT parts.
            val parsed = parseJwt(options.dpopProof).getOrElse { return Err(it) }

            // Validate typ header
            validateTypHeader(parsed.header).getOrElse { return Err(it) }

            // RFC 9449 §4.1: the embedded `jwk` MUST be a public key only. A wallet that
            // includes private-key components (`d`, RSA `p`/`q`/`dp`/`dq`/`qi`, OKP `d`) is
            // either misconfigured or actively trying to leak its key — both are spec
            // violations and the resource server MUST refuse the proof.
            validateJwkIsPublicKeyOnly(parsed.header.jwk).getOrElse { return Err(it) }

            // Verify JWT signature using embedded JWK
            verifySignature(options.dpopProof).getOrElse { return Err(it) }

            // Validate claims
            val now = options.now ?: Clock.System.now().epochSeconds
            validateClaims(parsed.payload, options, now).getOrElse { return Err(it) }

            // Validate JWK thumbprint if expected
            val jwkThumbprint = generateJwkThumbprint(parsed.header.jwk)
            val expectedThumbprint = options.expectedJwkThumbprint
            if (expectedThumbprint != null && jwkThumbprint != expectedThumbprint) {
                return Err(
                    DpopError.ClaimMismatch(
                        claimName = "jwk thumbprint",
                        expected = expectedThumbprint,
                        actual = jwkThumbprint,
                    ),
                )
            }

            // Validate allowed signing algorithms
            val allowedAlgs = options.allowedSigningAlgs
            if (allowedAlgs != null && !allowedAlgs.contains(parsed.header.alg)) {
                return Err(
                    DpopError.VerificationFailed(
                        reason = "Algorithm '${parsed.header.alg}' not in allowed list: $allowedAlgs",
                    ),
                )
            }

            Ok(
                VerifyDpopProofResult(
                    header = parsed.header,
                    payload = parsed.payload,
                    jwkThumbprint = jwkThumbprint,
                ),
            )
        } catch (expected: Exception) {
            Err(
                DpopError.VerificationFailed(
                    reason = "DPoP proof verification failed: ${expected.message}",
                    exception = expected,
                ),
            )
        }
    }

    /**
     * Parsed JWT structure
     */
    private data class ParsedJwt(
        val header: DpopJwtHeader,
        val payload: DpopJwtPayload,
    )

    /**
     * Parses a JWT into header and payload
     */
    private fun parseJwt(dpopProof: String): IdkResult<ParsedJwt, DpopError> {
        return try {
            // Split into header.payload.signature
            val parts = dpopProof.split(".")
            if (parts.size != JWT_PART_COUNT) {
                return Err(
                    DpopError.InvalidFormat(
                        reason = "JWT must have $JWT_PART_COUNT parts (header.payload.signature), got ${parts.size}",
                    ),
                )
            }

            // Decode header and payload
            val headerJson = parts[0].decodeFromBase64Url().decodeToString()
            val payloadJson = parts[1].decodeFromBase64Url().decodeToString()

            val header = lenientJson.decodeFromString<DpopJwtHeader>(headerJson)
            val payload = lenientJson.decodeFromString<DpopJwtPayload>(payloadJson)

            Ok(ParsedJwt(header, payload))
        } catch (expected: Exception) {
            Err(
                DpopError.InvalidFormat(
                    reason = "Failed to parse JWT: ${expected.message}",
                ),
            )
        }
    }

    /**
     * Validates that the typ header is "dpop+jwt"
     */
    private fun validateTypHeader(header: DpopJwtHeader): IdkResult<Unit, DpopError> {
        if (header.typ != "dpop+jwt") {
            return Err(
                DpopError.InvalidFormat(
                    reason = "typ header must be 'dpop+jwt', got '${header.typ}'",
                ),
            )
        }
        return Ok(Unit)
    }

    /**
     * RFC 9449 §4.1: the embedded `jwk` carries the holder's public key only. Reject any proof
     * whose JWK includes private-key parameters: EC/OKP `d` (RFC 7518 §6.2.2 / RFC 8037), or
     * RSA `d` / `p` / `q` / `dp` / `dq` / `qi` (RFC 7518 §6.3.2). The signature would still
     * verify in those cases, but the resource server MUST treat it as malformed.
     */
    private fun validateJwkIsPublicKeyOnly(jwk: Jwk): IdkResult<Unit, DpopError> {
        val violations =
            buildList {
                if (jwk.d != null) add("d")
                if (jwk.p != null) add("p")
                if (jwk.q != null) add("q")
                if (jwk.dP != null) add("dp")
                if (jwk.dQ != null) add("dq")
                if (jwk.qInv != null) add("qi")
            }
        return if (violations.isEmpty()) {
            Ok(Unit)
        } else {
            Err(
                DpopError.InvalidFormat(
                    reason = "DPoP proof header `jwk` MUST contain only public-key parameters; private fields present: ${violations.joinToString(", ")}",
                ),
            )
        }
    }

    /**
     * Verifies the JWT signature using the embedded public key
     */
    private suspend fun verifySignature(dpopProof: String,): IdkResult<Unit, DpopError> {
        // For DPoP, we verify using the embedded JWK in the header
        // The JwtService will extract the key from the JWT header automatically
        val verifyArgs =
            VerifyJwsArgs(
                jws = JwsCompact(dpopProof) as Jws,
                // identifier is null - JwtService will use the embedded jwk from the header
            )

        val validationResult =
            jwtService.verifyJws(verifyArgs).getOrElse { error ->
                return Err(
                    DpopError.VerificationFailed(
                        reason = "JWT verification error: ${error.message.defaultMessage}",
                        exception = error.exception,
                    ),
                )
            }

        return if (validationResult.isValid) {
            Ok(Unit)
        } else {
            Err(
                DpopError.VerificationFailed(
                    reason = "JWT signature verification failed: ${validationResult.errorMessages.joinToString()}",
                ),
            )
        }
    }

    /**
     * Validates all required DPoP claims
     */
    private fun validateClaims(
        payload: DpopJwtPayload,
        options: VerifyDpopProofOptions,
        now: Long,
    ): IdkResult<Unit, DpopError> {
        // Validate htm (HTTP method)
        if (payload.htm.uppercase() != options.httpMethod.uppercase()) {
            return Err(
                DpopError.ClaimMismatch(
                    claimName = "htm",
                    expected = options.httpMethod.uppercase(),
                    actual = payload.htm.uppercase(),
                ),
            )
        }

        // Validate htu (HTTP URL - normalize both for comparison)
        val normalizedPayloadHtu = normalizeUrl(payload.htu)
        val normalizedExpectedHtu = normalizeUrl(options.httpUrl)
        if (normalizedPayloadHtu != normalizedExpectedHtu) {
            return Err(
                DpopError.ClaimMismatch(
                    claimName = "htu",
                    expected = normalizedExpectedHtu,
                    actual = normalizedPayloadHtu,
                ),
            )
        }

        // Validate iat (issued at timestamp). RFC 9449: the server SHOULD reject proofs that
        // are too old or in the future. Both windows are config-overridable so deployments
        // can tighten or relax the freshness band per their threat model.
        val maxAgeSeconds = appConfigService.getProperty(CONFIG_MAX_AGE_SECONDS, Long::class, DEFAULT_MAX_AGE_SECONDS) ?: DEFAULT_MAX_AGE_SECONDS
        val clockSkewSeconds = appConfigService.getProperty(CONFIG_CLOCK_SKEW_SECONDS, Long::class, DEFAULT_CLOCK_SKEW_SECONDS) ?: DEFAULT_CLOCK_SKEW_SECONDS

        if (payload.iat > now + clockSkewSeconds) {
            return Err(
                DpopError.ClaimMismatch(
                    claimName = "iat",
                    expected = "not in future (now=$now, skew=$clockSkewSeconds)",
                    actual = payload.iat.toString(),
                ),
            )
        }

        if (payload.iat < now - maxAgeSeconds) {
            return Err(
                DpopError.ClaimMismatch(
                    claimName = "iat",
                    expected = "not older than $maxAgeSeconds seconds (now=$now)",
                    actual = payload.iat.toString(),
                ),
            )
        }

        // Validate nonce if expected
        val expectedNonce = options.expectedNonce
        if (expectedNonce != null && payload.nonce != expectedNonce) {
            return Err(
                DpopError.ClaimMismatch(
                    claimName = "nonce",
                    expected = expectedNonce,
                    actual = payload.nonce ?: "null",
                ),
            )
        }

        // Validate ath (access token hash) if access token provided
        val accessToken = options.accessToken
        if (accessToken != null) {
            val ath = payload.ath
            if (ath == null) {
                return Err(
                    DpopError.MissingClaim(
                        claimName = "ath",
                    ),
                )
            }

            val expectedAth = calculateAccessTokenHash(accessToken)
            // Constant-time compare on the DPoP `ath` claim. `ath` = SHA-256(access_token);
            // a non-CT compare here lets an attacker who can submit DPoP proofs against the
            // resource server confirm or reject candidate access-token hashes one byte at a
            // time, narrowing the search space for offline brute-force.
            if (!ConstantTime.equalsCT(ath, expectedAth)) {
                return Err(
                    DpopError.ClaimMismatch(
                        claimName = "ath",
                        expected = expectedAth,
                        actual = ath,
                    ),
                )
            }
        }

        return Ok(Unit)
    }

    /**
     * Normalize an `htu` value for RFC 9449 §4.3 comparison. Applies the rules RFC 3986 §6.2
     * defines as "syntax-based" + "scheme-based" normalisation:
     *  - drop the query (§6.2.3) and fragment (§6.2.3) — DPoP §4.3-9 explicitly says these
     *    components MUST be ignored when comparing;
     *  - lowercase scheme and authority (§6.2.2.1) — both are case-insensitive;
     *  - elide the default port for the scheme (§6.2.3 / scheme-based normalisation): `:443`
     *    for `https`, `:80` for `http`. The conformance suite probes both `HTTPS://...` and
     *    `https://host:443/...` against an `htu` advertised as `https://host/...` and expects
     *    the comparison to succeed.
     */
    private fun normalizeUrl(url: String): String {
        // Strip query + fragment first (RFC 3986 §6.2.3, DPoP §4.3-9).
        val queryStart = url.indexOf('?')
        val fragmentStart = url.indexOf('#')
        val cutPosition =
            when {
                queryStart != -1 && fragmentStart != -1 -> minOf(queryStart, fragmentStart)
                queryStart != -1 -> queryStart
                fragmentStart != -1 -> fragmentStart
                else -> url.length
            }
        val pathStripped = url.substring(0, cutPosition)

        // scheme://authority/path → split scheme + authority for case-insensitive comparison
        // and default-port elision. Anything we can't parse falls through verbatim so a
        // genuinely malformed `htu` still surfaces as a mismatch downstream.
        val schemeIdx = pathStripped.indexOf("://")
        if (schemeIdx <= 0) return pathStripped
        val scheme = pathStripped.substring(0, schemeIdx).lowercase()
        val rest = pathStripped.substring(schemeIdx + 3)
        val pathStart = rest.indexOf('/').let { if (it < 0) rest.length else it }
        val authorityRaw = rest.substring(0, pathStart)
        val path = rest.substring(pathStart)

        // Authority is `[userinfo@]host[:port]`. Lowercase host+port (RFC 3986 §6.2.2.1) and
        // strip the default port for the scheme (§6.2.3 scheme-based normalisation).
        val authority = authorityRaw.lowercase()
        val (hostPart, portSuffix) =
            authority.lastIndexOf(':').let { idx ->
                if (idx < 0 || idx < authority.lastIndexOf(']')) {
                    authority to ""
                } else {
                    authority.substring(0, idx) to authority.substring(idx)
                }
            }
        val normalizedAuthority =
            when {
                scheme == "https" && portSuffix == ":443" -> hostPart
                scheme == "http" && portSuffix == ":80" -> hostPart
                else -> hostPart + portSuffix
            }
        return "$scheme://$normalizedAuthority$path"
    }

    /**
     * Calculates the SHA-256 hash of an access token (RFC 9449 Section 4.2)
     */
    private fun calculateAccessTokenHash(accessToken: String): String {
        val tokenBytes = accessToken.encodeToByteArray()
        val hashBytes = hash(tokenBytes, DigestAlg.SHA256)
        return hashBytes.encodeToBase64Url()
    }

    companion object {
        private const val JWT_PART_COUNT = 3

        /** RFC 9449 §11.1: maximum proof age (`now - iat`) in seconds. Override via config. */
        const val CONFIG_MAX_AGE_SECONDS: String = "oauth2.dpop.proof.max-age-seconds"

        /**
         * RFC 9449 §11.1: future-`iat` tolerance in seconds. Override via config. The FAPI2
         * conformance suite probes `iat = now + 10s` (`…-iat-10seconds-after-succeeds`); 60s
         * matches OpenID Federation / FAPI defaults and mirrors the past-window above.
         */
        const val CONFIG_CLOCK_SKEW_SECONDS: String = "oauth2.dpop.proof.clock-skew-seconds"

        const val DEFAULT_MAX_AGE_SECONDS: Long = 60L
        const val DEFAULT_CLOCK_SKEW_SECONDS: Long = 60L
    }
}
