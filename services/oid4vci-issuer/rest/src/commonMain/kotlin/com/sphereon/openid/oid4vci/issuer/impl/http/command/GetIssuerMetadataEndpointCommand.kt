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
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOpts
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierResult
import com.sphereon.crypto.resolution.managed.MultiManagedIdentifierService
import com.sphereon.crypto.resolution.tryManagedIdentifierToJwk
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.MetadataCredentialRequestEncryption
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

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
    private val multiManagedIdentifierService: MultiManagedIdentifierService,
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

        // Resolve `credential_request_encryption.jwks` from the KMS at request time so we
        // never store private key material in YAML / git. Mirrors how the OAuth2 AS publishes
        // its signing JWKS via `GetJwksCommandImpl`.
        val resolvedRequestEncryption =
            resolveCredentialRequestEncryption(configProvider.credentialRequestEncryption)
                .getOrElse { error -> return Err(error) }

        val metadataResult =
            buildMetadataCommand.execute(
                BuildIssuerMetadataArgs(
                    issuerIdentifier = configProvider.issuerIdentifier,
                    baseUrl = baseUrl,
                    authorizationServers = configProvider.authorizationServers,
                    credentialConfigurations = configProvider.credentialConfigurations,
                    display = configProvider.display,
                    credentialResponseEncryption = configProvider.credentialResponseEncryption,
                    credentialRequestEncryption = resolvedRequestEncryption,
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

    /**
     * Walks the KMS to materialise the `credential_request_encryption.jwks` field whenever the
     * config provider supplied a [Oid4vciIssuerConfigProvider.credentialRequestDecryptionKey]
     * alias. The metadata template the config provider returns carries a placeholder `jwks`
     * (`{"keys": []}`) that we replace with the real public JWK here. When no decryption key
     * alias is configured (and no static jwks is in YAML either) the template arrives `null`
     * and we pass that through unchanged so the metadata field is omitted on the wire.
     *
     * Mirrors `GetJwksCommandImpl.executeInternal` — same `tryManagedIdentifierToJwk`
     * primitive, same kid-pinning rule (use `keyInfo.kid` when set so the published kid
     * matches what JWS / JWE signers stamp into headers).
     */
    private suspend fun resolveCredentialRequestEncryption(template: MetadataCredentialRequestEncryption?,): IdkResult<MetadataCredentialRequestEncryption?, IdkError> {
        if (template == null) return Ok(null)

        val decryptionOpts =
            configProvider.credentialRequestDecryptionKey
                ?: return Ok(template) // static-jwks path or null jwks — already correct on the template

        val resolvedIdentifier: ManagedIdentifierOptsOrResult =
            if (decryptionOpts is ManagedIdentifierOpts && decryptionOpts !is ManagedIdentifierResult<*>) {
                multiManagedIdentifierService
                    .resolve(decryptionOpts)
                    .getOrElse { return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to resolve credential_request_encryption decryption key: ${it.message}")) }
            } else {
                decryptionOpts
            }

        val jwkResult =
            tryManagedIdentifierToJwk(resolvedIdentifier).getOrElse { return Err(it) }
        val publicJwk =
            (jwkResult.identifier.toPublicKey() as? Jwk)
                ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Failed to convert credential_request_encryption key to public JWK"))

        // Pin kid to keyInfo.kid the same way the AS JWKS endpoint does, so a wallet that
        // includes `kid` in its JWE header can resolve back to the same KMS alias on decrypt.
        // Also stamp `use=enc` (RFC 7517 §4.2) and the default JWE key-management `alg`
        // (RFC 7517 §4.4) so RFC 7517 / OIDF `VCICheckCredentialRequestEncryptionSupported`
        // can confirm the key is intended for encryption — the suite rejects a JWK with no
        // `use` AND no JWE-compatible `alg` because it can't tell encryption keys from
        // signing keys. ECDH-ES is the only HAIP-permitted alg for P-256, so it's the
        // natural advertised default; wallets MAY still pick a more specific +A*KW variant.
        val publishedJwk =
            jwkResult.keyInfo.kid
                ?.takeIf { it.isNotBlank() }
                ?.let { keyInfoKid ->
                    if (publicJwk.kid == keyInfoKid) publicJwk else publicJwk.copy(kid = keyInfoKid)
                } ?: publicJwk
        val annotatedJwk =
            publishedJwk.copy(
                use = publishedJwk.use ?: "enc",
                alg = publishedJwk.alg ?: JwaAlgorithm.ECDH_ES,
            )

        val publicJwkElement =
            Json.Default.encodeToJsonElement(Jwk.serializer(), annotatedJwk).let {
                it as? JsonObject ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Public JWK did not serialize to a JSON object"))
            }
        val realJwks = JsonObject(mapOf("keys" to JsonArray(listOf(publicJwkElement))))
        return Ok(template.copy(jwks = realJwks))
    }
}
