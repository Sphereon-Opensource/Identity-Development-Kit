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

package com.sphereon.openid.oid4vci.holder.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.TypedServiceCommandAdapter
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.client.command.FetchAuthorizationServerMetadataCommand
import com.sphereon.oauth2.client.command.FetchServerMetadataArgs
import com.sphereon.openid.oid4vci.holder.ResolvedAuthorizationServer
import com.sphereon.openid.oid4vci.holder.SelectAuthorizationServerArgs
import com.sphereon.openid.oid4vci.holder.SelectAuthorizationServerCommand
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Selects and resolves the Authorization Server metadata.
 *
 * Per OID4VCI 1.1 and RFC 8414:
 * - If issuerMetadata.authorizationServers is null, AS URL = issuer URL itself.
 *
 * The actual metadata fetch (discovery-URL ordering, RFC 8414 vs OpenID Connect Discovery,
 * HTTP retrieval) is delegated to [FetchAuthorizationServerMetadataCommand], the single AS
 * metadata-fetch command shared with lib-oauth2-client. This command owns only AS *selection*.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<SelectAuthorizationServerCommand>())
class SelectAuthorizationServerCommandImpl(
    execution: SessionExecution,
    private val fetchAuthorizationServerMetadataCommand: FetchAuthorizationServerMetadataCommand,
) : TypedServiceCommandAdapter<SelectAuthorizationServerArgs, ResolvedAuthorizationServer, IdkError>(
        commandId = SelectAuthorizationServerCommand.COMMAND_ID,
        execution = execution,
        inputTypeToken = typeToken<SelectAuthorizationServerArgs>(),
        outputTypeToken = typeToken<ResolvedAuthorizationServer>(),
    ),
    SelectAuthorizationServerCommand {
    override val commandId: String get() = SelectAuthorizationServerCommand.COMMAND_ID

    override suspend fun supports(args: Any): Boolean = args is SelectAuthorizationServerArgs

    override suspend fun doExecute(
        args: SelectAuthorizationServerArgs,
        applyDuring: (SelectAuthorizationServerArgs) -> SelectAuthorizationServerArgs,
    ): IdkResult<ResolvedAuthorizationServer, IdkError> {
        val applied = applyDuring(args)
        val issuerMetadata = applied.issuerMetadata

        // Determine the AS URL: prefer the first entry in authorization_servers, or use issuer URL
        val authServers = issuerMetadata.authorizationServers
        val asUrl =
            when {
                applied.preferredAuthorizationServer != null -> applied.preferredAuthorizationServer!!
                !authServers.isNullOrEmpty() -> authServers.first()
                else -> issuerMetadata.credentialIssuer
            }.trimEnd('/')

        log.debug("Selecting authorization server: $asUrl")

        val metadataResult = fetchAuthorizationServerMetadataCommand.execute(FetchServerMetadataArgs(issuer = asUrl))
        if (metadataResult.isErr) {
            // Propagate the fetch command's own error surface as-is: it already carries a
            // specific code/message (NotFound / FetchFailed / ValidationFailed / IssuerMismatch /
            // InvalidUrl) via IdkError.fromDTO, so no new AS-selection-specific codes are needed.
            return Err(metadataResult.error)
        }

        log.debug("Successfully resolved AS metadata for: $asUrl")
        return Ok(ResolvedAuthorizationServer(authorizationServerUrl = asUrl, metadata = metadataResult.value))
    }
}
