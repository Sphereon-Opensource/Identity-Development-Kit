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

package com.sphereon.openid.oid4vp.universal.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.core.events.SessionEventService
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.ContentEncryptionAlgorithm
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KmsProviderCapabilities
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.QrCodeService
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.universal.CreateAuthRequestServiceCommand
import com.sphereon.openid.oid4vp.universal.CreateAuthorizationRequestInput
import com.sphereon.openid.oid4vp.universal.CreateAuthorizationRequestOutput
import com.sphereon.openid.oid4vp.universal.UniversalOid4vpConfigProvider
import com.sphereon.openid.oid4vp.universal.UniversalOid4vpEventTypes
import com.sphereon.openid.oid4vp.verifier.BuildAuthorizationRequestUriArgs
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.verifier.Oid4vpUriScheme
import com.sphereon.openid.oid4vp.verifier.Oid4vpVerifierService
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionCallbackConfig
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestObjectSigningConfig
import com.sphereon.openid.oid4vp.verifier.store.AuthorizationSessionStore
import com.sphereon.openid.oid4vp.verifier.store.ClientMetadataConfigurationStore
import com.sphereon.openid.oid4vp.verifier.store.DcqlQueryConfigurationStore
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.random.Random

/**
 * Typed service command implementation for creating authorization requests.
 *
 * POST /oid4vp/backend/auth/requests
 *
 * This implementation uses the Binary API v5 pattern with:
 * - Typed input: [CreateAuthorizationRequestInput] (JSON body)
 * - Typed output: [CreateAuthorizationRequestOutput]
 * - HTTP binding via [PublicApiCommand] on [CreateAuthRequestServiceCommand]
 *
 * The input is automatically deserialized from the request body by the BinaryCommandAdapter.
 * The output is automatically serialized to JSON.
 */
@Inject
@SingleIn(SessionScope::class)
class CreateAuthRequestServiceCommandImpl(
    execution: SessionExecution,
    private val oid4vpVerifierService: Oid4vpVerifierService,
    private val dcqlConfigStore: DcqlQueryConfigurationStore,
    private val clientMetadataConfigStore: ClientMetadataConfigurationStore,
    private val sessionEventService: SessionEventService,
    private val qrCodeService: QrCodeService,
    private val configProvider: UniversalOid4vpConfigProvider,
    private val requestObjectSigningConfig: RequestObjectSigningConfig,
    private val kms: KeyManagerService,
) : TypedServiceCommandAdapter<CreateAuthorizationRequestInput, CreateAuthorizationRequestOutput, IdkError>(
        commandId = CreateAuthRequestServiceCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<CreateAuthorizationRequestInput>(),
        outputTypeToken = typeToken<CreateAuthorizationRequestOutput>(),
    ),
    CreateAuthRequestServiceCommand {
    override val commandId: String get() = CreateAuthRequestServiceCommand.COMMAND_ID

    override suspend fun doExecute(
        args: CreateAuthorizationRequestInput,
        applyDuring: (CreateAuthorizationRequestInput) -> CreateAuthorizationRequestInput,
    ): IdkResult<CreateAuthorizationRequestOutput, IdkError> {
        val input = applyDuring(args)

        // 1. Validate input - must have either queryId or dcqlQuery
        val inputQueryId = input.queryId
        val inputDcqlQuery = input.dcqlQuery
        val inputClientMetadataId = input.clientMetadataId

        if (inputQueryId == null && inputDcqlQuery == null) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = "Either query_id or dcql_query must be provided",
                ),
            )
        }

        // 2. Resolve DCQL query (from config store or inline)
        val dcqlQuery =
            if (inputQueryId != null) {
                val config =
                    dcqlConfigStore.getByQueryId(inputQueryId).getOrNull()
                        ?: return Err(
                            IdkError.NOT_FOUND_ERROR(
                                message = "Query configuration not found: $inputQueryId",
                            ),
                        )
                if (!config.enabled) {
                    return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message = "Query configuration is disabled: $inputQueryId",
                        ),
                    )
                }
                config.dcqlQuery
            } else {
                inputDcqlQuery!!
            }

        // 3. Resolve client metadata (from config store or use default)
        val clientMetadataConfig =
            if (inputClientMetadataId != null) {
                clientMetadataConfigStore.getByClientMetadataId(inputClientMetadataId).getOrNull()
                    ?: return Err(
                        IdkError.NOT_FOUND_ERROR(
                            message = "Client metadata configuration not found: $inputClientMetadataId",
                        ),
                    )
            } else {
                // Use first available client metadata config as default
                val allConfigs = clientMetadataConfigStore.getAll().getOrNull()
                allConfigs?.values?.firstOrNull()
            }

        // Pre-generate the session correlation id BEFORE generating the JARM key, so the
        // JWK we publish in client_metadata can carry kid = correlationId. Per OID4VP §8.3
        // the wallet echoes the JWK's kid in the JWE header; the response endpoint then
        // uses JWE.kid → correlationId for a direct session lookup (no secondary index in
        // the session store needed).
        val correlationId =
            input.state?.takeIf { it.isNotBlank() }
                ?: ByteArray(16).also { Random.nextBytes(it) }.encodeToBase64Url()

        // Resolve the requested client-identifier scheme first — when set, the signing
        // config returns a binding whose JOSE header / prefix match. Same KMS alias /
        // cert / key serves all schemes; only the client_id prefix and JOSE header switch.
        val derivedFromSigner =
            if (requestObjectSigningConfig.enabled) {
                requestObjectSigningConfig.resolveSignerBinding(input.clientIdScheme)?.clientId
            } else {
                null
            }
        // Source of truth for the verifier's client_id, in priority order:
        //   1. Explicit `input.clientId` from the request body.
        //   2. Stored ClientMetadataConfig.
        //   3. The signed-JAR signer binding (`x509_hash:<hash>`, `x509_san_dns:<dns>`,
        //      `decentralized_identifier:did:...`) — the same identifier the JAR will
        //      carry per OID4VP §5.9.3, so deriving here keeps the outer client_id
        //      consistent with the signed payload's `iss` and the JOSE header's `kid`/`x5c`.
        val clientId =
            input.clientId
                ?: clientMetadataConfig?.clientId
                ?: derivedFromSigner
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message = "client_id must be provided or configured",
                    ),
                )

        // 4. Generate nonce
        val nonce = generateNonce()

        // 5. Determine response URI: deployment config > derived default.
        // Note: prior code read ClientMetadata.baseMetadata.redirectUris as a fallback,
        // but OID4VP §11.1 client_metadata doesn't define `redirect_uris` — that's OAuth2
        // RFC 7591. response_uri sourcing belongs to verifier deployment configuration.
        val responseUri =
            configProvider.getConfig().responseUri
                ?: "$clientId/response"

        // 6. Resolve client_id_scheme: explicit > detect from client_id prefix > default
        val resolvedScheme =
            input.clientIdScheme
                ?: ClientIdScheme.fromClientId(clientId)

        // 7. Create authorization request via existing verifier service.
        // Honour the caller's response_mode (default `direct_post`). HAIP requires
        // `direct_post.jwt` (encrypted response) — the input drives this; the verifier
        // doesn't gate on a profile flag because callers select it per-request.
        val resolvedResponseMode =
            input.responseMode?.takeIf { it.isNotBlank() }?.let { raw ->
                ResponseMode.entries.firstOrNull { it.value.equals(raw, ignoreCase = true) }
                    ?: return Err(
                        IdkError.ILLEGAL_ARGUMENT_ERROR(
                            message =
                                "Unknown response_mode '$raw'. Supported: " +
                                    ResponseMode.entries.joinToString(", ") { it.value },
                        ),
                    )
            } ?: ResponseMode.DIRECT_POST
        // For direct_post.jwt the wallet encrypts the response with the verifier's
        // public key. Generate an ephemeral ECDH-ES P-256 keypair in the dedicated
        // `ephemeral` KMS provider (memory-backed, APP-scoped — see oid4vp-verifier.yml)
        // so it never touches the persistent PKCS12 keystore. The alias is persisted on
        // the AuthorizationSession so the response endpoint can resolve the private key
        // back from the KMS at decryption time. No key material crosses session storage.
        val jarmKey: JarmEphemeralKey? =
            if (resolvedResponseMode == ResponseMode.DIRECT_POST_JWT) {
                val keyResult =
                    kms.generateKeyResult(
                        providerId = EPHEMERAL_PROVIDER_ID,
                        alias = "jarm-enc-${ByteArray(16).also { Random.nextBytes(it) }.encodeToBase64Url()}",
                        use = JwkUse.enc,
                        keyOperations = arrayOf(KeyOperations.DERIVE_KEY),
                        alg = SignatureAlgorithm.ECDSA_SHA256,
                        keyVisibility = KeyVisibility.PRIVATE,
                    )
                if (keyResult.isErr) {
                    return Err(
                        IdkError.UNKNOWN_ERROR(
                            message = "Failed to generate ephemeral JARM encryption key in '$EPHEMERAL_PROVIDER_ID' KMS provider: ${keyResult.error.message}",
                        ),
                    )
                }
                val pair =
                    keyResult.value.keyPair
                        ?: return Err(IdkError.UNKNOWN_ERROR(message = "Ephemeral JARM key generation returned no key pair"))

                // Pull the provider's capabilities so the metadata advertises only what the
                // verifier can actually decrypt. Hardcoding alg/enc lists here would drift
                // away from what DecryptJweCommandImpl supports the moment a new alg lands;
                // capabilities-driven advertisement keeps the metadata honest.
                val capabilities =
                    resolveProviderCapabilities(EPHEMERAL_PROVIDER_ID)
                        ?: return Err(
                            IdkError.UNKNOWN_ERROR(
                                message = "KMS provider '$EPHEMERAL_PROVIDER_ID' returned no capabilities — cannot advertise JARM enc algorithm support",
                            ),
                        )

                JarmEphemeralKey(
                    alias = pair.alias,
                    providerId = pair.providerId,
                    publicJwk =
                        pair.jose.publicJwk.copy(
                            // Pin kid to the session's correlationId (NOT the KMS alias). Per
                            // OID4VP §8.3 the wallet echoes this kid in the JWE header — by
                            // making it equal to the correlationId the response endpoint can
                            // resolve session directly via store.getByCorrelationId(jwe.kid),
                            // no secondary index over the KMS alias needed.
                            kid = correlationId,
                            // Public-only payload: strip the private scalar even if the KMS
                            // returned the full key. Defensive — the JWK must never be
                            // emitted on the wire with `d`.
                            d = null,
                            // Mark for encryption use so wallets and proxies don't reuse it
                            // for signing. RFC 7517 §4.2.
                            use = "enc",
                            // Override KMS-set `alg` (ES256, signing) with the JWE key-agreement
                            // alg the wallet must use. HAIP §5 + OID4VP §8.3 require alg=ECDH-ES
                            // on the encryption JWK; without this the wallet's
                            // `VP1FinalEncryptVPResponse: Failed to derive symmetric key` fires.
                            alg = com.sphereon.crypto.core.jose.JwaAlgorithm.ECDH_ES,
                        ),
                    supportedEncs = capabilities.contentEncryptionAlgorithms.toList(),
                    supportsKeyAgreement = capabilities.supportsKeyAgreement(),
                )
            } else {
                null
            }

        // Build the OID4VP §11.1 client_metadata. Caller-supplied ClientMetadataConfig
        // wins; otherwise emit the spec-defined default (vp_formats_supported + jwks +
        // encrypted_response_enc_values_supported when encryption is in play).
        val effectiveClientMetadata =
            clientMetadataConfig?.clientMetadata ?: buildOid4vpClientMetadata(jarmKey)

        val createArgs =
            CreateAuthorizationRequestArgs(
                dcqlQuery = dcqlQuery,
                clientId = clientId,
                responseUri = responseUri,
                responseMode = resolvedResponseMode,
                nonce = nonce,
                // Use the pre-generated correlationId. CreateAuthorizationRequestCommandImpl
                // copies state → effectiveState (the AuthorizationSession.correlationId), so
                // store-by-correlationId == lookup-by-JWE.kid.
                state = correlationId,
                clientMetadata = effectiveClientMetadata,
                clientIdScheme = resolvedScheme,
                jarmEncryptionKeyAlias = jarmKey?.alias,
                jarmEncryptionKeyProviderId = jarmKey?.providerId,
                // OID4VP §5.10: surfaces as `&request_uri_method=…` on the outer OAuth2 URL.
                // Caller-supplied; we don't second-guess (verifier-impl validates the value).
                requestUriMethod = input.requestUriMethod?.takeIf { it.isNotBlank() },
            )

        val created =
            oid4vpVerifierService.createAuthorizationRequest(createArgs).getOrElse { error ->
                return Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Failed to create authorization request: ${error.message.defaultMessage}",
                    ),
                )
            }

        val sessionId =
            created.sessionId
                ?: return Err(IdkError.UNKNOWN_ERROR(message = "Session ID not generated"))

        // 8. Update session with callback config if provided
        val inputCallback = input.callback
        if (inputCallback != null) {
            val callbackConfig =
                AuthorizationSessionCallbackConfig(
                    url = inputCallback.url,
                    statuses = inputCallback.statuses,
                )
            // Update the session with callback config
            oid4vpVerifierService.authorizationSessionStore.getByCorrelationId(sessionId).getOrNull()?.let { session ->
                oid4vpVerifierService.authorizationSessionStore.put(
                    sessionId,
                    session.copy(callback = callbackConfig),
                    input.ttlSeconds ?: AuthorizationSessionStore.DEFAULT_TTL_SECONDS,
                )
            }
        }

        // 9. Build authorization request URI
        // Validate request_uri_base is an HTTP(S) URL — it controls the URL the wallet
        // fetches the signed JAR from, not the wallet-deeplink scheme. A custom scheme
        // (`oid4vp://`, `haip://`) here would produce nonsense like
        // `oid4vp:/oid4vp/request-uri/<id>` that the wallet can't actually call.
        input.requestUriBase?.let { base ->
            val lower = base.lowercase()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message =
                            "request_uri_base must be an http(s) URL (got '$base'). " +
                                "The outer deeplink scheme is configured via 'wallet_uri_scheme'.",
                    ),
                )
            }
        }
        // walletUriScheme may be:
        //   - bare scheme name (`openid4vp`, `haip`, `oid4vp`) → maps to Oid4vpUriScheme enum
        //   - full URL (`https://demo.certification.openid.net/.../authorize`) for web wallets,
        //     universal links, or app links → passed through as a deeplink prefix
        val rawWalletUri = input.walletUriScheme?.trim()?.takeIf { it.isNotEmpty() }
        val deeplinkPrefix: String?
        val outerScheme: Oid4vpUriScheme
        if (rawWalletUri == null) {
            deeplinkPrefix = null
            outerScheme = Oid4vpUriScheme.OPENID4VP
        } else if (rawWalletUri.contains("://") && !rawWalletUri.endsWith("://")) {
            // Full URL — web-wallet endpoint, universal link, or app link.
            deeplinkPrefix = rawWalletUri
            outerScheme = Oid4vpUriScheme.OPENID4VP // ignored when deeplinkPrefix is set
        } else {
            // Bare scheme name (with or without trailing `://`).
            val schemeName = rawWalletUri.removeSuffix("://")
            outerScheme = Oid4vpUriScheme.entries.firstOrNull { it.scheme.equals(schemeName, ignoreCase = true) }
                ?: return Err(
                    IdkError.ILLEGAL_ARGUMENT_ERROR(
                        message =
                            "Unknown wallet_uri_scheme '$schemeName'. Supported: " +
                                Oid4vpUriScheme.entries.joinToString(", ") { it.scheme } +
                                " — or pass a full URL (e.g. https://wallet.example.com/authorize) for a web-wallet endpoint.",
                    ),
                )
            deeplinkPrefix = null
        }
        val requestUri =
            oid4vpVerifierService
                .buildAuthorizationRequestUri(
                    BuildAuthorizationRequestUriArgs(
                        request = created.request,
                        scheme = outerScheme,
                        useRequestUri = true,
                        requestUri = buildRequestUri(sessionId, input.requestUriBase),
                        deeplinkPrefix = deeplinkPrefix,
                    ),
                ).getOrElse { error ->
                    return Err(
                        IdkError.UNKNOWN_ERROR(
                            message = "Failed to build request URI: ${error.message.defaultMessage}",
                        ),
                    )
                }.value

        // 10. Generate QR code as data URI (only if qr_code options provided)
        val qrUri =
            input.qrCodeOptions?.let { qrOptions ->
                qrCodeService.generateDataUri(requestUri, qrOptions)
            }

        // 11. Emit SESSION_CREATED event
        emitSessionCreatedEvent(sessionId, input.queryId, requestUri)

        // 12. Build response per Universal OID4VP spec
        val output =
            CreateAuthorizationRequestOutput(
                correlationId = sessionId,
                queryId = input.queryId,
                requestUri = requestUri,
                statusUri = "/oid4vp/backend/auth/requests/$sessionId",
                qrUri = qrUri,
            )

        return Ok(output)
    }

    /**
     * Generate a 32-byte (256-bit entropy) nonce, base64url-encoded without padding (43 chars).
     *
     * 64-char hex was producing the OIDF `CheckNonceMaximumLength` warning ("Nonce contains
     * in excess of 43 characters. This may introduce interoperability issues."). Same entropy,
     * tighter encoding — every char is in the RFC 3986 unreserved set, so the verifier-side
     * `CheckForInvalidCharsInNonce` still passes.
     */
    private fun generateNonce(): String {
        val bytes = ByteArray(32)
        Random.nextBytes(bytes)
        return bytes.encodeToBase64Url()
    }

    /**
     * Build the verifier's `client_metadata` per OID4VP 1.0 final. The shape is the spec's
     * narrow set: §11.1 `vp_formats_supported`, plus §5.1 / §8.3 `jwks` and
     * `encrypted_response_enc_values_supported` when encryption is requested.
     *
     * No JARM singular fields (not OID4VP-defined: alg lives on the JWK), no OAuth2
     * ClientRegistration fields (the wallet "MUST ignore unrecognized parameters", but
     * emitting them is non-canonical noise that confuses conformance and pollutes the JAR).
     */
    private fun buildOid4vpClientMetadata(jarmKey: JarmEphemeralKey?): com.sphereon.openid.oid4vp.common.ClientMetadata {
        val vpFormats =
            mapOf(
                "dc+sd-jwt" to
                    com.sphereon.openid.oid4vp.common.VpFormatInfo(
                        sdJwtAlgValuesSupported = listOf("ES256"),
                        kbJwtAlgValuesSupported = listOf("ES256"),
                    ),
                "mso_mdoc" to
                    com.sphereon.openid.oid4vp.common.VpFormatInfo(
                        // -7 = ES256 in IANA COSE Algorithms (RFC 8152).
                        issuerAuthAlgValuesSupported = listOf(-7),
                        deviceAuthAlgValuesSupported = listOf(-7),
                    ),
            )
        return com.sphereon.openid.oid4vp.common.ClientMetadata(
            jwks =
                jarmKey?.let {
                    com.sphereon.crypto.core.jose
                        .JwkSet(arrayOf(it.publicJwk))
                },
            vpFormatsSupported = vpFormats,
            // KMS-capability-driven enc advertisement. The JWE `alg` is conveyed via
            // jwks[*].alg (set on the public JWK above), per OID4VP §8.3 — there's no
            // top-level alg list in spec-correct client_metadata.
            encryptedResponseEncValuesSupported =
                jarmKey
                    ?.supportedEncs
                    ?.takeIf { it.isNotEmpty() }
                    ?.map { it.identifier },
        )
    }

    /**
     * Bundle of the just-generated ephemeral encryption key plus the KMS-advertised
     * algorithms the verifier can actually decrypt under. The KMS holds the private
     * material under [alias] in the [providerId] provider; only [publicJwk] crosses the
     * wire (in client_metadata.jwks). The session stores [alias] + [providerId] so the
     * response endpoint can resolve the private half via the KMS at decrypt time.
     */
    private data class JarmEphemeralKey(
        val alias: String,
        val providerId: String,
        val publicJwk: Jwk,
        val supportedEncs: List<ContentEncryptionAlgorithm>,
        val supportsKeyAgreement: Boolean,
    )

    /**
     * Look up the named provider's capabilities so client_metadata can advertise the
     * algorithms the verifier can actually consume. `getAllCapabilities` is the only
     * KMS API today that returns capabilities keyed by providerId; query and pick.
     */
    private suspend fun resolveProviderCapabilities(providerId: String): KmsProviderCapabilities? {
        val result = kms.getAllCapabilities()
        if (result.isErr) return null
        return result.value.capabilities[providerId]
    }

    private companion object {
        const val EPHEMERAL_PROVIDER_ID: String = "ephemeral"
    }

    /**
     * Build the request URI for an OID4VP authorization request.
     *
     * Resolution order for base URL:
     * 1. [inputRequestUriBase] from the API call (caller override)
     * 2. [UniversalOid4vpConfig.externalBaseUrl] from configuration
     * 3. Falls back to a relative path if neither is set
     */
    private fun buildRequestUri(
        correlationId: String,
        inputRequestUriBase: String?,
    ): String {
        val base = (inputRequestUriBase ?: configProvider.getConfig().externalBaseUrl)?.trimEnd('/')
        return if (base != null) {
            "$base/oid4vp/request-uri/$correlationId"
        } else {
            "/oid4vp/request-uri/$correlationId"
        }
    }

    private suspend fun emitSessionCreatedEvent(
        correlationId: String,
        queryId: String?,
        requestUri: String,
    ) {
        try {
            sessionEventService.emit(
                sessionEventService
                    .eventBuilder()
                    .type(UniversalOid4vpEventTypes.SESSION_CREATED)
                    .origin(CreateAuthRequestServiceCommand.COMMAND_ID)
                    .payload(
                        buildJsonObject {
                            put("correlationId", correlationId)
                            queryId?.let { put("queryId", it) }
                            put("requestUri", requestUri)
                        },
                    ).build(),
            )
        } catch (expected: Exception) {
            // Best effort - don't fail the request if event emission fails
            log.warn("Failed to emit SESSION_CREATED event: ${expected.message}")
        }
    }
}
