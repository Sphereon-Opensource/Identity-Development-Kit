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

package com.sphereon.oauth2.server.authorization.impl.command.introspection

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.error.AuthorizationServerError
import com.sphereon.oauth2.server.authorization.impl.config.OAuth2ClientsConfigBinder
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Authoritative classification of clients that act as OAuth resource servers.
 *
 * Resource-server clients may introspect tokens issued to other clients. The classification
 * must therefore come from server-owned internal-client configuration, never from arbitrary
 * dynamic client metadata.
 */
fun interface InternalIntrospectionClientAuthorizer {
    suspend fun isInternalClient(clientId: String): IdkResult<Boolean, AuthorizationServerError.StorageError>
}

/**
 * Resolves both deployment-bootstrap internal clients and greenfield opaque-secret-backed
 * internal clients. Opaque clients are deliberately absent from [OAuth2ServersConfigProvider],
 * so consulting that legacy-shaped view alone incorrectly rejects valid resource servers.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<InternalIntrospectionClientAuthorizer>())
class ConfigBackedInternalIntrospectionClientAuthorizer(
    private val configBinder: OAuth2ClientsConfigBinder,
    private val asInstanceIdProvider: OAuth2ServerInstanceIdProvider,
    private val serversConfigProvider: OAuth2ServersConfigProvider,
) : InternalIntrospectionClientAuthorizer {
    override suspend fun isInternalClient(clientId: String): IdkResult<Boolean, AuthorizationServerError.StorageError> {
        if (clientId.isBlank()) return Ok(false)

        val bootstrapMatch =
            serversConfigProvider.getConfig().servers.values.any { server ->
                server.internalClients.values.any { it.clientId == clientId }
            }
        if (bootstrapMatch) return Ok(true)

        val activeServerId =
            asInstanceIdProvider.currentAsInstanceId()
                ?: serversConfigProvider.getConfig().defaultServer
        val opaqueClients = configBinder.loadOpaqueInternalClientRegistrations(activeServerId)
        return if (opaqueClients.isOk) {
            Ok(opaqueClients.value.containsKey(clientId))
        } else {
            Err(opaqueClients.error)
        }
    }
}
