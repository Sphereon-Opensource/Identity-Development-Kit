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
import com.sphereon.crypto.jose.jws.JwsIdentifierMode
import com.sphereon.crypto.jose.jws.command.CreateJwsArgs
import com.sphereon.crypto.jose.jws.command.CreateJwsCompactCommand
import com.sphereon.crypto.jose.jws.command.CreateJwsOpts
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.di.session.SessionScope
import com.sphereon.sdjwt.Disclosure
import com.sphereon.sdjwt.PresentSdJwtArgs
import com.sphereon.sdjwt.PresentSdJwtResult
import com.sphereon.sdjwt.SdJwt
import com.sphereon.sdjwt.SdJwtCodec
import com.sphereon.sdjwt.SdJwtCompact
import com.sphereon.sdjwt.SdMap
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

        log.debug("Creating SD-JWT presentation: hasHolderKey=${appliedArgs.holderKey != null}, hasAudience=${appliedArgs.audience != null}")

        try {
            // Step 1: Parse the full SD-JWT
            val parseResult = SdJwtCodec.parse(appliedArgs.sdJwt)
            if (parseResult.isErr) {
                log.error("Failed to parse SD-JWT for presentation: ${parseResult.error.message}")
                return IdkResult.err(parseResult.error)
            }
            val sdJwt = parseResult.value
            log.debug("Parsed SD-JWT with ${sdJwt.disclosures.size} disclosures")

            // Step 2: Select disclosures based on selection criteria
            val selectedDisclosures = selectDisclosures(sdJwt, appliedArgs.disclosureSelection)
            log.debug("Selected ${selectedDisclosures.size} of ${sdJwt.disclosures.size} disclosures for presentation")

            // Step 3: Build the presentation string (without KB-JWT first)
            val presentationWithoutKb =
                buildPresentationString(
                    jwt = sdJwt.jwt.value, // Assuming JwsCompact has a .value property
                    disclosures = selectedDisclosures,
                )

            // Step 4: Create Key Binding JWT if holder key is provided
            val kbJwt =
                if (appliedArgs.holderKey != null && appliedArgs.audience != null && appliedArgs.nonce != null) {
                    log.debug("Creating Key Binding JWT for holder authentication")
                    createKeyBindingJwt(
                        presentationWithoutKb = presentationWithoutKb,
                        audience = appliedArgs.audience!!,
                        nonce = appliedArgs.nonce!!,
                        holderKey = appliedArgs.holderKey!!,
                        digestAlg = getDigestAlgorithm(sdJwt.payload.undisclosedPayload),
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

            // Extract disclosed claim names
            val disclosedClaimNames = selectedDisclosures.map { it.key }.filter { it.isNotEmpty() }

            log.info(
                "Created SD-JWT presentation with ${selectedDisclosures.size} disclosed claims${if (kbJwt != null) {
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
    private fun selectDisclosures(
        sdJwt: SdJwtCompact,
        selection: SdMap?,
    ): List<Disclosure> {
        if (selection == null) {
            // Include all disclosures
            return sdJwt.disclosures
        }

        // Filter disclosures based on SdMap
        return sdJwt.disclosures.filter { disclosure ->
            val field = selection[disclosure.key]
            field?.sd == true
        }
    }

    /**
     * Build the presentation string (JWT~disclosure1~disclosure2~...~)
     */
    private fun buildPresentationString(
        jwt: String,
        disclosures: List<Disclosure>,
    ): String =
        buildString {
            append(jwt)
            for (disclosure in disclosures) {
                append(SdJwt.Companion.SEPARATOR)
                append(disclosure.encoded)
            }
            append(SdJwt.Companion.SEPARATOR) // Trailing separator
        }

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
        // Use JWK mode to embed the public key in the header for verification
        val kbOpts =
            CreateJwsOpts(
                noIssPayloadUpdate = true, // Don't add iss/client_id to KB-JWT payload
                noIdentifierInHeader = false, // DO embed JWK in header
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
}
