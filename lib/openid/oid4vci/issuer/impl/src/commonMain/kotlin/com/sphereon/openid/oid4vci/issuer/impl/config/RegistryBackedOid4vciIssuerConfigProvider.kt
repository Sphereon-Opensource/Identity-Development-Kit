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

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.config.INSTANCES_NAMESPACE
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerConfigProvider
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerInstanceIdProvider
import com.sphereon.openid.oid4vci.issuer.config.VctTypeMetadataProvider
import com.sphereon.openid.oid4vci.issuer.config.requireCurrentInstanceId
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciIssuerAuthorizationPolicy
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciIssuerAuthorizationPolicyProvider
import com.sphereon.openid.oid4vci.issuer.spi.IssuerKeyNameResolver
import com.sphereon.statuslist.StatusListDefinitionsProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.concurrent.Volatile

/**
 * Per-instance [Oid4vciIssuerConfigProvider] / [VctTypeMetadataProvider] backed by IDK's
 * ConfigService, selected at request time by [Oid4vciIssuerInstanceIdProvider].
 *
 * Computes the active config namespace from the resolved canonical party id. When VDX has projected
 * a software config binding for that party, the durable `config-key-prefix` is used. Otherwise this
 * falls back to `oid4vci.issuers.<instanceId>` for direct IDK and greenfield configurations.
 *
 * This is the runtime half of the per-issuer story: VDX may route by canonical party UUID while its
 * supported configuration APIs write beneath a stable logical instance prefix. The derived binding
 * projection joins those two durable identities without copying configuration or consulting an
 * in-memory registry. It does not inherit from the singular namespace: every selected issuer value
 * must still be persisted explicitly.
 *
 * ## Namespace shape (no brackets)
 * The instance id is appended as a PLAIN dotted segment (`$INSTANCES_NAMESPACE.$instanceId`), NOT
 * bracket-quoted. This matches the OAuth2 AS precedent (`oauth2.servers.$id`) and, decisively, the
 * keys VDX actually writes: `oid4vci.issuers.<partyId>.<key>` is written through the same
 * `PropertyKeyNormalizer` with the id un-bracketed, so its hyphens are normalised to dots. Reading
 * with a bracket-quoted id (`[<id>]`) would preserve the hyphens verbatim and fail to match the
 * persisted, hyphen-to-dot-normalised keys. Plain concatenation round-trips with the writer.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<Oid4vciIssuerConfigProvider>(),
)
@ContributesBinding(
    SessionScope::class,
    binding = binding<VctTypeMetadataProvider>(),
)
class RegistryBackedOid4vciIssuerConfigProvider(
    private val execution: SessionExecution,
    private val instanceIdProvider: Oid4vciIssuerInstanceIdProvider,
    private val authorizationPolicyProvider: Oid4vciIssuerAuthorizationPolicyProvider,
    statusListDefinitionsProvider: Provider<StatusListDefinitionsProvider>? = null,
    keyNameResolver: Provider<IssuerKeyNameResolver>? = null,
) : AbstractConfigOid4vciIssuerConfigProvider(
        execution = execution,
        statusListDefinitionsProvider = statusListDefinitionsProvider,
        namespaceProvider = {
            val instanceId = instanceIdProvider.requireCurrentInstanceId()
            val configService = execution.conf.conf(com.sphereon.core.api.conf.ConfigLevel.PRINCIPAL)
            configService
                .getPropertyAsString("$SERVICE_CONFIG_BINDING_BY_PARTY_PREFIX.$instanceId.config-key-prefix", null)
                ?.takeIf { it.isNotBlank() }
                ?: "$INSTANCES_NAMESPACE.$instanceId"
        },
        instanceIdProvider = {
            instanceIdProvider.requireCurrentInstanceId()
        },
        keyNameResolver = keyNameResolver,
    ) {

    @Volatile
    private var authorizationPolicy: Oid4vciIssuerAuthorizationPolicy? = null

    override suspend fun prepare() {
        val tenantId = execution.sessionContext.context.tenant.tenantId
        val instanceId = instanceIdProvider.requireCurrentInstanceId()
        authorizationPolicy = authorizationPolicyProvider.resolve(tenantId, instanceId)
    }

    override val authorizationServers: List<String>
        get() = preparedPolicy().authorizationServers
            .filter { it.enabled }
            .map { it.issuerIdentifier }
            .distinct()

    override val specProfile
        get() = preparedPolicy().profile

    override val oid4vciSpecVersion
        get() = specProfile.version

    private fun preparedPolicy(): Oid4vciIssuerAuthorizationPolicy =
        authorizationPolicy ?: error("OID4VCI issuer authorization policy has not been prepared")
}

private const val SERVICE_CONFIG_BINDING_BY_PARTY_PREFIX = "_derived.software.config-bindings.by-party"
