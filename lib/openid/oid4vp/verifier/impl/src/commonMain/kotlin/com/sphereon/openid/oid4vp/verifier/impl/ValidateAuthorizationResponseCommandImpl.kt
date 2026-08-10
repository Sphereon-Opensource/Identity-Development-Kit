/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Encoding
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Err
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeTo
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventCategories
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.events.EventTypes
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.events.SessionEventService
import com.sphereon.di.session.SessionScope
import com.sphereon.jsonld.JsonLdError
import com.sphereon.jsonld.WellKnownContexts
import com.sphereon.jsonld.WellKnownCredentialTypes
import com.sphereon.jsonld.command.JsonLdContextValidator
import com.sphereon.jsonld.command.JsonLdSchemaValidator
import com.sphereon.jsonld.command.ValidateJsonLdContextInput
import com.sphereon.jsonld.command.ValidateJsonLdSchemaInput
import com.sphereon.mdoc.data.device.DeviceResponseCborCodec
import com.sphereon.openid.oid4vp.common.CredentialFormat
import com.sphereon.openid.oid4vp.common.responseUri
import com.sphereon.openid.oid4vp.verifier.CredentialIssuerRef
import com.sphereon.openid.oid4vp.verifier.CredentialTrustValidation
import com.sphereon.openid.oid4vp.verifier.CredentialTrustValidationMode
import com.sphereon.openid.oid4vp.verifier.MatchedCredential
import com.sphereon.openid.oid4vp.verifier.Oid4vpCredentialTrustValidationArgs
import com.sphereon.openid.oid4vp.verifier.Oid4vpCredentialTrustValidator
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseCommandService
import com.sphereon.openid.oid4vp.verifier.ValidationResult
import com.sphereon.openid.oid4vp.verifier.VerifyHolderBindingArgs
import com.sphereon.openid.oid4vp.verifier.VerifyHolderBindingCommand
import com.sphereon.openid.oid4vp.verifier.impl.event.emitOid4vpSessionHistoryEvent
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import com.sphereon.sdjwt.SdJwtCodec
import com.sphereon.statuslist.CredentialStatusDecision
import com.sphereon.statuslist.CredentialStatusEvaluation
import com.sphereon.statuslist.CredentialStatusPolicy
import com.sphereon.statuslist.describeStatus
import com.sphereon.statuslist.evaluateCredentialStatus
import com.sphereon.statuslist.spi.CredentialStatusVerifier
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Implementation of ValidateAuthorizationResponseCommand for OpenID4VP RP (Verifier).
 *
 * Validates authorization responses against the original DCQL query:
 * - Validates state parameter matches original request
 * - Validates VP token format matches requested formats
 * - Validates credentials satisfy DCQL query requirements
 * - Validates required claims are present
 *
 * OpenID4VP 1.0 Final:
 * - VP tokens are self-descriptive (format is determined from the token itself)
 * - NO presentation_submission validation (that's DIF PE, not used in OID4VP 1.0 Final)
 *
 * Reference: OpenID4VP 1.0 Final Section 7 - Verifiable Presentation Validation
 */
@Inject
@SingleIn(SessionScope::class)
class ValidateAuthorizationResponseCommandImpl(
    execution: SessionExecution,
    private val authorizationSessionStore: AuthorizationSessionStore,
    private val verifyHolderBindingCommand: VerifyHolderBindingCommand,
    private val jsonLdContextValidator: JsonLdContextValidator,
    private val jsonLdSchemaValidator: JsonLdSchemaValidator,
    private val deviceResponseCborCodec: DeviceResponseCborCodec,
    /**
     * Optional, possibly-empty set of credential-status mechanisms. Populated only when status-list
     * implementations (e.g. `lib-statuslist-impl`) are on the verifier's classpath. Empty ⇒ no status
     * checking runs at all; non-empty ⇒ each matched credential is evaluated against its per-query
     * [CredentialStatusPolicy]. The set is declared `allowEmpty` by `CredentialStatusVerifierMultibinds`
     * in lib-statuslist-public, so the graph resolves even with zero implementations.
     */
    private val credentialStatusVerifiers: Set<CredentialStatusVerifier>,
    /**
     * Optional, possibly-empty set of OID4VP credential-trust validators. No default: a defaulted
     * `Set<T>` constructor parameter is silently skipped by Metro codegen (the binding is never
     * resolved and the default is always used), which would make this always resolve to an empty
     * set regardless of what is actually contributed - defeating the R3 fail-closed trust-domain
     * validator. The set is declared `allowEmpty` by `Oid4vpCredentialTrustValidatorMultibinds` in
     * lib-openid-oid4vp-verifier-public (mirrors `credentialStatusVerifiers`/
     * `CredentialStatusVerifierMultibinds` above), so the graph still resolves with zero
     * implementations when no trust validator is contributed.
     */
    private val credentialTrustValidators: Set<Oid4vpCredentialTrustValidator>,
    private val eventService: SessionEventService? = null,
) : TypedServiceCommandAdapter<ValidateAuthorizationResponseArgs, ValidationResult, IdkError>(
        commandId = ValidateAuthorizationResponseCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<ValidateAuthorizationResponseArgs>(),
        outputTypeToken = typeToken<ValidationResult>(),
    ),
    ValidateAuthorizationResponseCommand,
    ValidateAuthorizationResponseCommandService {
    override val commandId: String get() = ValidateAuthorizationResponseCommand.COMMAND_ID
    private var pendingHistorySession: AuthorizationSession? = null

    override suspend fun supports(args: Any): Boolean = args is ValidateAuthorizationResponseArgs

    override suspend fun validateAuthorizationResponse(args: ValidateAuthorizationResponseArgs): IdkResult<ValidationResult, IdkError> = execute(args)

    override suspend fun doExecute(
        args: ValidateAuthorizationResponseArgs,
        applyDuring: (ValidateAuthorizationResponseArgs) -> ValidateAuthorizationResponseArgs,
    ): IdkResult<ValidationResult, IdkError> {
        val correlationId =
            args.originalRequest.state?.takeIf(String::isNotBlank)
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "OID4VP authorization response has no session correlation state",
                    ),
                )
        pendingHistorySession =
            authorizationSessionStore.get(correlationId).getOrElse { return Err(it) }
                ?: return Err(
                    IdkError.NOT_FOUND_ERROR(
                        resource = "authorizationSession:$correlationId",
                        message = "OID4VP authorization session not found",
                    ),
                )
        val result = doExecuteInternal(args, applyDuring)
        emitOutcome(result)
        pendingHistorySession = null
        return result
    }

    /**
     * Emit the technical credential-status rejection detail (status list URI / index / value) as an
     * event for ops/audit. Deliberately separate from the short user-facing error so the URI and index
     * never leak into the verification result returned to the wallet/relying party UI.
     */
    private suspend fun emitStatusRejected(
        queryId: String,
        evaluation: CredentialStatusEvaluation,
    ) {
        val es = eventService ?: return
        val rejected = evaluation.rejectedStatus
        es.emit(
            es
                .eventBuilder()
                .type(EventTypes.OID4VP_CREDENTIAL_STATUS_REJECTED)
                .subsystem(EventSubsystems.OID4VP)
                .category(EventCategories.SECURITY)
                .origin(ValidateAuthorizationResponseCommand.COMMAND_ID)
                .payload(
                    buildJsonObject {
                        put("credentialQueryId", queryId)
                        rejected?.let {
                            put("statusValue", it.value)
                            put("status", describeStatus(it))
                            put("statusListUri", it.statusListUri)
                        }
                        evaluation.reason?.let { put("detail", it) }
                    },
                ).build(),
        )
    }

    private suspend fun emitOutcome(result: IdkResult<ValidationResult, IdkError>,) {
        val isValid = result.getOrNull()?.valid == true
        val type = if (isValid) EventTypes.OID4VP_RESPONSE_VERIFIED else EventTypes.OID4VP_RESPONSE_FAILED
        val es = eventService ?: return
        es.emitOid4vpSessionHistoryEvent(
            type = type,
            origin = ValidateAuthorizationResponseCommand.COMMAND_ID,
            session = requireNotNull(pendingHistorySession) {
                "OID4VP validation outcome has no persisted authorization session"
            },
            stage = "RESPONSE_VALIDATION",
            outcome = if (isValid) "VERIFIED" else "FAILED",
            errorCode = if (isValid) null else "validation_failed",
        )
    }

    private suspend fun doExecuteInternal(
        args: ValidateAuthorizationResponseArgs,
        applyDuring: (ValidateAuthorizationResponseArgs) -> ValidateAuthorizationResponseArgs,
    ): IdkResult<ValidationResult, IdkError> {
        val processedArgs = applyDuring(args)

        log.debug("Validating authorization response against DCQL query")

        val parsedResponse = processedArgs.parsedResponse
        val originalRequest = processedArgs.originalRequest
        val dcqlQuery = processedArgs.dcqlQuery
        val expectedNonce = processedArgs.expectedNonce

        val errors = mutableListOf<String>()
        val matchedCredentials = mutableListOf<MatchedCredential>()
        // Query IDs the wallet actually submitted a presentation for. A required credential that WAS
        // submitted but then discarded (status rejection, holder-binding failure, ...) must not also be
        // reported as "not found" — the specific discard reason is already in `errors`.
        val submittedQueryIds = mutableSetOf<String>()

        val authorizationSession =
            originalRequest.state
                ?.takeIf { it.isNotBlank() }
                ?.let { authorizationSessionStore.getByCorrelationId(it).getOrNull() }
        val effectiveVerifierId = processedArgs.verifierId ?: authorizationSession?.verifierId
        val effectiveDcqlQueryId = processedArgs.dcqlQueryId ?: authorizationSession?.dcqlQueryId
        val effectiveTemplateId = processedArgs.templateId ?: authorizationSession?.templateId

        // Per-DCQL-query credential status policies, pinned on the session at create time. Loaded only
        // to drive status acceptance; absence (no session / no map) falls back to the strict default
        // per query, and matters only when status verifiers are actually wired (see the loop below).
        val statusPolicies: Map<String, CredentialStatusPolicy> =
            if (credentialStatusVerifiers.isEmpty()) {
                emptyMap()
            } else {
                authorizationSession?.credentialStatusPolicies ?: emptyMap()
            }

        // Validate state parameter
        if (originalRequest.state != null && parsedResponse.state != originalRequest.state) {
            errors.add("State mismatch: expected '${originalRequest.state}', got '${parsedResponse.state}'")
        }

        // Validate each presentation in the VP token (DCQL format: Map of queryId -> presentations).
        //
        // OID4VP §8.1: each Presentation value is Credential-Format dependent — a JSON string
        // for compact formats (dc+sd-jwt, jwt_vc_json, mso_mdoc) or a JSON object for the W3C
        // Data Integrity formats (ldp_vc/ldp_vp). We branch on the element shape and NEVER
        // blanket-cast to jsonPrimitive.
        val vpToken = parsedResponse.vpToken

        for ((queryId, presentationElements) in vpToken.presentationElements) {
            submittedQueryIds.add(queryId)
            // Validate the credential query ID exists in the DCQL query
            val credentialQueries = dcqlQuery.credentials
            val matchingQuery = credentialQueries.find { it.id == queryId }

            if (matchingQuery == null) {
                errors.add("Credential query ID '$queryId' from vp_token not found in DCQL query")
                continue
            }

            // Validate each presentation for this query ID
            for ((presentationIndex, presentationElement) in presentationElements.withIndex()) {
                // ldp_vc/ldp_vp Data Integrity presentations arrive as a JSON object. The
                // verifier's holder-binding/claim-extraction pipeline below only supports the
                // compact string formats (SD-JWT KB-JWT, mdoc DeviceAuth, JWT proof). Surface a
                // clean validation error for the LDP object form rather than letting a downstream
                // `.jsonPrimitive` cast blow up.
                if (presentationElement !is JsonPrimitive || !presentationElement.isString) {
                    errors.add(
                        "Presentation for query '$queryId' at index $presentationIndex is a JSON object " +
                            "(ldp_vc/ldp_vp Data Integrity format). Cryptographic verification of W3C Data " +
                            "Integrity presentations is not supported by this verifier; only compact formats " +
                            "(dc+sd-jwt, jwt_vc_json, mso_mdoc) can be verified.",
                    )
                    continue
                }
                val presentation = presentationElement.content
                val detectedFormat = CredentialFormat.detectFormat(presentation)

                if (detectedFormat == null) {
                    errors.add("Could not determine format of presentation for query '$queryId' at index $presentationIndex")
                    continue
                }

                // Validate the required Credential Format Identifier.
                //
                // Special case for `vc+ld+json+jwt`: detectFormat returns
                // JWT_VC_JSON for any compact JWS, since VCDM 1.1 JWT-VC and
                // VCDM 2.0 JOSE-enveloped credentials are indistinguishable
                // at the wire level. When the verifier explicitly asked for
                // `vc+ld+json+jwt`, accept the JWT and let the JSON-LD shape
                // validator (validateVcLdJsonShape) enforce VCDM 2.0 by
                // peeking at the body's @context. A JWT_VC_JSON body
                // smuggled in under that query format will be caught there.
                val queryFormat = matchingQuery.format
                val isVcLdJsonJwtQueryOverJwtWire =
                    queryFormat == CredentialFormat.VC_LD_JSON_JWT.value &&
                        detectedFormat == CredentialFormat.JWT_VC_JSON
                val formatMatches =
                    queryFormat == detectedFormat.value ||
                        CredentialFormat.fromValueLenient(queryFormat) == detectedFormat ||
                        isVcLdJsonJwtQueryOverJwtWire

                if (!formatMatches) {
                    errors.add("Presentation format '${detectedFormat.value}' for query '$queryId' does not match required format '$queryFormat'")
                    continue
                }

                // OID4VP 1.0 Final Sections 5.3 and 6.1 allow a Credential Query to accept a
                // Credential without a cryptographic Holder Binding proof. Credential integrity,
                // authenticity, disclosure integrity, and any proof that is actually present are
                // still verified. The format-specific command enforces the query's binding mode.
                val bindingArgs =
                    VerifyHolderBindingArgs(
                        presentation = presentation,
                        format = detectedFormat.value,
                        expectedNonce = expectedNonce,
                        expectedAudience = originalRequest.clientId,
                        requireCryptographicHolderBinding = matchingQuery.require_cryptographic_holder_binding,
                        clientId = originalRequest.clientId,
                        responseUri = originalRequest.responseUri,
                        verifierEncryptionJwkThumbprint = processedArgs.verifierEncryptionJwkThumbprint,
                    )
                val bindingResult =
                    verifyHolderBindingCommand
                        .execute(bindingArgs)
                        .getOrElse { error ->
                            errors.add(
                                "Holder binding verification command failed for query '$queryId' " +
                                    "at index $presentationIndex: ${error.message.defaultMessage}",
                            )
                            continue
                        }
                if (!bindingResult.verified) {
                    val detail = bindingResult.errors.takeIf { it.isNotEmpty() }?.joinToString("; ") ?: "no detail"
                    // Distinguish trust-establishment failure from cryptographic mismatch.
                    // For SD-JWT issuer JWTs the verifier sets `issuerTrustEstablished=false`
                    // when no key could be resolved through the trust chain (e.g. relative
                    // `kid: "#0"` not qualified by `iss`, did:web fetch failed, no trust
                    // anchor for an x5c chain). In that state `signatureValid=false` is
                    // misleading on its own, so the message calls it out explicitly.
                    val rootCause =
                        when {
                            bindingResult.issuerTrustEstablished == false -> {
                                "issuer trust establishment failed (no verification key resolved for the issuer JWT — " +
                                    "check kid/iss qualification, did:web reachability, or trust anchors)"
                            }

                            bindingResult.issuerCryptoVerified == false -> {
                                "issuer JWT signature did not verify against the resolved key"
                            }

                            !bindingResult.signatureValid -> {
                                "signature/binding check failed"
                            }

                            !bindingResult.nonceValid -> {
                                "nonce mismatch"
                            }

                            !bindingResult.audienceValid -> {
                                "audience mismatch"
                            }

                            bindingResult.sdHashValid == false -> {
                                "sd_hash mismatch"
                            }

                            else -> {
                                "verification not satisfied"
                            }
                        }
                    errors.add(
                        "Holder binding verification failed for query '$queryId' at index " +
                            "$presentationIndex (method=${bindingResult.bindingMethod}, " +
                            "issuerTrustEstablished=${bindingResult.issuerTrustEstablished}, " +
                            "issuerCryptoVerified=${bindingResult.issuerCryptoVerified}, " +
                            "signatureValid=${bindingResult.signatureValid}, " +
                            "nonceValid=${bindingResult.nonceValid}, " +
                            "audienceValid=${bindingResult.audienceValid}): " +
                            "$rootCause — $detail",
                    )
                    continue
                }

                // VCDM 2.0 + JSON-LD: enforce the same `@context` and JSON
                // Schema validators that the issuer side runs. Only runs
                // when the verifier asked for `vc+ld+json+jwt`; SD-JWT,
                // mdoc, and VCDM 1.1 jwt_vc_json presentations skip the
                // JWT-body decode entirely.
                if (queryFormat == CredentialFormat.VC_LD_JSON_JWT.value) {
                    val vcLdViolation = validateVcLdJsonShape(presentation)
                    if (vcLdViolation != null) {
                        errors.add(
                            "VCDM 2.0 + JSON-LD validation failed for query '$queryId' at index $presentationIndex: " +
                                vcLdViolation,
                        )
                        continue
                    }
                }

                val issuer = extractCredentialIssuer(presentation, detectedFormat)
                val trust =
                    validateCredentialTrust(
                        verifierId = effectiveVerifierId,
                        dcqlQueryId = effectiveDcqlQueryId,
                        templateId = effectiveTemplateId,
                        credentialQueryId = queryId,
                        presentation = presentation,
                        detectedFormat = detectedFormat,
                        issuer = issuer,
                    )
                if (trust.enabled && trust.mode == CredentialTrustValidationMode.DEFAULT_ENFORCE && trust.trusted != true) {
                    errors.add(
                        "Credential trust validation failed for query '$queryId' at index $presentationIndex: " +
                            (trust.details ?: trust.status ?: "issuer is not trusted by the resolved trust domains"),
                    )
                    log.warn("Credential '$queryId' rejected by trust-domain validation: ${trust.details ?: trust.status}")
                    continue
                }

                // Credential status (revocation/suspension) check. Runs only when status verifiers are
                // wired; otherwise skipped entirely. `extractDisclosedClaims` strips the `status` claim,
                // so we read the full credential payload separately. A REJECT discards the presentation
                // per OID4VP §10, the same as a holder-binding failure.
                if (credentialStatusVerifiers.isNotEmpty()) {
                    val statusClaims = credentialClaimsForStatus(presentation, detectedFormat) ?: JsonObject(emptyMap())
                    val policy = statusPolicies[queryId] ?: CredentialStatusPolicy()
                    val evaluation = evaluateCredentialStatus(credentialStatusVerifiers, statusClaims, policy)
                    if (evaluation.decision == CredentialStatusDecision.REJECT) {
                        // User-facing: just the credential and its state ("EuPid is revoked"). The status
                        // list URI / index are operator diagnostics — logged and emitted as an event, not
                        // returned to the end user.
                        val word = evaluation.rejectedStatus?.let { describeStatus(it) }
                        errors.add(if (word != null) "$queryId is $word" else "$queryId could not be validated")
                        log.warn("Credential '$queryId' rejected by status check: ${evaluation.reason}")
                        emitStatusRejected(queryId, evaluation)
                        continue
                    }
                }

                // Extract disclosed claims from the presentation
                val disclosedClaims = extractDisclosedClaims(presentation, detectedFormat)

                matchedCredentials.add(
                    MatchedCredential(
                        credentialQueryId = queryId,
                        format = detectedFormat.value,
                        presentation = presentation,
                        disclosedClaims = disclosedClaims,
                        issuer = issuer,
                        trust = trust.takeIf { it.enabled || it.trustDomainIds.isNotEmpty() },
                    ),
                )
            }
        }

        // Validate all required credentials are present
        val requiredCredentials =
            dcqlQuery.credentials.filter { query ->
                // If no credential_sets, all credentials are required
                dcqlQuery.credential_sets.isNullOrEmpty()
            }

        for (required in requiredCredentials) {
            if (matchedCredentials.none { it.credentialQueryId == required.id }) {
                // Only "not found" when the wallet never submitted it. If it was submitted but
                // discarded (e.g. revoked status), the specific rejection error already explains why,
                // so adding "not found" on top would just confuse the end user.
                if (required.id !in submittedQueryIds) {
                    errors.add("Required credential '${required.id}' not found in response")
                }
            }
        }

        // Validate credential_sets if present (OR logic)
        dcqlQuery.credential_sets?.filter { it.required }?.forEach { credentialSet ->
            val anySatisfied =
                credentialSet.options.any { option ->
                    option.all { credId ->
                        matchedCredentials.any { it.credentialQueryId == credId }
                    }
                }
            if (!anySatisfied) {
                errors.add("No option in required credential_set satisfied")
            }
        }

        val valid = errors.isEmpty()

        log.info("Validation result: valid=$valid, matched=${matchedCredentials.size}, errors=${errors.size}")

        val result =
            ValidationResult(
                valid = valid,
                matchedCredentials = matchedCredentials,
                errors = errors,
            )

        // Best-effort session update using state as session correlation key.
        val correlationId = originalRequest.state
        if (!correlationId.isNullOrBlank()) {
            authorizationSessionStore.storeValidationResult(correlationId = correlationId, validationResult = result).fold(
                success = { pendingHistorySession = it },
                failure = { e -> log.warn("Failed to update authorization session with validation result: ${e.message.defaultMessage}") },
            )
        }

        return Ok(result)
    }

    /**
     * If [presentation] is a JWT compact serialization whose payload looks
     * like a VCDM 2.0 + JSON-LD credential (i.e. has a top-level `@context`
     * containing `https://www.w3.org/ns/credentials/v2`), validate the
     * `@context` chain (UNTP `@vocab` MUST-NOT) and the JSON Schema
     * registered for the primary `type`. Returns null on success or when the
     * presentation does not look like VCDM 2.0; returns a human-readable
     * reason string on validation failure.
     *
     * Holder binding has already been verified by the time this is called,
     * so we can safely decode the payload without re-checking the signature.
     * Decode is best-effort: malformed bytes return null (no crash).
     */
    private suspend fun validateVcLdJsonShape(presentation: String): String? {
        val payload = decodeJwtPayloadOrNull(presentation) ?: return null
        val context = payload["@context"] ?: return null
        if (!referencesVcdm2Context(context)) return null

        val contextResult =
            jsonLdContextValidator.validate(
                ValidateJsonLdContextInput(context = context),
            )
        if (contextResult.isErr) {
            return formatJsonLdError(contextResult.error)
        }

        // Schema validation runs against the VC body. Our format handler
        // emits the VC body merged with JWT registered claims at the JWT
        // root, so the same payload validates cleanly: JSON Schema's
        // `additionalProperties: true` (default for UNTP schemas) lets the
        // extra `iss`/`iat`/`exp`/`sub`/`cnf` keys pass through.
        val primaryType = primaryCredentialTypeOrNull(payload) ?: return null
        val schemaResult =
            jsonLdSchemaValidator.validate(
                ValidateJsonLdSchemaInput(payload = payload, credentialType = primaryType),
            )
        // Unknown types (no schema in the registry) are a soft pass; schema
        // mismatches and malformed schemas abort.
        if (schemaResult.isErr && schemaResult.error !is JsonLdError.NoSchemaRegistered) {
            return formatJsonLdError(schemaResult.error)
        }

        return null
    }

    private fun decodeJwtPayloadOrNull(presentation: String): JsonObject? {
        val parts = presentation.split(".")
        if (parts.size != 3) return null
        val bytes =
            try {
                parts[1].decodeFromBase64Url()
            } catch (expected: IllegalArgumentException) {
                return null
            }
        val parsed =
            try {
                JSON_LENIENT.parseToJsonElement(bytes.decodeToString())
            } catch (expected: kotlinx.serialization.SerializationException) {
                return null
            }
        return parsed as? JsonObject
    }

    private fun referencesVcdm2Context(context: kotlinx.serialization.json.JsonElement): Boolean {
        val target = WellKnownContexts.VCDM_2_0
        return when (context) {
            is JsonPrimitive -> context.isString && context.content == target
            is JsonArray -> context.any { (it as? JsonPrimitive)?.let { p -> p.isString && p.content == target } == true }
            else -> false
        }
    }

    private fun primaryCredentialTypeOrNull(payload: JsonObject): String? {
        val types = (payload["type"] as? JsonArray) ?: return null
        val stringTypes = types.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
        return stringTypes.firstOrNull { it != WellKnownCredentialTypes.VERIFIABLE_CREDENTIAL }
            ?: stringTypes.firstOrNull()
    }

    private fun formatJsonLdError(error: JsonLdError): String = "${error.code}: ${error.message.defaultMessage}"

    /**
     * Extract disclosed claims from a credential presentation.
     *
     * - SD-JWT: parses the compact serialization and resolves all disclosures into native values.
     * - mso_mdoc: CBOR-decodes the `DeviceResponse` and walks each document's issuer-signed
     *   namespaces, surfacing every disclosed `IssuerSignedItem` as a claim.
     *
     * Claim-key shape: mdoc data elements are namespace-qualified, so keys are
     * `<namespace>.<elementIdentifier>` (e.g. `org.iso.18013.5.1.given_name`). This mirrors the
     * mdoc DCQL `claims[].path` representation `[namespace, elementIdentifier]` (a two-segment
     * path joined by `.`), keeping the verifier's normalized claims map aligned with the query
     * paths used to request them. Values are kept consistent with the SD-JWT mapping: primitives
     * (String/Long/Boolean/Double) pass through; anything richer is rendered via toString().
     */
    internal fun extractDisclosedClaims(
        presentation: String,
        format: CredentialFormat,
    ): Map<String, Any?> =
        when (format) {
            CredentialFormat.SD_JWT_VC, CredentialFormat.W3C_VC_SD_JWT -> {
                SdJwtCodec.parse(presentation).fold(
                    success = { sdJwt ->
                        // fullPayload has all disclosures resolved into a JsonObject
                        val payload = sdJwt.payload.fullPayload
                        // Filter out SD-JWT internal fields and unwrap JsonPrimitive to native types
                        val internalKeys = setOf("_sd", "_sd_alg", "cnf", "iss", "iat", "exp", "nbf", "vct", "status")
                        payload.filterKeys { it !in internalKeys }.mapValues { (_, element) ->
                            when (element) {
                                is JsonPrimitive -> element.booleanOrNull ?: element.longOrNull ?: element.doubleOrNull ?: element.contentOrNull
                                else -> element.toString()
                            }
                        }
                    },
                    failure = { error ->
                        log.warn("Failed to parse SD-JWT presentation for claim extraction: ${error.message}")
                        emptyMap()
                    },
                )
            }

            CredentialFormat.MSO_MDOC -> {
                extractMdocClaims(presentation)
            }

            else -> {
                log.debug("Claim extraction not implemented for format: ${format.value}")
                emptyMap()
            }
        }

    /**
     * Full credential payload (including the `status` / `credentialStatus` claims that
     * [extractDisclosedClaims] deliberately strips) for status-list verification. SD-JWT → the
     * resolved full payload; compact JWT-VC → the decoded JWT body; mdoc and other shapes → null (no
     * status read wired yet, handled as "no reference" by the evaluator).
     */
    private fun credentialClaimsForStatus(
        presentation: String,
        format: CredentialFormat,
    ): JsonObject? =
        when (format) {
            CredentialFormat.SD_JWT_VC, CredentialFormat.W3C_VC_SD_JWT -> {
                SdJwtCodec
                    .parse(presentation)
                    .getOrNull()
                    ?.payload
                    ?.fullPayload
            }

            CredentialFormat.JWT_VC_JSON, CredentialFormat.VC_LD_JSON_JWT -> {
                decodeJwtPayloadOrNull(presentation)
            }

            else -> {
                null
            }
        }

    private suspend fun validateCredentialTrust(
        verifierId: String?,
        dcqlQueryId: String?,
        templateId: String?,
        credentialQueryId: String,
        presentation: String,
        detectedFormat: CredentialFormat,
        issuer: CredentialIssuerRef?,
    ): CredentialTrustValidation {
        val validationArgs =
            Oid4vpCredentialTrustValidationArgs(
                verifierId = verifierId,
                dcqlQueryId = dcqlQueryId,
                templateId = templateId,
                credentialQueryId = credentialQueryId,
                format = detectedFormat.value,
                presentation = presentation,
                issuer = issuer,
            )
        val validator =
            credentialTrustValidators.firstOrNull { it.supports(validationArgs) }
                ?: return CredentialTrustValidation(
                    enabled = false,
                    mode = CredentialTrustValidationMode.DISABLED,
                    details = "No OID4VP credential trust validator configured",
                )

        return validator.validate(validationArgs).getOrElse { error ->
            CredentialTrustValidation(
                enabled = true,
                trusted = false,
                mode = CredentialTrustValidationMode.DEFAULT_ENFORCE,
                status = "VALIDATION_ERROR",
                details = error.message.defaultMessage,
            )
        }
    }

    private fun extractCredentialIssuer(
        presentation: String,
        format: CredentialFormat,
    ): CredentialIssuerRef? =
        when (format) {
            CredentialFormat.SD_JWT_VC, CredentialFormat.W3C_VC_SD_JWT -> {
                val issuerJwt = presentation.substringBefore("~").takeIf { it.contains(".") }
                issuerJwt?.let(::extractJwtIssuer)
            }

            CredentialFormat.JWT_VC_JSON, CredentialFormat.VC_LD_JSON_JWT -> {
                extractJwtIssuer(presentation)
            }

            CredentialFormat.MSO_MDOC -> {
                extractMdocIssuer(presentation)
            }

            else -> {
                null
            }
        }

    private fun extractJwtIssuer(compactJwt: String): CredentialIssuerRef? {
        val header = decodeJwtHeaderOrNull(compactJwt)
        val payload = decodeJwtPayloadOrNull(compactJwt)
        if (header == null && payload == null) return null

        val issuer = payload?.stringClaim("iss") ?: payload?.vcIssuer()
        val kid = header?.stringClaim("kid")
        val x5c = header?.stringArrayClaim("x5c").orEmpty()
        val did = issuer?.extractDid() ?: kid?.extractDid()
        val oidfedEntityId = issuer?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        val method =
            when {
                x5c.isNotEmpty() -> "x509"
                did != null -> "did"
                oidfedEntityId != null -> "openid_federation"
                else -> null
            }
        return CredentialIssuerRef(
            issuer = issuer,
            method = method,
            did = did,
            oidfedEntityId = oidfedEntityId,
            kid = kid,
            x5c = x5c,
        )
    }

    private fun extractMdocIssuer(presentation: String): CredentialIssuerRef? {
        val deviceResponseBytes =
            try {
                presentation.decodeFromBase64Url()
            } catch (_: IllegalArgumentException) {
                return null
            }
        val deviceResponse =
            deviceResponseCborCodec.decode(deviceResponseBytes).getOrNull()?.value ?: return null
        val document = deviceResponse.documents?.firstOrNull() ?: return null
        val issuerAuth = document.issuerSigned.issuerAuth
        val x5chain = issuerAuth.protectedHeader.x5chain ?: issuerAuth.unprotectedHeader?.x5chain
        val x5c = x5chain?.value?.map { it.value.encodeTo(Encoding.BASE64) }.orEmpty()
        return CredentialIssuerRef(
            issuer = document.docType.toString(),
            method = if (x5c.isNotEmpty()) "x509" else null,
            x5c = x5c,
        )
    }

    private fun decodeJwtHeaderOrNull(presentation: String): JsonObject? {
        val parts = presentation.split(".")
        if (parts.size != 3) return null
        val bytes =
            try {
                parts[0].decodeFromBase64Url()
            } catch (_: IllegalArgumentException) {
                return null
            }
        val parsed =
            try {
                JSON_LENIENT.parseToJsonElement(bytes.decodeToString())
            } catch (_: kotlinx.serialization.SerializationException) {
                return null
            }
        return parsed as? JsonObject
    }

    private fun JsonObject.stringClaim(name: String): String? =
        (this[name] as? JsonPrimitive)
            ?.takeIf { it.isString }
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }

    private fun JsonObject.stringArrayClaim(name: String): List<String>? =
        (this[name] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull }
            ?.filter { it.isNotBlank() }

    private fun JsonObject.vcIssuer(): String? {
        val issuerElement = this["issuer"]
        if (issuerElement is JsonPrimitive && issuerElement.isString) {
            return issuerElement.contentOrNull?.takeIf { it.isNotBlank() }
        }
        if (issuerElement is JsonObject) {
            issuerElement.stringClaim("id")?.let { return it }
        }
        val vc = this["vc"] as? JsonObject ?: return null
        val vcIssuer = vc["issuer"]
        if (vcIssuer is JsonPrimitive && vcIssuer.isString) {
            return vcIssuer.contentOrNull?.takeIf { it.isNotBlank() }
        }
        if (vcIssuer is JsonObject) {
            return vcIssuer.stringClaim("id")
        }
        return null
    }

    private fun String.extractDid(): String? {
        val value = substringAfter("decentralized_identifier:", this)
        if (!value.startsWith("did:")) return null
        val fragmentIndex = value.indexOf('#')
        val queryIndex = value.indexOf('?')
        val end =
            listOf(fragmentIndex, queryIndex)
                .filter { it >= 0 }
                .minOrNull()
                ?: value.length
        return value.substring(0, end)
    }

    /**
     * Extract the disclosed issuer-signed data elements from an mso_mdoc presentation.
     *
     * The presentation wire form is base64url(CBOR(DeviceResponse)) per ISO 18013-7 §B.3.
     * Holder binding has already been verified by the time this runs, so the CBOR decode is a
     * pure structural read. Every `IssuerSignedItem` across every document and namespace is
     * surfaced under the `<namespace>.<elementIdentifier>` key.
     */
    private fun extractMdocClaims(presentation: String): Map<String, Any?> {
        val deviceResponseBytes =
            try {
                presentation.decodeFromBase64Url()
            } catch (expected: IllegalArgumentException) {
                log.warn("Failed to base64url-decode mso_mdoc presentation for claim extraction: ${expected.message}")
                return emptyMap()
            }

        val deviceResponse =
            deviceResponseCborCodec
                .decode(deviceResponseBytes)
                .getOrElse { error ->
                    log.warn("Failed to CBOR-decode DeviceResponse for claim extraction: ${error.message.defaultMessage}")
                    return emptyMap()
                }.value

        val documents = deviceResponse.documents
        if (documents.isNullOrEmpty()) {
            log.debug("mso_mdoc DeviceResponse contains no documents; no claims to extract")
            return emptyMap()
        }

        val claims = mutableMapOf<String, Any?>()
        documents.forEach { document ->
            val nameSpaces = document.issuerSigned.getNameSpaces() ?: return@forEach
            nameSpaces.forEach { nameSpace ->
                val items = document.issuerSigned.getIssuerSignedItems(nameSpace.toString()) ?: return@forEach
                items.forEach { item ->
                    val key = "$nameSpace.${item.elementIdentifier}"
                    claims[key] = unwrapMdocElementValue(item.elementValue)
                }
            }
        }
        return claims
    }

    /**
     * Render a CBOR-decoded mdoc element value into a serialization-friendly value consistent
     * with how SD-JWT claims land in the map. The codec already unwraps each CBOR item to its
     * underlying Kotlin value, so native primitives pass through; richer structures (nested
     * maps/arrays, byte strings, tagged dates) are rendered via toString().
     */
    private fun unwrapMdocElementValue(value: Any?): Any? =
        when (value) {
            null -> null
            is String, is Boolean, is Long, is Int, is Double, is Float -> value
            else -> value.toString()
        }

    private companion object {
        val JSON_LENIENT =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
    }
}
