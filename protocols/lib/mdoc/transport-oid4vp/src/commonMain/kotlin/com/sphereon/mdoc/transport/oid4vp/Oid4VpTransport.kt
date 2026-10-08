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
 *
 */

package com.sphereon.mdoc.transport.oid4vp

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.api.toException
import com.sphereon.core.compat.Uuid
import com.sphereon.core.defaults.log.AppNoLogService
import com.sphereon.crypto.core.cose.CoseKeyType
import com.sphereon.crypto.jose.jwe.JweHeader
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.data.device.DeviceRequest
import com.sphereon.mdoc.data.device.DeviceRequestCborCodec
import com.sphereon.mdoc.data.device.DeviceResponse
import com.sphereon.mdoc.data.device.DeviceResponseCborCodec
import com.sphereon.mdoc.data.device.Document
import com.sphereon.mdoc.engagement.EngagementData
import com.sphereon.mdoc.json.oid4vpJsonSerializer
import com.sphereon.mdoc.oid4vp.MdocOid4vpService
import com.sphereon.mdoc.oid4vp.Oid4VPFormatIdentifier
import com.sphereon.mdoc.oid4vp.Oid4VPPresentationDefinition
import com.sphereon.mdoc.oid4vp.Oid4VPPresentationSubmission
import com.sphereon.mdoc.oid4vp.Oid4vpHolderFlow
import com.sphereon.mdoc.oid4vp.Oid4vpHolderResult
import com.sphereon.mdoc.oid4vp.assertedPathEntry
import com.sphereon.mdoc.transport.AbstractMdocTransport
import com.sphereon.mdoc.transport.oid4vp.Oid4vpError
import com.sphereon.oauth2.common.jarm.JarmConfig
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.common.oid4vpNonce
import com.sphereon.openid.oid4vp.common.responseUri
import com.sphereon.openid.oid4vp.common.selectEncryptedResponseJwk
import com.sphereon.openid.oid4vp.holder.JarmOptions
import com.sphereon.openid.oid4vp.holder.Oid4vpHolderService
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * OID4VP transport implementation for mdoc holder (ISO 18013-7 Annex B).
 *
 * This implements the holder side of the OpenID4VP presentation protocol:
 * 1. Parse `mdoc-openid4vp://` invocation URI
 * 2. Fetch Authorization Request Object from `request_uri` (JWT over HTTPS)
 * 3. Verify JWT signature using x509_san_dns client_id scheme
 * 4. Resolve the restricted Presentation-Exchange profile required by ISO 18013-7 Annex B
 * 5. Match holder's documents to requested input descriptors
 * 6. User consent and authentication
 * 7. Sign mdocs with device authentication (DeviceResponse)
 * 8. Encrypt Authorization Response using JARM
 * 9. POST to `response_uri` (Direct Post mode)
 * 10. Redirect user
 *
 * ## Key Differences from REST API Transport
 *
 * - **Protocol**: OAuth 2.0 / OpenID4VP vs plain HTTP POST
 * - **Request Format**: JWT Authorization Request with the ISO 18013-7 restricted Presentation-Exchange profile vs CBOR (DeviceRequest)
 * - **Response Format**: JWT (Authorization Response with `vp_token`) vs CBOR (DeviceResponse)
 * - **Encryption**: JARM (JWT encryption) vs session-level encryption
 * - **SessionTranscript**: Uses the ISO 18013-7 Annex B three-element handover vs RestApiHandover
 *
 * ## Profile-specific handover
 *
 * This transport uses the ISO 18013-7 Annex B handover, not the final
 * OpenID4VP/DCQL handover used by the regular OID4VP implementation. The ISO
 * handover hashes `[client_id, mdocGeneratedNonce]` and
 * `[response_uri, mdocGeneratedNonce]`, then carries the authorization-request
 * nonce. The two profiles must never share a transcript builder.
 *
 * ## Security
 *
 * Per ISO 18013-7 B.5:
 * - `request_uri` and `response_uri` MUST use HTTPS
 * - Authorization Request Object MUST be signed JWT
 * - `client_id` MUST be verified using x509_san_dns scheme
 * - `nonce` MUST be at least 16 bytes (128 bits)
 * - Authorization Response MUST be encrypted using JARM
 *
 * @param connectionMethod OID4VP connection method with options
 * @param execution Session execution context for logging
 * @param httpClient Ktor HTTP client for HTTPS requests
 * @param oid4vpService Service for OID4VP operations (signing, matching)
 * @param engagementData Engagement data (not used for Annex B - the ISO OID4VP handover is request-derived)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpTransfer", exact = true)
class Oid4VpTransport(
    connectionMethod: Oid4vpConnectionMethod,
    execution: SessionExecution,
    private val httpClient: HttpClient,
    private val oid4vpService: MdocOid4vpService,
    private val oid4vpHolder: Oid4vpHolderService,
    private val deviceRequestCborCodec: DeviceRequestCborCodec,
    private val deviceResponseCborCodec: DeviceResponseCborCodec,
    engagementData: EngagementData?,
) : AbstractMdocTransport<String>(connectionMethod, execution) {
    private val log = execution.log
    private val options = connectionMethod.options

    // OID4VP flow state
    private val mutex = Mutex()
    private var resolvedRequest: ResolvedOid4vpRequest? = null
    private var presentationDefinition: Oid4VPPresentationDefinition? = null
    private var holderFlowResult: Oid4vpHolderResult? = null
    private var usePresentationExchange: Boolean = false

    // For standard TransferManager flow: store the DeviceResponse to avoid decode issues
    internal var deviceResponseForStandardFlow: DeviceResponse? = null

    // These are populated from the Authorization Request Object fetched from request_uri
    private var authorizationRequestNonce: String? = null
    private var responseUri: String? = null
    private var mdocGeneratedNonce: String? = null

    override val role: MdocRole = MdocRole.MDOC // OID4VP is holder-side only in ISO 18013-7

    init {
        setEngagementData(engagementData)
    }

    override fun setContextForSending(
        key: String,
        value: Any,
    ) {
        when (key) {
            "deviceResponse" -> {
                if (value is DeviceResponse) {
                    deviceResponseForStandardFlow = value
                    log.info("DeviceResponse set for standard flow sending via context")
                }
            }
        }
    }

    override fun getContext(key: String): Any? =
        when (key) {
            "sessionTranscript" -> {
                // Return the SessionTranscript for OID4VP
                try {
                    getSessionTranscript()
                } catch (expected: Exception) {
                    log.error("Failed to get SessionTranscript", exception = expected)
                    null
                }
            }

            else -> {
                null
            }
        }

    override suspend fun open(
        senderKey: CoseKeyType,
        id: String,
        engagementData: EngagementData?,
    ): IdkResult<String, IdkErrorType> {
        return mutex.withLock {
            log.info("Opening OID4VP transfer: clientId=${options.clientId}, request_uri=${options.requestUri}")

            try {
                if (options.requestUri == null) {
                    return@withLock Err(
                        Oid4vpError
                            .InvalidRequest(
                                "request_uri must be provided in mdoc-openid4vp:// URI",
                            ).toIdkError(),
                    )
                }

                // Update state to CONNECTING while resolving the authorization request
                mutableEngagementState.value = com.sphereon.mdoc.engagement.MdocEngagementState.CONNECTING
                log.info("State: CONNECTING - Resolving Authorization Request from: ${options.requestUri}")

                val authorizationRequestUri = buildAuthorizationRequestUri()
                val parsedRequest =
                    oid4vpHolder
                        .parseAuthorizationRequest(authorizationRequestUri)
                        .getOrElse { return@withLock Err(it) }
                val hasPresentationDefinition = parsedRequest.additionalParameters.containsKey("presentation_definition")
                val hasPresentationDefinitionUri = parsedRequest.additionalParameters.containsKey("presentation_definition_uri")
                if (!hasPresentationDefinition || hasPresentationDefinitionUri || parsedRequest.additionalParameters.containsKey("dcql_query")) {
                    return@withLock Err(
                        Oid4vpError.InvalidRequest(
                            "ISO 18013-7 mdoc-openid4vp requests must contain inline restricted Presentation Exchange and must not contain dcql_query or presentation_definition_uri",
                        ).toIdkError(),
                    )
                }
                if (options.presentationDefinitionUri != null) {
                    return@withLock Err(
                        Oid4vpError.InvalidRequest(
                            "ISO 18013-7 mdoc-openid4vp does not support presentation_definition_uri",
                        ).toIdkError(),
                    )
                }
                if (parsedRequest.responseType != "vp_token") {
                    return@withLock Err(
                        Oid4vpError.InvalidRequest(
                            "ISO 18013-7 mdoc-openid4vp requires response_type=vp_token",
                        ).toIdkError(),
                    )
                }
                if (parsedRequest.responseMode?.lowercase() != ResponseMode.DIRECT_POST_JWT.value) {
                    return@withLock Err(
                        Oid4vpError.InvalidRequest(
                            "ISO 18013-7 mdoc-openid4vp requires response_mode=direct_post.jwt",
                        ).toIdkError(),
                    )
                }
                usePresentationExchange = true

                val resolvedRequest =
                    oid4vpHolder
                        .resolveAuthorizationRequest(parsedRequest)
                        .getOrElse { return@withLock Err(it) }

                if (resolvedRequest.request.clientId != options.clientId) {
                    return@withLock Err(
                        Oid4vpError.InvalidRequest(
                            "Authorization Request client_id does not match the mdoc-openid4vp invocation",
                        ).toIdkError(),
                    )
                }
                if (resolvedRequest.verifierInfo.clientIdScheme != ClientIdScheme.X509_SAN_DNS) {
                    return@withLock Err(
                        Oid4vpError.InvalidRequest(
                            "ISO 18013-7 mdoc-openid4vp requires client_id_scheme=x509_san_dns",
                        ).toIdkError(),
                    )
                }

                val presentationDefinition =
                    if (usePresentationExchange) {
                        resolvePresentationDefinition(parsedRequest)
                            ?: return@withLock Err(
                                Oid4vpError.InvalidRequest("presentation_definition missing from Authorization Request").toIdkError(),
                            )
                    } else {
                        null
                    }

                this.resolvedRequest = resolvedRequest
                this.presentationDefinition = presentationDefinition
                this.mdocGeneratedNonce = Uuid.v4String()
                // The signed Request Object is authoritative for values covered by the
                // ISO 18013-7 Annex B SessionTranscript. Resolution has already merged and
                // validated it, so never fall back to the outer invocation URI here.
                this.authorizationRequestNonce = resolvedRequest.request.oid4vpNonce
                this.responseUri = resolvedRequest.request.responseUri

                if (authorizationRequestNonce == null) {
                    return@withLock Err(
                        Oid4vpError.InvalidRequest("nonce missing from Authorization Request").toIdkError(),
                    )
                }
                if (responseUri == null) {
                    return@withLock Err(
                        Oid4vpError.InvalidRequest("response_uri missing from Authorization Request").toIdkError(),
                    )
                }
                if (!responseUri!!.startsWith("https://")) {
                    return@withLock Err(Oid4vpError.InsecureUri(responseUri!!).toIdkError())
                }
                if (authorizationRequestNonce!!.encodeToByteArray().size < 16) {
                    return@withLock Err(
                        Oid4vpError.InvalidRequest("nonce must be at least 16 UTF-8 bytes").toIdkError(),
                    )
                }
                // Validate profile-specific verifier metadata before document
                // selection/signing. This also ensures response encryption cannot
                // silently fall back to regular OID4VP metadata semantics.
                iso18013JarmOptions(resolvedRequest)

                log.info("Authorization Request resolved successfully (presentation_exchange=$usePresentationExchange)")
                if (presentationDefinition != null) {
                    log.debug("Presentation Definition ID: ${presentationDefinition.id}")
                    log.debug("Input Descriptors: ${presentationDefinition.input_descriptors.size}")
                }
                log.debug("Response URI: $responseUri")
                log.debug("Nonce: ${authorizationRequestNonce?.take(16)}...") // Log first 16 chars only

                markOpen()
                Ok(id)
            } catch (e: CancellationException) {
                log.info("OID4VP transfer opening was cancelled")
                throw e
            } catch (e: Oid4vpError) {
                Err(e.toIdkError())
            } catch (expected: Exception) {
                log.error("Failed to open OID4VP connection", exception = expected)
                Err(Oid4vpError.ConnectionFailed(responseUri ?: options.requestUri ?: "unknown", expected).toIdkError())
            }
        }
    }

    override suspend fun messageReceiveBlocking(): ByteArray {
        requireOpen()

        return mutex.withLock {
            log.debug("OID4VP: messageReceive() called")

            // ISO 18013-7 Annex B uses the restricted Presentation-Exchange profile. The
            // regular OID4VP/DCQL path is handled by lib-openid-oid4vp and must not enter here.
            val presentationDef = presentationDefinition
                ?: error("ISO 18013-7 Presentation Definition not resolved")
            val docRequest = presentationDef.toDocRequest()
            val deviceRequest =
                DeviceRequest(
                    docRequests = arrayOf(docRequest),
                    oid4vpRequest = presentationDef,
                    macKeys = null,
                    original = null,
                )

            log.info("Created DeviceRequest from OID4VP request (presentation_exchange=$usePresentationExchange)")
            deviceRequestCborCodec
                .encode(deviceRequest)
                .getOrElse { throw it.toException() }
        }
    }

    /**
     * Get the ISO 18013-7 Annex B session transcript.
     *
     * The handover is `[clientIdHash, responseUriHash, nonce]`, where each URI hash
     * is SHA-256 of the CBOR array `[value, mdocGeneratedNonce]`. This is intentionally
     * not the final OpenID4VP/DCQL handover used by the regular OID4VP flow.
     *
     * Must be called after open() has completed successfully so the signed request
     * inputs (`client_id`, `response_uri`, `nonce`) and generated nonce are populated.
     *
     */
    fun getSessionTranscript(): com.sphereon.mdoc.transfer.reader.SessionTranscript {
        requireOpen()

        val clientId =
            resolvedRequest
                ?.request
                ?.clientId
                ?: error("Authorization Request not resolved")
        val responseUri =
            this.responseUri
                ?: error("response_uri not set - Authorization Request not fetched")
        val nonce =
            this.authorizationRequestNonce
                ?: error("nonce not set - Authorization Request not fetched")
        val generatedNonce = mdocGeneratedNonce ?: error("mdoc-generated nonce not set - Authorization Request not fetched")

        log.info("Creating ISO 18013-7 Annex B OID4VP session transcript")
        log.debug("clientId: $clientId")
        log.debug("responseUri: $responseUri")
        log.debug("authzRequestNonce: ${nonce.take(16)}...")
        log.debug("mdocGeneratedNonce: ${generatedNonce.take(16)}...")

        val handover =
            com.sphereon.mdoc.transfer.reader.Iso18013Oid4vpHandover.fromInputs(
                clientId = clientId,
                responseUri = responseUri,
                mdocGeneratedNonce = generatedNonce,
                nonce = nonce,
            )

        val sessionTranscript =
            com.sphereon.mdoc.transfer.reader.SessionTranscript(
                handover = handover as com.sphereon.mdoc.transfer.reader.Handover<*, com.sphereon.cbor.CborItem<*>>,
                original = null,
            )

        log.info("ISO 18013-7 Annex B OID4VP session transcript created")
        return sessionTranscript
    }

    override suspend fun messageSendBlocking(message: ByteArray) {
        requireOpen()

        mutex.withLock {
            val responseUri =
                this.responseUri
                    ?: error("response_uri not set - Authorization Request not fetched")
            val resolvedRequest =
                this.resolvedRequest
                    ?: error("Authorization Request not resolved")

            log.debug("Sending ${message.size} bytes over OID4VP to $responseUri")

            try {
                val deviceResponse: DeviceResponse

                if (holderFlowResult != null) {
                    // Scenario 1: Use the properly signed DeviceResponse from executeHolderFlow()
                    log.info("Using DeviceResponse from holderFlowResult (OID4VP-specific flow)")
                    deviceResponse = holderFlowResult!!.deviceResponse
                } else if (deviceResponseForStandardFlow != null) {
                    // Scenario 2a: Use the DeviceResponse object set by setDeviceResponseForSending()
                    log.info("Using DeviceResponse from deviceResponseForStandardFlow (standard flow, object passed)")
                    deviceResponse = deviceResponseForStandardFlow!!
                } else {
                    // Scenario 2b: Decode from message bytes (fallback for standard TransferManager flow)
                    log.info("Decoding DeviceResponse from message bytes (standard flow, decoding bytes)")
                    deviceResponse =
                        deviceResponseCborCodec
                            .decode(message)
                            .getOrElse { throw it.toException() }
                            .value
                }

                val authorizationResponse = buildPresentationAuthorizationResponse(deviceResponse, resolvedRequest)
                val submissionResult =
                    oid4vpHolder
                        .submitAuthorizationResponse(
                            resolvedRequest,
                            authorizationResponse,
                            ResponseMode.DIRECT_POST_JWT,
                            iso18013JarmOptions(resolvedRequest),
                        ).getOrElse { throw it.toException() }

                log.info("Authorization Response submitted: ${submissionResult::class.simpleName}")
            } catch (e: CancellationException) {
                log.debug("Message send was cancelled")
                throw e
            } catch (e: Oid4vpError) {
                throw e
            } catch (expected: Exception) {
                log.error("Failed to send Authorization Response", exception = expected)
                throw Oid4vpError.NetworkError(responseUri, expected)
            }
        }
    }

    override fun close() {
        log.info("Closing OID4VP transfer")
        resolvedRequest = null
        presentationDefinition = null
        holderFlowResult = null
        authorizationRequestNonce = null
        responseUri = null
        mdocGeneratedNonce = null
        usePresentationExchange = false
        super.close()
    }

    /**
     * Execute the OID4VP holder flow to sign documents.
     *
     * This should be called after the holder has selected which documents to present
     * and obtained user consent. It creates the DeviceResponse with signed mdocs.
     *
     * @param availableDocuments Array of documents the holder can present
     * @param mdocNonce Optional mdoc-generated nonce (UUID v4 by default)
     * @return Result containing signed DeviceResponse and presentation_submission
     */
    suspend fun executeHolderFlow(
        availableDocuments: Array<Document>,
        mdocNonce: String = "",
    ): IdkResult<Oid4vpHolderResult, IdkErrorType> {
        return mutex.withLock {
            try {
                val presentationDef =
                    presentationDefinition
                        ?: return@withLock Err(
                            Oid4vpError.InvalidRequest("Presentation Definition not resolved").toIdkError(),
                        )

                val nonce =
                    authorizationRequestNonce
                        ?: return@withLock Err(
                            Oid4vpError.InvalidRequest("Authorization Request nonce missing").toIdkError(),
                        )

                val responseUri =
                    this.responseUri
                        ?: return@withLock Err(
                            Oid4vpError.InvalidRequest("response_uri missing from Authorization Request").toIdkError(),
                        )

                val establishedMdocNonce = this.mdocGeneratedNonce
                if (establishedMdocNonce != null && mdocNonce.isNotBlank() && mdocNonce != establishedMdocNonce) {
                    return@withLock Err(
                        Oid4vpError.InvalidRequest(
                            "mdoc-generated nonce cannot change after the ISO 18013-7 session transcript is established",
                        ).toIdkError(),
                    )
                }
                val effectiveMdocNonce =
                    establishedMdocNonce
                        ?: mdocNonce.takeIf { it.isNotBlank() }
                        ?: Uuid.v4String().also { this.mdocGeneratedNonce = it }

                log.info("Executing OID4VP holder flow with ${availableDocuments.size} available documents")

                val holderFlow = Oid4vpHolderFlow(oid4vpService, AppNoLogService())
                val result =
                    holderFlow.processAuthorizationRequest(
                        presentationDefinition = presentationDef,
                        availableDocuments = availableDocuments,
                        clientId =
                            resolvedRequest
                                ?.request
                                ?.clientId
                                ?: return@withLock Err(
                                    Oid4vpError.InvalidRequest("Authorization Request not resolved").toIdkError(),
                                ),
                        responseUri = responseUri,
                        authorizationRequestNonce = nonce,
                        verifierEncryptionJwkThumbprint = null,
                        mdocNonce = effectiveMdocNonce,
                        iso18013MdocGeneratedNonce = effectiveMdocNonce,
                    )

                // Store for later use in messageSendBlocking
                this.holderFlowResult = result

                log.info("OID4VP holder flow completed successfully")
                Ok(result)
            } catch (e: CancellationException) {
                throw e
            } catch (expected: Exception) {
                log.error("Failed to execute OID4VP holder flow", exception = expected)
                Err(Oid4vpError.SigningFailed(expected).toIdkError())
            }
        }
    }

    private fun buildAuthorizationRequestUri(): String {
        val requestUri =
            options.requestUri
                ?: error("request_uri must be provided in mdoc-openid4vp:// URI")
        val params =
            buildMap {
                put("client_id", options.clientId)
                put("request_uri", requestUri)
            }

        val query =
            params.entries.joinToString("&") { (key, value) ->
                "$key=${value.encodeURLParameter()}"
            }
        return "openid4vp://?$query"
    }

    /**
     * Resolve the ISO 18013-7 Annex B JWE profile without using regular OID4VP
     * metadata names. Annex B uses the singular JARM-style
     * `authorization_encrypted_response_alg` / `enc` members and requires an
     * unsigned ECDH-ES/A256GCM response. Regular OID4VP continues to derive its
     * configuration from `jwks[].alg` and
     * `encrypted_response_enc_values_supported` in the holder module.
     */
    private fun iso18013JarmOptions(resolvedRequest: ResolvedOid4vpRequest): JarmOptions {
        try {
            val metadataElement =
                resolvedRequest.request.additionalParameters["client_metadata"]
                    ?: throw IllegalArgumentException("client_metadata is required")
            val metadata =
                (if (metadataElement is JsonPrimitive && metadataElement.isString) {
                    oid4vpJsonSerializer.parseToJsonElement(metadataElement.content)
                } else {
                    metadataElement
                }).requireJsonObject("client_metadata")

            val encryptionAlg = metadata.requiredString("authorization_encrypted_response_alg", "client_metadata")
            val contentEncryptionAlg = metadata.requiredString("authorization_encrypted_response_enc", "client_metadata")
            require(encryptionAlg == "ECDH-ES") {
                "client_metadata.authorization_encrypted_response_alg must be ECDH-ES"
            }
            require(contentEncryptionAlg == "A256GCM") {
                "client_metadata.authorization_encrypted_response_enc must be A256GCM"
            }

            val vpFormats = metadata.requiredObject("vp_formats", "client_metadata")
            require(vpFormats.keys == setOf("mso_mdoc")) {
                "client_metadata.vp_formats must contain only mso_mdoc for ISO 18013-7"
            }

            val encryptionJwk =
                resolvedRequest.clientMetadata
                    ?.selectEncryptedResponseJwk(requireAlgorithm = false)
                    ?: throw IllegalArgumentException("client_metadata.jwks has no encryption key")
            require(encryptionJwk.use == "enc") {
                "client_metadata.jwks encryption key must set use=enc"
            }
            require(!encryptionJwk.kid.isNullOrBlank()) {
                "client_metadata.jwks encryption key must have kid"
            }
            val encryptionJwkAlg = encryptionJwk.alg?.value
            require(encryptionJwkAlg == null || encryptionJwkAlg == encryptionAlg) {
                "client_metadata.jwks encryption key alg conflicts with authorization_encrypted_response_alg"
            }

            return JarmOptions(
                // The Annex B static wallet metadata uses this symbolic issuer.
                issuer = "https://self-issued.me/v2",
                jarmConfig =
                    JarmConfig.encrypted(
                        keyEncryptionAlg = encryptionAlg,
                        contentEncryptionAlg = contentEncryptionAlg,
                    ),
                protectedHeaderOverrides =
                    JweHeader().apply {
                        // ISO 18013-7 Annex B.4.3.3.2: apu and apv are base64url
                        // values and are also consumed as ECDH-ES PartyU/PartyV info.
                        apu = (mdocGeneratedNonce ?: error("mdoc-generated nonce not set")).encodeToByteArray().encodeToBase64Url()
                        apv = authorizationRequestNonce!!.encodeToByteArray().encodeToBase64Url()
                        kid = encryptionJwk.kid
                    },
            )
        } catch (expected: Oid4vpError) {
            throw expected
        } catch (expected: Exception) {
            throw Oid4vpError.InvalidRequest("Invalid ISO 18013-7 verifier metadata: ${expected.message}")
        }
    }

    private fun resolvePresentationDefinition(request: AuthorizationRequest): Oid4VPPresentationDefinition? {
        // Annex B deliberately permits only an inline Presentation Definition.
        // Do not retain a URI-fetch fallback here: that would silently widen the
        // restricted ISO profile and make it too easy to route regular OID4VP
        // request-by-reference/PE semantics through this transport.
        return request.additionalParameters["presentation_definition"]?.let(::parsePresentationDefinition)
    }

    private fun parsePresentationDefinition(definition: JsonElement): Oid4VPPresentationDefinition =
        try {
            oid4vpJsonSerializer.decodeFromJsonElement(
                Oid4VPPresentationDefinition.serializer() as DeserializationStrategy<Oid4VPPresentationDefinition>,
                validateIso18013PresentationDefinition(definition),
            )
        } catch (expected: Oid4vpError) {
            throw expected
        } catch (expected: Exception) {
            throw Oid4vpError.InvalidPresentationDefinition(expected.message ?: "invalid Presentation Definition")
        }

    /**
     * Extension to encode DeviceResponse as base64url for vp_token.
     */
    private fun DeviceResponse.encodeToBase64UrlNoPadding(): String {
        val cborBytes =
            deviceResponseCborCodec
                .encode(this)
                .getOrElse { throw it.toException() }
        return cborBytes.encodeToBase64Url()
    }

    private fun buildPresentationAuthorizationResponse(
        deviceResponse: DeviceResponse,
        resolvedRequest: ResolvedOid4vpRequest,
    ): AuthorizationResponse {
        val presentationDef =
            presentationDefinition
                ?: error("Presentation Definition not resolved")
        val presentationSubmission =
            holderFlowResult?.presentationSubmission
                ?: Oid4VPPresentationSubmission.fromPresentationDefinition(presentationDef)
        val presentationSubmissionElement =
            oid4vpJsonSerializer.parseToJsonElement(
                oid4vpJsonSerializer.encodeToString(Oid4VPPresentationSubmission.serializer(), presentationSubmission),
            )

        return AuthorizationResponse(
            code = "",
            state = resolvedRequest.request.state,
            additionalParameters =
                mapOf(
                    "vp_token" to JsonPrimitive(deviceResponse.encodeToBase64UrlNoPadding()),
                    "presentation_submission" to presentationSubmissionElement,
                ),
        )
    }

    private data class DcqlClaimPath(
        val path: List<String>,
        val intentToRetain: Boolean? = null,
    )
}

/**
 * Validate the deliberately small Presentation Exchange dialect permitted by
 * ISO/IEC TS 18013-7 Annex B. The regular OID4VP implementation uses DCQL;
 * this validator therefore lives with the ISO transport and must not be
 * weakened by the shared holder resolver's legacy PE compatibility.
 *
 * Unknown members are rejected instead of being silently discarded by the
 * lenient JSON serializer. This preserves the profile's wire distinction:
 * exactly one mso_mdoc format, one document type per descriptor, required
 * limit disclosure, and direct namespace/element paths only.
 */
internal fun validateIso18013PresentationDefinition(definition: JsonElement): JsonElement {
    val normalized =
        if (definition is JsonPrimitive && definition.isString) {
            oid4vpJsonSerializer.parseToJsonElement(definition.content)
        } else {
            definition
        }
    val presentationDefinition = normalized.requireJsonObject("presentation_definition")
    presentationDefinition.requireOnlyKeys(setOf("id", "input_descriptors"), "presentation_definition")
    presentationDefinition.requiredString("id", "presentation_definition")

    val inputDescriptors = presentationDefinition.requiredArray("input_descriptors", "presentation_definition")
    require(inputDescriptors.isNotEmpty()) { "presentation_definition.input_descriptors must not be empty" }
    val descriptorIds = mutableSetOf<String>()
    inputDescriptors.forEachIndexed { index, descriptorElement ->
        val path = "presentation_definition.input_descriptors[$index]"
        val descriptor = descriptorElement.requireJsonObject(path)
        descriptor.requireOnlyKeys(setOf("id", "format", "constraints"), path)
        val descriptorId = descriptor.requiredString("id", path)
        require(descriptorIds.add(descriptorId)) { "$path.id must be unique" }

        val format = descriptor.requiredObject("format", path)
        format.requireOnlyKeys(setOf("mso_mdoc"), "$path.format")
        val msoMdoc = format.requiredObject("mso_mdoc", "$path.format")
        msoMdoc.requireOnlyKeys(setOf("alg"), "$path.format.mso_mdoc")
        val algorithms = msoMdoc.requiredArray("alg", "$path.format.mso_mdoc")
        require(algorithms.isNotEmpty()) { "$path.format.mso_mdoc.alg must not be empty" }
        algorithms.forEachIndexed { algorithmIndex, algorithm ->
            algorithm.requireJsonString("$path.format.mso_mdoc.alg[$algorithmIndex]")
        }

        val constraints = descriptor.requiredObject("constraints", path)
        constraints.requireOnlyKeys(setOf("limit_disclosure", "fields"), "$path.constraints")
        require(constraints.requiredString("limit_disclosure", "$path.constraints") == "required") {
            "$path.constraints.limit_disclosure must be 'required'"
        }
        val fields = constraints.requiredArray("fields", "$path.constraints")
        fields.forEachIndexed { fieldIndex, fieldElement ->
            val fieldPath = "$path.constraints.fields[$fieldIndex]"
            val field = fieldElement.requireJsonObject(fieldPath)
            field.requireOnlyKeys(setOf("path", "intent_to_retain"), fieldPath)
            val paths = field.requiredArray("path", fieldPath)
            require(paths.isNotEmpty()) { "$fieldPath.path must not be empty" }
            paths.forEachIndexed { pathIndex, pathElement ->
                val pathValue = pathElement.requireJsonString("$fieldPath.path[$pathIndex]")
                try {
                    assertedPathEntry(pathValue)
                } catch (expected: Exception) {
                    throw IllegalArgumentException("$fieldPath.path[$pathIndex] is not a direct namespace/element path", expected)
                }
            }
            field.requiredBoolean("intent_to_retain", fieldPath)
        }
    }
    return normalized
}

private fun JsonElement.requireJsonObject(path: String): JsonObject =
    this as? JsonObject ?: throw IllegalArgumentException("$path must be a JSON object")

private fun JsonObject.requireOnlyKeys(
    allowed: Set<String>,
    path: String,
) {
    val unknown = keys - allowed
    require(unknown.isEmpty()) { "$path contains unsupported member(s): ${unknown.sorted().joinToString(", ")}" }
}

private fun JsonObject.requiredMember(
    name: String,
    path: String,
): JsonElement =
    get(name) ?: throw IllegalArgumentException("$path.$name is required")

private fun JsonObject.requiredObject(
    name: String,
    path: String,
): JsonObject = requiredMember(name, path).requireJsonObject("$path.$name")

private fun JsonObject.requiredArray(
    name: String,
    path: String,
): JsonArray =
    requiredMember(name, path) as? JsonArray
        ?: throw IllegalArgumentException("$path.$name must be a JSON array")

private fun JsonObject.requiredString(
    name: String,
    path: String,
): String = requiredMember(name, path).requireJsonString("$path.$name")

private fun JsonObject.requiredBoolean(
    name: String,
    path: String,
): Boolean {
    val value = requiredMember(name, path) as? JsonPrimitive
    require(value != null && !value.isString && value.contentOrNull in setOf("true", "false")) {
        "$path.$name must be a JSON boolean"
    }
    return value.content == "true"
}

private fun JsonElement.requireJsonString(path: String): String {
    val value = this as? JsonPrimitive
    require(value != null && value.isString && !value.content.isBlank()) {
        "$path must be a non-empty JSON string"
    }
    return value.content
}
