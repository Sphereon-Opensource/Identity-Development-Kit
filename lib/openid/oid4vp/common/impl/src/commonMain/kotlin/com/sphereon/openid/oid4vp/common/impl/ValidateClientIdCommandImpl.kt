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

package com.sphereon.openid.oid4vp.common.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.resolution.IdentifierService
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierX5cOpts
import com.sphereon.di.session.SessionScope
import com.sphereon.did.resolver.DidResolverRegistry
import com.sphereon.did.resolver.DidResolutionOptions
import com.sphereon.did.models.VerificationPurpose
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.ClientIdValidationError
import com.sphereon.openid.oid4vp.common.ClientIdValidationErrorType
import com.sphereon.openid.oid4vp.common.JarConstants
import com.sphereon.openid.oid4vp.common.ValidateClientIdArgs
import com.sphereon.openid.oid4vp.common.ValidateClientIdCommand
import com.sphereon.openid.oid4vp.common.ValidateClientIdCommandService
import com.sphereon.openid.oid4vp.common.ValidateClientIdResult
import com.sphereon.openid.oid4vp.common.VerifyVerifierAttestationArgs
import com.sphereon.openid.oid4vp.common.VerifyVerifierAttestationCommand
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Implementation of ValidateClientIdCommand.
 *
 * Validates client_id according to OpenID4VP 1.0 Final Section 5.2.
 *
 * Scheme-specific validation:
 * - PRE_REGISTERED: Always valid (trust pre-configured)
 * - REDIRECT_URI: JAR MUST NOT be used, client_id = redirect_uri/response_uri
 * - X509_SAN_DNS: JAR required with x5c, SAN DNS name matches client_id
 * - X509_SAN_URI: JAR required with x5c, SAN URI matches client_id
 * - X509_HASH: JAR required with x5c, certificate SHA-256 hash matches client_id
 * - VERIFIER_ATTESTATION: Not yet implemented
 * - OPENID_FEDERATION: Not supported (out of scope)
 * - DECENTRALIZED_IDENTIFIER: Not supported (out of scope)
 * - ORIGIN: Not allowed (reserved for Digital Credentials API)
 */
@Inject
@SingleIn(SessionScope::class)
class ValidateClientIdCommandImpl(
    execution: SessionExecution,
    private val identifierService: IdentifierService,
    private val didResolverRegistry: DidResolverRegistry? = null,
    private val verifyVerifierAttestationCommand: VerifyVerifierAttestationCommand? = null,
) : TypedServiceCommandAdapter<ValidateClientIdArgs, ValidateClientIdResult>(
    commandId = ValidateClientIdCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<ValidateClientIdArgs>(),
    outputTypeToken = typeToken<ValidateClientIdResult>(),
), ValidateClientIdCommand, ValidateClientIdCommandService {

    override val commandId: String get() = ValidateClientIdCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is ValidateClientIdArgs

    override suspend fun validateClientId(args: ValidateClientIdArgs): IdkResult<ValidateClientIdResult, IdkError> {
        return execute(args)
    }

    override suspend fun doExecute(
        args: ValidateClientIdArgs,
        applyDuring: (ValidateClientIdArgs) -> ValidateClientIdArgs
    ): IdkResult<ValidateClientIdResult, IdkError> {
        val processedArgs = applyDuring(args)
        val parsed = processedArgs.parsedClientId
        val scheme = parsed.clientIdScheme

        log.debug("Validating client_id '${parsed.clientId}' with scheme $scheme")

        // First, validate JAR requirements (signer method, typ header) if JAR was used
        val jarErrors = validateJarRequirements(processedArgs, scheme)
        if (jarErrors.isNotEmpty()) {
            // If JAR validation fails, return early with errors
            return Ok(ValidateClientIdResult(
                valid = false,
                scheme = scheme,
                clientId = parsed.clientId,
                errors = jarErrors
            ))
        }

        return when (scheme) {
            ClientIdScheme.PRE_REGISTERED -> validatePreRegistered(processedArgs)
            ClientIdScheme.REDIRECT_URI -> validateRedirectUri(processedArgs)
            ClientIdScheme.X509_SAN_DNS -> validateX509SanDns(processedArgs)
            ClientIdScheme.X509_SAN_URI -> validateX509SanUri(processedArgs)
            ClientIdScheme.X509_HASH -> validateX509Hash(processedArgs)
            ClientIdScheme.VERIFIER_ATTESTATION -> validateVerifierAttestation(processedArgs)
            ClientIdScheme.OPENID_FEDERATION -> notSupported(processedArgs, "OpenID Federation is not yet supported")
            ClientIdScheme.DECENTRALIZED_IDENTIFIER -> validateDecentralizedIdentifier(processedArgs)
            ClientIdScheme.ORIGIN -> notAllowed(processedArgs)
        }
    }

    /**
     * Validate JAR requirements for the given scheme.
     *
     * Per OpenID4VP 1.0 Section 5.2 and 5.6:
     * - Validates that JAR is used/not used according to scheme requirements
     * - Validates that the JAR typ header is "oauth-authz-req+jwt"
     * - Validates that the JAR signer method is allowed for the scheme
     *
     * @return List of validation errors, empty if validation passed
     */
    private fun validateJarRequirements(
        args: ValidateClientIdArgs,
        scheme: ClientIdScheme
    ): List<ClientIdValidationError> {
        val errors = mutableListOf<ClientIdValidationError>()

        // Check if JAR is required but not used
        if (scheme.jarRequired && !args.jarUsed) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.JAR_REQUIRED,
                message = "JAR (signed request) is required for $scheme scheme",
                details = "OpenID4VP 1.0 Section 5.2 requires a signed authorization request for this scheme"
            ))
            return errors // Return early, no point checking signer method
        }

        // Check if JAR is used but not allowed
        if (!scheme.jarAllowed && args.jarUsed) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.JAR_NOT_ALLOWED,
                message = "JAR (signed request) MUST NOT be used with $scheme scheme",
                details = "OpenID4VP 1.0 Section 5.2 prohibits signed requests for this scheme"
            ))
            return errors
        }

        // If JAR was used, validate typ header and signer method
        if (args.jarUsed) {
            // Validate typ header (must be "oauth-authz-req+jwt")
            val typHeader = args.jarTypHeader
            if (typHeader != null && typHeader != JarConstants.JAR_JWT_TYP) {
                errors.add(ClientIdValidationError(
                    type = ClientIdValidationErrorType.JAR_TYP_HEADER_INVALID,
                    message = "JAR typ header is invalid",
                    details = "Expected '${JarConstants.JAR_JWT_TYP}', got '$typHeader'"
                ))
            }

            // Validate signer method
            val signerMethod = args.jarSignerMethod
            if (signerMethod != null) {
                val allowedMethods = scheme.allowedSignerMethods
                if (allowedMethods.isNotEmpty() && signerMethod !in allowedMethods) {
                    errors.add(ClientIdValidationError(
                        type = ClientIdValidationErrorType.JAR_SIGNER_METHOD_NOT_ALLOWED,
                        message = "JAR signer method '$signerMethod' is not allowed for $scheme scheme",
                        details = "Allowed methods: ${allowedMethods.joinToString()}"
                    ))
                }
            }
        }

        return errors
    }

    /**
     * PRE_REGISTERED: Trust is pre-configured, always valid.
     */
    private fun validatePreRegistered(args: ValidateClientIdArgs): IdkResult<ValidateClientIdResult, IdkError> {
        log.debug("Pre-registered client_id - trusting as configured")
        return Ok(ValidateClientIdResult(
            valid = true,
            scheme = ClientIdScheme.PRE_REGISTERED,
            clientId = args.parsedClientId.clientId
        ))
    }

    /**
     * REDIRECT_URI: JAR MUST NOT be used, client_id must match redirect_uri or response_uri.
     *
     * OpenID4VP 1.0 Section 5.2: "When using client_id_scheme redirect_uri, the request
     * MUST NOT be signed using JAR."
     */
    private fun validateRedirectUri(args: ValidateClientIdArgs): IdkResult<ValidateClientIdResult, IdkError> {
        val errors = mutableListOf<ClientIdValidationError>()

        // JAR MUST NOT be used
        if (args.jarUsed) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.JAR_NOT_ALLOWED,
                message = "JAR (signed request) MUST NOT be used with redirect_uri scheme",
                details = "OpenID4VP 1.0 Section 5.2 requires unsigned requests for redirect_uri scheme"
            ))
        }

        // client_id must match redirect_uri or response_uri
        val clientId = args.parsedClientId.clientId
        val matchesRedirectUri = args.redirectUri == clientId
        val matchesResponseUri = args.responseUri == clientId

        if (!matchesRedirectUri && !matchesResponseUri) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.REDIRECT_URI_MISMATCH,
                message = "client_id does not match redirect_uri or response_uri",
                details = "client_id='$clientId', redirect_uri='${args.redirectUri}', response_uri='${args.responseUri}'"
            ))
        }

        val valid = errors.isEmpty()
        log.debug("redirect_uri validation ${if (valid) "passed" else "failed"}: ${errors.map { it.message }}")

        return Ok(ValidateClientIdResult(
            valid = valid,
            scheme = ClientIdScheme.REDIRECT_URI,
            clientId = clientId,
            errors = errors
        ))
    }

    /**
     * X509_SAN_DNS: JAR required with x5c, SAN DNS name must match client_id.
     */
    private suspend fun validateX509SanDns(args: ValidateClientIdArgs): IdkResult<ValidateClientIdResult, IdkError> {
        val errors = mutableListOf<ClientIdValidationError>()
        val warnings = mutableListOf<String>()

        // JAR with x5c is required
        val certificateCheck = validateX509Prerequisites(args, errors)
        if (certificateCheck != null) {
            return certificateCheck
        }

        val certificates = args.jarSignerCertificates!!
        val leafCert = certificates.first()
        val clientId = args.parsedClientId.clientId

        // Verify and extract SAN
        val verifiedCert = verifyAndResolveX509(certificates, errors)

        // Check if SAN contains the DNS name
        val san = leafCert.subjectAlternativeNames
        if (san == null) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.SAN_DNS_MISMATCH,
                message = "Certificate does not have Subject Alternative Name extension",
                details = "x509_san_dns requires the certificate to have a SAN extension with DNS names"
            ))
        } else if (!san.containsDnsName(clientId)) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.SAN_DNS_MISMATCH,
                message = "SAN DNS name does not match client_id",
                details = "client_id='$clientId', SAN DNS names=${san.dnsNames}"
            ))
        }

        val valid = errors.isEmpty()
        log.debug("x509_san_dns validation ${if (valid) "passed" else "failed"}: ${errors.map { it.message }}")

        return Ok(ValidateClientIdResult(
            valid = valid,
            scheme = ClientIdScheme.X509_SAN_DNS,
            clientId = clientId,
            verifiedCertificate = if (valid) verifiedCert else null,
            errors = errors,
            warnings = warnings
        ))
    }

    /**
     * X509_SAN_URI: JAR required with x5c, SAN URI must match client_id.
     */
    private suspend fun validateX509SanUri(args: ValidateClientIdArgs): IdkResult<ValidateClientIdResult, IdkError> {
        val errors = mutableListOf<ClientIdValidationError>()
        val warnings = mutableListOf<String>()

        // JAR with x5c is required
        val certificateCheck = validateX509Prerequisites(args, errors)
        if (certificateCheck != null) {
            return certificateCheck
        }

        val certificates = args.jarSignerCertificates!!
        val leafCert = certificates.first()
        val clientId = args.parsedClientId.clientId

        // Verify and extract SAN
        val verifiedCert = verifyAndResolveX509(certificates, errors)

        // Check if SAN contains the URI
        val san = leafCert.subjectAlternativeNames
        if (san == null) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.SAN_URI_MISMATCH,
                message = "Certificate does not have Subject Alternative Name extension",
                details = "x509_san_uri requires the certificate to have a SAN extension with URIs"
            ))
        } else if (!san.containsUri(clientId)) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.SAN_URI_MISMATCH,
                message = "SAN URI does not match client_id",
                details = "client_id='$clientId', SAN URIs=${san.uris}"
            ))
        }

        val valid = errors.isEmpty()
        log.debug("x509_san_uri validation ${if (valid) "passed" else "failed"}: ${errors.map { it.message }}")

        return Ok(ValidateClientIdResult(
            valid = valid,
            scheme = ClientIdScheme.X509_SAN_URI,
            clientId = clientId,
            verifiedCertificate = if (valid) verifiedCert else null,
            errors = errors,
            warnings = warnings
        ))
    }

    /**
     * X509_HASH: JAR required with x5c, certificate SHA-256 hash (base64url) must match client_id.
     */
    private suspend fun validateX509Hash(args: ValidateClientIdArgs): IdkResult<ValidateClientIdResult, IdkError> {
        val errors = mutableListOf<ClientIdValidationError>()
        val warnings = mutableListOf<String>()

        // JAR with x5c is required
        val certificateCheck = validateX509Prerequisites(args, errors)
        if (certificateCheck != null) {
            return certificateCheck
        }

        val certificates = args.jarSignerCertificates!!
        val leafCert = certificates.first()
        val clientId = args.parsedClientId.clientId

        // Verify certificate
        val verifiedCert = verifyAndResolveX509(certificates, errors)

        // Compute SHA-256 hash of the certificate DER and encode as base64url (no padding)
        val certHash = hash(leafCert.der, DigestAlg.SHA256)
        val certHashBase64Url = certHash.encodeToBase64Url().trimEnd('=')

        // Compare with client_id
        if (certHashBase64Url != clientId) {
            // Also try with padding in case client sent it with padding
            val certHashBase64UrlPadded = certHash.encodeToBase64Url()
            if (certHashBase64UrlPadded != clientId) {
                errors.add(ClientIdValidationError(
                    type = ClientIdValidationErrorType.CERTIFICATE_HASH_MISMATCH,
                    message = "Certificate hash does not match client_id",
                    details = "client_id='$clientId', computed_hash='$certHashBase64Url'"
                ))
            }
        }

        val valid = errors.isEmpty()
        log.debug("x509_hash validation ${if (valid) "passed" else "failed"}: ${errors.map { it.message }}")

        return Ok(ValidateClientIdResult(
            valid = valid,
            scheme = ClientIdScheme.X509_HASH,
            clientId = clientId,
            verifiedCertificate = if (valid) verifiedCert else null,
            errors = errors,
            warnings = warnings
        ))
    }

    /**
     * VERIFIER_ATTESTATION: Validate attestation JWT.
     *
     * Per OpenID4VP 1.0 Section 5.9.3 and Section 12:
     * - JAR is required
     * - JAR header must contain `jwt` header with verifier attestation JWT
     * - Attestation JWT must be verified against trusted issuers
     * - Attestation sub claim must match client_id (without prefix)
     * - Attestation cnf.jwk must match the key used to sign the JAR
     */
    private suspend fun validateVerifierAttestation(args: ValidateClientIdArgs): IdkResult<ValidateClientIdResult, IdkError> {
        val errors = mutableListOf<ClientIdValidationError>()
        val clientId = args.parsedClientId.clientId

        // 1. Check if attestation JWT is present
        val attestationJwt = args.jarAttestationJwt
        if (attestationJwt == null) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.ATTESTATION_JWT_MISSING,
                message = "Verifier attestation JWT is missing",
                details = "The JAR jwt header must contain the verifier attestation JWT"
            ))
            return Ok(ValidateClientIdResult(
                valid = false,
                scheme = ClientIdScheme.VERIFIER_ATTESTATION,
                clientId = clientId,
                errors = errors
            ))
        }

        // 2. Check if JAR signer JWK is available for cnf binding verification
        if (args.jarSignerJwk == null) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.JAR_SIGNER_JWK_MISSING,
                message = "JAR signer JWK is missing",
                details = "The JWK used to sign the JAR is needed to verify cnf binding"
            ))
        }

        // 3. Check if trusted issuers are configured
        val trustedIssuers = args.trustedAttestationIssuers
        if (trustedIssuers.isNullOrEmpty()) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.VALIDATION_ERROR,
                message = "No trusted attestation issuers configured",
                details = "At least one trusted issuer must be configured for verifier_attestation scheme"
            ))
        }

        // 4. Verify the attestation JWT
        if (verifyVerifierAttestationCommand != null && errors.isEmpty()) {
            val verifyResult = verifyVerifierAttestationCommand.execute(
                VerifyVerifierAttestationArgs(
                    attestationJwt = attestationJwt,
                    expectedClientId = clientId,
                    trustedIssuers = trustedIssuers ?: emptyList(),
                    jarSignerJwk = args.jarSignerJwk
                )
            )

            if (verifyResult.isErr) {
                errors.add(ClientIdValidationError(
                    type = ClientIdValidationErrorType.VALIDATION_ERROR,
                    message = "Failed to verify attestation JWT",
                    details = verifyResult.error.message.defaultMessage
                ))
            } else {
                val result = verifyResult.value
                if (!result.valid) {
                    // Convert attestation validation errors to client_id validation errors
                    result.errors.forEach { attestationError ->
                        errors.add(ClientIdValidationError(
                            type = attestationError.type,
                            message = attestationError.message,
                            details = attestationError.details
                        ))
                    }
                }
            }
        } else if (verifyVerifierAttestationCommand == null) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.SCHEME_NOT_SUPPORTED,
                message = "Verifier attestation verification not available",
                details = "VerifyVerifierAttestationCommand is not injected"
            ))
        }

        val valid = errors.isEmpty()
        log.debug("verifier_attestation validation ${if (valid) "passed" else "failed"}: ${errors.map { it.message }}")

        return Ok(ValidateClientIdResult(
            valid = valid,
            scheme = ClientIdScheme.VERIFIER_ATTESTATION,
            clientId = clientId,
            errors = errors
        ))
    }

    /**
     * DECENTRALIZED_IDENTIFIER: Resolve DID and extract verification method for JAR signature verification.
     *
     * Per OpenID4VP 1.0 Section 5.2:
     * - JAR is required and must be signed by the DID
     * - The signer's DID must match the client_id identifier
     * - DID document must be resolved to obtain the key material
     * - The returned key material is used to verify the JAR signature
     *
     * Unlike other schemes where key material comes from the JAR header (x5c, jwk),
     * for DID the key material comes exclusively from the resolved DID document.
     */
    private suspend fun validateDecentralizedIdentifier(args: ValidateClientIdArgs): IdkResult<ValidateClientIdResult, IdkError> {
        val errors = mutableListOf<ClientIdValidationError>()
        val clientId = args.parsedClientId.clientId

        // JAR requirements are already validated in validateJarRequirements

        // 1. Validate that the signer's DID matches the client_id
        val signerKid = args.jarSignerKid
        if (signerKid == null) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.VALIDATION_ERROR,
                message = "Cannot verify DID match: JAR kid header is missing",
                details = "The JAR must include a kid header referencing the DID verification method"
            ))
            return Ok(ValidateClientIdResult(
                valid = false,
                scheme = ClientIdScheme.DECENTRALIZED_IDENTIFIER,
                clientId = clientId,
                errors = errors
            ))
        }

        // Extract the DID from the kid (format: did:method:id#key-1 or just did:method:id)
        val signerDid = signerKid.substringBefore('#')

        if (signerDid != clientId) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.JAR_SIGNER_DID_MISMATCH,
                message = "JAR signer DID does not match client_id",
                details = "client_id='$clientId', signer DID='$signerDid'"
            ))
            return Ok(ValidateClientIdResult(
                valid = false,
                scheme = ClientIdScheme.DECENTRALIZED_IDENTIFIER,
                clientId = clientId,
                errors = errors
            ))
        }

        // 2. Parse the DID to extract method
        val method = extractDidMethod(clientId)
        if (method == null) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.DID_INVALID_FORMAT,
                message = "Invalid DID format",
                details = "Expected format: did:method:identifier, got: $clientId"
            ))
            return Ok(ValidateClientIdResult(
                valid = false,
                scheme = ClientIdScheme.DECENTRALIZED_IDENTIFIER,
                clientId = clientId,
                errors = errors
            ))
        }

        // 3. Check if we have a resolver for this DID method
        if (didResolverRegistry == null) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.SCHEME_NOT_SUPPORTED,
                message = "DID resolution not available",
                details = "DidResolverRegistry is not configured. Add lib-did-resolver-impl to enable DID resolution."
            ))
            return Ok(ValidateClientIdResult(
                valid = false,
                scheme = ClientIdScheme.DECENTRALIZED_IDENTIFIER,
                clientId = clientId,
                errors = errors
            ))
        }
        val resolver = didResolverRegistry.getResolver(method)
        if (resolver == null) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.DID_METHOD_NOT_SUPPORTED,
                message = "DID method not supported: $method",
                details = "No resolver available for DID method: $method"
            ))
            return Ok(ValidateClientIdResult(
                valid = false,
                scheme = ClientIdScheme.DECENTRALIZED_IDENTIFIER,
                clientId = clientId,
                errors = errors
            ))
        }

        // 4. Resolve the DID document
        log.debug("Resolving DID for client_id validation: $clientId")
        val resolutionResult = resolver.resolve(clientId, DidResolutionOptions())

        if (resolutionResult.isErr) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.DID_RESOLUTION_FAILED,
                message = "DID resolution failed",
                details = resolutionResult.error.message.defaultMessage
            ))
            return Ok(ValidateClientIdResult(
                valid = false,
                scheme = ClientIdScheme.DECENTRALIZED_IDENTIFIER,
                clientId = clientId,
                errors = errors
            ))
        }

        val resolution = resolutionResult.value
        val didDocument = resolution.didDocument
        if (didDocument == null) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.DID_DOCUMENT_NOT_FOUND,
                message = "DID document not found",
                details = "Resolution succeeded but no document returned for: $clientId"
            ))
            return Ok(ValidateClientIdResult(
                valid = false,
                scheme = ClientIdScheme.DECENTRALIZED_IDENTIFIER,
                clientId = clientId,
                errors = errors
            ))
        }

        // 5. Find the verification method by kid fragment
        val kidFragment = if (signerKid.contains('#')) signerKid.substringAfter('#') else null
        val (verificationMethod, verificationMethodId) = if (!kidFragment.isNullOrEmpty()) {
            // Look for verification method by fragment
            val vm = didDocument.verificationMethod?.find { vm ->
                vm.id == signerKid || vm.id.endsWith("#$kidFragment")
            }
            vm to (vm?.id ?: signerKid)
        } else {
            // If no fragment, use first authentication method
            val authMethods = resolution.verificationMethodsByPurpose[VerificationPurpose.AUTHENTICATION]
            val vm = authMethods?.firstOrNull()
            vm to (vm?.id ?: clientId)
        }

        if (verificationMethod == null) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.DID_VERIFICATION_METHOD_NOT_FOUND,
                message = "Verification method not found",
                details = "No verification method found for kid: $signerKid in DID document"
            ))
            return Ok(ValidateClientIdResult(
                valid = false,
                scheme = ClientIdScheme.DECENTRALIZED_IDENTIFIER,
                clientId = clientId,
                errors = errors
            ))
        }

        // 6. Extract the public key JWK
        val publicKeyJwk = verificationMethod.publicKeyJwk
        if (publicKeyJwk == null) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.DID_NO_KEY_MATERIAL,
                message = "No key material in verification method",
                details = "Verification method ${verificationMethod.id} does not contain a publicKeyJwk"
            ))
            return Ok(ValidateClientIdResult(
                valid = false,
                scheme = ClientIdScheme.DECENTRALIZED_IDENTIFIER,
                clientId = clientId,
                errors = errors
            ))
        }

        log.debug("decentralized_identifier validation passed - resolved verification method: $verificationMethodId")

        // Return the resolved key material for JAR signature verification
        return Ok(ValidateClientIdResult(
            valid = true,
            scheme = ClientIdScheme.DECENTRALIZED_IDENTIFIER,
            clientId = clientId,
            resolvedSignerJwk = publicKeyJwk,
            resolvedVerificationMethodId = verificationMethodId,
            errors = errors
        ))
    }

    /**
     * Extracts the DID method from a DID string.
     * @return The method or null if the DID format is invalid
     */
    private fun extractDidMethod(did: String): String? {
        val parts = did.split(":")
        return if (parts.size >= 2 && parts[0] == "did") {
            parts[1]
        } else {
            null
        }
    }

    /**
     * Scheme not supported (OPENID_FEDERATION).
     */
    private fun notSupported(args: ValidateClientIdArgs, reason: String): IdkResult<ValidateClientIdResult, IdkError> {
        log.debug("Scheme ${args.parsedClientId.clientIdScheme} not supported: $reason")
        return Ok(ValidateClientIdResult(
            valid = false,
            scheme = args.parsedClientId.clientIdScheme,
            clientId = args.parsedClientId.clientId,
            errors = listOf(ClientIdValidationError(
                type = ClientIdValidationErrorType.SCHEME_NOT_SUPPORTED,
                message = reason
            ))
        ))
    }

    /**
     * ORIGIN: Not allowed in wallet requests.
     */
    private fun notAllowed(args: ValidateClientIdArgs): IdkResult<ValidateClientIdResult, IdkError> {
        log.warn("Origin scheme is not allowed in wallet requests")
        return Ok(ValidateClientIdResult(
            valid = false,
            scheme = ClientIdScheme.ORIGIN,
            clientId = args.parsedClientId.clientId,
            errors = listOf(ClientIdValidationError(
                type = ClientIdValidationErrorType.VALIDATION_ERROR,
                message = "Origin scheme is reserved for Digital Credentials API and MUST NOT be accepted by wallets"
            ))
        ))
    }

    /**
     * Validate prerequisites for X.509 schemes (JAR required, certificates present).
     * Returns a result if validation fails, null if prerequisites are met.
     */
    private fun validateX509Prerequisites(
        args: ValidateClientIdArgs,
        errors: MutableList<ClientIdValidationError>
    ): IdkResult<ValidateClientIdResult, IdkError>? {
        // JAR is required
        if (!args.jarUsed) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.JAR_REQUIRED,
                message = "JAR (signed request) is required for ${args.parsedClientId.clientIdScheme} scheme",
                details = "The authorization request must be signed with x5c header containing the certificate chain"
            ))
            return Ok(ValidateClientIdResult(
                valid = false,
                scheme = args.parsedClientId.clientIdScheme,
                clientId = args.parsedClientId.clientId,
                errors = errors
            ))
        }

        // Certificates must be present
        if (args.jarSignerCertificates.isNullOrEmpty()) {
            errors.add(ClientIdValidationError(
                type = ClientIdValidationErrorType.CERTIFICATE_MISSING,
                message = "X.509 certificate chain is missing from JAR x5c header",
                details = "The ${args.parsedClientId.clientIdScheme} scheme requires the JAR to include an x5c header"
            ))
            return Ok(ValidateClientIdResult(
                valid = false,
                scheme = args.parsedClientId.clientIdScheme,
                clientId = args.parsedClientId.clientId,
                errors = errors
            ))
        }

        return null
    }

    /**
     * Verify X.509 certificate chain using identifier resolution service.
     */
    private suspend fun verifyAndResolveX509(
        certificates: List<Certificate>,
        errors: MutableList<ClientIdValidationError>
    ): Certificate? {
        val x5cStrings = certificates.map { it.derToBase64() }

        val opts = ExternalIdentifierX5cOpts(
            identifier = x5cStrings,
            verify = true
        )

        val result = identifierService.resolve(opts)
        return result.fold(
            success = { resolved ->
                val x5cResult = resolved as? ExternalIdentifierResult.X5c
                if (x5cResult != null) {
                    val verificationResult = x5cResult.verificationResult
                    if (verificationResult.error) {
                        errors.add(ClientIdValidationError(
                            type = ClientIdValidationErrorType.CERTIFICATE_VALIDATION_FAILED,
                            message = "X.509 certificate chain validation failed",
                            details = verificationResult.message
                        ))
                        null
                    } else {
                        x5cResult.certificates.firstOrNull()
                    }
                } else {
                    errors.add(ClientIdValidationError(
                        type = ClientIdValidationErrorType.CERTIFICATE_VALIDATION_FAILED,
                        message = "Unexpected identifier resolution result type"
                    ))
                    null
                }
            },
            failure = { error ->
                log.error("X.509 identifier resolution failed: ${error.message.defaultMessage}")
                errors.add(ClientIdValidationError(
                    type = ClientIdValidationErrorType.CERTIFICATE_VALIDATION_FAILED,
                    message = "X.509 certificate resolution failed",
                    details = error.message.defaultMessage
                ))
                null
            }
        )
    }

}
