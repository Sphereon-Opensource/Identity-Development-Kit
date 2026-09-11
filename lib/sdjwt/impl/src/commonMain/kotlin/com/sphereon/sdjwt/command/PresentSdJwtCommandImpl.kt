/*
 * Copyright (c) 2026 Sphereon B.V.
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
package com.sphereon.sdjwt.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.interop.toKeyInfoJwk
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsCompactCommand
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierKeyResult
import com.sphereon.crypto.resolution.managed.KeyInfoIdentifierResolutionService
import com.sphereon.di.session.SessionScope
import com.sphereon.sdjwt.Disclosure
import com.sphereon.sdjwt.PresentSdJwtArgs
import com.sphereon.sdjwt.PresentSdJwtResult
import com.sphereon.sdjwt.SdJwt
import com.sphereon.sdjwt.SdJwtCodec
import com.sphereon.sdjwt.SdJwtCompact
import com.sphereon.sdjwt.SdMap
import com.sphereon.sdjwt.SdJwtPresentation
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import kotlin.time.Clock

/*
 * Command interface for presenting SD-JWTs.
 */

/**
 * Implementation of PresentSdJwtCommand.
 *
 * This command creates an SD-JWT presentation according to RFC 9901 §7:
 * 1. Parse the full SD-JWT from issuer
 * 2. Select which disclosures to include based on disclosureSelection
 * 3. Optionally create a Key Binding JWT (KB-JWT) for holder authentication
 * 4. Format as: JWT~selectedDisclosure1~selectedDisclosure2~...~kbJwt
 *
 * @property execution Session execution context
 * @property createJwsCompactCommand Command for creating KB-JWT (if needed)
 * @property plugin Optional plugin for command execution
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("PresentSdJwtCommandImpl", exact = true)
class PresentSdJwtCommandImpl(
    execution: SessionExecution,
    private val createJwsCompactCommand: CreateJwsCompactCommand,
    private val keyInfoIdentifierResolutionService: KeyInfoIdentifierResolutionService,
) : TypedServiceCommandAdapter<PresentSdJwtArgs, PresentSdJwtResult, IdkError>(
        commandId = PresentSdJwtCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<PresentSdJwtArgs>(),
        outputTypeToken = typeToken<PresentSdJwtResult>(),
    ),
    PresentSdJwtCommand {
    override val commandId: String get() = PresentSdJwtCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is PresentSdJwtArgs

    override suspend fun doExecute(
        args: PresentSdJwtArgs,
        applyDuring: (PresentSdJwtArgs) -> PresentSdJwtArgs,
    ): IdkResult<PresentSdJwtResult, IdkError> {
        val appliedArgs = applyDuring(args)
        val holderKey = appliedArgs.holderKey
        val audience = appliedArgs.audience
        val nonce = appliedArgs.nonce

        log.debug("Creating SD-JWT presentation: hasHolderKey=${holderKey != null}, hasAudience=${audience != null}")

        try {
            // Step 1: Parse the full SD-JWT
            val parseResult = SdJwtCodec.parse(appliedArgs.sdJwt)
            if (parseResult.isErr) {
                log.error("Failed to parse SD-JWT for presentation: ${parseResult.error.message}")
                return IdkResult.err(parseResult.error)
            }
            val sdJwt = parseResult.value
            log.debug("Parsed SD-JWT with ${sdJwt.disclosures.size} disclosures")
            val resolvedHolderKey =
                if (holderKey != null && audience != null && nonce != null) {
                    // Resolve once and retain the result through validation and signing. This prevents
                    // a mutable alias/kid selector from changing the key between those operations.
                    validateHolderKeyAgainstCnf(sdJwt, holderKey)
                } else {
                    null
                }

            // Step 2: Select disclosures based on selection criteria
            val selection =
                SdJwtPresentation.select(
                    compact = appliedArgs.sdJwt,
                    disclosurePaths = appliedArgs.disclosurePaths,
                    disclosureSelection = appliedArgs.disclosureSelection,
                )
            val presentationWithoutKb = selection.presentationWithoutKeyBinding
            log.debug("Selected ${selection.disclosedClaims.size} named disclosures for presentation")

            // Step 4: Create Key Binding JWT if holder key is provided
            val kbJwt =
                if (resolvedHolderKey != null && audience != null && nonce != null) {
                    log.debug("Creating Key Binding JWT for holder authentication")
                    createKeyBindingJwt(
                        presentationWithoutKb = presentationWithoutKb,
                        audience = audience,
                        nonce = nonce,
                        holderKey = resolvedHolderKey,
                        digestAlg = selection.digestAlgorithm,
                        opts = appliedArgs.kbJwtOpts,
                    )
                } else {
                    log.debug("No Key Binding JWT created (missing holderKey, audience, or nonce)")
                    null
                }

            // Step 5: Build final presentation
            val finalPresentation =
                if (kbJwt != null) {
                    "$presentationWithoutKb$kbJwt"
                } else {
                    presentationWithoutKb
                }

            // Extract disclosed claim names (array-element disclosures carry no claim name)
            val disclosedClaimNames = selection.disclosedClaims

            log.info(
                "Created SD-JWT presentation with ${disclosedClaimNames.size} disclosed claims${if (kbJwt != null) {
                    " and Key Binding"
                } else {
                    ""
                }}"
            )

            return PresentSdJwtResult(
                presentation = finalPresentation,
                disclosedClaims = disclosedClaimNames,
            ).asOkResult()
        } catch (expected: Exception) {
            log.error("Failed to create SD-JWT presentation: ${expected.message}", expected)
            return IdkResult.err(
                IdkError.fromString(
                    message = "Failed to create SD-JWT presentation: ${expected.message}",
                    exception = expected,
                ),
            )
        }
    }

    /**
     * Select which disclosures to include in the presentation.
     *
     * If disclosureSelection is null, include all disclosures.
     * Otherwise, filter based on the SdMap.
     */
    /**
     * Create a Key Binding JWT (KB-JWT) for holder authentication.
     *
     * RFC 9901 §4.3 requires:
     * - aud: audience (verifier identifier)
     * - nonce: fresh nonce from verifier
     * - iat: issued at time
     * - sd_hash: hash of the presentation (without KB-JWT)
     */
    private suspend fun createKeyBindingJwt(
        presentationWithoutKb: String,
        audience: String,
        nonce: String,
        holderKey: ManagedIdentifierOptsOrResult,
        digestAlg: DigestAlg,
        opts: CreateJwsOpts,
    ): String {
        // Calculate sd_hash (hash of the presentation without KB-JWT)
        val sdHash =
            hash(
                dataInput = presentationWithoutKb.encodeToByteArray(),
                digestAlgorithm = digestAlg,
            ).encodeToBase64Url()
        // Build KB-JWT payload
        val kbPayload =
            buildJsonObject {
                put("aud", audience)
                put("nonce", nonce)
                put("iat", Clock.System.now().epochSeconds)
                put("sd_hash", sdHash)
            }

        // Sign the KB-JWT
        // Per RFC 9901, KB-JWT should ONLY contain: aud, nonce, iat, sd_hash in payload
        // Set noIssPayloadUpdate=true to prevent adding iss, client_id, client_id_scheme
        // RFC 9901 Section 4.3 requires typ and alg in the KB-JWT header. The verifier obtains the
        // holder verification key from the issuer-signed SD-JWT cnf claim, so do not duplicate a
        // jwk, kid, x5c, or other key identifier in the KB-JWT header.
        val kbOpts =
            CreateJwsOpts(
                noIssPayloadUpdate = true, // Don't add iss/client_id to KB-JWT payload
                noIdentifierInHeader = true,
                protectedHeader =
                    buildJsonObject {
                        opts.protectedHeader?.forEach { (name, value) -> put(name, value) }
                        put("typ", "kb+jwt")
                    },
            )
        val kbJwsArgs =
            CreateJwsArgs(
                issuer = holderKey,
                payload = kbPayload,
                mode = JwsIdentifierMode.JWK,
                opts = kbOpts,
            )

        val kbResult = createJwsCompactCommand.execute(kbJwsArgs)
        check(kbResult.isOk) { "Failed to create Key Binding JWT: ${kbResult.error.message}" }

        return kbResult.value.jwt
    }

    /**
     * Ensure the key that will sign the KB-JWT is the public key bound by the issuer in cnf.jwk.
     *
     * Managed provider/alias/kid values select custody, but are not cryptographic key identity.
     * Compare the actual public JWK and issuer-declared constraints before any signing operation
     * so a caller cannot supply a different key under matching managed metadata.
     */
    private suspend fun validateHolderKeyAgainstCnf(
        sdJwt: SdJwtCompact,
        holderKey: ManagedIdentifierOptsOrResult,
    ): ManagedIdentifierKeyResult {
        val cnf =
            sdJwt.payload.undisclosedPayload["cnf"] as? JsonObject
                ?: throw IllegalArgumentException("Cannot create KB-JWT: issuer-signed cnf.jwk is required")
        val cnfJwkElement =
            cnf["jwk"]
                ?: throw IllegalArgumentException("Cannot create KB-JWT: issuer-signed cnf.jwk is required")
        require(cnfJwkElement is JsonObject) { "Issuer-signed cnf.jwk must be a JSON object" }

        val privateMembers = cnfJwkElement.keys.intersect(PRIVATE_JWK_MEMBERS)
        require(privateMembers.isEmpty()) {
            "Issuer-signed cnf.jwk must contain public key material only; private/secret members are forbidden: ${privateMembers.sorted().joinToString()}"
        }

        val expected = Jwk.fromJsonObject(cnfJwkElement)
        val holderResult =
            keyInfoIdentifierResolutionService.resolve(holderKey).getOrElse {
                throw IllegalArgumentException("Cannot resolve supplied holder key for cnf.jwk validation: ${it.message.defaultMessage}")
            }
        val actual =
            toKeyInfoJwk(holderResult.keyInfo).key?.toPublicKey()
                ?: throw IllegalArgumentException("Supplied holder key does not expose a JOSE public JWK for cnf.jwk validation")

        val mismatches = mutableListOf<String>()
        if (actual.toMinimalJwk().toJsonObject() != expected.toMinimalJwk().toJsonObject()) {
            mismatches += "public key material"
        }

        expected.alg?.value?.let { expectedAlgorithm ->
            val actualAlgorithm =
                actual.alg?.value
                    ?: holderResult.keyInfo.signatureAlgorithm?.jose?.value
                    ?: actual.getSignatureAlgorithm()?.jose?.value
            if (actualAlgorithm != expectedAlgorithm) mismatches += "alg"
        }
        expected.use?.let { expectedUse ->
            if (actual.use != expectedUse) mismatches += "use"
        }
        expected.key_ops?.let { expectedOperations ->
            if (actual.key_ops?.toSet() != expectedOperations.toSet()) mismatches += "key_ops"
        }

        if (actual.use != null && actual.use != "sig") mismatches += "holder use"
        if (actual.key_ops?.contains(com.sphereon.crypto.core.jose.JoseKeyOperations.SIGN) == false) {
            mismatches += "holder key_ops"
        }

        require(mismatches.isEmpty()) {
            "Supplied holder key does not match issuer-signed cnf.jwk (${mismatches.distinct().joinToString()})"
        }
        return holderResult
    }

    /**
     * Extract digest algorithm from JWT payload.
     * Falls back to SHA-256 if not specified (RFC 9901 default).
     */
    private fun getDigestAlgorithm(payload: JsonObject): DigestAlg {
        val algClaim = payload[SdJwt.Companion.SD_ALG_CLAIM]?.toString()?.trim('"')
        return if (algClaim != null) {
            DigestAlg.entries.find { it.httpHeaderId?.equals(algClaim, ignoreCase = true) == true }
                ?: SdJwt.Companion.DEFAULT_HASH_ALG
        } else {
            SdJwt.Companion.DEFAULT_HASH_ALG
        }
    }

    private companion object {
        val PRIVATE_JWK_MEMBERS = setOf("d", "p", "q", "dp", "dq", "qi", "k", "oth")
    }
}
