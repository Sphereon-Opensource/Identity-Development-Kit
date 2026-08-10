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
import com.sphereon.openid.oid4vci.common.model.DeferredCredentialRequest
import com.sphereon.openid.oid4vci.issuer.command.HandleDeferredCredentialRequestArgs
import com.sphereon.openid.oid4vci.issuer.command.HandleDeferredCredentialRequestCommand
import com.sphereon.openid.oid4vci.issuer.impl.encryption.CredentialResponseEncryptor
import com.sphereon.openid.oid4vci.issuer.impl.encryption.MaybeEncryptedCredentialResponse
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString

/**
 * Endpoint command for handling deferred credential requests.
 *
 * POST /deferredCredential
 *
 * OID4VCI 1.1 Section 10.2: pending responses carry transaction_id + interval
 * but no credential — return 202, otherwise 200.
 */
interface HandleDeferredCredentialEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.protocol.deferred"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.POST,
                pathPattern = "/deferredCredential",
                consumes = setOf(MediaType.ApplicationJson, MediaType.Custom(ACCEPT_JWT)),
                produces = setOf(MediaType.ApplicationJson, MediaType.Custom(ACCEPT_JWT)),
                operationId = "handleDeferredCredentialRequest",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer"),
                summary = "Handle a deferred credential request",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<HandleDeferredCredentialEndpointCommand>())
class HandleDeferredCredentialEndpointCommandImpl(
    execution: SessionExecution,
    private val handleDeferredCommand: HandleDeferredCredentialRequestCommand,
    private val decryptJweCommand: DecryptJweCommand,
    private val credentialResponseEncryptor: CredentialResponseEncryptor,
    private val configProvider: com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider,
) : HttpEndpointCommandAdapter(
        id = HandleDeferredCredentialEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = HandleDeferredCredentialEndpointCommand.ENDPOINT,
    ),
    HandleDeferredCredentialEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        val accessToken =
            extractAccessToken(request)
                ?: return Err(IdkError.UNAUTHORIZED_ERROR(message = "Missing or invalid Authorization header"))
        // RFC 9449 §4.1: exactly one `DPoP` header is REQUIRED.
        val dpopValues = request.headerValuesIgnoreCase("DPoP")
        if (dpopValues.size > 1) {
            return Err(
                IdkError.UNAUTHORIZED_ERROR(
                    message = "Multiple DPoP HTTP headers presented (${dpopValues.size}); RFC 9449 §4.1 requires exactly one",
                ),
            )
        }
        val dpopProof = dpopValues.singleOrNull()

        val requestBody =
            decryptRequestIfNeeded(request, decryptJweCommand, configProvider.credentialRequestDecryptionKey())
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to decrypt JWE request body"))

        val deferredRequest =
            try {
                protocolJson.decodeFromString<DeferredCredentialRequest>(requestBody)
            } catch (expected: Exception) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Malformed deferred credential request: ${expected.message}"))
            }

        val response =
            handleDeferredCommand
                .execute(
                    HandleDeferredCredentialRequestArgs(
                        accessToken = accessToken,
                        dpopProof = dpopProof,
                        deferredRequest = deferredRequest,
                        // RFC 9449 §7.1: use the metadata-advertised public URL so DPoP `htu`
                        // verification compares against what the wallet signed (matches the
                        // `deferred_credential_endpoint` field built from the issuer identifier).
                        httpUrl = "${configProvider.issuerIdentifier}/deferredCredential",
                        httpMethod = request.method,
                    ),
                ).getOrElse { error -> return Err(error) }

        // OID4VCI 1.0 §10.2: a pending response carries `transaction_id` + `interval` and no
        // credentials — render as 202 unencrypted (encryption applies only to the issued
        // credential body). When the credential is ready, encrypt-or-render per §8.3.5.
        val isPending = response.transactionId != null && response.credentials == null
        if (isPending) {
            return Ok(
                GenericHttpResponse(
                    statusCode = 202,
                    headers = JSON_HEADERS,
                    body = protocolJson.encodeToString(response),
                ),
            )
        }

        val maybeEncrypted =
            credentialResponseEncryptor
                .encryptIfRequested(response, deferredRequest.credentialResponseEncryption)
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
                Ok(jsonResponse(200, protocolJson.encodeToString(maybeEncrypted.response)))
            }
        }
    }
}
