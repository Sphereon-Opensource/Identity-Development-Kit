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

package com.sphereon.openid.oid4vci.issuer.impl.config

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.config.CredentialIssuancePolicyConfig
import com.sphereon.openid.oid4vci.issuer.config.CredentialIssuancePolicyResolver
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default [CredentialIssuancePolicyResolver] backed by IDK's ConfigService.
 *
 * Properties are read from the principal config (allowing tenant/app overrides) under the
 * namespace `sphereon.oid4vci.issuer.credentials.<credentialConfigurationId>`.
 *
 * Example configuration (application.yml):
 * ```yaml
 * sphereon:
 *   app:
 *     oid4vci:
 *       issuer:
 *         credentials:
 *           UniversityDegree_SD-JWT:
 *             iae:
 *               enabled: true
 *               interaction-type: urn:openid:dcp:iae:openid4vp_presentation
 *               dcql-query-id: university-degree-query
 *             grants:
 *               pre-authorized-code:
 *                 allowed: true
 *                 tx-code-required: false
 *               authorization-code:
 *                 allowed: true
 *             nonce:
 *               ttl-seconds: 300
 *             deferred:
 *               retry-interval-seconds: 5
 *             encryption:
 *               response-required: false
 * ```
 *
 * Or via environment variables:
 * ```
 * SPHEREON_APP_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_SD-JWT_IAE_ENABLED=true
 * SPHEREON_APP_OID4VCI_ISSUER_CREDENTIALS_UNIVERSITYDEGREE_SD-JWT_GRANTS_PRE_AUTHORIZED_CODE_ALLOWED=false
 * ```
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CredentialIssuancePolicyResolver>())
@ContributesBinding(SessionScope::class, binding = binding<CredentialIssuancePolicyResolver?>())
class DefaultCredentialIssuancePolicyResolver(
    private val execution: SessionExecution,
) : CredentialIssuancePolicyResolver {
    private val configService: PrincipalConfigService
        get() = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService

    override suspend fun resolve(credentialConfigurationId: String): CredentialIssuancePolicyConfig {
        val prefix = "${CredentialIssuancePolicyConfig.CONFIG_NAMESPACE}.[$credentialConfigurationId]"

        val defaults = CredentialIssuancePolicyConfig()

        return CredentialIssuancePolicyConfig(
            iaeEnabled =
                configService.getProperty(
                    "$prefix.iae.enabled",
                    Boolean::class,
                    defaults.iaeEnabled,
                ) ?: defaults.iaeEnabled,
            iaeInteractionType =
                configService.getPropertyAsString(
                    "$prefix.iae.interaction-type",
                    defaults.iaeInteractionType,
                ) ?: defaults.iaeInteractionType,
            iaeDcqlQueryId =
                configService.getPropertyAsString(
                    "$prefix.iae.dcql-query-id",
                    null,
                ),
            preAuthorizedCodeAllowed =
                configService.getProperty(
                    "$prefix.grants.pre-authorized-code.allowed",
                    Boolean::class,
                    defaults.preAuthorizedCodeAllowed,
                ) ?: defaults.preAuthorizedCodeAllowed,
            txCodeRequired =
                configService.getProperty(
                    "$prefix.grants.pre-authorized-code.tx-code-required",
                    Boolean::class,
                    defaults.txCodeRequired,
                ) ?: defaults.txCodeRequired,
            authorizationCodeAllowed =
                configService.getProperty(
                    "$prefix.grants.authorization-code.allowed",
                    Boolean::class,
                    defaults.authorizationCodeAllowed,
                ) ?: defaults.authorizationCodeAllowed,
            nonceTtlSeconds =
                configService.getProperty(
                    "$prefix.nonce.ttl-seconds",
                    Long::class,
                    defaults.nonceTtlSeconds,
                ) ?: defaults.nonceTtlSeconds,
            deferredRetryIntervalSeconds =
                configService.getProperty(
                    "$prefix.deferred.retry-interval-seconds",
                    Int::class,
                    defaults.deferredRetryIntervalSeconds,
                ) ?: defaults.deferredRetryIntervalSeconds,
            encryptionResponseRequired =
                configService.getProperty(
                    "$prefix.encryption.response-required",
                    Boolean::class,
                    defaults.encryptionResponseRequired,
                ) ?: defaults.encryptionResponseRequired,
        )
    }
}
