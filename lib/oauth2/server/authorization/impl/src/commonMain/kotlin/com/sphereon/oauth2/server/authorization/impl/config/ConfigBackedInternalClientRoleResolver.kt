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

package com.sphereon.oauth2.server.authorization.impl.config

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig
import com.sphereon.oauth2.common.config.OAuth2ServerInstanceIdProvider
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import com.sphereon.oauth2.server.authorization.service.InternalClientRoleResolver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Resolves an internal role from tenant-owned AS configuration without materializing its secret.
 *
 * Opaque secret-authority registrations intentionally have no `client-secret` property. They are
 * therefore absent from [OAuth2ServerInstanceConfig.internalClients], whose legacy DTO requires
 * plaintext credential material. Reading the role's non-secret `client-id` directly preserves
 * that separation and gives embedded resource servers the same identity used in distributed
 * deployments.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<InternalClientRoleResolver>())
class ConfigBackedInternalClientRoleResolver(
    private val execution: SessionExecution,
    private val asInstanceIdProvider: OAuth2ServerInstanceIdProvider,
    private val serversConfigProvider: OAuth2ServersConfigProvider,
) : InternalClientRoleResolver {
    override fun resolveClientId(role: String): String? {
        val normalizedRole = role.trim().takeIf(String::isNotEmpty) ?: return null
        val serversConfig = serversConfigProvider.getConfig()
        val activeServerId = asInstanceIdProvider.currentAsInstanceId() ?: serversConfig.defaultServer
        val propertyKey =
            "${OAuth2ServerInstanceConfig.CONFIG_PREFIX}.$activeServerId.internal-clients.$normalizedRole.client-id"

        return execution.conf.tenant
            .getPropertyAsString(propertyKey, null)
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: serversConfig.getServer(activeServerId)
                ?.internalClients
                ?.get(normalizedRole)
                ?.clientId
                ?.trim()
                ?.takeIf(String::isNotEmpty)
    }
}
