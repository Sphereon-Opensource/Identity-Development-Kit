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

package com.sphereon.openid.oid4vp.universal.impl.config

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.universal.UniversalOid4vpConfig
import com.sphereon.openid.oid4vp.universal.UniversalOid4vpConfigProvider
import com.sphereon.openid.oid4vp.verifier.config.INSTANCES_NAMESPACE
import com.sphereon.openid.oid4vp.verifier.config.Oid4vpVerifierInstanceIdProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Binds Universal OID4VP configuration from IDK's ConfigService.
 *
 * Configuration properties use the instance prefix when a verifier instance has been resolved:
 * `oid4vp.verifiers.<verifierId>.universal`, falling back to the legacy deployment-wide
 * `oid4vp.universal` prefix.
 *
 * ```properties
 * oid4vp.verifiers.default.universal.external-base-url=https://default.example.com
 * oid4vp.universal.external-base-url=https://verifier.example.com
 * ```
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UniversalOid4vpConfigProvider>())
class UniversalOid4vpConfigBinder(
    private val execution: SessionExecution,
    private val instanceIdProvider: Oid4vpVerifierInstanceIdProvider,
) : UniversalOid4vpConfigProvider {
    private val configService: PrincipalConfigService
        get() = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService

    override fun getConfig(): UniversalOid4vpConfig {
        val prefix = UniversalOid4vpConfig.CONFIG_PREFIX
        val instancePrefix =
            instanceIdProvider
                .currentInstanceId()
                ?.takeIf { it.isNotBlank() }
                ?.let { instanceId ->
                    val configPrefix =
                        configService
                            .getPropertyAsString("$SERVICE_CONFIG_BINDING_BY_PARTY_PREFIX.$instanceId.config-key-prefix", null)
                            ?.takeIf { it.isNotBlank() }
                            ?: "$INSTANCES_NAMESPACE.$instanceId"
                    "$configPrefix.universal"
                }

        return UniversalOid4vpConfig(
            externalBaseUrl = firstConfiguredString(instancePrefix?.let { "$it.external-base-url" }, "$prefix.external-base-url"),
            responseUri = firstConfiguredString(instancePrefix?.let { "$it.response-uri" }, "$prefix.response-uri"),
        )
    }

    private fun firstConfiguredString(vararg keys: String?): String? =
        keys
            .filterNotNull()
            .firstNotNullOfOrNull { key -> configService.getPropertyAsString(key, null)?.takeIf { it.isNotBlank() } }

    private companion object {
        const val SERVICE_CONFIG_BINDING_BY_PARTY_PREFIX = "_derived.software.config-bindings.by-party"
    }
}
