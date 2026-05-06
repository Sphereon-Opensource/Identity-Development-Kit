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
import com.sphereon.mdoc.transport.AbstractMdocTransport
import com.sphereon.mdoc.transport.oid4vp.Oid4vpError
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.common.oid4vpNonce
import com.sphereon.openid.oid4vp.common.responseUri
import com.sphereon.openid.oid4vp.holder.Oid4vpHolderService
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
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
 * 4. Resolve DCQL (default) or Presentation Definition (legacy)
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
 * - **Request Format**: JWT (Authorization Request with DCQL or legacy Presentation Definition) vs CBOR (DeviceRequest)
 * - **Response Format**: JWT (Authorization Response with `vp_token`) vs CBOR (DeviceResponse)
 * - **Encryption**: JARM (JWT encryption) vs session-level encryption
 * - **SessionTranscript**: Uses OID4VP §B.2.6 OpenID4VPHandover (sha-256 of CBOR-encoded
 *   `[clientId, nonce, jwkThumbprint OR null, responseUri]`) vs RestApiHandover
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
 * @param engagementData Engagement data (not used for OID4VP - uses OID4VPHandover instead)
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
                val hasDcqlQuery = parsedRequest.additionalParameters.containsKey("dcql_query")
                val hasPresentationDefinition =
                    parsedRequest.additionalParameters.containsKey("presentation_definition") ||
                        parsedRequest.additionalParameters.containsKey("presentation_definition_uri")
                usePresentationExchange = hasPresentationDefinition && !hasDcqlQuery

                val resolvedRequest =
                    oid4vpHolder
                        .resolveAuthorizationRequest(parsedRequest)
                        .getOrElse { return@withLock Err(it) }

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
                this.authorizationRequestNonce = parsedRequest.oid4vpNonce
                this.responseUri = parsedRequest.responseUri ?: parsedRequest.redirectUri

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

            // For OID4VP, the "request" is DCQL (default) or a Presentation Definition (legacy)
            // We need to convert it to a DeviceRequest format for compatibility with existing flow
            val resolvedRequest =
                resolvedRequest
                    ?: error("Authorization Request not resolved")

            val presentationDef = presentationDefinition

            val deviceRequest =
                if (presentationDef != null) {
                    // Convert Presentation Definition to DocRequest
                    val docRequest = presentationDef.toDocRequest()
                    DeviceRequest(
                        docRequests = arrayOf(docRequest),
                        oid4vpRequest = presentationDef,
                        macKeys = null,
                        original = null,
                    )
                } else {
                    val dcqlQuery =
                        resolvedRequest.dcqlQuery
                            ?: error("No Presentation Definition or DCQL query resolved")
                    DcqlMdocRequestMapper.toDeviceRequest(dcqlQuery, log)
                }

            log.info("Created DeviceRequest from OID4VP request (presentation_exchange=$usePresentationExchange)")
            deviceRequestCborCodec
                .encode(deviceRequest)
                .getOrElse { throw it.toException() }
        }
    }

    /**
     * Get the OID4VP session transcript per §B.2.6.
     *
     * The handover is `["OpenID4VPHandover", sha256(handoverInfoBytes)]` where
     * `handoverInfo = [client_id, nonce, jwkThumbprint OR null, response_uri]`. The
     * thumbprint slot is non-null when the response will be encrypted (`direct_post.jwt`,
     * `dc_api.jwt`) and null otherwise.
     *
     * Must be called after open() has completed successfully so the auth-request inputs
     * (`response_uri`, `nonce`) are populated.
     *
     * @param verifierEncryptionJwkThumbprint Raw 32-byte SHA-256 thumbprint (RFC 7638) of
     *   the verifier's encryption-key JWK; null for unencrypted response modes.
     */
    fun getSessionTranscript(verifierEncryptionJwkThumbprint: ByteArray? = null): com.sphereon.mdoc.transfer.reader.SessionTranscript {
        requireOpen()

        val clientId = options.clientId
        val responseUri =
            this.responseUri
                ?: error("response_uri not set - Authorization Request not fetched")
        val nonce =
            this.authorizationRequestNonce
                ?: error("nonce not set - Authorization Request not fetched")

        log.info("Creating OID4VP session transcript per §B.2.6")
        log.debug("clientId: $clientId")
        log.debug("responseUri: $responseUri")
        log.debug("authzRequestNonce: ${nonce.take(16)}...")
        log.debug("jwkThumbprint: ${if (verifierEncryptionJwkThumbprint != null) "present (${verifierEncryptionJwkThumbprint.size} bytes)" else "null (unencrypted)"}")

        val handover =
            com.sphereon.mdoc.oid4vp.oid4vpHandoverFromInputs(
                clientId = clientId,
                nonce = nonce,
                jwkThumbprint = verifierEncryptionJwkThumbprint,
                responseUri = responseUri,
            )

        val sessionTranscript =
            com.sphereon.mdoc.transfer.reader.SessionTranscript
                .fromOid4vpHandover(handover)

        log.info("OID4VP session transcript created")
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

                val submissionResult =
                    if (usePresentationExchange) {
                        val authorizationResponse = buildPresentationAuthorizationResponse(deviceResponse, resolvedRequest)
                        oid4vpHolder
                            .submitAuthorizationResponse(
                                resolvedRequest,
                                authorizationResponse,
                                ResponseMode.DIRECT_POST_JWT,
                            ).getOrElse { throw it.toException() }
                    } else {
                        val selectedCredentials = buildSelectedCredentials(deviceResponse, resolvedRequest)
                        val authorizationResponse =
                            oid4vpHolder
                                .createAuthorizationResponse(resolvedRequest, selectedCredentials)
                                .getOrElse { throw it.toException() }
                        oid4vpHolder
                            .submitAuthorizationResponse(resolvedRequest, authorizationResponse, null)
                            .getOrElse { throw it.toException() }
                    }

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
        mdocNonce: String = Uuid.v4String(),
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

                log.info("Executing OID4VP holder flow with ${availableDocuments.size} available documents")

                val holderFlow = Oid4vpHolderFlow(oid4vpService, AppNoLogService())
                val result =
                    holderFlow.processAuthorizationRequest(
                        presentationDefinition = presentationDef,
                        availableDocuments = availableDocuments,
                        clientId = options.clientId,
                        responseUri = responseUri,
                        authorizationRequestNonce = nonce,
                        mdocNonce = mdocNonce,
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
                options.responseUri?.let {
                    put("response_uri", it)
                    put("redirect_uri", it)
                }
                options.nonce?.let { put("nonce", it) }
                options.presentationDefinitionUri?.let { put("presentation_definition_uri", it) }
            }

        val query =
            params.entries.joinToString("&") { (key, value) ->
                "$key=${value.encodeURLParameter()}"
            }
        return "openid4vp://?$query"
    }

    private suspend fun resolvePresentationDefinition(request: AuthorizationRequest): Oid4VPPresentationDefinition? {
        val inlineDefinition = request.additionalParameters["presentation_definition"]
        if (inlineDefinition != null) {
            return parsePresentationDefinition(inlineDefinition)
        }

        val definitionUri = request.additionalParameters["presentation_definition_uri"]?.jsonPrimitive?.content
        if (!definitionUri.isNullOrBlank()) {
            return fetchPresentationDefinition(definitionUri)
        }

        return null
    }

    private fun parsePresentationDefinition(definition: JsonElement): Oid4VPPresentationDefinition =
        oid4vpJsonSerializer.decodeFromJsonElement(
            Oid4VPPresentationDefinition.serializer() as DeserializationStrategy<Oid4VPPresentationDefinition>,
            definition,
        )

    private suspend fun fetchPresentationDefinition(uri: String): Oid4VPPresentationDefinition {
        log.debug("Fetching Presentation Definition from: $uri")

        val response =
            httpClient.get(uri) {
                header("Accept", "application/json")
                header("User-Agent", "mdoc-wallet-oid4vp/1.0")
            }

        if (!response.status.isSuccess()) {
            throw Oid4vpError.ServerError(
                uri = uri,
                statusCode = response.status.value,
                serverMessage = "Failed to fetch Presentation Definition: ${response.status}",
            )
        }

        val json = response.bodyAsText()
        return oid4vpJsonSerializer.decodeFromString(
            Oid4VPPresentationDefinition.serializer() as DeserializationStrategy<Oid4VPPresentationDefinition>,
            json,
        )
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

    private fun buildSelectedCredentials(
        deviceResponse: DeviceResponse,
        resolvedRequest: ResolvedOid4vpRequest,
    ): List<SelectedCredential> {
        val presentation = deviceResponse.encodeToBase64UrlNoPadding()
        val ids =
            resolvedRequest.dcqlQuery
                ?.credentials
                ?.map { it.id }
                .orEmpty()
                .ifEmpty { listOf("mdoc") }

        return ids.map { id ->
            SelectedCredential(
                credentialQueryId = id,
                credentialId = "mdoc",
                presentation = presentation,
                format = Oid4VPFormatIdentifier.MSO_MDOC.value,
            )
        }
    }

    private data class DcqlClaimPath(
        val path: List<String>,
        val intentToRetain: Boolean? = null,
    )
}
