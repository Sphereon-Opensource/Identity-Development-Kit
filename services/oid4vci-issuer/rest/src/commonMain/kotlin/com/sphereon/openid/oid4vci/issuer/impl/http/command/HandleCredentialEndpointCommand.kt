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

package com.sphereon.openid.oid4vci.issuer.impl.http.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.command.HttpEndpointCommand
import com.sphereon.core.api.http.command.HttpEndpointCommandAdapter
import com.sphereon.core.api.http.command.headerValuesIgnoreCase
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.response.jsonResponse
import com.sphereon.crypto.jose.jwe.DecryptJweCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.common.model.Oid4vciErrors
import com.sphereon.openid.oid4vci.issuer.command.HandleCredentialRequestArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleCredentialRequestCommand
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.impl.encryption.CredentialResponseEncryptor
import com.sphereon.openid.oid4vci.issuer.impl.encryption.MaybeEncryptedCredentialResponse
import com.sphereon.openid.oid4vci.rest.Oid4vciRestConfigProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString

/**
 * Endpoint command for handling credential requests.
 *
 * POST /credential
 *
 * Handles both JSON and JWE-encrypted request bodies per OID4VCI 1.1.
 */
interface HandleCredentialEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.protocol.credential"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/credential",
                consumes = setOf(MediaType.ApplicationJson, MediaType.Custom(ACCEPT_JWT)),
                produces = setOf(MediaType.ApplicationJson, MediaType.Custom(ACCEPT_JWT)),
                operationId = "handleCredentialRequest",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer"),
                summary = "Handle a credential request",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleCredentialEndpointCommand>())
class HandleCredentialEndpointCommandImpl(
    execution: SessionExecution,
    private val handleCredentialRequestCommand: HandleCredentialRequestCommand,
    private val decryptJweCommand: DecryptJweCommand,
    private val configProvider: Oid4vciIssuerConfigProvider,
    private val restConfigProvider: Oid4vciRestConfigProvider,
    private val publicUrlResolver: Oid4vciIssuerPublicUrlResolver,
    private val credentialResponseEncryptor: CredentialResponseEncryptor,
) : HttpEndpointCommandAdapter(
        id = HandleCredentialEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = HandleCredentialEndpointCommand.ENDPOINT,
    ),
    HandleCredentialEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        // Keep the credential request path aligned with issuer metadata. Hybrid/config providers
        // may build tenant design-backed credential configurations lazily, and the snapshot below
        // must see the same prepared view that /.well-known/openid-credential-issuer advertises.
        configProvider.prepare()
        val publicUrls =
            publicUrlResolver
                .resolve(request, configProvider, restConfigProvider)
                .getOrElse { return Err(it) }

        val accessToken =
            extractAccessToken(request)
                ?: return Err(IdkError.UNAUTHORIZED_ERROR(message = "Missing or invalid Authorization header"))
        // RFC 9449 §4.1: exactly one `DPoP` HTTP header is REQUIRED — the resource server
        // MUST refuse multi-value. Inspect the multi-value view (preserved by the transport
        // adapter) rather than the joined scalar [headers], because the join semantics across
        // intermediaries (Caddy, Go `net/http`, browsers) are not consistent enough to detect
        // duplicates by parsing the joined string.
        val dpopValues = request.headerValuesIgnoreCase("DPoP")
        if (dpopValues.size > 1) {
            return Err(
                IdkError.UNAUTHORIZED_ERROR(
                    message = "Multiple DPoP HTTP headers presented (${dpopValues.size}); RFC 9449 §4.1 requires exactly one",
                ),
            )
        }
        // Caddy / Go's `net/http` canonicalizes incoming header names to MIME-canonical form
        // (`DPoP` → `Dpop`); resolve case-insensitively per RFC 9110 §5.1.
        val dpopProof = dpopValues.singleOrNull()

        val requestBody =
            decryptRequestIfNeeded(request, decryptJweCommand, configProvider.credentialRequestDecryptionKey())
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to decrypt JWE request body"))

        log.info("Credential request body: $requestBody")

        val credentialRequest =
            try {
                protocolJson.decodeFromString<CredentialRequest>(requestBody)
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed credential request: ${expected.message}"))
            }

        // OID4VCI 1.0 §11.2.4 + HAIP §8: when the issuer advertises
        // `credential_response_encryption.encryption_required = true` the wallet MUST send a
        // `credential_response_encryption` object, or the issuer rejects with
        // `invalid_encryption_parameters` (HTTP 400). Enforced here — before format-handler
        // dispatch — because the failure is purely transport-level and the orchestrator is
        // grant-/format-aware. The encryptor itself separately validates the alg/enc/zip
        // values once a request *does* carry the encryption block.
        val responseEncryptionMetadata = configProvider.credentialResponseEncryption
        if (responseEncryptionMetadata?.encryptionRequired == true && credentialRequest.credentialResponseEncryption == null) {
            return Err(
                IdkError.fromString(
                    message = "Credential response encryption is required by issuer policy but the request did not include `credential_response_encryption`",
                    code = Oid4vciErrors.INVALID_ENCRYPTION_PARAMETERS,
                ),
            )
        }

        val response =
            handleCredentialRequestCommand
                .execute(
                    HandleCredentialRequestArgs(
                        accessToken = accessToken,
                        dpopProof = dpopProof,
                        credentialRequest = credentialRequest,
                        issuerIdentifier = configProvider.issuerIdentifier,
                        credentialConfigurations = configProvider.credentialConfigurations,
                        // RFC 9449 §7.1: the AS bridge needs the publicly-visible request URL
                        // to verify DPoP `htu` against the proof. The wallet sets `htu` to the
                        // value advertised in `credential_endpoint` metadata, which is built
                        // from the issuer identifier (`{issuerIdentifier}/credential`). Using
                        // the metadata-derived URL ensures we compare apples-to-apples even
                        // when the OID4VCI HTTP adapter mounts under a sub-path
                        // (`/oid4vci/credential`) — `request.path` arrives at this command
                        // already stripped of the adapter's `adapterBasePath`, so a host-only
                        // reconstruction would miss the `/oid4vci` prefix the wallet signed.
                        httpUrl = "${publicUrls.endpointBaseUrl}/credential",
                        httpMethod = request.method,
                    ),
                ).getOrElse { error ->
                    log.info("Credential error: ${error.code} - ${error.message.defaultMessage}")
                    return Err(error)
                }

        // OID4VCI 1.0 §8.3.4: a deferred-issuance response carries `transaction_id` (and
        // `interval`) without `credentials` — the issuer MUST return HTTP 202 so the wallet
        // knows to poll `/deferredCredential`. Encryption applies only to issued credential
        // bodies, so the deferral envelope is rendered as plain JSON. Mirrors the predicate
        // used by [HandleDeferredCredentialEndpointCommandImpl] for §10.2 polling.
        val isPending = response.transactionId != null && response.credentials == null
        if (isPending) {
            val pendingJson = protocolJson.encodeToString(response)
            log.info("Credential response (deferred): $pendingJson")
            return Ok(
                GenericHttpResponse(
                    statusCode = 202,
                    headers = JSON_HEADERS,
                    body = pendingJson,
                ),
            )
        }

        // OID4VCI 1.0 §8.3.5: when the wallet supplied `credential_response_encryption`, the
        // entire response body is a single JWE-compact string with `Content-Type:
        // application/jwt`. Otherwise the body is the JSON-serialized [CredentialResponse].
        val maybeEncrypted =
            credentialResponseEncryptor
                .encryptIfRequested(response, credentialRequest.credentialResponseEncryption)
                .getOrElse { error -> return Err(error) }

        return when (maybeEncrypted) {
            is MaybeEncryptedCredentialResponse.Encrypted -> {
                Ok(
                    GenericHttpResponse(
                        statusCode = 200,
                        headers = JWT_HEADERS,
                        body = maybeEncrypted.jweCompact,
                    ),
                )
            }

            is MaybeEncryptedCredentialResponse.Plain -> {
                val responseJson = protocolJson.encodeToString(maybeEncrypted.response)
                log.info("Credential response: $responseJson")
                Ok(jsonResponse(200, responseJson))
            }
        }
    }
}
