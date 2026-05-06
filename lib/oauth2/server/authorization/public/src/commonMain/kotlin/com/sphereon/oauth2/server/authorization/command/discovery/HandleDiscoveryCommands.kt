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

package com.sphereon.oauth2.server.authorization.command.discovery

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.oauth2.common.model.AuthorizationServerMetadata

/**
 * Args for [HandleDiscoveryRequestCommand]. Both fields are optional:
 *  - [serverId] selects a specific server when more than one is configured
 *  - [baseUrlOverride] forces the issuer URL used in the response (typically derived from the
 *    incoming request when running behind a reverse proxy with a path prefix)
 */
data class HandleDiscoveryRequestArgs(
    val serverId: String? = null,
    val baseUrlOverride: String? = null,
)

/**
 * Orchestration command for `GET /.well-known/oauth-authorization-server` (RFC 8414) and the
 * OIDC variant `GET /.well-known/openid-configuration`. Wraps the lower-level
 * [com.sphereon.oauth2.server.authorization.command.BuildServerMetadataCommand].
 */
interface HandleDiscoveryRequestCommand : ServiceCommand<HandleDiscoveryRequestArgs, AuthorizationServerMetadata, IdkError> {
    override val commandId: String get() = COMMAND_ID

    companion object {
        const val COMMAND_ID = "oauth2.discovery.handle-discovery-request"
    }
}
