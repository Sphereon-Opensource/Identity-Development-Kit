/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.crypto.jose.jws.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.Encoding
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.sign.SignatureService
import com.sphereon.crypto.jose.jws.*
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierX5cOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierDidOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwkOpts
import com.sphereon.crypto.resolution.managed.ManagedOptsKid
import com.sphereon.crypto.resolution.managed.ManagedIdentifierKeyResult
import com.sphereon.crypto.resolution.IdentifierService
import com.sphereon.crypto.resolution.IdentifierOptsOrResult
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionScope
import kotlinx.datetime.Clock
import kotlinx.serialization.json.jsonPrimitive
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Command implementation for verifying JWS signatures
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("VerifyJwsCommandImpl", exact = true)
class VerifyJwsCommandImpl(
    execution: SessionExecution,
    private val identifierService: IdentifierService,
    private val signatureService: SignatureService,
) : TypedServiceCommandAdapter<VerifyJwsArgs, JwsValidationResult>(
    commandId = VerifyJwsCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<VerifyJwsArgs>(),
    outputTypeToken = typeToken<JwsValidationResult>(),
), VerifyJwsCommand {

    override val commandId: String get() = VerifyJwsCommand.COMMAND_ID

    override suspend fun doExecute(
        args: VerifyJwsArgs,
        applyDuring: (VerifyJwsArgs) -> VerifyJwsArgs
    ): IdkResult<JwsValidationResult, IdkError> {
        val appliedArgs = applyDuring(args)

        // Validate required fields
        val jws = appliedArgs.jws ?: return IdkResult.err(IdkError.fromString("JWS is required"))

        // Convert to general format
        val general = try {
            JwsUtils.toGeneral(jws)
        } catch (e: Exception) {
            return IdkResult.err(IdkError.fromString("Invalid JWS format: ${e.message}", exception = e))
        }

        // Verify each signature
        val errorMessages = mutableListOf<String>()
        val signaturesWithIdentifiers = mutableListOf<JwsJsonSignatureWithIdentifier>()

        for ((index, signature) in general.signatures.withIndex()) {
            try {
                // Decode protected header
                val protectedHeader = JwsUtils.decodeBase64UrlToJson(signature.protected)

                // Extract algorithm from header (used for multiple checks below)
                val algValue = protectedHeader["alg"]?.jsonPrimitive?.content

                // SECURITY: Reject "none" algorithm (RFC 9901 §8.1, RFC 8725 §2.1)
                if (algValue?.lowercase() == "none") {
                    errorMessages.add("Signature $index: Algorithm 'none' is not allowed for security reasons")
                    continue
                }

                // Resolve identifier from header or use provided identifier
                val identifierResult = if (appliedArgs.identifier != null) {
                    // Use provided identifier
                    identifierService.resolve(appliedArgs.identifier as IdentifierOptsOrResult)
                } else {
                    // Resolve from header
                    resolveIdentifierFromHeader(protectedHeader)
                }

                if (identifierResult.isErr) {
                    errorMessages.add("Signature $index: Failed to resolve identifier - ${identifierResult.error.message}")
                    continue
                }

                val identifierOptsOrResult = identifierResult.value

                // Extract keyInfo from either managed or external result
                var keyInfo = when (identifierOptsOrResult) {
                    is ManagedIdentifierKeyResult -> identifierOptsOrResult.keyInfo
                    is ExternalIdentifierResult -> identifierOptsOrResult.keyInfo
                    else -> {
                        errorMessages.add("Signature $index: Unsupported identifier result type")
                        continue
                    }
                }

                // Update keyInfo with algorithm from JWT header if needed
                // This is a fallback in case the identifier resolution didn't set it correctly
                if (algValue != null && keyInfo.signatureAlgorithm == null) {
                    // Map JWT alg value to SignatureAlgorithm
                    try {
                        val jwaAlg = JwaAlgorithm.fromValue(algValue)
                        val signatureAlg = SignatureAlgorithm.fromJose(jwaAlg)

                        // Update the keyInfo with the algorithm from the JWT header
                        // NOTE: The identifier resolution service should have already set the correct algorithm,
                        // so this code path should not normally be reached. Keeping as a fallback.
                        keyInfo = when (keyInfo) {
                            is ResolvedKeyInfo -> {
                                keyInfo.copy(signatureAlgorithm = signatureAlg)
                            }
                            else -> keyInfo
                        }
                    } catch (e: Exception) {
                        // If algorithm is not recognized, continue without setting it
                    }
                }

                // Create signing input
                val signingInput = JwsUtils.createSigningInput(signature.protected, general.payload)

                // Decode signature
                val signatureBytes = signature.signature.decodeFrom(Encoding.BASE64URL)

                // Verify signature
                log.debug("Verifying signature with keyInfo.signatureAlgorithm = ${keyInfo.signatureAlgorithm}")
                val isValid = try {
                    signatureService.isValidRawSignature(
                        keyInfo = keyInfo,
                        input = signingInput,
                        signature = signatureBytes
                    )
                } catch (e: Exception) {
                    errorMessages.add("Signature $index: Verification failed - ${e.message}")
                    false
                }

                if (!isValid) {
                    errorMessages.add("Signature $index: Invalid signature")
                }

                signaturesWithIdentifiers.add(
                    JwsJsonSignatureWithIdentifier(
                        protected = signature.protected,
                        header = signature.header,
                        signature = signature.signature,
                        identifier = identifierOptsOrResult
                    )
                )
            } catch (e: Exception) {
                errorMessages.add("Signature $index: Unexpected error - ${e.message}")
            }
        }

        val result = JwsValidationResult(
            jws = JwsJsonGeneralWithIdentifiers(
                payload = general.payload,
                signatures = signaturesWithIdentifiers
            ),
            isValid = errorMessages.isEmpty(),
            errorMessages = errorMessages,
            verificationTime = Clock.System.now().toEpochMilliseconds()
        )

        return result.asOkResult()
    }

    private suspend fun resolveIdentifierFromHeader(
        protectedHeader: kotlinx.serialization.json.JsonObject
    ): IdkResult<IdentifierOptsOrResult, IdkError> {
        // Priority: x5c > jwk in header > kid (DID or managed)

        // Check for x5c (X.509 certificate chain)
        val x5c = protectedHeader["x5c"]?.let { element ->
            if (element is kotlinx.serialization.json.JsonArray) {
                element.map { it.jsonPrimitive.content }
            } else null
        }

        if (x5c != null) {
            val opts = ExternalIdentifierX5cOpts(
                identifier = x5c,
                verify = true
            )
            val result = identifierService.resolve(opts)
            return if (result.isErr) {
                IdkResult.err(IdkError.fromDTO(result.error))
            } else {
                IdkResult.ok(result.value)
            }
        }

        // Check for jwk in header
        val jwkElement = protectedHeader["jwk"]
        if (jwkElement is kotlinx.serialization.json.JsonObject) {
            // Convert JsonObject to Jwk
            val jwk = Jwk.fromJsonObject(jwkElement)

            // Create ResolvedKeyInfo directly from the embedded JWK
            val keyInfo = ResolvedKeyInfo.fromKey(jwk)

            // Create an ExternalIdentifierResult.Jwk with the embedded key
            val opts = ExternalIdentifierJwkOpts(identifier = jwk)

            val result = ExternalIdentifierResult.Jwk(
                identifierOpts = opts,
                jwks = arrayOf(keyInfo),
                keyInfo = keyInfo,
                x5c = null
            )

            return IdkResult.ok(result)
        }

        // Check for kid (could be DID or managed key)
        val kid = protectedHeader["kid"]?.jsonPrimitive?.content

        if (kid != null) {
            // Extract algorithm from JWT header to provide as a hint for key resolution
            val algValue = protectedHeader["alg"]?.jsonPrimitive?.content
            val signatureAlg = algValue?.let {
                try {
                    val jwaAlg = JwaAlgorithm.fromValue(it)
                    SignatureAlgorithm.fromJose(jwaAlg)
                } catch (e: Exception) {
                    null
                }
            }

            val opts: IdentifierOptsOrResult = if (kid.startsWith("did:")) {
                ExternalIdentifierDidOpts(identifier = kid)
            } else {
                // Managed key kid
                // Pass algorithm hint via the lookup KeyInfo if available
                val lookup = if (signatureAlg != null) {
                    KeyInfo<KeyType>(
                        kid = kid,
                        signatureAlgorithm = signatureAlg
                    )
                } else {
                    KeyInfo<KeyType>(
                        kid = kid
                    )
                }
                ManagedOptsKid(identifier = kid, lookup = lookup)
            }
            val result = identifierService.resolve(opts)
            return if (result.isErr) {
                IdkResult.err(IdkError.fromDTO(result.error))
            } else {
                IdkResult.ok(result.value)
            }
        }

        return IdkResult.err(
            IdkError.fromString("Could not resolve identifier from JWS header. No x5c, jwk, or kid found.")
        )
    }
}
