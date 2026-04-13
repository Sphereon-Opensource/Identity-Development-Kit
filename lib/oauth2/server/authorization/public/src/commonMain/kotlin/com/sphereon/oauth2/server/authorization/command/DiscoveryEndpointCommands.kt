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

package com.sphereon.oauth2.server.authorization.command

import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata

// ============================================================================
// BuildServerMetadataCommand
// ============================================================================

/**
 * Arguments for building authorization server metadata
 */
data class BuildServerMetadataArgs(
    val serverId: String? = null,
    val baseUrlOverride: String? = null
)

/**
 * Build server metadata command
 *
 * RFC 8414: OAuth 2.0 Authorization Server Metadata
 *
 * Builds the complete authorization server metadata document from
 * the current server configuration. Only valid for HOSTED mode servers.
 */
interface BuildServerMetadataCommand : ServiceCommand<BuildServerMetadataArgs, AuthorizationServerMetadata> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.discovery.metadata"
    }
}
