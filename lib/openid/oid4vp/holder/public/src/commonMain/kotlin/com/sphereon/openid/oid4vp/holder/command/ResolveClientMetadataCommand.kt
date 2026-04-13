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

package com.sphereon.openid.oid4vp.holder.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.openid.oid4vp.common.ClientMetadata
import kotlinx.serialization.Serializable

/**
 * Result wrapper for resolved client metadata.
 *
 * Since client_metadata is OPTIONAL per OpenID4VP 1.0 spec, this wrapper allows
 * the Command interface to work with nullable metadata.
 *
 * @property metadata The resolved client metadata, or null if not provided
 */
@Serializable
data class ResolvedClientMetadata(
    val metadata: ClientMetadata? = null
)

/**
 * Command for resolving client metadata from an authorization request.
 *
 * Resolution follows OpenID4VP 1.0 Section 5.3 priority order:
 * 1. Fetch from `client_metadata_uri` if present (HTTPS required)
 * 2. Parse embedded `client_metadata` if present
 * 3. Return null metadata if not provided (client_metadata is OPTIONAL per spec)
 *
 * @see ClientMetadata
 * @see ResolvedClientMetadata
 */
interface ResolveClientMetadataCommand : ServiceCommand<AuthorizationRequest, ResolvedClientMetadata> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oid4vp.holder.resolvemeta"
    }
}

/**
 * Command service interface for resolving client metadata.
 */
interface ResolveClientMetadataCommandService {
    suspend fun resolveClientMetadata(request: AuthorizationRequest): IdkResult<ResolvedClientMetadata, IdkError>
}
