package com.sphereon.openid.oid4vci.issuer.impl.config

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationGrant
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationServerDeployment
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationServerLifecycle
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciBoundAuthorizationServer
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciIssuerAuthorizationPolicy
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciIssuerAuthorizationPolicyProvider
import com.sphereon.openid.oid4vci.issuer.config.INSTANCES_NAMESPACE
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerSpecProfile
import com.sphereon.openid.oid4vci.issuer.config.requireCanonicalOid4vciIssuerInstanceId
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Standalone projection of an immutable authorization policy from a plural issuer resource. */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciIssuerAuthorizationPolicyProvider>())
@OptIn(ExperimentalUuidApi::class)
class ResourceBackedOid4vciAuthorizationPolicyProvider(
    private val execution: SessionExecution,
) : Oid4vciIssuerAuthorizationPolicyProvider {
    override suspend fun resolve(tenantId: String, issuerInstanceId: String): Oid4vciIssuerAuthorizationPolicy {
        val issuerId = Uuid.parse(requireCanonicalOid4vciIssuerInstanceId(issuerInstanceId))
        val root = "$INSTANCES_NAMESPACE.$issuerInstanceId"
        val config = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService
        fun required(key: String): String = config.getPropertyAsString("$root.$key")
            ?.takeIf { it.isNotBlank() } ?: error("Missing required issuer resource property '$root.$key'")
        fun optional(key: String): String? = config.getPropertyAsString("$root.$key")?.takeIf { it.isNotBlank() }
        fun uuid(key: String): Uuid = Uuid.parse(requireCanonicalOid4vciIssuerInstanceId(required(key)))
        fun grants(value: String): Set<Oid4vciAuthorizationGrant> =
            value.split(',').map { Oid4vciAuthorizationGrant.valueOf(it.trim()) }.toSet()

        required("identifier")
        val servers = required("authorizationServerIds").split(',').map { rawId ->
            val id = Uuid.parse(requireCanonicalOid4vciIssuerInstanceId(rawId.trim()))
            val prefix = "authorizationServers.$id"
            fun serverRequired(key: String): String = required("$prefix.$key")
            fun serverOptional(key: String): String? = optional("$prefix.$key")
            Oid4vciBoundAuthorizationServer(
                id = id,
                tenantId = serverRequired("tenantId"),
                issuerIdentifier = serverRequired("issuerIdentifier"),
                enabled = serverRequired("enabled").toBooleanStrict(),
                default = serverRequired("default").toBooleanStrict(),
                lifecycle = Oid4vciAuthorizationServerLifecycle.valueOf(serverRequired("lifecycle")),
                deployment = Oid4vciAuthorizationServerDeployment.valueOf(serverRequired("deployment")),
                credentialIssuancePurpose = serverRequired("credentialIssuancePurpose").toBooleanStrict(),
                allowedGrants = grants(serverRequired("allowedGrants")),
                revision = serverRequired("revision").toLong(),
                authorizationEndpoint = serverOptional("authorizationEndpoint"),
                tokenEndpoint = serverOptional("tokenEndpoint"),
                runtimeServerKey = serverOptional("runtimeServerKey"),
                jwksUri = serverOptional("jwksUri"),
                discoveryCurrent = serverRequired("discoveryCurrent").toBooleanStrict(),
                bindingRevision = serverRequired("bindingRevision").toLong(),
            )
        }
        return Oid4vciIssuerAuthorizationPolicy(
            tenantId = tenantId,
            issuerId = issuerId,
            issuerCapabilityId = uuid("issuerCapabilityId"),
            authorizationServers = servers,
            profile = Oid4vciIssuerSpecProfile.parse(required("profile")),
            profileRevision = required("profileRevision").toLong(),
        )
    }
}
