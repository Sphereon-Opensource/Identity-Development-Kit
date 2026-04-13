/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.oauth2.server.authorization.impl.command.discovery

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.AuthorizationServerMode
import com.sphereon.oauth2.common.config.FeaturePolicy
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.common.config.isEnabled
import com.sphereon.oauth2.common.config.isRequired
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataArgs
import com.sphereon.oauth2.server.authorization.command.BuildServerMetadataCommand
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of BuildServerMetadataCommand
 *
 * Builds the authorization server metadata document (RFC 8414)
 * from the current server configuration.
 *
 * Only valid for HOSTED mode servers. Returns error for EXTERNAL servers.
 */
@Inject
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("BuildServerMetadataCommandImpl", exact = true)
class BuildServerMetadataCommandImpl(
    execution: SessionExecution,
    private val configProvider: OAuth2ServersConfigProvider
) : TypedServiceCommandAdapter<BuildServerMetadataArgs, AuthorizationServerMetadata>(
    commandId = BuildServerMetadataCommand.COMMAND_ID,
    execution = execution,
    inputTypeToken = typeToken<BuildServerMetadataArgs>(),
    outputTypeToken = typeToken<AuthorizationServerMetadata>(),
), BuildServerMetadataCommand {

    override val commandId: String get() = BuildServerMetadataCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is BuildServerMetadataArgs

    override suspend fun doExecute(
        args: BuildServerMetadataArgs,
        applyDuring: (BuildServerMetadataArgs) -> BuildServerMetadataArgs
    ): IdkResult<AuthorizationServerMetadata, IdkError> {
        val applied = applyDuring(args)
        return executeInternal(applied.serverId, applied.baseUrlOverride)
            .mapError { IdkError.fromDTO(it) }
    }

    private fun executeInternal(
        serverId: String?,
        baseUrlOverride: String?
    ): IdkResult<AuthorizationServerMetadata, AuthorizationServerError> {
        val config = if (serverId != null) {
            configProvider.getServer(serverId)
                ?: return Err(
                    AuthorizationServerError.InvalidRequest(
                        details = "Server '$serverId' not found in configuration"
                    )
                )
        } else {
            configProvider.serverConfig
        }

        if (config.mode != AuthorizationServerMode.HOSTED) {
            return Err(
                AuthorizationServerError.InvalidRequest(
                    details = "Cannot build metadata for EXTERNAL server. Use metadata discovery instead."
                )
            )
        }

        val baseUrl = (baseUrlOverride ?: config.issuer ?: config.baseUrl).trimEnd('/')

        val metadata = AuthorizationServerMetadata(
            issuer = baseUrl,
            tokenEndpoint = "$baseUrl/token",
            authorizationEndpoint = "$baseUrl/authorize",
            jwksUri = config.jwksUri ?: "$baseUrl/.well-known/jwks.json",
            grantTypesSupported = config.grantTypesEnabled.toList(),
            responseTypesSupported = config.responseTypesSupported.toList(),
            scopesSupported = config.scopesSupported,
            tokenEndpointAuthMethodsSupported = config.tokenEndpointAuthMethodsSupported.toList(),
            codeChallengeMethodsSupported = if (config.pkce.isEnabled) config.pkceMethodsSupported.toList() else null,
            dpopSigningAlgValuesSupported = if (config.dpop.isEnabled) config.dpopSigningAlgValuesSupported?.toList() else null,
            requirePushedAuthorizationRequests = if (config.par.isRequired) true else if (config.par.isEnabled) false else null,
            pushedAuthorizationRequestEndpoint = if (config.par.isEnabled) "$baseUrl/par" else null,
            introspectionEndpoint = if (config.introspection.isEnabled) "$baseUrl/introspect" else null,
            introspectionEndpointAuthMethodsSupported = if (config.introspection.isEnabled)
                config.introspectionEndpointAuthMethodsSupported.toList() else null,
            revocationEndpoint = if (config.revocation.isEnabled) "$baseUrl/revoke" else null,
            revocationEndpointAuthMethodsSupported = if (config.revocation.isEnabled)
                config.revocationEndpointAuthMethodsSupported.toList() else null,
            // Attestation-based client auth metadata
            clientAttestationPopNonceRequired = if (config.attestation.isEnabled) config.attestationChallengeRequired else null,
            challengeEndpoint = if (config.attestation.isEnabled && config.attestationChallengeRequired) "$baseUrl/attestation-challenge" else null,
            clientAttestationSigningAlgValuesSupported = if (config.attestation.isEnabled)
                config.clientAttestationSigningAlgValuesSupported?.toList() else null,
            clientAttestationPopSigningAlgValuesSupported = if (config.attestation.isEnabled)
                config.clientAttestationPopSigningAlgValuesSupported?.toList() else null,
            // OIDC metadata (OpenID Connect Discovery 1.0)
            userinfoEndpoint = if (config.oidc.isEnabled) "$baseUrl/userinfo" else null,
            subjectTypesSupported = if (config.oidc.isEnabled) config.subjectTypesSupported else null,
            idTokenSigningAlgValuesSupported = if (config.oidc.isEnabled)
                config.idTokenSigningAlgValuesSupported?.toList() ?: listOf("ES256") else null,
            claimsSupported = if (config.oidc.isEnabled)
                config.claimsSupported ?: listOf("sub", "name", "preferred_username", "email", "email_verified", "phone_number", "phone_number_verified") else null,
        )

        return Ok(metadata)
    }
}
