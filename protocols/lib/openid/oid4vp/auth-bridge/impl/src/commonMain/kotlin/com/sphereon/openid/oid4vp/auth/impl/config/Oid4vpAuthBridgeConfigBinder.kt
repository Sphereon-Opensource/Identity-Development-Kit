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
 *
 */

package com.sphereon.openid.oid4vp.auth.impl.config

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.auth.config.Oid4vpAuthBridgeConfigProvider
import com.sphereon.openid.oid4vp.auth.model.Oid4vpAuthBridgeConfig
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Implementation that binds OID4VP Auth Bridge configuration from IDK's ConfigService.
 *
 * Configuration properties use the prefix "oid4vp.auth-bridge":
 *
 * ```properties
 * oid4vp.auth-bridge.default-query-id=my-query-id
 * oid4vp.auth-bridge.claims-mapping-config-id=my-mapping-id
 * oid4vp.auth-bridge.claims-mapping-config={"id":"my-mapping","name":"My Mapping","credentialMappings":[...]}
 * oid4vp.auth-bridge.session-ttl-seconds=300
 * oid4vp.auth-bridge.auto-create-user=true
 * oid4vp.auth-bridge.user-identifier-claim-path=sub
 * oid4vp.auth-bridge.universal-api.base-url=http://localhost:8080/oid4vp
 * oid4vp.auth-bridge.universal-api.connection-timeout-ms=30000
 * oid4vp.auth-bridge.universal-api.request-timeout-ms=120000
 * oid4vp.auth-bridge.user-api.base-url=http://localhost:8080/api/users/v1
 * oid4vp.auth-bridge.user-api.connection-timeout-ms=30000
 * oid4vp.auth-bridge.user-api.request-timeout-ms=60000
 * ```
 *
 * ## YAML Configuration
 *
 * ```yaml
 * sphereon:
 *   app:
 *     oid4vp:
 *       auth-bridge:
 *         default-query-id: my-query-id
 *         claims-mapping-config-id: my-mapping-id
 *         claims-mapping-config: '{"id":"my-mapping","name":"My Mapping","credentialMappings":[...]}'
 *         session-ttl-seconds: 300
 *         auto-create-user: true
 *         user-identifier-claim-path: sub
 *         universal-api:
 *           base-url: http://localhost:8080/oid4vp
 *           connection-timeout-ms: 30000
 *           request-timeout-ms: 120000
 *         user-api:
 *           base-url: http://localhost:8080/api/users/v1
 *           connection-timeout-ms: 30000
 *           request-timeout-ms: 60000
 * ```
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vpAuthBridgeConfigProvider>())
class Oid4vpAuthBridgeConfigBinder(
    private val execution: SessionExecution,
) : Oid4vpAuthBridgeConfigProvider {
    private val configService: PrincipalConfigService
        get() = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService

    override fun getConfig(): Oid4vpAuthBridgeConfig {
        val prefix = Oid4vpAuthBridgeConfig.CONFIG_PREFIX

        return Oid4vpAuthBridgeConfig(
            defaultQueryId = configService.getPropertyAsString("$prefix.default-query-id", null),
            defaultDcqlQuery = configService.getPropertyAsString("$prefix.default-dcql-query", null),
            clientId = configService.getPropertyAsString("$prefix.client-id", null),
            responseUri = configService.getPropertyAsString("$prefix.response-uri", null),
            sessionTtlSeconds =
                configService.getProperty(
                    "$prefix.session-ttl-seconds",
                    Long::class,
                    Oid4vpAuthBridgeConfig.DEFAULT_SESSION_TTL_SECONDS,
                ) ?: Oid4vpAuthBridgeConfig.DEFAULT_SESSION_TTL_SECONDS,
            autoCreateUser =
                configService.getProperty(
                    "$prefix.auto-create-user",
                    Boolean::class,
                    Oid4vpAuthBridgeConfig.DEFAULT_AUTO_CREATE_USER,
                ) ?: Oid4vpAuthBridgeConfig.DEFAULT_AUTO_CREATE_USER,
            requireReconciliation =
                configService.getProperty(
                    "$prefix.require-reconciliation",
                    Boolean::class,
                    Oid4vpAuthBridgeConfig.DEFAULT_REQUIRE_RECONCILIATION,
                ) ?: Oid4vpAuthBridgeConfig.DEFAULT_REQUIRE_RECONCILIATION,
            userIdentifierClaimPath =
                configService.getPropertyAsString(
                    "$prefix.user-identifier-claim-path",
                    Oid4vpAuthBridgeConfig.DEFAULT_USER_IDENTIFIER_CLAIM_PATH,
                ) ?: Oid4vpAuthBridgeConfig.DEFAULT_USER_IDENTIFIER_CLAIM_PATH,
            universalApiBaseUrl =
                configService.getPropertyAsString(
                    "$prefix.universal-api.base-url",
                    Oid4vpAuthBridgeConfig.DEFAULT_UNIVERSAL_API_BASE_URL,
                ) ?: Oid4vpAuthBridgeConfig.DEFAULT_UNIVERSAL_API_BASE_URL,
            universalApiConnectionTimeoutMs =
                configService.getProperty(
                    "$prefix.universal-api.connection-timeout-ms",
                    Long::class,
                    Oid4vpAuthBridgeConfig.DEFAULT_UNIVERSAL_API_CONNECTION_TIMEOUT_MS,
                ) ?: Oid4vpAuthBridgeConfig.DEFAULT_UNIVERSAL_API_CONNECTION_TIMEOUT_MS,
            universalApiRequestTimeoutMs =
                configService.getProperty(
                    "$prefix.universal-api.request-timeout-ms",
                    Long::class,
                    Oid4vpAuthBridgeConfig.DEFAULT_UNIVERSAL_API_REQUEST_TIMEOUT_MS,
                ) ?: Oid4vpAuthBridgeConfig.DEFAULT_UNIVERSAL_API_REQUEST_TIMEOUT_MS,
            userApiBaseUrl =
                configService.getPropertyAsString(
                    "$prefix.user-api.base-url",
                    Oid4vpAuthBridgeConfig.DEFAULT_USER_API_BASE_URL,
                ) ?: Oid4vpAuthBridgeConfig.DEFAULT_USER_API_BASE_URL,
            userApiConnectionTimeoutMs =
                configService.getProperty(
                    "$prefix.user-api.connection-timeout-ms",
                    Long::class,
                    Oid4vpAuthBridgeConfig.DEFAULT_USER_API_CONNECTION_TIMEOUT_MS,
                ) ?: Oid4vpAuthBridgeConfig.DEFAULT_USER_API_CONNECTION_TIMEOUT_MS,
            userApiRequestTimeoutMs =
                configService.getProperty(
                    "$prefix.user-api.request-timeout-ms",
                    Long::class,
                    Oid4vpAuthBridgeConfig.DEFAULT_USER_API_REQUEST_TIMEOUT_MS,
                ) ?: Oid4vpAuthBridgeConfig.DEFAULT_USER_API_REQUEST_TIMEOUT_MS,
            frontendUrl = configService.getPropertyAsString("$prefix.frontend-url", null),
            stsBaseUrl = configService.getPropertyAsString("$prefix.sts-base-url", null),
        )
    }
}
