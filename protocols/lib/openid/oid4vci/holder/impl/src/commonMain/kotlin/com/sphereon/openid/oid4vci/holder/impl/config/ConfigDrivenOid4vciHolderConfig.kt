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

package com.sphereon.openid.oid4vci.holder.impl.config

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderConfig
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * [Oid4vciHolderConfig] backed by IDK's ConfigService.
 *
 * Reads holder configuration from principal config, with sensible defaults
 * for all properties. All values are optional in config — the defaults from
 * the [Oid4vciHolderConfig] interface apply when not configured.
 *
 * Example configuration (application.yml):
 * ```yaml
 * sphereon:
 *   oid4vci:
 *     holder:
 *       clientId: my-wallet-client
 *       preferredFormat: dc+sd-jwt
 *       autoRequestNonce: true
 *       defaultDeferredPollingInterval: 5
 *       maxDeferredPollingAttempts: 60
 *       requireVerifiedSignedMetadata: false
 * ```
 *
 * Environment variable equivalents (via PropertyKeyNormalizer):
 * ```
 * SPHEREON_OID4VCI_HOLDER_CLIENTID=my-wallet-client
 * SPHEREON_OID4VCI_HOLDER_PREFERREDFORMAT=dc+sd-jwt
 * SPHEREON_OID4VCI_HOLDER_REQUIREVERIFIEDSIGNEDMETADATA=true
 * ```
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciHolderConfig>())
class ConfigDrivenOid4vciHolderConfig(
    private val execution: SessionExecution,
) : Oid4vciHolderConfig {
    private val configService: PrincipalConfigService
        get() = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService

    override val clientId: String?
        get() = configService.getPropertyAsString("$NAMESPACE.clientId")

    override val preferredFormat: String?
        get() = configService.getPropertyAsString("$NAMESPACE.preferredFormat")

    override val autoRequestNonce: Boolean
        get() = configService.getPropertyAsString("$NAMESPACE.autoRequestNonce")?.toBoolean() ?: true

    override val defaultDeferredPollingInterval: Int
        get() =
            configService.getPropertyAsString("$NAMESPACE.defaultDeferredPollingInterval")?.toIntOrNull()
                ?: DEFAULT_DEFERRED_POLLING_INTERVAL

    override val maxDeferredPollingAttempts: Int
        get() =
            configService.getPropertyAsString("$NAMESPACE.maxDeferredPollingAttempts")?.toIntOrNull()
                ?: DEFAULT_MAX_DEFERRED_POLLING_ATTEMPTS

    override val requireVerifiedSignedMetadata: Boolean
        get() = configService.getPropertyAsString("$NAMESPACE.requireVerifiedSignedMetadata")?.toBoolean() ?: false

    companion object {
        private const val NAMESPACE = "oid4vci.holder"
        private const val DEFAULT_DEFERRED_POLLING_INTERVAL = 5
        private const val DEFAULT_MAX_DEFERRED_POLLING_ATTEMPTS = 60
    }
}
