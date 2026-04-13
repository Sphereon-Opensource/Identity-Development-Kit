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
import com.sphereon.core.api.http.describe.HttpEndpointDescriptor
import com.sphereon.core.api.http.describe.HttpMethod
import com.sphereon.core.api.http.describe.MediaType
import com.sphereon.core.api.http.jsonResponse
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.command.BuildIssuerMetadataArgs
import com.sphereon.openid.oid4vci.issuer.command.BuildIssuerMetadataCommand
import com.sphereon.openid.oid4vci.issuer.command.BuildSignedIssuerMetadataArgs
import com.sphereon.openid.oid4vci.issuer.command.BuildSignedIssuerMetadataCommand
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.rest.Oid4vciRestConfigProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.encodeToString

/**
 * Endpoint command for OID4VCI Issuer Metadata discovery.
 *
 * GET /.well-known/openid-credential-issuer
 */
interface GetIssuerMetadataEndpointCommand : HttpEndpointCommand {
    companion object {
        const val COMMAND_ID = "oid4vci.protocol.metadata"

        val ENDPOINT =
            HttpEndpointDescriptor(
                method = HttpMethod.GET,
                pathPattern = "/.well-known/openid-credential-issuer",
                produces =
                    setOf(
                        MediaType.ApplicationJson,
                        MediaType.Custom(ACCEPT_JWT),
                        MediaType.Custom(ACCEPT_ISSUER_METADATA_JWT),
                    ),
                operationId = "getIssuerMetadata",
                commandId = COMMAND_ID,
                tags = setOf("oid4vci-issuer", "metadata"),
                summary = "Get OID4VCI credential issuer metadata",
            )
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<GetIssuerMetadataEndpointCommand>())
class GetIssuerMetadataEndpointCommandImpl(
    execution: SessionExecution,
    private val buildMetadataCommand: BuildIssuerMetadataCommand,
    private val buildSignedMetadataCommand: BuildSignedIssuerMetadataCommand,
    private val configProvider: Oid4vciIssuerConfigProvider,
    private val restConfigProvider: Oid4vciRestConfigProvider,
) : HttpEndpointCommandAdapter(
        id = GetIssuerMetadataEndpointCommand.COMMAND_ID,
        execution = execution,
        endpoint = GetIssuerMetadataEndpointCommand.ENDPOINT,
    ),
    GetIssuerMetadataEndpointCommand {
    override suspend fun doExecute(
        args: GenericHttpRequest,
        applyDuring: (GenericHttpRequest) -> GenericHttpRequest,
    ): IdkResult<GenericHttpResponse, IdkError> {
        val request = applyDuring(args)

        // Use the REST external-base-url for constructing endpoint URIs (e.g. /oid4vci/credential).
        // This is the server root, separate from the issuer identifier which may include a path
        // (e.g. identifier = "https://example.com/oid4vci", base = "https://example.com").
        val baseUrl = (restConfigProvider.getConfig().externalBaseUrl ?: configProvider.issuerIdentifier).trimEnd('/')

        val acceptHeader = request.headers["Accept"] ?: request.headers["accept"] ?: ""
        val wantsJwt =
            acceptHeader.contains(ACCEPT_JWT, ignoreCase = true) ||
                acceptHeader.contains(ACCEPT_ISSUER_METADATA_JWT, ignoreCase = true)

        val metadataResult =
            buildMetadataCommand.execute(
                BuildIssuerMetadataArgs(
                    issuerIdentifier = configProvider.issuerIdentifier,
                    baseUrl = baseUrl,
                    authorizationServers = configProvider.authorizationServers,
                    credentialConfigurations = configProvider.credentialConfigurations,
                    display = configProvider.display,
                    credentialResponseEncryption = configProvider.credentialResponseEncryption,
                    credentialRequestEncryption = configProvider.credentialRequestEncryption,
                    batchCredentialIssuance = configProvider.batchCredentialIssuance,
                ),
            )

        val metadata =
            metadataResult.getOrElse { error ->
                return Err(error)
            }

        val signingKey = configProvider.signingKey

        if (wantsJwt) {
            if (signingKey == null) {
                return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Signed issuer metadata is not supported: no signing key configured"))
            }
            return buildSignedMetadataCommand
                .execute(
                    BuildSignedIssuerMetadataArgs(
                        metadata = metadata,
                        signingKey = signingKey,
                    ),
                ).map { jwt ->
                    GenericHttpResponse(statusCode = 200, headers = JWT_HEADERS, body = jwt.jwt)
                }
        }

        // JSON response: also populate signed_metadata when a signing key is configured
        if (signingKey != null) {
            return buildSignedMetadataCommand
                .execute(
                    BuildSignedIssuerMetadataArgs(
                        metadata = metadata,
                        signingKey = signingKey,
                    ),
                ).map { jwt ->
                    val enriched = metadata.copy(signedMetadata = jwt.jwt)
                    jsonResponse(200, protocolJson.encodeToString(enriched))
                }
        }

        return Ok(jsonResponse(200, protocolJson.encodeToString(metadata)))
    }
}
