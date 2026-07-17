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

package com.sphereon.openid.oid4vp.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.compat.Uuid
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.tryGenerateJwkThumbprint
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.di.session.SessionScope
import com.sphereon.mdoc.data.device.DeviceResponseCborCodec
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.data.device.IssuerSigned
import com.sphereon.mdoc.data.device.IssuerSignedCborCodec
import com.sphereon.mdoc.data.mso.MobileSecurityObjectCborCodec
import com.sphereon.mdoc.oid4vp.MdocOid4vpService
import com.sphereon.mdoc.oid4vp.Oid4VPConstraintField
import com.sphereon.mdoc.oid4vp.Oid4VPConstraints
import com.sphereon.mdoc.oid4vp.Oid4VPFormat
import com.sphereon.mdoc.oid4vp.Oid4VPInputDescriptor
import com.sphereon.mdoc.oid4vp.Oid4VPPresentationDefinition
import com.sphereon.mdoc.oid4vp.Oid4VPPresentationSubmission
import com.sphereon.mdoc.oid4vp.Oid4VPSupportedAlgorithm
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.openid.oid4vp.common.CredentialFormat
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.common.VpToken
import com.sphereon.openid.oid4vp.common.buildOid4vpAuthorizationResponse
import com.sphereon.openid.oid4vp.common.responseUri
import com.sphereon.openid.oid4vp.common.selectEncryptedResponseJwk
import com.sphereon.openid.oid4vp.holder.CreateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.holder.CreateAuthorizationResponseCommand
import com.sphereon.openid.oid4vp.holder.CreateAuthorizationResponseCommandService
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.sdjwt.PresentSdJwtArgs
import com.sphereon.sdjwt.command.PresentSdJwtCommand
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Implementation of CreateAuthorizationResponseCommand for OpenID4VP.
 *
 * Creates an authorization response by:
 * - Matching selected credentials against DCQL query
 * - Building VP tokens in the appropriate format (jwt_vc, ldp_vc, mso_mdoc, etc.)
 * - Applying holder binding if required
 * - Constructing the response parameters
 *
 * Reference: OpenID4VP 1.0 Section 6 - Authorization Response
 *
 * Response format (OpenID4VP 1.0 Final):
 * - vp_token: Self-descriptive credential(s)
 * - state: State from original request (if present)
 * - Note: presentation_submission is NOT used in OID4VP 1.0 Final (DCQL responses are self-descriptive)
 *
 * VP Token formats:
 * - JWT VP: Verifiable Presentation in JWT format
 * - SD-JWT: Selective Disclosure JWT
 * - mDoc/mDL: ISO 18013-5 mobile documents
 * - LDP VP: JSON-LD Verifiable Presentation
 */
@Inject
@SingleIn(SessionScope::class)
class CreateAuthorizationResponseCommandImpl(
    execution: SessionExecution,
    /**
     * SD-JWT presentation command (`sdjwt.jwt.present`), used to produce a Key Binding JWT
     * (RFC 9901 §4.3) for `dc+sd-jwt` / `vc+sd-jwt` credentials that carry a holder key.
     */
    private val presentSdJwtCommand: PresentSdJwtCommand,
    /**
     * mdoc OID4VP presentation service (ISO 18013-5 / 18013-7). Builds the ISO
     * `DeviceResponse` with a `DeviceAuth` COSE_Sign1 over the OID4VP §B.2.6
     * `OpenID4VPHandover` SessionTranscript for `mso_mdoc` credentials.
     */
    private val mdocOid4vpService: MdocOid4vpService,
    /** Decodes the stored `mso_mdoc` credential (base64url(CBOR(IssuerSigned))). */
    private val issuerSignedCborCodec: IssuerSignedCborCodec,
    /** Encodes the produced ISO `DeviceResponse` to CBOR for the vp_token entry. */
    private val deviceResponseCborCodec: DeviceResponseCborCodec,
    /** Reads the issued MSO (docType + device key) out of the stored `IssuerSigned`. */
    private val mobileSecurityObjectCborCodec: MobileSecurityObjectCborCodec,
) : TypedServiceCommandAdapter<CreateAuthorizationResponseArgs, AuthorizationResponse, IdkError>(
        commandId = CreateAuthorizationResponseCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateAuthorizationResponseArgs>(),
        outputTypeToken = typeToken<AuthorizationResponse>(),
    ),
    CreateAuthorizationResponseCommand,
    CreateAuthorizationResponseCommandService {
    override val commandId: String get() = CreateAuthorizationResponseCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is CreateAuthorizationResponseArgs

    override suspend fun createAuthorizationResponse(
        request: ResolvedOid4vpRequest,
        selectedCredentials: List<SelectedCredential>,
    ): IdkResult<AuthorizationResponse, IdkError> = execute(CreateAuthorizationResponseArgs(request, selectedCredentials))

    override suspend fun doExecute(
        args: CreateAuthorizationResponseArgs,
        applyDuring: (CreateAuthorizationResponseArgs) -> CreateAuthorizationResponseArgs,
    ): IdkResult<AuthorizationResponse, IdkError> {
        val processedArgs = applyDuring(args)
        val request = processedArgs.request
        val selectedCredentials = processedArgs.selectedCredentials

        log.debug("Creating authorization response for ${selectedCredentials.size} selected credential(s)")

        // Validate that credentials were provided
        if (selectedCredentials.isEmpty()) {
            log.error("No credentials selected for authorization response")
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "At least one credential must be selected",
                ),
            )
        }

        // ISO/IEC 18013-7 deployments using mdoc-openid4vp carry a Presentation Definition.
        // Keep the protocol in this holder command: UI and interaction orchestration still use
        // the normalized credential requirements, while the wire response retains the profile's
        // vp_token + presentation_submission shape.
        val presentationDefinition = parsePresentationDefinition(request)
        if (presentationDefinition != null) {
            if (selectedCredentials.size != 1 || presentationDefinition.input_descriptors.size != 1) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message =
                            "The mdoc Presentation Definition response currently requires exactly one " +
                                "input descriptor and one selected credential",
                    ),
                )
            }
            val presentation = resolvePresentation(request, selectedCredentials.single()).getOrElse { return Err(it) }
            val submission = Oid4VPPresentationSubmission.fromPresentationDefinition(presentationDefinition)
            return Ok(
                AuthorizationResponse(
                    code = "",
                    state = request.request.state,
                    additionalParameters =
                        mapOf(
                            "vp_token" to JsonPrimitive(presentation),
                            "presentation_submission" to
                                responseJson.encodeToJsonElement(Oid4VPPresentationSubmission.serializer(), submission),
                        ),
                ),
            )
        }

        // Build VP token from selected credentials. For SD-JWT credentials that carry a holder
        // key, the holder produces a Key Binding JWT (RFC 9901 §4.3) binding the presentation to
        // the verifier (audience = client_id) and the request nonce; other formats pass through.
        val vpToken =
            buildVpToken(request, selectedCredentials).getOrElse { error ->
                return Err(error)
            }

        log.info("Created VP token with ${selectedCredentials.size} presentation(s)")

        // Build authorization response using type-safe builder
        // OpenID4VP 1.0 Final: vp_token is self-descriptive, no presentation_submission needed
        val response =
            buildOid4vpAuthorizationResponse {
                vpToken(vpToken)
                request.request.state?.let { state(it) }
            }

        log.debug("Authorization response created successfully")
        return Ok(response)
    }

    /**
     * Build VP token from selected credentials.
     *
     * OpenID4VP 1.0 Final §8.1 (DCQL Format):
     * - vp_token is a JSON object where keys are credential query IDs
     * - Every value is an array of one or more Presentations
     *
     * Example:
     * ```json
     * {
     *   "driver_license_query": ["eyJhbGc..."],
     *   "employment_query": ["eyJhbGc...", "eyJhbGc..."]
     * }
     * ```
     *
     * For `dc+sd-jwt` / `vc+sd-jwt` credentials that carry a [SelectedCredential.holderKeyAlias],
     * the holder appends a freshly signed Key Binding JWT (RFC 9901 §4.3) so the verifier's
     * holder-binding check (KB-JWT signature + nonce + aud + sd_hash) passes. The KB-JWT is bound
     * to the verifier `client_id` (audience) and the authorization request `nonce`.
     *
     * Note: SelectedCredential.credentialQueryId maps the presentation to its DCQL credential
     * query. Multiple credentials can satisfy the same query.
     */
    private suspend fun buildVpToken(
        request: ResolvedOid4vpRequest,
        credentials: List<SelectedCredential>,
    ): IdkResult<VpToken, IdkError> {
        val withPresentations = mutableListOf<Pair<String, String>>()
        for (credential in credentials) {
            val presentation =
                resolvePresentation(request, credential).getOrElse { return Err(it) }
            withPresentations += credential.credentialQueryId to presentation
        }

        val grouped =
            withPresentations
                .groupBy({ it.first }, { it.second })

        return Ok(VpToken.fromStrings(grouped))
    }

    /**
     * Resolve the wire presentation for a selected credential.
     *
     * - `mso_mdoc`: the stored credential is the issued `IssuerSigned` (base64url(CBOR)). The
     *   holder decodes it, builds an ISO `DeviceResponse` whose `DeviceAuth` COSE_Sign1 is signed
     *   over the OID4VP §B.2.6 `OpenID4VPHandover` SessionTranscript (bound to `client_id`,
     *   `nonce`, `response_uri`) using the holder's device key, and submits base64url(CBOR) of
     *   that `DeviceResponse`. See [resolveMdocPresentation].
     * - `dc+sd-jwt` / `vc+sd-jwt` with a holder key: a Key Binding JWT (RFC 9901 §4.3) is
     *   appended via [PresentSdJwtCommand].
     * - everything else (and SD-JWT without a holder key): submitted as stored.
     */
    private suspend fun resolvePresentation(
        request: ResolvedOid4vpRequest,
        credential: SelectedCredential,
    ): IdkResult<String, IdkError> {
        val format = CredentialFormat.fromValueLenient(credential.format)
        val holderKeyAlias = credential.holderKeyAlias

        if (format?.isMdoc == true) {
            return resolveMdocPresentation(request, credential)
        }

        if (format?.isSdJwt != true || holderKeyAlias.isNullOrBlank()) {
            return Ok(credential.presentation)
        }

        val nonce =
            request.request.nonce
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message =
                            "Cannot create SD-JWT Key Binding JWT for query '${credential.credentialQueryId}': " +
                                "authorization request has no nonce",
                    ),
                )
        val audience = request.verifierInfo.clientId

        // disclosureSelection = null discloses all disclosures present on the issued SD-JWT.
        // The DCQL claim filtering happens verifier-side; the holder here binds the credential to
        // the verifier and nonce. holderKey embeds the holder public JWK in the KB-JWT header
        // (PresentSdJwtCommandImpl uses JwsIdentifierMode.JWK) so the verifier can verify it
        // against the issuer SD-JWT `cnf.jwk`.
        val presentResult =
            presentSdJwtCommand.execute(
                PresentSdJwtArgs(
                    sdJwt = credential.presentation,
                    audience = audience,
                    nonce = nonce,
                    holderKey = ManagedOptsKeyInfo(identifier = KeyInfo<Nothing>(alias = holderKeyAlias)),
                ),
            )
        return presentResult.map { it.presentation }
    }

    /**
     * Build the `mso_mdoc` OID4VP presentation: an ISO 18013-5/-7 `DeviceResponse`.
     *
     * The stored [SelectedCredential.presentation] is the issued `IssuerSigned`
     * (base64url(CBOR), OID4VCI §A.4). A bare `IssuerSigned` is NOT a `DeviceResponse`, so the
     * verifier's `DeviceResponseCborCodec` rejects it; the holder must wrap it and add holder
     * binding. Steps:
     *
     *  1. base64url-decode + CBOR-decode the stored `IssuerSigned`; read the issued MSO to learn
     *     the `docType` (and, via the MSO `deviceKeyInfo.deviceKey`, the device key the holder
     *     binds against — resolved inside [MdocOid4vpService]).
     *  2. wrap it in a [Document] (the device-signed half is produced by the signer).
     *  3. derive an ISO 18013-7 presentation definition whose input-descriptor `id` is the
     *     `docType` and whose constraint fields cover every issued element (`$['ns']['element']`),
     *     so all held elements are disclosed for this single-credential request.
     *  4. match document <-> descriptor (derives the device key from the MSO) and call
     *     [MdocOid4vpService.createDeviceResponse], which signs `DeviceAuth` over the §B.2.6
     *     `OpenID4VPHandover` SessionTranscript built from `client_id` + `nonce` + `response_uri`
     *     For `direct_post.jwt`, the handover includes the RFC 7638 thumbprint of the exact
     *     verifier encryption JWK used for the response JWE. The verifier reconstructs the same
     *     transcript via `SessionTranscript.fromOid4vpClientIdAndResponseUri(...)`.
     *  5. submit base64url(CBOR(DeviceResponse)) as the vp_token entry (the form the verifier's
     *     `DeviceResponseCborCodec` decodes; `CredentialFormat.detectFormat` reads it as mso_mdoc).
     */
    private suspend fun resolveMdocPresentation(
        request: ResolvedOid4vpRequest,
        credential: SelectedCredential,
    ): IdkResult<String, IdkError> {
        val nonce =
            request.request.nonce
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message =
                            "Cannot create mdoc DeviceResponse for query '${credential.credentialQueryId}': " +
                                "authorization request has no nonce",
                    ),
                )
        // The verifier reconstructs the §B.2.6 handover from the request's client_id +
        // response_uri; the holder MUST use the identical values or DeviceAuth fails.
        val clientId = request.request.clientId
        val responseUri =
            request.request.responseUri
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message =
                            "Cannot create mdoc DeviceResponse for query '${credential.credentialQueryId}': " +
                                "authorization request has no response_uri",
                    ),
                )

        val issuerSignedBytes =
            try {
                credential.presentation.decodeFromBase64Url()
            } catch (expected: Exception) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message =
                            "Stored mso_mdoc credential for query '${credential.credentialQueryId}' is not valid " +
                                "base64url: ${expected.message}",
                    ),
                )
            }

        val issuerSigned: IssuerSigned =
            issuerSignedCborCodec
                .decode(issuerSignedBytes)
                .getOrElse {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message =
                                "Failed to CBOR-decode stored IssuerSigned for query " +
                                    "'${credential.credentialQueryId}': ${it.message.defaultMessage}",
                        ),
                    )
                }.value

        val msoPayload =
            issuerSigned.issuerAuth.payload?.value
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "Stored mso_mdoc credential for query '${credential.credentialQueryId}' has no MSO payload",
                    ),
                )
        val mso =
            mobileSecurityObjectCborCodec
                .decode(msoPayload)
                .getOrElse {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Failed to decode MSO for query '${credential.credentialQueryId}': ${it.message.defaultMessage}",
                        ),
                    )
                }.value
        val docType = mso.docType

        val document = Document(docType = docType, issuerSigned = issuerSigned, deviceSigned = null, original = null)

        // Preserve the exact requested elements. For the ISO Presentation Definition profile we
        // reuse its constraints directly. For DCQL, translate namespace/element claim paths to
        // the ISO JSONPath form. A DCQL query without claim constraints requests the full mdoc.
        val presentationDescriptor =
            parsePresentationDefinition(request)
                ?.input_descriptors
                ?.firstOrNull { it.id.toString() == credential.credentialQueryId }
        val dcqlCredentialQuery =
            request.dcqlQuery
                ?.credentials
                ?.firstOrNull { it.id == credential.credentialQueryId }
        val requestedDcqlFields =
            dcqlCredentialQuery
                ?.claims
                .orEmpty()
                .flatMap { claim ->
                    val namespaces =
                        when {
                            claim.path.size >= 2 -> listOf(claim.path[0])
                            claim.path.size == 1 ->
                                (dcqlCredentialQuery?.meta?.get("namespace_values") as? JsonArray)
                                    ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                                    .orEmpty()
                            else -> emptyList()
                        }
                    val elementIdentifier = claim.path.lastOrNull()
                    if (elementIdentifier == null) {
                        emptyList()
                    } else {
                        namespaces.map { namespace ->
                            Oid4VPConstraintField(
                                path = arrayOf("$['$namespace']['$elementIdentifier']"),
                                intent_to_retain = claim.intent_to_retain ?: false,
                            )
                        }
                    }
                }
        val constraintFields =
            presentationDescriptor?.constraints?.fields?.toList()
                ?: requestedDcqlFields.takeIf { it.isNotEmpty() }
                ?: (issuerSigned.nameSpaces ?: emptyMap()).flatMap { (nameSpace, items) ->
                    items.map { encoded ->
                        Oid4VPConstraintField(
                            path = arrayOf("$['$nameSpace']['${encoded.data().elementIdentifier}']"),
                            intent_to_retain = false,
                        )
                    }
                }
        if (constraintFields.isEmpty()) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Stored mso_mdoc credential for query '${credential.credentialQueryId}' discloses no elements",
                ),
            )
        }

        val presentationDefinition =
            Oid4VPPresentationDefinition(
                id = credential.credentialQueryId,
                input_descriptors =
                    arrayOf(
                        Oid4VPInputDescriptor(
                            id = docType,
                            format = Oid4VPFormat(mso_mdoc = Oid4VPSupportedAlgorithm(alg = arrayOf("ES256"))),
                            constraints = Oid4VPConstraints(fields = constraintFields.toTypedArray()),
                        ),
                    ),
            )

        // mdocGeneratedNonce is NOT part of the OID4VP 1.0 final §B.2.6 OpenID4VPHandover
        // (the transcript is [client_id, nonce, jwkThumbprint|null, response_uri]); it only
        // tags the per-document match result and never enters the signed transcript, so a fresh
        // value is fine here.
        val matchResults =
            mdocOid4vpService.matchDocumentsAndDescriptors(
                mdocNonce = Uuid.v4String(),
                applicableDocuments = arrayOf(document),
                presentationDefinition = presentationDefinition,
            )

        // The match result derives the device key from the MSO `deviceKeyInfo.deviceKey`, which is
        // a PUBLIC key carrying only a kid — not the KMS alias/providerId needed to locate the
        // holder's PRIVATE device key for DeviceAuth signing. Overlay the wallet's holder key
        // alias (the same one the proof-of-possession bound at issuance) so the COSE signer can
        // resolve the managed private key. Without this the signer fails with "Need to provide an
        // alias".
        val holderKeyAlias = credential.holderKeyAlias
        val signableMatchResults =
            if (!holderKeyAlias.isNullOrBlank()) {
                matchResults
                    .map { match ->
                        if (match.document != null && match.documentError == null) {
                            match.copy(
                                deviceKeyInfo =
                                    KeyInfo(
                                        alias = holderKeyAlias,
                                        keyVisibility = KeyVisibility.PRIVATE,
                                        signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                                    ),
                            )
                        } else {
                            match
                        }
                    }.toTypedArray()
            } else {
                matchResults
            }

        val verifierEncryptionJwkThumbprint =
            when (ResponseMode.fromValue(request.request.responseMode ?: ResponseMode.DIRECT_POST.value)) {
                ResponseMode.DIRECT_POST_JWT,
                ResponseMode.IAE_POST_JWT -> {
                    val encryptionJwk =
                        request.clientMetadata
                            ?.selectEncryptedResponseJwk()
                            ?: return Err(
                                IdkError.ILLEGAL_ARGUMENT_ERROR(
                                    message =
                                        "Cannot create encrypted mdoc DeviceResponse for query " +
                                            "'${credential.credentialQueryId}': client_metadata.jwks has no " +
                                            "encryption JWK with an alg",
                                ),
                            )
                    val encodedThumbprint =
                        tryGenerateJwkThumbprint(encryptionJwk)
                            .getOrElse { return Err(it) }
                    try {
                        encodedThumbprint.decodeFromBase64Url()
                    } catch (expected: Exception) {
                        return Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message =
                                    "Cannot create encrypted mdoc DeviceResponse for query " +
                                        "'${credential.credentialQueryId}': verifier JWK thumbprint is invalid: " +
                                        expected.message,
                            ),
                        )
                    }
                }

                else -> null
            }

        val deviceResponse =
            try {
                mdocOid4vpService.createDeviceResponse(
                    matchingDocuments = signableMatchResults,
                    presentationDefinition = presentationDefinition,
                    clientId = clientId,
                    responseUri = responseUri,
                    authorizationRequestNonce = nonce,
                    verifierEncryptionJwkThumbprint = verifierEncryptionJwkThumbprint,
                )
            } catch (expected: Exception) {
                return Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to build mdoc DeviceResponse for query '${credential.credentialQueryId}': ${expected.message}",
                    ),
                )
            }

        if (deviceResponse.documents.isNullOrEmpty()) {
            return Err(
                IdkError.UNKNOWN_ERROR(
                    message =
                        "mdoc DeviceResponse for query '${credential.credentialQueryId}' has no documents " +
                            "(documentErrors=${deviceResponse.documentErrors})",
                ),
            )
        }

        val deviceResponseBytes =
            deviceResponseCborCodec
                .encode(deviceResponse)
                .getOrElse {
                    return Err(
                        IdkError.UNKNOWN_ERROR(
                            message =
                                "Failed to CBOR-encode mdoc DeviceResponse for query " +
                                    "'${credential.credentialQueryId}': ${it.message.defaultMessage}",
                        ),
                    )
                }

        return Ok(deviceResponseBytes.encodeToBase64Url())
    }

    private fun parsePresentationDefinition(request: ResolvedOid4vpRequest): Oid4VPPresentationDefinition? {
        val definitionElement = request.request.additionalParameters["presentation_definition"] ?: return null
        val normalized: JsonElement =
            when (definitionElement) {
                is JsonPrimitive -> responseJson.parseToJsonElement(definitionElement.content)
                else -> definitionElement
            }
        return responseJson.decodeFromJsonElement(Oid4VPPresentationDefinition.serializer(), normalized)
    }

    private companion object {
        val responseJson = Json { ignoreUnknownKeys = true }
    }
}
