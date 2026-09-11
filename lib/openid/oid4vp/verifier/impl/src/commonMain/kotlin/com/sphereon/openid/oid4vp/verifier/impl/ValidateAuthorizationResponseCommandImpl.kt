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
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.events.EventCategories
import com.sphereon.core.api.events.EventSubsystems
import com.sphereon.core.api.events.EventTypes
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.events.SessionEventService
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.jose.jws.command.VerifyJwsCommand
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolutionPolicy
import com.sphereon.crypto.resolution.IdentifierMethodDefaults
import com.sphereon.di.session.SessionScope
import com.sphereon.jsonld.JsonLdError
import com.sphereon.jsonld.WellKnownContexts
import com.sphereon.jsonld.WellKnownCredentialTypes
import com.sphereon.jsonld.command.JsonLdContextValidator
import com.sphereon.jsonld.command.JsonLdSchemaValidator
import com.sphereon.jsonld.command.ValidateJsonLdContextInput
import com.sphereon.jsonld.command.ValidateJsonLdSchemaInput
import com.sphereon.mdoc.data.device.DeviceResponseCborCodec
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodec
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.CredentialFormat
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.common.clientMetadata
import com.sphereon.openid.oid4vp.common.responseUri
import com.sphereon.openid.oid4vc.common.vcdm.VcdmClassifier
import com.sphereon.openid.oid4vc.common.vcdm.VcdmClassification
import com.sphereon.openid.oid4vc.common.vcdm.VcdmDocumentKind
import com.sphereon.openid.oid4vc.common.vcdm.VcdmProfiles
import com.sphereon.openid.oid4vc.common.vcdm.VcdmUris
import com.sphereon.openid.oid4vc.common.vcdm.VcdmVersion
import com.sphereon.openid.oid4vc.common.PresentationFormat
import com.sphereon.openid.oid4vp.verifier.CredentialIssuerRef
import com.sphereon.openid.oid4vp.verifier.CredentialTrustValidation
import com.sphereon.openid.oid4vp.verifier.CredentialTrustValidationMode
import com.sphereon.openid.oid4vp.verifier.TrustedAuthenticationResolution
import com.sphereon.openid.oid4vp.verifier.MatchedCredential
import com.sphereon.openid.oid4vp.verifier.VerifiedCredentialEvidence
import com.sphereon.openid.oid4vp.verifier.VerifiedCredentialStatus
import com.sphereon.openid.oid4vp.verifier.VerifiedCredentialStatusOutcome
import com.sphereon.openid.oid4vp.verifier.Oid4vpCredentialTrustValidationArgs
import com.sphereon.openid.oid4vp.verifier.Oid4vpCredentialTrustValidator
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseCommandService
import com.sphereon.openid.oid4vp.verifier.ValidationResult
import com.sphereon.openid.oid4vp.verifier.VcdmDataIntegrityVerificationArgs
import com.sphereon.openid.oid4vp.verifier.VcdmDataIntegrityVerifier
import com.sphereon.openid.oid4vp.verifier.VerifyHolderBindingArgs
import com.sphereon.openid.oid4vp.verifier.VerifyHolderBindingCommand
import com.sphereon.openid.oid4vp.verifier.impl.event.emitOid4vpSessionHistoryEvent
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSession
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import com.sphereon.sdjwt.SdJwtCodec
import com.sphereon.statuslist.CredentialStatusDecision
import com.sphereon.statuslist.CredentialStatusEvaluation
import com.sphereon.statuslist.CredentialStatusInput
import com.sphereon.statuslist.CredentialStatusMetadata
import com.sphereon.statuslist.CredentialStatusPolicy
import com.sphereon.statuslist.CredentialStatusReference
import com.sphereon.statuslist.MdocCredentialStatusMetadata
import com.sphereon.statuslist.describeStatus
import com.sphereon.statuslist.evaluateCredentialStatus
import com.sphereon.statuslist.spi.CredentialStatusVerifier
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.Instant

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
    private val verifyJwsCommand: VerifyJwsCommand,
    private val jsonLdContextValidator: JsonLdContextValidator,
    private val jsonLdSchemaValidator: JsonLdSchemaValidator,
    private val deviceResponseCborCodec: DeviceResponseCborCodec,
    private val mobileSecurityObjectCborCodec: MobileSecurityObjectCborCodec,
    private val vcdmDataIntegrityVerifier: VcdmDataIntegrityVerifier,
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

    private suspend fun validateOnePresentation(
        queryId: String,
        presentationIndex: Int,
        presentationElement: JsonElement,
        matchingQuery: DcqlCredentialQuery,
        processedArgs: ValidateAuthorizationResponseArgs,
        bindingRequest: AuthorizationRequest,
        bindingNonce: String,
        effectiveVerifierId: String?,
        effectiveDcqlQueryId: String?,
        effectiveTemplateId: String?,
        statusPolicies: Map<String, CredentialStatusPolicy>,
        persistedJwtIssuerAlgorithms: Set<String>?,
        errors: MutableList<String>,
        matchedCredentials: MutableList<MatchedCredential>,
    ) {
        // OID4VP ldp_vc values are JSON objects. The value can be either a credential or
        // a holder-bound presentation, so classify the VCDM document itself before
        // selecting assertionMethod versus authentication proof semantics before
        // trust/status or DCQL result processing; never route an object through a
        // compact-JWT holder-binding path.
        if (presentationElement is JsonObject) {
            if (matchingQuery.format != LDP_VC_FORMAT) {
                errors.add("JSON-object presentation for query '$queryId' requires ldp_vc format")
                return
            }
            val classifiedDocument = VcdmClassifier.classifyDocument(presentationElement)
            if (classifiedDocument.isErr) {
                errors.add(
                    "VCDM document classification failed for query '$queryId' at index $presentationIndex: " +
                        classifiedDocument.error.message.defaultMessage,
                )
                return
            }
            val documentKind = classifiedDocument.value.kind
            if (documentKind == VcdmDocumentKind.CREDENTIAL && matchingQuery.require_cryptographic_holder_binding) {
                errors.add(
                    "Presentation for query '$queryId' at index $presentationIndex rejected: " +
                        "a bare ldp_vc credential cannot satisfy cryptographic holder binding; return a holder-bound VerifiablePresentation",
                )
                return
            }
            val declaredDataIntegrityController = dataIntegrityController(presentationElement, documentKind)
            val diArgs =
                VcdmDataIntegrityVerificationArgs(
                    document = presentationElement,
                    expectedProofPurpose = if (documentKind == VcdmDocumentKind.CREDENTIAL) {
                        ProofPurpose.ASSERTION_METHOD
                    } else {
                        ProofPurpose.AUTHENTICATION
                    },
                    expectedController = declaredDataIntegrityController,
                    expectedDomain = if (documentKind == VcdmDocumentKind.PRESENTATION) bindingRequest.clientId else null,
                    expectedChallenge = if (documentKind == VcdmDocumentKind.PRESENTATION) bindingNonce else null,
                    requireDomainAndChallenge = documentKind == VcdmDocumentKind.PRESENTATION,
                    verificationMethodResolutionPolicy = processedArgs.verificationMethodResolutionPolicy,
                )
            val diResult = try {
                vcdmDataIntegrityVerifier.verify(diArgs)
            } catch (expected: Exception) {
                errors.add(
                    "Data Integrity verification failed for query '$queryId' at index $presentationIndex: " +
                        (expected.message ?: "unexpected verifier error"),
                )
                return
            }
            if (diResult.isErr) {
                errors.add(
                    "Data Integrity verification failed for query '$queryId' at index $presentationIndex: " +
                        diResult.error.message.defaultMessage,
                )
                return
            }
            // VCDM 2.0 permits omitting `holder`; in that case acceptance must use the
            // controller authenticated by the successful Data Integrity verification and
            // must reject an absent or ambiguous presenter identity. VCDM 1.1 retains
            // its existing holder/controller behavior.
            if (documentKind == VcdmDocumentKind.PRESENTATION &&
                classifiedDocument.value.version == VcdmVersion.V2_0 &&
                declaredDataIntegrityController == null &&
                diResult.value.authenticatedControllers.isEmpty()
            ) {
                errors.add(
                    "Data Integrity VCDM 2.0 presentation without holder requires an authenticated controller",
                )
                return
            }
            val verifiedDocument = diResult.value.verifiedDocument
            val renderedPresentation = JSON_LENIENT.encodeToString(JsonElement.serializer(), presentationElement)
            if (documentKind == VcdmDocumentKind.PRESENTATION) {
                val childFailure = verifyDataIntegrityPresentationChildren(
                    document = verifiedDocument,
                    queryId = queryId,
                    expectedDomain = bindingRequest.clientId,
                    expectedChallenge = bindingNonce,
                    expectedAudience = bindingRequest.clientId,
                    verifierEncryptionJwkThumbprint = processedArgs.verifierEncryptionJwkThumbprint,
                    verifierId = effectiveVerifierId,
                    dcqlQueryId = effectiveDcqlQueryId,
                    templateId = effectiveTemplateId,
                    statusPolicy = statusPolicies[queryId] ?: CredentialStatusPolicy(),
                    trustedAuthentications = processedArgs.trustedAuthentications,
                    verificationMethodResolutionPolicy = processedArgs.verificationMethodResolutionPolicy,
                    issuerAlgAllowlist = persistedJwtIssuerAlgorithms,
                )
                if (childFailure != null) {
                    errors.add(
                        "Data Integrity presentation for query '$queryId' at index $presentationIndex rejected at " +
                            "the nested credential verification boundary: $childFailure",
                    )
                    return
                }
                // A holder-authenticated ldp_vc presentation is not an issuer credential. Every nested
                // VC has already had its own proof, trust and status checks applied above.
                matchedCredentials.add(
                    MatchedCredential(
                        credentialQueryId = queryId,
                        credentialFormat = CredentialFormat.LDP_VC,
                        presentationFormat = PresentationFormat.LDP_VP,
                        presentation = renderedPresentation,
                        disclosedClaims = extractDataIntegrityClaims(verifiedDocument),
                        issuer = null,
                        trust = null,
                    ),
                )
                return
            }
            val objectIssuer = extractDataIntegrityIssuer(verifiedDocument)
            val objectTrust = validateCredentialTrustForFormat(
                verifierId = effectiveVerifierId,
                dcqlQueryId = effectiveDcqlQueryId,
                templateId = effectiveTemplateId,
                credentialQueryId = queryId,
                presentation = renderedPresentation,
                format = LDP_VC_FORMAT,
                issuer = objectIssuer,
            )
            if (objectTrust.enabled && objectTrust.mode == CredentialTrustValidationMode.DEFAULT_ENFORCE && objectTrust.trusted != true) {
                errors.add(
                    "Credential trust validation failed for query '$queryId' at index $presentationIndex: " +
                        (objectTrust.details ?: objectTrust.status ?: "issuer is not trusted"),
                )
                return
            }
            val statusPolicy = statusPolicies[queryId] ?: CredentialStatusPolicy()
            var statusEvaluation = CredentialStatusEvaluation(CredentialStatusDecision.SKIPPED)
            if (credentialStatusVerifiers.isNotEmpty()) {
                val evaluation = evaluateCredentialStatus(credentialStatusVerifiers, verifiedDocument, statusPolicy)
                statusEvaluation = evaluation
                if (evaluation.decision == CredentialStatusDecision.REJECT) {
                    val rejectedStatus = evaluation.rejectedStatus
                    errors.add(if (rejectedStatus != null) "$queryId is ${describeStatus(rejectedStatus)}" else "$queryId could not be validated")
                    emitStatusRejected(queryId, evaluation)
                    return
                }
            }
            matchedCredentials.add(
                MatchedCredential(
                    credentialQueryId = queryId,
                    credentialFormat = CredentialFormat.LDP_VC,
                    presentation = renderedPresentation,
                    disclosedClaims = extractDataIntegrityClaims(verifiedDocument),
                    issuer = objectIssuer,
                    trust = objectTrust.takeIf { it.enabled || it.trustDomainIds.isNotEmpty() },
                    verificationEvidence = verificationEvidence(renderedPresentation, objectIssuer, objectTrust, statusPolicy, statusEvaluation, verifiedCredentialTemporalFacts(verifiedDocument, vcdm = true)),
                ),
            )
            return
        }
        if (presentationElement !is JsonPrimitive || !presentationElement.isString) {
            errors.add(
                "Presentation for query '$queryId' at index $presentationIndex must be a string or JSON object",
            )
            return
        }
        val presentation = presentationElement.content
        // Compact-JWS inputs are classified exactly once here. The classification's
        // document kind is authoritative for routing: a compact credential must not
        // enter JWT VP nonce/audience holder-binding logic.
        val compactClassificationResult =
            if (!presentation.contains('~') && presentation.contains('.')) {
                VcdmClassifier.classifyCompactJws(presentation)
            } else {
                null
            }
        val compactClassification = compactClassificationResult?.getOrNull()
        val detectedCredentialFormat =
            when {
                compactClassification?.document?.kind == VcdmDocumentKind.CREDENTIAL -> compactClassification.credentialFormat
                compactClassification?.document?.kind == VcdmDocumentKind.PRESENTATION -> null
                presentation.contains('.') && !presentation.contains('~') -> null
                presentation.contains('~') ->
                    CredentialFormat.SD_JWT_VC.takeIf {
                        presentation.substringBefore('~').count { character -> character == '.' } == 2
                    }
                presentation.length > MDOC_MIN_ENCODED_LENGTH -> CredentialFormat.MSO_MDOC
                else -> null
            }

        if (detectedCredentialFormat == null && compactClassification?.document?.kind != VcdmDocumentKind.PRESENTATION) {
            val classificationFailure =
                compactClassificationResult?.let { result ->
                    if (result.isErr) result.error.message.defaultMessage else null
                }
            errors.add(
                "Could not determine format of presentation for query '$queryId' at index $presentationIndex" +
                    (classificationFailure?.let { ": $it" } ?: ""),
            )
            return
        }

        // Validate the required Credential Format Identifier.
        //
        // A compact VCDM 2.0 credential is classified directly as
        // `jwt_vc_json-ld`; the JSON-LD shape validator below still
        // enforces the requested schema and context.
        val queryFormat = matchingQuery.format
        val requestedCredentialFormat = CredentialFormat.fromValueLenient(queryFormat)
        val formatMatches = if (compactClassification?.document?.kind == VcdmDocumentKind.PRESENTATION) {
            requestedCredentialFormat != null
        } else if (compactClassification != null) {
            requestedCredentialFormat == compactClassification.credentialFormat
        } else {
            requestedCredentialFormat == detectedCredentialFormat
        }

        if (!formatMatches) {
            errors.add("Credential format '$queryFormat' for query '$queryId' does not match the submitted credential")
            return
        }

        // JWT VP algorithm negotiation is pinned to the authorization request that was
        // persisted for this session. Never accept an algorithm merely because a caller
        // supplied it in a response-side structure: the signed VP must use one of the
        // verifier's original `vp_formats_supported[format].alg_values` values.
        if (
            compactClassification?.document?.kind == VcdmDocumentKind.PRESENTATION &&
            compactClassification.presentationFormat?.isJwt == true
        ) {
            val acceptedMetadataFormat = requestedCredentialFormat?.value ?: queryFormat
            val acceptedAlgorithms = bindingRequest.clientMetadata
                ?.vpFormatsSupported
                ?.get(acceptedMetadataFormat)
                ?.algValuesSupported
            val emittedAlgorithm = (compactClassification.protectedHeader["alg"] as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
            if (acceptedAlgorithms.isNullOrEmpty()) {
                errors.add(
                    "JWT VP for query '$queryId' rejected: persisted authorization request " +
                        "does not advertise non-empty alg_values for $acceptedMetadataFormat",
                )
                return
            }
            if (emittedAlgorithm == null || emittedAlgorithm !in acceptedAlgorithms) {
                errors.add(
                    "JWT VP for query '$queryId' rejected: protected alg '$emittedAlgorithm' " +
                        "is not in the persisted verifier allowlist for $acceptedMetadataFormat: $acceptedAlgorithms",
                )
                return
            }
        }

        // VCDM credentials and presentations are distinct documents. Appendix B requires
        // a secured VP (with nonce/audience binding) when holder binding is requested;
        // a bare VC never enters JWT-VP holder-binding logic. Issuer authenticity and
        // VC semantics are still mandatory when holder binding is disabled.
        val isClassifiedVcdmCredential =
            compactClassification?.document?.kind == VcdmDocumentKind.CREDENTIAL
        var issuer: CredentialIssuerRef? = null
        var trust: CredentialTrustValidation? = null
        if (isClassifiedVcdmCredential) {
            if (matchingQuery.require_cryptographic_holder_binding) {
                errors.add(
                    "Presentation for query '$queryId' at index $presentationIndex rejected: " +
                        "VCDM credential requires a bound Verifiable Presentation when " +
                        "require_cryptographic_holder_binding is true",
                )
                return
            }

            issuer = extractCredentialIssuer(presentation, requireNotNull(detectedCredentialFormat))
            // Cryptographic issuer verification is the first security callback. Trust
            // validators must not receive attacker-controlled claims until the JWS has
            // verified successfully; this also keeps status lookups behind authenticity.
            val credentialFailure =
                verifyVcdmCredential(
                    presentation,
                    compactClassification,
                    issuer,
                    processedArgs.trustedAuthentications,
                    persistedJwtIssuerAlgorithms,
                )
            if (credentialFailure != null) {
                errors.add(
                    "Presentation for query '$queryId' at index $presentationIndex rejected: " +
                        credentialFailure,
                )
                return
            }
        }

        if (compactClassification?.document?.kind == VcdmDocumentKind.PRESENTATION || compactClassification == null) {
            val bindingArgs =
                VerifyHolderBindingArgs(
                    presentation = presentation,
                    credentialFormat = if (compactClassification?.document?.kind == VcdmDocumentKind.PRESENTATION) null else detectedCredentialFormat,
                    presentationFormat = compactClassification?.presentationFormat,
                    expectedNonce = bindingNonce,
                    expectedAudience = bindingRequest.clientId,
                    requireCryptographicHolderBinding = matchingQuery.require_cryptographic_holder_binding,
                    trustedAuthentications = processedArgs.trustedAuthentications,
                    clientId = bindingRequest.clientId,
                    responseUri = bindingRequest.responseUri,
                    expectedMdocDocumentType =
                        (matchingQuery.meta["doctype_value"] as? JsonPrimitive)
                            ?.takeIf { it.isString }
                            ?.content,
                    verifierEncryptionJwkThumbprint = processedArgs.verifierEncryptionJwkThumbprint,
                    mdocDocumentResponseDecryptionKey = processedArgs.mdocDocumentResponseDecryptionKey,
                    mdocDocumentResponseEncryptionParameters = processedArgs.mdocDocumentResponseEncryptionParameters,
                    mdocDocumentResponseEncryptionProviders = processedArgs.mdocDocumentResponseEncryptionProviders,
                    iso18013MdocGeneratedNonce = processedArgs.iso18013MdocGeneratedNonce,
                )
            val bindingResultOrError = verifyHolderBindingCommand.execute(bindingArgs)
            if (bindingResultOrError.isErr) {
                errors.add(
                    "Holder binding verification command failed for query '$queryId' " +
                        "at index $presentationIndex: ${bindingResultOrError.error.message.defaultMessage}",
                )
                return
            }
            val bindingResult = bindingResultOrError.value
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
                return
            }
        }

        // A VCDM VP may contain multiple VC/EnvelopedVC entries. J4 recursively verifies
        // every child; outer holder proof never substitutes for child issuer proofs. The
        // returned credential-format set is authoritative for DCQL matching and is only
        // available after holder binding and child authentication have succeeded.
        if (compactClassification?.document?.kind == VcdmDocumentKind.PRESENTATION) {
            val recursiveResult =
                verifyVcdmPresentationChildren(
                    artifact = presentation,
                    classification = compactClassification,
                    queryId = queryId,
                    expectedNonce = bindingNonce,
                    expectedAudience = bindingRequest.clientId,
                    verifierEncryptionJwkThumbprint = processedArgs.verifierEncryptionJwkThumbprint,
                    verifierId = effectiveVerifierId,
                    dcqlQueryId = effectiveDcqlQueryId,
                    templateId = effectiveTemplateId,
                    statusPolicy = statusPolicies[queryId] ?: CredentialStatusPolicy(),
                    trustedAuthentications = processedArgs.trustedAuthentications,
                    verificationMethodResolutionPolicy = processedArgs.verificationMethodResolutionPolicy,
                    issuerAlgAllowlist = persistedJwtIssuerAlgorithms,
                )
            if (recursiveResult.error != null) {
                errors.add(
                    "VCDM presentation for query '$queryId' at index $presentationIndex rejected at " +
                        "the J4 recursive verification boundary: ${recursiveResult.error}",
                )
                return
            }
            val matchedCredentialFormat = requestedCredentialFormat
                ?: run {
                    errors.add("Credential format '$queryFormat' is not supported for query '$queryId'")
                    return
                }
            if (matchedCredentialFormat !in recursiveResult.credentialFormats) {
                errors.add("Credential format '$queryFormat' for query '$queryId' does not match any verified presentation child")
                return
            }
        }

        // VCDM 2.0 + JSON-LD: enforce the same `@context` and JSON
        // Schema validators that the issuer side runs. Only runs
        // when the verifier asked for `jwt_vc_json-ld`; SD-JWT,
        // mdoc, and VCDM 1.1 jwt_vc_json presentations skip the
        // JWT-body decode entirely.
        if (queryFormat == CredentialFormat.JWT_VC_JSON_LD.value) {
            val vcLdViolation = validateVcLdJsonShape(presentation)
            if (vcLdViolation != null) {
                errors.add(
                    "VCDM 2.0 + JSON-LD validation failed for query '$queryId' at index $presentationIndex: " +
                        vcLdViolation,
                )
                return
            }
        }

        // The outer VCDM VP is a holder-authenticated presentation, not a credential
        // issued by its `iss`/holder. Its child VCs have already gone through issuer
        // trust, authenticity, semantics, and status checks above. Do not re-run those
        // credential checks against the VP holder or expose that holder as the issuer of
        // an inner credential.
        if (compactClassification?.document?.kind == VcdmDocumentKind.PRESENTATION) {
            val matchedCredentialFormat = requestedCredentialFormat
                ?: run {
                    errors.add("Credential format '$queryFormat' is not supported for query '$queryId'")
                    return
                }
            matchedCredentials.add(
                MatchedCredential(
                    credentialQueryId = queryId,
                    credentialFormat = matchedCredentialFormat,
                    presentationFormat = compactClassification.presentationFormat,
                    presentation = presentation,
                    disclosedClaims = extractDisclosedClaims(
                        presentation,
                        matchedCredentialFormat,
                    ),
                    issuer = null,
                    trust = null,
                ),
            )
            return
        }

        val resolvedIssuer = issuer ?: extractCredentialIssuer(presentation, requireNotNull(detectedCredentialFormat))
        val resolvedTrust =
            trust
                ?: if (detectedCredentialFormat == CredentialFormat.MSO_MDOC) {
                    validateMdocTrustForAllDocuments(
                        verifierId = effectiveVerifierId,
                        dcqlQueryId = effectiveDcqlQueryId,
                        templateId = effectiveTemplateId,
                        credentialQueryId = queryId,
                        presentation = presentation,
                        firstIssuer = resolvedIssuer,
                    )
                } else {
                    validateCredentialTrust(
                        verifierId = effectiveVerifierId,
                        dcqlQueryId = effectiveDcqlQueryId,
                        templateId = effectiveTemplateId,
                        credentialQueryId = queryId,
                        presentation = presentation,
                        detectedFormat = requireNotNull(detectedCredentialFormat),
                        issuer = resolvedIssuer,
                    )
                }
        if (resolvedTrust.enabled && resolvedTrust.mode == CredentialTrustValidationMode.DEFAULT_ENFORCE && resolvedTrust.trusted != true) {
            errors.add(
                "Credential trust validation failed for query '$queryId' at index $presentationIndex: " +
                    (resolvedTrust.details ?: resolvedTrust.status ?: "issuer is not trusted by the resolved trust domains"),
            )
            log.warn("Credential '$queryId' rejected by trust-domain validation: ${resolvedTrust.details ?: resolvedTrust.status}")
            return
        }

        // Credential status (revocation/suspension) check. Runs only when status verifiers are
        // wired; otherwise skipped entirely. `extractDisclosedClaims` strips the `status` claim,
        // so we read the full credential payload separately. A REJECT discards the presentation
        // per OID4VP §10, the same as a holder-binding failure.
        val statusInput = credentialStatusInputFor(presentation, requireNotNull(detectedCredentialFormat))
        val statusPolicy = statusPolicies[queryId] ?: CredentialStatusPolicy()
        var statusEvaluation = CredentialStatusEvaluation(CredentialStatusDecision.SKIPPED)
        if (credentialStatusVerifiers.isEmpty()) {
            if (requireNotNull(detectedCredentialFormat) == CredentialFormat.MSO_MDOC &&
                statusInput.hasMdocStatusReference()
            ) {
                errors.add("$queryId could not be validated: mso_mdoc status verifier is not configured")
                log.warn("Credential '$queryId' carries an mso_mdoc status reference but no status verifier is configured")
                return
            }
        } else {
            val evaluation = evaluateCredentialStatus(credentialStatusVerifiers, statusInput ?: CredentialStatusInput(), statusPolicy)
            statusEvaluation = evaluation
            if (evaluation.decision == CredentialStatusDecision.REJECT) {
                // User-facing: just the credential and its state ("EuPid is revoked"). The status
                // list URI / index are operator diagnostics — logged and emitted as an event, not
                // returned to the end user.
                val word = evaluation.rejectedStatus?.let { describeStatus(it) }
                errors.add(if (word != null) "$queryId is $word" else "$queryId could not be validated")
                log.warn("Credential '$queryId' rejected by status check: ${evaluation.reason}")
                emitStatusRejected(queryId, evaluation)
                return
            }
        }

        // Extract disclosed claims from the presentation
        val disclosedClaims = extractDisclosedClaims(presentation, requireNotNull(detectedCredentialFormat))

        matchedCredentials.add(
            MatchedCredential(
                credentialQueryId = queryId,
                credentialFormat = requireNotNull(detectedCredentialFormat),
                presentationFormat = null,
                presentation = presentation,
                disclosedClaims = disclosedClaims,
                issuer = resolvedIssuer,
                trust = resolvedTrust.takeIf { it.enabled || it.trustDomainIds.isNotEmpty() },
                verificationEvidence = verificationEvidence(presentation, resolvedIssuer, resolvedTrust, statusPolicy, statusEvaluation, verifiedCredentialTemporalFacts(statusInput?.claims?.takeIf { detectedCredentialFormat != CredentialFormat.MSO_MDOC }, vcdm = detectedCredentialFormat == CredentialFormat.JWT_VC_JSON || detectedCredentialFormat == CredentialFormat.JWT_VC_JSON_LD)),
            ),
        )
    }

    private fun verificationEvidence(
        presentation: String,
        issuer: CredentialIssuerRef?,
        trust: CredentialTrustValidation,
        policy: CredentialStatusPolicy,
        evaluation: CredentialStatusEvaluation,
        temporalFacts: com.sphereon.openid.oid4vp.verifier.VerifiedCredentialTemporalFacts?,
    ) = VerifiedCredentialEvidence(
        presentationSha256 = hash(presentation.encodeToByteArray(), DigestAlg.SHA256).encodeToBase64Url(),
        verifiedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        issuer = issuer,
        trust = trust,
        temporalFacts = temporalFacts,
        status = VerifiedCredentialStatus(
            evaluation = VerifiedCredentialStatusOutcome.valueOf(evaluation.decision.name),
            resolvedValues = evaluation.resolved.map { it.value },
            required = policy.requireStatus,
            rejectOnUnresolvable = policy.rejectOnUnresolvable,
        ),
    )

    private suspend fun doExecuteInternal(
        args: ValidateAuthorizationResponseArgs,
        applyDuring: (ValidateAuthorizationResponseArgs) -> ValidateAuthorizationResponseArgs,
    ): IdkResult<ValidationResult, IdkError> {
        val processedArgs = applyDuring(args)

        log.debug("Validating authorization response against DCQL query")

        val parsedResponse = processedArgs.parsedResponse
        val originalRequest = processedArgs.originalRequest
        val callerExpectedNonce = processedArgs.expectedNonce

        val errors = mutableListOf<String>()
        val matchedCredentials = mutableListOf<MatchedCredential>()
        // Query IDs the wallet actually submitted a presentation for. A required credential that WAS
        // submitted but then discarded (status rejection, holder-binding failure, ...) must not also be
        // reported as "not found" — the specific discard reason is already in `errors`.
        val submittedQueryIds = mutableSetOf<String>()

        // `doExecute` has already loaded and retained the session before entering this method.
        // Use that exact persisted object as the authority for algorithm negotiation; a
        // response-side request/caller structure must never be able to replace it.
        val authorizationSession =
            requireNotNull(pendingHistorySession) {
                "OID4VP validation has no persisted authorization session"
            }
        // The persisted authorization session is the verifier-owned DCQL authority. A response
        // caller must not replace the requested mdoc document type (or any other query contract)
        // by supplying a different query alongside the response.
        val dcqlQuery = authorizationSession.dcqlQuery
        val effectiveVerifierId = processedArgs.verifierId ?: authorizationSession.verifierId
        val effectiveDcqlQueryId = processedArgs.dcqlQueryId ?: authorizationSession.dcqlQueryId
        val effectiveTemplateId = processedArgs.templateId ?: authorizationSession.templateId
        val persistedRequest = authorizationSession.authorizationRequest
        val bindingRequest = persistedRequest
        val bindingNonce = persistedRequest.nonce ?: callerExpectedNonce
        if (persistedRequest.clientId != originalRequest.clientId) {
            errors.add("Authorization request client_id does not match the persisted session")
        }
        if (persistedRequest.nonce != null && persistedRequest.nonce != callerExpectedNonce) {
            errors.add("Authorization request nonce does not match the persisted session")
        }

        // Per-DCQL-query credential status policies, pinned on the session at create time. Loaded only
        // to drive status acceptance; absence (no session / no map) falls back to the strict default
        // per query. An authenticated mso_mdoc status reference is never silently accepted merely
        // because a deployment forgot to contribute the mdoc status verifier.
        val statusPolicies: Map<String, CredentialStatusPolicy> =
            if (credentialStatusVerifiers.isEmpty()) {
                emptyMap()
            } else {
                authorizationSession.credentialStatusPolicies ?: emptyMap()
            }

        // Validate state parameter
        if (originalRequest.state != null && parsedResponse.state != originalRequest.state) {
            errors.add("State mismatch: expected '${originalRequest.state}', got '${parsedResponse.state}'")
        }

        // Validate each presentation in the VP token (DCQL format: Map of queryId -> presentations).
        //
        // OID4VP §8.1: each Presentation value is Credential-Format dependent — a JSON string
        // for compact formats (dc+sd-jwt, jwt_vc_json, mso_mdoc) or a JSON object for the W3C
        // Data Integrity format (ldp_vc). We branch on the element shape and NEVER
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
            // The persisted verifier metadata is the sole issuer-JWS algorithm authority. Thread
            // this per-query value through every VCDM child recursion boundary, including VCDM 2
            // nested presentations and envelopes.
            val persistedJwtIssuerAlgorithms = bindingRequest.clientMetadata
                ?.vpFormatsSupported
                ?.get(matchingQuery.format)
                ?.algValuesSupported
                ?.toSet()

            // Validate each presentation for this query ID
            for ((presentationIndex, presentationElement) in presentationElements.withIndex()) {
                validateOnePresentation(
                    queryId = queryId,
                    presentationIndex = presentationIndex,
                    presentationElement = presentationElement,
                    matchingQuery = matchingQuery,
                    processedArgs = processedArgs,
                    bindingRequest = bindingRequest,
                    bindingNonce = bindingNonce ?: processedArgs.expectedNonce,
                    effectiveVerifierId = effectiveVerifierId,
                    effectiveDcqlQueryId = effectiveDcqlQueryId,
                    effectiveTemplateId = effectiveTemplateId,
                    statusPolicies = statusPolicies,
                    persistedJwtIssuerAlgorithms = persistedJwtIssuerAlgorithms,
                    errors = errors,
                    matchedCredentials = matchedCredentials,
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

    /**
     * Verify and evaluate every secured child in an ldp_vc VerifiablePresentation. The Data
     * Integrity adapter verifies one document at a time; this format-aware boundary dispatches
     * direct DI children and JOSE envelopes, then applies issuer trust and status independently.
     * A VP holder is deliberately never used as a child VC issuer.
     */
    private suspend fun verifyDataIntegrityPresentationChildren(
        document: JsonObject,
        queryId: String,
        expectedDomain: String?,
        expectedChallenge: String?,
        expectedAudience: String,
        verifierEncryptionJwkThumbprint: ByteArray?,
        verifierId: String?,
        dcqlQueryId: String?,
        templateId: String?,
        statusPolicy: CredentialStatusPolicy,
        trustedAuthentications: List<TrustedAuthenticationResolution>,
        verificationMethodResolutionPolicy: VerificationMethodResolutionPolicy,
        issuerAlgAllowlist: Set<String>?,
        depth: Int = 0,
        state: VcdmRecursionState = VcdmRecursionState(),
    ): String? {
        if (depth > VCDM_MAX_RECURSION_DEPTH) return "maximum nested Data Integrity presentation depth exceeded"
        if (depth == 0 && state.decodedBytes == 0L) {
            state.decodedBytes = document.toString().encodeToByteArray().size.toLong()
            if (state.decodedBytes > VCDM_MAX_DECODED_BYTES) return "maximum VCDM decoded byte size exceeded"
        }
        val parentClassification = VcdmClassifier.classifyDocument(document)
            .getOrElse { return "VCDM Data Integrity presentation classification failed: ${it.message.defaultMessage}" }
        if (parentClassification.kind != VcdmDocumentKind.PRESENTATION) {
            return "VCDM Data Integrity child container is not a VerifiablePresentation"
        }
        val childrenElement = dataIntegrityChildren(document)
            ?: return "VCDM Data Integrity presentation has no verifiableCredential entries"
        val children = when (childrenElement) {
            is JsonObject -> listOf(childrenElement)
            is JsonArray -> childrenElement
            is JsonPrimitive -> {
                if (parentClassification.version == VcdmVersion.V1_1 && childrenElement.isString) {
                    listOf(childrenElement)
                } else {
                    return "VCDM Data Integrity verifiableCredential has an invalid scalar child"
                }
            }
            else -> return "VCDM Data Integrity verifiableCredential must contain secured children"
        }
        if (children.isEmpty()) return "VCDM Data Integrity presentation has an empty verifiableCredential collection"

        for ((index, child) in children.withIndex()) {
            if (parentClassification.version == VcdmVersion.V1_1) {
                val compactFailure =
                    verifyVcdm11Child(
                        child = child,
                        queryId = queryId,
                        verifierId = verifierId,
                        dcqlQueryId = dcqlQueryId,
                        templateId = templateId,
                        statusPolicy = statusPolicy,
                        trustedAuthentications = trustedAuthentications,
                        verificationMethodResolutionPolicy = verificationMethodResolutionPolicy,
                        issuerAlgAllowlist = issuerAlgAllowlist,
                        state = state,
                    )
                if (compactFailure != null) return "verifiableCredential child[$index] rejected: $compactFailure"
                continue
            }
            val childObject = child as? JsonObject
                ?: return "verifiableCredential child[$index] is not a JSON object"
            val childTypes = strictStringOrStringArray(childObject["type"])
            if (parentClassification.version == VcdmVersion.V2_0 && childTypes?.any { it in VCDM2_ENVELOPED_TYPES } == true) {
                val envelopeFailure =
                    verifyVcdm2EnvelopedChild(
                        child = childObject,
                        queryId = queryId,
                        expectedNonce = expectedChallenge,
                        expectedAudience = expectedAudience,
                        verifierEncryptionJwkThumbprint = verifierEncryptionJwkThumbprint,
                        verifierId = verifierId,
                        dcqlQueryId = dcqlQueryId,
                        templateId = templateId,
                        statusPolicy = statusPolicy,
                        trustedAuthentications = trustedAuthentications,
                        verificationMethodResolutionPolicy = verificationMethodResolutionPolicy,
                        issuerAlgAllowlist = issuerAlgAllowlist,
                        depth = depth,
                        state = state,
                    )
                if (envelopeFailure != null) return "verifiableCredential child[$index] rejected: $envelopeFailure"
                continue
            }
            if (state.nodes >= VCDM_MAX_RECURSION_NODES) return "maximum nested Data Integrity presentation node count exceeded"
            val childArtifact = childObject.toString()
            if (!state.artifacts.add(childArtifact)) return "verifiableCredential child[$index] is duplicate or cyclic"
            state.decodedBytes += childArtifact.encodeToByteArray().size
            if (state.decodedBytes > VCDM_MAX_DECODED_BYTES) return "maximum VCDM decoded byte size exceeded"
            state.nodes++
            val childClassification = VcdmClassifier.classifyDocument(childObject)
                .getOrElse { return "verifiableCredential child[$index] VCDM classification failed: ${it.message.defaultMessage}" }
            val childKind = childClassification.kind
            if (
                childKind == VcdmDocumentKind.PRESENTATION &&
                (parentClassification.version != VcdmVersion.V2_0 || childClassification.version != VcdmVersion.V2_0)
            ) {
                return "verifiableCredential child[$index] nested presentations require VCDM 2.0 at both levels"
            }
            val childController = dataIntegrityController(childObject, childKind)
            val childResult = try {
                vcdmDataIntegrityVerifier.verify(
                    VcdmDataIntegrityVerificationArgs(
                        document = childObject,
                        expectedProofPurpose = if (childKind == VcdmDocumentKind.CREDENTIAL) {
                            ProofPurpose.ASSERTION_METHOD
                        } else {
                            ProofPurpose.AUTHENTICATION
                        },
                        expectedController = childController,
                        expectedDomain = if (childKind == VcdmDocumentKind.PRESENTATION) expectedDomain else null,
                        expectedChallenge = if (childKind == VcdmDocumentKind.PRESENTATION) expectedChallenge else null,
                        requireDomainAndChallenge = childKind == VcdmDocumentKind.PRESENTATION,
                        verificationMethodResolutionPolicy = verificationMethodResolutionPolicy,
                    ),
                )
            } catch (expected: Exception) {
                return "verifiableCredential child[$index] Data Integrity verification failed: ${expected.message ?: "unexpected verifier error"}"
            }
            if (childResult.isErr) return "verifiableCredential child[$index] Data Integrity verification failed: ${childResult.error.message.defaultMessage}"
            if (childKind == VcdmDocumentKind.PRESENTATION &&
                childClassification.version == VcdmVersion.V2_0 &&
                childController == null &&
                childResult.value.authenticatedControllers.isEmpty()
            ) {
                return "verifiableCredential child[$index] holderless VCDM 2.0 presentation requires an authenticated controller"
            }
            val verifiedChild = childResult.value.verifiedDocument
            if (childKind == VcdmDocumentKind.PRESENTATION) {
                val nestedFailure = verifyDataIntegrityPresentationChildren(
                    document = verifiedChild,
                    queryId = queryId,
                    expectedDomain = expectedDomain,
                    expectedChallenge = expectedChallenge,
                    expectedAudience = expectedAudience,
                    verifierEncryptionJwkThumbprint = verifierEncryptionJwkThumbprint,
                    verifierId = verifierId,
                    dcqlQueryId = dcqlQueryId,
                    templateId = templateId,
                    statusPolicy = statusPolicy,
                    trustedAuthentications = trustedAuthentications,
                    verificationMethodResolutionPolicy = verificationMethodResolutionPolicy,
                    issuerAlgAllowlist = issuerAlgAllowlist,
                    depth = depth + 1,
                    state = state,
                )
                if (nestedFailure != null) return "verifiableCredential child[$index] rejected: $nestedFailure"
            } else {
                val issuer = extractDataIntegrityIssuer(childObject) ?: extractDataIntegrityIssuer(verifiedChild)
                val trust = validateCredentialTrustForFormat(
                    verifierId = verifierId,
                    dcqlQueryId = dcqlQueryId,
                    templateId = templateId,
                    credentialQueryId = queryId,
                    presentation = JSON_LENIENT.encodeToString(JsonElement.serializer(), childObject),
                    format = LDP_VC_FORMAT,
                    issuer = issuer,
                )
                if (trust.enabled && trust.mode == CredentialTrustValidationMode.DEFAULT_ENFORCE && trust.trusted != true) {
                    return "verifiableCredential child[$index] trust validation failed: ${trust.details ?: trust.status ?: "issuer is not trusted"}"
                }
                if (credentialStatusVerifiers.isNotEmpty()) {
                    val evaluation = evaluateCredentialStatus(credentialStatusVerifiers, verifiedChild, statusPolicy)
                    if (evaluation.decision == CredentialStatusDecision.REJECT) {
                        emitStatusRejected(queryId, evaluation)
                        return "verifiableCredential child[$index] status rejected"
                    }
                }
            }
        }
        return null
    }

    private fun dataIntegrityChildren(document: JsonObject): JsonElement? {
        return document["verifiableCredential"]
    }

    private fun dataIntegrityController(document: JsonObject, kind: VcdmDocumentKind): String? {
        val field = if (kind == VcdmDocumentKind.CREDENTIAL) "issuer" else "holder"
        return identifier(document[field])
    }

    private fun identifier(element: JsonElement?): String? = when (element) {
        is JsonPrimitive -> element.takeIf { it.isString }?.content
        is JsonObject -> (element["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        else -> null
    }

    /**
     * Verify issuer authenticity and the cross-envelope constraints for a compact VCDM VC.
     *
     * A bare VC is allowed only when the DCQL query explicitly disables cryptographic holder
     * binding. That switch affects the holder proof, never issuer authenticity: the compact JWS
     * is always verified through the normal identifier/trust resolution path.
     */
    private suspend fun verifyVcdmCredential(
        presentation: String,
        classification: com.sphereon.openid.oid4vc.common.vcdm.VcdmClassification?,
        issuer: CredentialIssuerRef?,
        trustedAuthentications: List<TrustedAuthenticationResolution>,
        issuerAlgAllowlist: Set<String>?,
    ): String? {
        val resolvedClassification = classification ?: return "VCDM credential classification is missing"
        val matchingTrustedAuthentications = trustedAuthentications.filter { it.controller == issuer?.issuer }
        if (matchingTrustedAuthentications.size > 1) {
            return "multiple configured issuer-authentication sources match the credential issuer"
        }
        val configuredIssuerAuthentication = matchingTrustedAuthentications.singleOrNull()
        val header = decodeJwtHeaderOrNull(presentation) ?: return "VCDM credential JWT header is malformed"
        val alg = header.stringClaim("alg")
        if (alg == null || alg.equals("none", ignoreCase = true)) {
            return "VCDM credential issuer JWS algorithm is missing or alg:none"
        }
        val credentialFormat = resolvedClassification.credentialFormat
        if (credentialFormat?.isJwt == true &&
            (issuerAlgAllowlist.isNullOrEmpty() || alg !in issuerAlgAllowlist)
        ) {
            return "VCDM credential issuer JWS alg '$alg' is not in the persisted verifier allowlist " +
                "for ${credentialFormat.value}: ${issuerAlgAllowlist ?: emptySet<String>()}"
        }
        val typ = header.stringClaim("typ")
        val cty = header.stringClaim("cty")
        val headerIssuer = header.stringClaim("iss")
        if (header.containsKey("typ") && typ == null) return "VCDM credential JOSE typ header is malformed"
        if (header.containsKey("cty") && cty == null) return "VCDM credential JOSE cty header is malformed"
        if (header.containsKey("iss") && headerIssuer == null) return "VCDM credential JOSE iss header is malformed"
        if (headerIssuer != null && headerIssuer != issuer?.issuer) {
            return "JOSE header iss must agree with the VCDM credential issuer"
        }
        if (resolvedClassification.document.version == VcdmVersion.V1_1 && typ != null && !typ.equals("JWT", ignoreCase = true)) {
            return "VCDM 1.1 jwt_vc_json typ must be JWT"
        }
        if (resolvedClassification.document.version == VcdmVersion.V2_0 &&
            typ != null &&
            typ !in setOf("vc+jwt", "application/vc+jwt")
        ) {
            return "VCDM 2.0 jwt_vc_json-ld typ must be vc+jwt or application/vc+jwt when present"
        }
        if (resolvedClassification.document.version == VcdmVersion.V2_0 &&
            cty !in setOf(null, "vc", "application/vc")
        ) {
            return "VCDM 2.0 jwt_vc_json-ld cty must identify a verifiable credential"
        }
        if (header.containsKey("jwk")) {
            // A JWK carried by the protected header is attacker-controlled. It is not an issuer
            // trust anchor and must never be accepted as the credential issuer key.
            return "VCDM credential issuer JWS contains an untrusted embedded jwk"
        }
        if (header.containsKey("jku") || header.containsKey("x5u")) {
            return "VCDM credential issuer JWS contains an untrusted remote key URL"
        }
        val kid = header.stringClaim("kid")
        if (header.containsKey("kid") && kid == null) return "VCDM credential JOSE kid header is malformed"
        val x5cElement = header["x5c"]
        if (x5cElement != null &&
            (x5cElement !is JsonArray || x5cElement.any { it !is JsonPrimitive || !it.isString || it.content.isBlank() })
        ) {
            return "VCDM credential issuer x5c header is malformed"
        }
        val x5c = header.stringArrayClaim("x5c").orEmpty()
        if (x5c.isNotEmpty() && configuredIssuerAuthentication?.identifier?.method != IdentifierMethodDefaults.X5C) {
            return "VCDM credential issuer x5c requires verifier-configured X.509 issuer authentication"
        }
        if (x5c.isNotEmpty() && kid != null) {
            return "VCDM credential issuer x5c cannot be combined with kid"
        }
        if (x5c.isNotEmpty()) {
            val configuredChain =
                (configuredIssuerAuthentication?.identifier?.identifier as? List<*>)
                    ?.mapNotNull { it as? String }
            if (configuredChain == null || x5c != configuredChain) {
                return "VCDM credential issuer x5c does not match the verifier-configured X.509 chain"
            }
        }
        val issuerId = issuer?.issuer
        val issuerDid = issuerId?.extractDid()
        if (issuerDid == null && configuredIssuerAuthentication == null) {
            return "non-DID VCDM credential issuer requires verifier-configured issuer authentication"
        }
        val kidDid = kid?.extractDid()
        if (issuerDid == null && kidDid != null) {
            return "VCDM credential issuer DID kid does not match the JWT iss issuer"
        }
        val trustedJwks =
            when {
                x5c.isNotEmpty() -> {
                    // The configured X.509 source is passed below. Never derive a trusted key
                    // set from the presented header itself.
                    null
                }

                issuerDid != null -> {
                    if (kid == null || kidDid != issuerDid || !kid.startsWith("$issuerDid#")) {
                        return "VCDM credential issuer kid must be an absolute DID URL for the JWT iss DID"
                    }
                    null
                }

                else -> {
                    // A non-DID kid (including a JWK thumbprint) is resolved by the configured
                    // identifier-aware JWS verifier. Do not impose a DID-only assumption here;
                    // managed keys, JWKS, X.509 and federation are external resolver concerns.
                    null
                }
            }
        val verification =
            try {
                // The configured source is authoritative. In particular, an x5c header is only
                // accepted above when it exactly matches the admitted X.509 source; it is never
                // converted into token-controlled trust material here.
                verifyJwsCommand.execute(
                    VerifyJwsArgs(
                        jws = JwsCompact(presentation),
                        identifier = configuredIssuerAuthentication?.identifier,
                        // A configured source is authoritative. In particular, never fall back to
                        // a token-supplied x5c-derived key when the verifier has admitted a peer
                        // source for this issuer.
                        trustedJwks = configuredIssuerAuthentication?.trustedJwks
                            ?: trustedJwks.takeUnless { configuredIssuerAuthentication != null },
                    ),
                )
            } catch (expected: Exception) {
                return "VCDM credential issuer JWS verification failed: ${expected.message ?: "unexpected verifier error"}"
            }
        if (verification.isErr) {
            return "VCDM credential issuer JWS verification failed: ${verification.error.message.defaultMessage}"
        }
        if (!verification.value.isValid || verification.value.cryptoVerified != true) {
            val detail = verification.value.errorMessages.takeIf { it.isNotEmpty() }?.joinToString("; ")
            return "VCDM credential issuer JWS signature result is invalid${detail?.let { ": $it" } ?: ""}"
        }
        if (verification.value.trustEstablished != true) {
            val detail = verification.value.errorMessages.takeIf { it.isNotEmpty() }?.joinToString("; ")
            return "VCDM credential issuer JWS trust result is invalid${detail?.let { ": $it" } ?: ""}"
        }

        val payload = decodeJwtPayloadOrNull(presentation) ?: return "VCDM credential JWT payload is malformed"
        return validateVcdmCredentialSemantics(payload, resolvedClassification)
    }

    /** J4: recursively verify every compact child of a secured VCDM presentation. */
    private suspend fun verifyVcdmPresentationChildren(
        artifact: String,
        classification: VcdmClassification,
        queryId: String,
        expectedNonce: String?,
        expectedAudience: String,
        verifierEncryptionJwkThumbprint: ByteArray?,
        verifierId: String?,
        dcqlQueryId: String?,
        templateId: String?,
        statusPolicy: CredentialStatusPolicy,
        trustedAuthentications: List<TrustedAuthenticationResolution>,
        verificationMethodResolutionPolicy: VerificationMethodResolutionPolicy,
        issuerAlgAllowlist: Set<String>?,
    ): VcdmVerifiedChildren {
        val state = VcdmRecursionState()
        state.artifacts += artifact
        state.decodedBytes = artifact.encodeToByteArray().size.toLong()
        if (state.decodedBytes > VCDM_MAX_DECODED_BYTES) {
            return VcdmVerifiedChildren(emptySet(), "maximum VCDM decoded byte size exceeded")
        }
        val error = verifyVcdmPresentationChildrenRecursive(
            classification, queryId, expectedNonce, expectedAudience, verifierEncryptionJwkThumbprint,
            verifierId, dcqlQueryId, templateId, statusPolicy, trustedAuthentications,
            verificationMethodResolutionPolicy, issuerAlgAllowlist, depth = 0, state = state,
        )
        return VcdmVerifiedChildren(state.credentialFormats, error)
    }

    private suspend fun verifyVcdmPresentationChildrenRecursive(
        classification: VcdmClassification,
        queryId: String,
        expectedNonce: String?,
        expectedAudience: String,
        verifierEncryptionJwkThumbprint: ByteArray?,
        verifierId: String?,
        dcqlQueryId: String?,
        templateId: String?,
        statusPolicy: CredentialStatusPolicy,
        trustedAuthentications: List<TrustedAuthenticationResolution>,
        verificationMethodResolutionPolicy: VerificationMethodResolutionPolicy,
        issuerAlgAllowlist: Set<String>?,
        depth: Int,
        state: VcdmRecursionState,
    ): String? {
        if (depth > VCDM_MAX_RECURSION_DEPTH) return "maximum nested presentation depth exceeded"
        val childElement = classification.document.json["verifiableCredential"]
            ?: return "no verifiableCredential entry"
        if (childElement is JsonNull) return "verifiableCredential entry is null"
        val children: List<JsonElement> =
            when (classification.document.version) {
                VcdmVersion.V1_1 -> when (childElement) {
                    is JsonPrimitive -> listOf(childElement)
                    is JsonArray -> childElement
                    else -> return "malformed VCDM 1.1 verifiableCredential representation"
                }
                VcdmVersion.V2_0 -> when (childElement) {
                    is JsonArray -> childElement
                    is JsonObject -> listOf(childElement)
                    is JsonPrimitive -> return "raw JWT child is forbidden in a VCDM 2.0 presentation"
                    else -> return "malformed VCDM 2.0 verifiableCredential representation"
                }
                else -> return "unsupported VCDM presentation version"
            }
        if (children.isEmpty()) return "empty verifiableCredential collection"
        for ((index, child) in children.withIndex()) {
            if (state.nodes >= VCDM_MAX_RECURSION_NODES) return "maximum nested presentation node count exceeded"
            val failure =
                if (classification.document.version == VcdmVersion.V1_1) {
                    verifyVcdm11Child(
                        child, queryId, verifierId, dcqlQueryId, templateId, statusPolicy, trustedAuthentications,
                        verificationMethodResolutionPolicy, issuerAlgAllowlist, state,
                    )
                } else {
                    verifyVcdm2EnvelopedChild(
                        child = child,
                        queryId = queryId,
                        expectedNonce = expectedNonce,
                        expectedAudience = expectedAudience,
                        verifierEncryptionJwkThumbprint = verifierEncryptionJwkThumbprint,
                        verifierId = verifierId,
                        dcqlQueryId = dcqlQueryId,
                        templateId = templateId,
                        statusPolicy = statusPolicy,
                        trustedAuthentications = trustedAuthentications,
                        verificationMethodResolutionPolicy = verificationMethodResolutionPolicy,
                        issuerAlgAllowlist = issuerAlgAllowlist,
                        depth = depth,
                        state = state,
                    )
                }
            if (failure != null) return "verifiableCredential[$index]: $failure"
        }
        return null
    }

    private suspend fun verifyVcdm11Child(
        child: JsonElement,
        queryId: String,
        verifierId: String?,
        dcqlQueryId: String?,
        templateId: String?,
        statusPolicy: CredentialStatusPolicy,
        trustedAuthentications: List<TrustedAuthenticationResolution>,
        verificationMethodResolutionPolicy: VerificationMethodResolutionPolicy,
        issuerAlgAllowlist: Set<String>?,
        state: VcdmRecursionState,
    ): String? {
        if (child is JsonObject) {
            return verifyVcdm11DataIntegrityChild(
                child = child,
                queryId = queryId,
                verifierId = verifierId,
                dcqlQueryId = dcqlQueryId,
                templateId = templateId,
                statusPolicy = statusPolicy,
                verificationMethodResolutionPolicy = verificationMethodResolutionPolicy,
                issuerAlgAllowlist = issuerAlgAllowlist,
                state = state,
            )
        }
        val compact = (child as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return "VCDM 1.1 child must be a secured compact jwt_vc_json string or secured JSON credential object"
        if (compact.isBlank() || !state.artifacts.add(compact)) return "duplicate or cyclic child artifact"
        state.decodedBytes += compact.encodeToByteArray().size
        if (state.decodedBytes > VCDM_MAX_DECODED_BYTES) return "maximum VCDM decoded byte size exceeded"
        state.nodes++
        val childClassification = VcdmClassifier.classifyCompactJws(compact).getOrNull()
            ?: return "malformed or unsecured VCDM 1.1 child credential"
        if (childClassification.document.version != VcdmVersion.V1_1 ||
            childClassification.document.kind != VcdmDocumentKind.CREDENTIAL
        ) return "VCDM 1.1 presentation child must be a jwt_vc_json credential"
        val failure = verifyVcdmChildCredential(
            compact,
            childClassification,
            queryId,
            verifierId,
            dcqlQueryId,
            templateId,
            statusPolicy,
            trustedAuthentications,
            issuerAlgAllowlist,
        )
        if (failure == null) state.credentialFormats += requireNotNull(childClassification.credentialFormat)
        return failure
    }

    private suspend fun verifyVcdm11DataIntegrityChild(
        child: JsonObject,
        queryId: String,
        verifierId: String?,
        dcqlQueryId: String?,
        templateId: String?,
        statusPolicy: CredentialStatusPolicy,
        verificationMethodResolutionPolicy: VerificationMethodResolutionPolicy,
        issuerAlgAllowlist: Set<String>?,
        state: VcdmRecursionState,
    ): String? {
        if (!state.artifacts.add(child.toString())) return "duplicate or cyclic child artifact"
        state.decodedBytes += child.toString().encodeToByteArray().size
        if (state.decodedBytes > VCDM_MAX_DECODED_BYTES) return "maximum VCDM decoded byte size exceeded"
        state.nodes++
        val classification = VcdmClassifier.classifyDocument(child).getOrNull()
            ?: return "malformed or unsecured VCDM 1.1 JSON child credential"
        if (classification.version != VcdmVersion.V1_1 || classification.kind != VcdmDocumentKind.CREDENTIAL) {
            return "VCDM 1.1 presentation child must be a VCDM 1.1 credential"
        }
        val controller = dataIntegrityController(child, VcdmDocumentKind.CREDENTIAL)
        val verification = try {
            vcdmDataIntegrityVerifier.verify(
                VcdmDataIntegrityVerificationArgs(
                    document = child,
                    expectedProofPurpose = ProofPurpose.ASSERTION_METHOD,
                    expectedController = controller,
                    verificationMethodResolutionPolicy = verificationMethodResolutionPolicy,
                ),
            )
        } catch (expected: Exception) {
            return "VCDM 1.1 JSON child Data Integrity verification failed: ${expected.message ?: "unexpected verifier error"}"
        }
        if (verification.isErr) return "VCDM 1.1 JSON child Data Integrity verification failed: ${verification.error.message.defaultMessage}"
        val verifiedDocument = verification.value.verifiedDocument
        validateVerifiedVcdmCredentialProfile(verifiedDocument, VcdmVersion.V1_1)?.let { return it }
        val issuer = extractDataIntegrityIssuer(child) ?: extractDataIntegrityIssuer(verifiedDocument)
        val trust = validateCredentialTrustForFormat(
            verifierId = verifierId,
            dcqlQueryId = dcqlQueryId,
            templateId = templateId,
            credentialQueryId = queryId,
            presentation = child.toString(),
            format = LDP_VC_FORMAT,
            issuer = issuer,
        )
        if (trust.enabled && trust.mode == CredentialTrustValidationMode.DEFAULT_ENFORCE && trust.trusted != true) {
            return "VCDM 1.1 JSON child trust validation failed: ${trust.details ?: trust.status ?: "issuer is not trusted"}"
        }
        if (credentialStatusVerifiers.isNotEmpty()) {
            val evaluation = evaluateCredentialStatus(credentialStatusVerifiers, verifiedDocument, statusPolicy)
            if (evaluation.decision == CredentialStatusDecision.REJECT) {
                emitStatusRejected(queryId, evaluation)
                return "VCDM 1.1 JSON child status rejected"
            }
        }
        state.credentialFormats += CredentialFormat.LDP_VC
        return null
    }

    private suspend fun verifyVcdm2EnvelopedChild(
        child: JsonElement,
        queryId: String,
        expectedNonce: String?,
        expectedAudience: String,
        verifierEncryptionJwkThumbprint: ByteArray?,
        verifierId: String?,
        dcqlQueryId: String?,
        templateId: String?,
        statusPolicy: CredentialStatusPolicy,
        trustedAuthentications: List<TrustedAuthenticationResolution>,
        verificationMethodResolutionPolicy: VerificationMethodResolutionPolicy,
        issuerAlgAllowlist: Set<String>?,
        depth: Int,
        state: VcdmRecursionState,
    ): String? {
        val envelope = child as? JsonObject
            ?: return "VCDM 2.0 child must be an EnvelopedVerifiableCredential or EnvelopedVerifiablePresentation object"
        val declaredTypes = strictStringOrStringArray(envelope["type"])
        if (declaredTypes?.none { it in VCDM2_ENVELOPED_TYPES } == true) {
            return verifyDirectDataIntegrityChild(
                child = envelope,
                queryId = queryId,
                expectedNonce = expectedNonce,
                expectedAudience = expectedAudience,
                verifierEncryptionJwkThumbprint = verifierEncryptionJwkThumbprint,
                verifierId = verifierId,
                dcqlQueryId = dcqlQueryId,
                templateId = templateId,
                statusPolicy = statusPolicy,
                trustedAuthentications = trustedAuthentications,
                verificationMethodResolutionPolicy = verificationMethodResolutionPolicy,
                issuerAlgAllowlist = issuerAlgAllowlist,
                depth = depth,
                state = state,
            )
        }
        val context = strictStringOrStringArray(envelope["@context"])
            ?: return "malformed VCDM 2.0 enveloped child @context"
        if (context.firstOrNull() != VcdmProfiles.V2_0_CONTEXT) {
            return "VCDM 2.0 enveloped child has an incorrect base context"
        }
        val type = strictStringOrStringArray(envelope["type"])
            ?: return "malformed VCDM 2.0 enveloped child type"
        if (type.size != 1 || type[0] !in VCDM2_ENVELOPED_TYPES) return "unsupported VCDM 2.0 enveloped child type"
        val id = (envelope["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            ?: return "malformed VCDM 2.0 enveloped child id"
        val prefix = if (type[0] == VCDM2_ENVELOPED_VC_TYPE) VCDM2_VC_DATA_URI_PREFIX else VCDM2_VP_DATA_URI_PREFIX
        if (!id.startsWith(prefix)) return "VCDM 2.0 enveloped child id has an incorrect media type"
        val compact = id.removePrefix(prefix).takeIf { it.isNotBlank() }
            ?: return "empty VCDM 2.0 enveloped child artifact"
        if (!state.artifacts.add(id) || !state.artifacts.add(compact)) return "duplicate or cyclic child artifact"
        state.decodedBytes += compact.encodeToByteArray().size
        if (state.decodedBytes > VCDM_MAX_DECODED_BYTES) return "maximum VCDM decoded byte size exceeded"
        state.nodes++
        val childClassification = VcdmClassifier.classifyCompactJws(compact).getOrNull()
            ?: return "malformed or unsecured VCDM 2.0 enveloped child"
        if (childClassification.document.version != VcdmVersion.V2_0) return "VCDM 2.0 enveloped child has the wrong VCDM version"
        if (type[0] == VCDM2_ENVELOPED_VC_TYPE) {
            if (childClassification.document.kind != VcdmDocumentKind.CREDENTIAL) {
                return "EnvelopedVerifiableCredential must contain a VCDM credential"
            }
            val failure = verifyVcdmChildCredential(
                compact,
                childClassification,
                queryId,
                verifierId,
                dcqlQueryId,
                templateId,
                statusPolicy,
                trustedAuthentications,
                issuerAlgAllowlist,
            )
            if (failure == null) state.credentialFormats += requireNotNull(childClassification.credentialFormat)
            return failure
        }
        if (childClassification.document.kind != VcdmDocumentKind.PRESENTATION) return "EnvelopedVerifiablePresentation must contain a VCDM presentation"
        return verifyNestedVcdmPresentation(
            compact, childClassification, queryId, expectedNonce, expectedAudience, verifierEncryptionJwkThumbprint,
            verifierId, dcqlQueryId, templateId, statusPolicy, trustedAuthentications,
            verificationMethodResolutionPolicy, issuerAlgAllowlist, depth + 1, state,
        )
    }

    private suspend fun verifyDirectDataIntegrityChild(
        child: JsonObject,
        queryId: String,
        expectedNonce: String?,
        expectedAudience: String,
        verifierEncryptionJwkThumbprint: ByteArray?,
        verifierId: String?,
        dcqlQueryId: String?,
        templateId: String?,
        statusPolicy: CredentialStatusPolicy,
        trustedAuthentications: List<TrustedAuthenticationResolution>,
        verificationMethodResolutionPolicy: VerificationMethodResolutionPolicy,
        issuerAlgAllowlist: Set<String>?,
        depth: Int,
        state: VcdmRecursionState,
    ): String? {
        val artifact = child.toString()
        if (!state.artifacts.add(artifact)) return "duplicate or cyclic direct Data Integrity child"
        state.decodedBytes += artifact.encodeToByteArray().size
        if (state.decodedBytes > VCDM_MAX_DECODED_BYTES) return "maximum VCDM decoded byte size exceeded"
        if (state.nodes >= VCDM_MAX_RECURSION_NODES) return "maximum nested presentation node count exceeded"
        state.nodes++
        val classification = VcdmClassifier.classifyDocument(child)
            .getOrElse { return "direct Data Integrity child classification failed: ${it.message.defaultMessage}" }
        if (classification.version != VcdmVersion.V2_0) return "direct Data Integrity child has the wrong VCDM version"
        val controller = dataIntegrityController(child, classification.kind)
        val verification =
            try {
                vcdmDataIntegrityVerifier.verify(
                    VcdmDataIntegrityVerificationArgs(
                        document = child,
                        expectedProofPurpose = if (classification.kind == VcdmDocumentKind.CREDENTIAL) {
                            ProofPurpose.ASSERTION_METHOD
                        } else {
                            ProofPurpose.AUTHENTICATION
                        },
                        expectedController = controller,
                        expectedDomain = if (classification.kind == VcdmDocumentKind.PRESENTATION) expectedAudience else null,
                        expectedChallenge = if (classification.kind == VcdmDocumentKind.PRESENTATION) expectedNonce else null,
                        requireDomainAndChallenge = classification.kind == VcdmDocumentKind.PRESENTATION,
                        verificationMethodResolutionPolicy = verificationMethodResolutionPolicy,
                    ),
                )
            } catch (expected: Exception) {
                return "direct Data Integrity child verification failed: ${expected.message ?: "unexpected verifier error"}"
            }
        if (verification.isErr) {
            return "direct Data Integrity child verification failed: ${verification.error.message.defaultMessage}"
        }
        if (classification.kind == VcdmDocumentKind.CREDENTIAL) {
            validateVerifiedVcdmCredentialProfile(
                verification.value.verifiedDocument,
                VcdmVersion.V2_0,
            )?.let { return it }
        }
        if (classification.kind == VcdmDocumentKind.PRESENTATION &&
            classification.version == VcdmVersion.V2_0 &&
            controller == null &&
            verification.value.authenticatedControllers.isEmpty()
        ) {
            return "direct Data Integrity holderless VCDM 2.0 presentation requires an authenticated controller"
        }
        val verifiedChild = verification.value.verifiedDocument
        if (classification.kind == VcdmDocumentKind.PRESENTATION) {
            return verifyDataIntegrityPresentationChildren(
                document = verifiedChild,
                queryId = queryId,
                expectedDomain = expectedAudience,
                expectedChallenge = expectedNonce,
                expectedAudience = expectedAudience,
                verifierEncryptionJwkThumbprint = verifierEncryptionJwkThumbprint,
                verifierId = verifierId,
                dcqlQueryId = dcqlQueryId,
                templateId = templateId,
                statusPolicy = statusPolicy,
                trustedAuthentications = trustedAuthentications,
                verificationMethodResolutionPolicy = verificationMethodResolutionPolicy,
                issuerAlgAllowlist = issuerAlgAllowlist,
                depth = depth + 1,
                state = state,
            )
        }

        val issuer = extractDataIntegrityIssuer(child) ?: extractDataIntegrityIssuer(verifiedChild)
        val trust = validateCredentialTrustForFormat(
            verifierId = verifierId,
            dcqlQueryId = dcqlQueryId,
            templateId = templateId,
            credentialQueryId = queryId,
            presentation = JSON_LENIENT.encodeToString(JsonElement.serializer(), child),
            format = LDP_VC_FORMAT,
            issuer = issuer,
        )
        if (trust.enabled && trust.mode == CredentialTrustValidationMode.DEFAULT_ENFORCE && trust.trusted != true) {
            return "direct Data Integrity child trust validation failed: ${trust.details ?: trust.status ?: "issuer is not trusted"}"
        }
        if (credentialStatusVerifiers.isNotEmpty()) {
            val evaluation = evaluateCredentialStatus(credentialStatusVerifiers, verifiedChild, statusPolicy)
            if (evaluation.decision == CredentialStatusDecision.REJECT) {
                emitStatusRejected(queryId, evaluation)
                return "direct Data Integrity child status rejected"
            }
        }
        if (classification.kind == VcdmDocumentKind.CREDENTIAL) {
            state.credentialFormats += CredentialFormat.LDP_VC
        }
        return null
    }

    /**
     * Cryptographic success does not make an arbitrary JSON object a VC. Validate the document
     * returned by the DI verifier against the profile selected by its authenticated VCDM version
     * before trust/status evaluation or child-format accounting.
     */
    private fun validateVerifiedVcdmCredentialProfile(
        document: JsonObject,
        version: VcdmVersion,
    ): String? {
        val validation = when (version) {
            VcdmVersion.V1_1 -> VcdmProfiles.v1_1.validateCredential(document)
            VcdmVersion.V2_0 -> VcdmProfiles.v2_0.validateCredential(document)
            else -> return "unsupported VCDM version '${version.value}' for Data Integrity credential profile validation"
        }
        if (validation.valid) return null
        return "VCDM ${version.value} Data Integrity credential profile validation failed: " +
            validation.errors.joinToString("; ") { it.message.defaultMessage }
    }

    private suspend fun verifyNestedVcdmPresentation(
        compact: String,
        classification: VcdmClassification,
        queryId: String,
        expectedNonce: String?,
        expectedAudience: String,
        verifierEncryptionJwkThumbprint: ByteArray?,
        verifierId: String?,
        dcqlQueryId: String?,
        templateId: String?,
        statusPolicy: CredentialStatusPolicy,
        trustedAuthentications: List<TrustedAuthenticationResolution>,
        verificationMethodResolutionPolicy: VerificationMethodResolutionPolicy,
        issuerAlgAllowlist: Set<String>?,
        depth: Int,
        state: VcdmRecursionState,
    ): String? {
        if (depth > VCDM_MAX_RECURSION_DEPTH) return "maximum nested presentation depth exceeded"
        val resolvedExpectedNonce = expectedNonce ?: return "nested VCDM presentation expected nonce is missing"
        val holderBinding =
            try {
                verifyHolderBindingCommand.execute(
                    VerifyHolderBindingArgs(
                        presentation = compact,
                        presentationFormat = classification.presentationFormat,
                        expectedNonce = resolvedExpectedNonce,
                        expectedAudience = expectedAudience,
                        requireCryptographicHolderBinding = true,
                        trustedAuthentications = trustedAuthentications,
                        clientId = expectedAudience,
                        responseUri = null,
                        verifierEncryptionJwkThumbprint = verifierEncryptionJwkThumbprint,
                    ),
                )
            } catch (expected: Exception) {
                return "nested VCDM presentation holder verification failed: ${expected.message ?: "unexpected verifier error"}"
            }
        if (holderBinding.isErr) return "nested VCDM presentation holder verification failed: ${holderBinding.error.message.defaultMessage}"
        if (!holderBinding.value.verified) return "nested VCDM presentation nonce/audience binding failed"
        return verifyVcdmPresentationChildrenRecursive(
            classification, queryId, expectedNonce, expectedAudience, verifierEncryptionJwkThumbprint,
            verifierId, dcqlQueryId, templateId, statusPolicy, trustedAuthentications,
            verificationMethodResolutionPolicy, issuerAlgAllowlist, depth, state,
        )
    }

    private suspend fun verifyVcdmChildCredential(
        compact: String,
        classification: VcdmClassification,
        queryId: String,
        verifierId: String?,
        dcqlQueryId: String?,
        templateId: String?,
        statusPolicy: CredentialStatusPolicy,
        trustedAuthentications: List<TrustedAuthenticationResolution>,
        issuerAlgAllowlist: Set<String>?,
    ): String? {
        val format = requireNotNull(classification.credentialFormat)
        val issuer = extractCredentialIssuer(compact, format)
        // Verify the child issuer proof before invoking trust or status callbacks. The outer VP
        // holder is never reused as an issuer for this child.
        verifyVcdmCredential(compact, classification, issuer, trustedAuthentications, issuerAlgAllowlist)?.let { return "child credential rejected: $it" }
        val trust = validateCredentialTrust(
            verifierId, dcqlQueryId, templateId, queryId, compact, format, issuer,
        )
        if (trust.enabled && trust.mode == CredentialTrustValidationMode.DEFAULT_ENFORCE && trust.trusted != true) {
            return "child credential trust validation failed: ${trust.details ?: trust.status ?: "issuer is not trusted"}"
        }
        if (credentialStatusVerifiers.isNotEmpty()) {
            val input = credentialStatusInputFor(compact, format) ?: CredentialStatusInput()
            val evaluation = evaluateCredentialStatus(credentialStatusVerifiers, input, statusPolicy)
            if (evaluation.decision == CredentialStatusDecision.REJECT) {
                emitStatusRejected(queryId, evaluation)
                return "child credential status rejected"
            }
        }
        return null
    }

    /** VCDM permits both a single string and an array of strings for @context and type. */
    private fun strictStringOrStringArray(element: JsonElement?): List<String>? =
        when (element) {
            is JsonPrimitive -> element.takeIf { it.isString && it.content.isNotBlank() }?.let { listOf(it.content) }
            is JsonArray -> element.takeIf { it.isNotEmpty() }?.map { item ->
                (item as? JsonPrimitive)?.takeIf { it.isString && it.content.isNotBlank() }?.content ?: return null
            }
            else -> null
        }

    private data class VcdmVerifiedChildren(
        val credentialFormats: Set<CredentialFormat>,
        val error: String? = null,
    )

    private class VcdmRecursionState {
        val artifacts: MutableSet<String> = mutableSetOf()
        val credentialFormats: MutableSet<CredentialFormat> = linkedSetOf()
        var nodes: Int = 1
        var decodedBytes: Long = 0
    }

    private fun validateVcdmCredentialSemantics(
        payload: JsonObject,
        classification: com.sphereon.openid.oid4vc.common.vcdm.VcdmClassification,
    ): String? {
        val profile = if (classification.document.version == VcdmVersion.V1_1) VcdmProfiles.v1_1 else VcdmProfiles.v2_0
        val profileResult = profile.validateCredential(classification.document.json)
        if (!profileResult.valid) {
            return profileResult.errors.firstOrNull()?.message?.defaultMessage
                ?: "VCDM credential profile validation failed"
        }

        val vc = payload["vc"] as? JsonObject
        val issuerElement =
            if (classification.document.version == VcdmVersion.V1_1) {
                // In jwt_vc_json the issuer is represented by the registered JWT `iss` claim;
                // an optional vc.issuer must agree with it when present.
                vc?.get("issuer") ?: payload["iss"] ?: return "VCDM 1.1 credential issuer is required"
            } else {
                payload["issuer"] ?: return "VCDM 2.0 credential issuer is required"
            }
        val issuer = issuerIdentifier(issuerElement)
            ?: return "VCDM credential issuer must be a string or an object with a string id"
        if (!VcdmUris.isValid(issuer)) return "VCDM credential issuer must be a URI"

        val iss = payload["iss"]?.stringJsonClaim("iss")
        if (classification.document.version == VcdmVersion.V1_1 && iss == null) {
            return "VCDM 1.1 credential JWT iss claim is required and must be a string"
        }
        if (payload.containsKey("iss") && iss == null) return "JWT iss must be a string"
        if (iss != null && iss != issuer) return "JWT iss must agree with the VCDM credential issuer"

        val idElement = if (classification.document.version == VcdmVersion.V1_1) vc?.get("id") else payload["id"]
        val jti =
            if (payload.containsKey("jti")) {
                payload["jti"]?.stringJsonClaim("jti") ?: return "JWT jti must be a string"
            } else {
                null
            }
        val id = idElement?.stringJsonClaim("id")
        if (idElement != null && id == null) return "VCDM credential id must be a string"
        if (id != null && !VcdmUris.isValid(id)) return "VCDM credential id must be a URI"
        if (jti != null && id != null && jti != id) return "JWT jti must agree with the VCDM credential id"

        val subjectElement =
            if (classification.document.version == VcdmVersion.V1_1) vc?.get("credentialSubject")
            else payload["credentialSubject"]
        val subjectIds = validateCredentialSubjects(subjectElement)
            ?: return "VCDM credentialSubject is malformed"
        val sub = payload["sub"]?.stringJsonClaim("sub")
            ?: if (payload.containsKey("sub")) return "JWT sub must be a string" else null
        if (sub != null && (subjectIds.size != 1 || subjectIds.single() != sub)) {
            return "JWT sub must agree with one unambiguous credentialSubject.id"
        }

        val temporalFailure = validateCredentialTemporalClaims(payload, vc, classification.document.version)
        if (temporalFailure != null) return temporalFailure
        return null
    }

    private fun validateCredentialSubjects(
        element: JsonElement?,
    ): List<String>? {
        val subjects =
            when (element) {
                is JsonObject -> listOf(element)
                is JsonArray -> element.map { it as? JsonObject ?: return null }
                else -> return null
            }
        if (subjects.isEmpty()) return null
        val ids = mutableListOf<String>()
        for (subject in subjects) {
            val id = subject["id"]
            if (id != null) {
                val value = (id as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
                if (!VcdmUris.isValid(value)) return null
                ids += value
            }
        }
        return if (subjects.size == 1 && ids.size == 1) ids else emptyList()
    }

    private fun validateCredentialTemporalClaims(
        payload: JsonObject,
        vc: JsonObject?,
        version: VcdmVersion,
    ): String? {
        val body = if (version == VcdmVersion.V1_1) vc else payload
        val fromName = if (version == VcdmVersion.V1_1) "issuanceDate" else "validFrom"
        val untilName = if (version == VcdmVersion.V1_1) "expirationDate" else "validUntil"
        val from = body?.get(fromName)?.let { parseInstantClaim(it, fromName) }
        if (from is TemporalParseFailure) return from.message
        val until = body?.get(untilName)?.let { parseInstantClaim(it, untilName) }
        if (until is TemporalParseFailure) return until.message
        val fromInstant = (from as? TemporalInstant)?.value
        val untilInstant = (until as? TemporalInstant)?.value
        if (fromInstant != null && untilInstant != null && fromInstant > untilInstant) {
            return "$fromName must not be after $untilName"
        }
        val now = Clock.System.now()
        if (fromInstant != null && now < fromInstant) {
            // VCDM 2.0 explicitly permits a credential to become valid in the future.  This is
            // not a malformed document or failed proof; it is a consuming-layer effective-time
            // policy result. Keep the legacy wording for callers that key off it, while making
            // the distinction explicit in the returned verifier diagnostic.
            return if (version == VcdmVersion.V2_0) {
                "VCDM credential is not yet effective (not yet valid: $fromName is in the future); " +
                    "credential structure and issuer proof remain valid"
            } else {
                "VCDM credential is not yet valid ($fromName is in the future)"
            }
        }
        if (untilInstant != null && now > untilInstant) return "VCDM credential is expired ($untilName is in the past)"

        // Parse each optional claim independently. An absent claim must not return from this
        // function: doing so would skip validation of a later iat/exp claim (the usual VCDM 2
        // profile has iat/exp without nbf).
        val iat = if (payload.containsKey("iat")) {
            parseNumericClaim(payload["iat"], "iat")
                ?: return "JWT iat must be a finite JSON number"
        } else {
            null
        }
        val nbf = if (payload.containsKey("nbf")) {
            parseNumericClaim(payload["nbf"], "nbf")
                ?: return "JWT nbf must be a finite JSON number"
        } else {
            null
        }
        val exp = if (payload.containsKey("exp")) {
            parseNumericClaim(payload["exp"], "exp")
                ?: return "JWT exp must be a finite JSON number"
        } else {
            null
        }
        // RFC 7519 gives iat, nbf, and exp independent meanings. A credential can be signed
        // after its validity has already begun, so nbf earlier than iat is conforming.
        val nowSeconds = now.epochSeconds.toDouble()
        if (iat != null && iat > nowSeconds) return "VCDM credential is not yet valid (iat is in the future)"
        if (nbf != null && nbf > nowSeconds) return "VCDM credential is not yet valid (nbf is in the future)"
        if (exp != null && exp <= nowSeconds) return "VCDM credential is expired (exp is not in the future)"
        return null
    }

    private fun parseInstantClaim(element: JsonElement, name: String): TemporalClaim {
        val primitive = element as? JsonPrimitive
        if (primitive == null || !primitive.isString) return TemporalParseFailure("$name must be an ISO-8601 date-time string")
        return try {
            TemporalInstant(Instant.parse(primitive.content))
        } catch (_: IllegalArgumentException) {
            TemporalParseFailure("$name is not a valid ISO-8601 date-time")
        }
    }

    private fun parseNumericClaim(element: JsonElement?, name: String): Double? {
        if (element == null) return null
        val primitive = element as? JsonPrimitive ?: return null
        if (primitive.isString) return null
        return primitive.doubleOrNull?.takeIf { it.isFinite() }
    }

    private fun issuerIdentifier(element: JsonElement): String? =
        when (element) {
            is JsonPrimitive -> element.takeIf { it.isString }?.content
            is JsonObject -> (element["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            else -> null
        }

    private fun JsonElement.stringJsonClaim(name: String): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content

    private sealed interface TemporalClaim
    private data class TemporalInstant(val value: Instant) : TemporalClaim
    private data class TemporalParseFailure(val message: String) : TemporalClaim


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
     * resolved full payload; compact JWT-VC → the decoded JWT body; mdoc → a metadata-only status
     * reference extracted from the authenticated MSO (never a disclosed namespace claim).
     */
    private fun credentialStatusInputFor(
        presentation: String,
        format: CredentialFormat,
    ): CredentialStatusInput? =
        when (format) {
            CredentialFormat.SD_JWT_VC, CredentialFormat.W3C_VC_SD_JWT -> {
                SdJwtCodec
                    .parse(presentation)
                    .getOrNull()
                    ?.payload
                    ?.fullPayload
                    ?.let { CredentialStatusInput(claims = it) }
            }

            CredentialFormat.JWT_VC_JSON, CredentialFormat.JWT_VC_JSON_LD -> {
                decodeJwtPayloadOrNull(presentation)?.let { CredentialStatusInput(claims = it) }
            }

            CredentialFormat.MSO_MDOC -> {
                extractMdocStatusMetadata(presentation)?.let { metadata ->
                    CredentialStatusInput(metadata = metadata)
                }
            }

            else -> {
                null
            }
        }

    /**
     * Adapt the status reference carried by an authenticated MSO to the common status verifier
     * claim shape. This is deliberately metadata-only: MSO status never becomes an ordinary
     * disclosed namespace claim.
     */
    private fun extractMdocStatusMetadata(presentation: String): CredentialStatusMetadata? {
        val deviceResponseBytes = runCatching { presentation.decodeFromBase64Url() }.getOrNull() ?: return null
        val response = deviceResponseCborCodec.decode(deviceResponseBytes).getOrNull()?.value ?: return null
        val statuses = response.documents
            ?.asSequence()
            ?.mapNotNull { document ->
                val payload = document.issuerSigned.issuerAuth.payload?.value ?: return@mapNotNull null
                mobileSecurityObjectCborCodec.decode(payload).getOrNull()?.value?.status
            }
            ?.toList()
            .orEmpty()
        if (statuses.isEmpty()) return null
        val references = statuses.flatMap { status ->
            buildList {
                status.statusList?.let { statusList ->
                    add(
                        CredentialStatusReference(
                            mechanism = "mdoc_status",
                            uri = statusList.uri,
                            // `idx` is an unsigned ISO value. Do not let a value larger than
                            // Int.MAX_VALUE wrap into a valid-looking negative/low index.
                            index = if (statusList.idx <= Int.MAX_VALUE.toUInt()) statusList.idx.toInt() else -1,
                            certificate = statusList.certificate,
                        ),
                    )
                }
                status.identifierList?.let { identifierList ->
                    add(
                        CredentialStatusReference(
                            mechanism = "mdoc_status",
                            uri = identifierList.uri,
                            index = 0,
                            identifier = identifierList.id,
                            certificate = identifierList.certificate,
                        ),
                    )
                }
            }
        }
        return CredentialStatusMetadata(MdocCredentialStatusMetadata(references))
    }

    private fun CredentialStatusInput?.hasMdocStatusReference(): Boolean =
        this?.metadata?.mdoc?.references?.any { it.mechanism == "mdoc_status" } == true

    private suspend fun validateCredentialTrust(
        verifierId: String?,
        dcqlQueryId: String?,
        templateId: String?,
        credentialQueryId: String,
        presentation: String,
        detectedFormat: CredentialFormat,
        issuer: CredentialIssuerRef?,
    ): CredentialTrustValidation {
        return validateCredentialTrustForFormat(
            verifierId = verifierId,
            dcqlQueryId = dcqlQueryId,
            templateId = templateId,
            credentialQueryId = credentialQueryId,
            presentation = presentation,
            format = detectedFormat.value,
            issuer = issuer,
        )
    }

    /**
     * A DeviceResponse may carry more than one mdoc. The issuer metadata used for trust must be
     * checked for every clear document; validating only the first document would let a trusted
     * first mdoc hide an untrusted second issuer in the same response.
     */
    private suspend fun validateMdocTrustForAllDocuments(
        verifierId: String?,
        dcqlQueryId: String?,
        templateId: String?,
        credentialQueryId: String,
        presentation: String,
        firstIssuer: CredentialIssuerRef?,
    ): CredentialTrustValidation {
        val issuers = extractMdocIssuers(presentation)
        if (issuers.isEmpty()) {
            return validateCredentialTrustForFormat(
                verifierId = verifierId,
                dcqlQueryId = dcqlQueryId,
                templateId = templateId,
                credentialQueryId = credentialQueryId,
                presentation = presentation,
                format = CredentialFormat.MSO_MDOC.value,
                issuer = firstIssuer,
            )
        }
        var firstValidation: CredentialTrustValidation? = null
        for (issuer in issuers) {
            val validation = validateCredentialTrustForFormat(
                verifierId = verifierId,
                dcqlQueryId = dcqlQueryId,
                templateId = templateId,
                credentialQueryId = credentialQueryId,
                presentation = presentation,
                format = CredentialFormat.MSO_MDOC.value,
                issuer = issuer,
            )
            firstValidation = firstValidation ?: validation
            if (validation.enabled &&
                validation.mode == CredentialTrustValidationMode.DEFAULT_ENFORCE &&
                validation.trusted != true
            ) {
                return validation
            }
        }
        return requireNotNull(firstValidation)
    }

    private suspend fun validateCredentialTrustForFormat(
        verifierId: String?,
        dcqlQueryId: String?,
        templateId: String?,
        credentialQueryId: String,
        presentation: String,
        format: String,
        issuer: CredentialIssuerRef?,
    ): CredentialTrustValidation {
        val validationArgs =
            Oid4vpCredentialTrustValidationArgs(
                verifierId = verifierId,
                dcqlQueryId = dcqlQueryId,
                templateId = templateId,
                credentialQueryId = credentialQueryId,
                format = format,
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

            CredentialFormat.JWT_VC_JSON, CredentialFormat.JWT_VC_JSON_LD -> {
                extractJwtIssuer(presentation)
            }

            CredentialFormat.MSO_MDOC -> {
                extractMdocIssuer(presentation)
            }

            else -> {
                null
            }
        }

    private fun extractDataIntegrityIssuer(document: JsonObject): CredentialIssuerRef? {
        val issuerElement = document["issuer"]
            ?: (document["vc"] as? JsonObject)?.get("issuer")
        val issuer = when (val element = issuerElement) {
            is JsonPrimitive -> element.takeIf { it.isString }?.content
            is JsonObject -> (element["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            else -> null
        } ?: return null
        val did = issuer.extractDid()
        return CredentialIssuerRef(
            issuer = issuer,
            method = if (did != null) "did" else null,
            did = did,
            oidfedEntityId = issuer.takeIf { it.startsWith("https://") || it.startsWith("http://") },
        )
    }

    private fun extractDataIntegrityClaims(document: JsonObject): Map<String, Any?> {
        val subject = document["credentialSubject"] as? JsonObject ?: return emptyMap()
        return subject.mapValues { (_, value) ->
            when (value) {
                is JsonPrimitive -> value.booleanOrNull ?: value.longOrNull ?: value.doubleOrNull ?: value.contentOrNull
                else -> value.toString()
            }
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
        return extractMdocIssuers(presentation).firstOrNull()
    }

    private fun extractMdocIssuers(presentation: String): List<CredentialIssuerRef> {
        val deviceResponseBytes =
            try {
                presentation.decodeFromBase64Url()
            } catch (_: IllegalArgumentException) {
                return emptyList()
            }
        val deviceResponse =
            deviceResponseCborCodec.decode(deviceResponseBytes).getOrNull()?.value ?: return emptyList()
        return deviceResponse.documents.orEmpty().map { document ->
            val issuerAuth = document.issuerSigned.issuerAuth
            val x5chain = issuerAuth.protectedHeader.x5chain ?: issuerAuth.unprotectedHeader?.x5chain
            val x5c = x5chain?.value?.map { it.value.encodeTo(Encoding.BASE64) }.orEmpty()
            CredentialIssuerRef(
                issuer = document.docType.toString(),
                method = if (x5c.isNotEmpty()) "x509" else null,
                x5c = x5c,
                docType = document.docType.toString(),
            )
        }
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
        const val MDOC_MIN_ENCODED_LENGTH = 20
        const val VCDM_MAX_RECURSION_DEPTH = 4
        const val VCDM_MAX_RECURSION_NODES = 32
        const val VCDM_MAX_DECODED_BYTES = 1024L * 1024L
        const val VCDM2_ENVELOPED_VC_TYPE = "EnvelopedVerifiableCredential"
        const val VCDM2_ENVELOPED_VP_TYPE = "EnvelopedVerifiablePresentation"
        const val VCDM2_VC_DATA_URI_PREFIX = "data:application/vc+jwt,"
        const val VCDM2_VP_DATA_URI_PREFIX = "data:application/vp+jwt,"
        const val LDP_VC_FORMAT = "ldp_vc"
        val VCDM2_ENVELOPED_TYPES = setOf(VCDM2_ENVELOPED_VC_TYPE, VCDM2_ENVELOPED_VP_TYPE)
        val JSON_LENIENT =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
    }
}
