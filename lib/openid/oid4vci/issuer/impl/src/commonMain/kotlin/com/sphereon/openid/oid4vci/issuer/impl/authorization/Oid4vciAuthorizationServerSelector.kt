@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.openid.oid4vci.issuer.impl.authorization

import com.sphereon.openid.oid4vci.issuer.authorization.*
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.binding
import kotlin.uuid.ExperimentalUuidApi

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<Oid4vciAuthorizationServerSelection>())
class Oid4vciAuthorizationServerSelector : Oid4vciAuthorizationServerSelection {
    override fun select(
        policy: Oid4vciIssuerAuthorizationPolicy,
        request: Oid4vciAuthorizationSelectionRequest,
    ): Oid4vciAuthorizationPolicySnapshot {
        require(request.credentialSelections.isNotEmpty()) { "At least one credential configuration is required" }
        require(request.requiredGrants.isNotEmpty()) { "At least one issuance grant is required" }

        // An issuing policy is invalid without exactly one enabled default,
        // even when every requested credential carries an explicit override.
        val defaultId = resolveDefault(policy).id
        val selectedIds = request.credentialSelections.map { credential ->
            request.templateAuthorizationServerId ?: credential.authorizationServerId ?: defaultId
        }.toSet()
        if (selectedIds.size != 1) {
            fail(
                Oid4vciAuthorizationSelectionError.MIXED_AUTHORIZATION_SERVERS,
                "Credential configurations resolve to different authorization servers",
            )
        }
        request.credentialSelections.forEach { credential ->
            if (credential.allowedGrants?.containsAll(request.requiredGrants) == false) {
                fail(Oid4vciAuthorizationSelectionError.UNSUPPORTED_GRANT, "Credential configuration '${credential.credentialConfigurationId}' does not allow every requested grant")
            }
        }
        if (request.templateAllowedGrants?.containsAll(request.requiredGrants) == false) {
            fail(Oid4vciAuthorizationSelectionError.UNSUPPORTED_GRANT, "Issuance template does not allow every requested grant")
        }

        val selected = policy.authorizationServers.firstOrNull { it.id == selectedIds.single() }
            ?: fail(
                Oid4vciAuthorizationSelectionError.UNKNOWN_AUTHORIZATION_SERVER,
                "Selected authorization server is not bound to this issuer",
            )
        validate(policy, selected, request.requiredGrants)
        return Oid4vciAuthorizationPolicySnapshot(
            issuerId = policy.issuerId,
            authorizationServerId = selected.id,
            authorizationServerIssuer = selected.issuerIdentifier,
            authorizationServerDeployment = selected.deployment,
            authorizationServerRuntimeKey = selected.runtimeServerKey,
            authorizationServerTokenEndpoint = requireNotNull(selected.tokenEndpoint),
            authorizationServerJwksUri = selected.jwksUri,
            grantAuthorizationServer = selected.issuerIdentifier.takeIf { advertisedAuthorizationServers(policy).size > 1 },
            applicableGrants = request.requiredGrants,
            profile = policy.profile,
            profileRevision = policy.profileRevision,
            authorizationServerRevision = selected.revision,
            bindingRevision = selected.bindingRevision,
        )
    }

    fun advertisedAuthorizationServers(policy: Oid4vciIssuerAuthorizationPolicy): List<String> =
        policy.authorizationServers
            .filter { it.enabled }
            .map { it.issuerIdentifier }
            .distinct()

    fun grantAuthorizationServer(
        policy: Oid4vciIssuerAuthorizationPolicy,
        snapshot: Oid4vciAuthorizationPolicySnapshot,
    ): String? = snapshot.authorizationServerIssuer.takeIf { advertisedAuthorizationServers(policy).size > 1 }

    private fun resolveDefault(policy: Oid4vciIssuerAuthorizationPolicy): Oid4vciBoundAuthorizationServer {
        val defaults = policy.authorizationServers.filter { it.enabled && it.default }
        if (defaults.isEmpty()) {
            fail(Oid4vciAuthorizationSelectionError.MISSING_DEFAULT, "Issuer has no enabled default authorization server")
        }
        if (defaults.size > 1) {
            fail(Oid4vciAuthorizationSelectionError.MULTIPLE_DEFAULTS, "Issuer has more than one enabled default authorization server")
        }
        return defaults.single()
    }

    private fun validate(
        policy: Oid4vciIssuerAuthorizationPolicy,
        selected: Oid4vciBoundAuthorizationServer,
        requiredGrants: Set<Oid4vciAuthorizationGrant>,
    ) {
        if (selected.tenantId != policy.tenantId) {
            fail(Oid4vciAuthorizationSelectionError.CROSS_TENANT_AUTHORIZATION_SERVER, "Authorization server belongs to another tenant")
        }
        if (!selected.enabled) {
            fail(Oid4vciAuthorizationSelectionError.DISABLED_AUTHORIZATION_SERVER, "Authorization-server binding is disabled")
        }
        if (selected.lifecycle != Oid4vciAuthorizationServerLifecycle.ACTIVE) {
            fail(Oid4vciAuthorizationSelectionError.INACTIVE_AUTHORIZATION_SERVER, "Authorization server is not active")
        }
        if (!selected.credentialIssuancePurpose) {
            fail(Oid4vciAuthorizationSelectionError.INCOMPATIBLE_PURPOSE, "Authorization server has no credential-issuance purpose")
        }
        if (selected.deployment == Oid4vciAuthorizationServerDeployment.EXTERNAL && !selected.discoveryCurrent) {
            fail(Oid4vciAuthorizationSelectionError.STALE_DISCOVERY, "External authorization-server discovery is not current")
        }
        if (!selected.allowedGrants.containsAll(requiredGrants)) {
            fail(Oid4vciAuthorizationSelectionError.UNSUPPORTED_GRANT, "Authorization server does not allow every requested grant")
        }
        if (selected.tokenEndpoint.isNullOrBlank()) {
            fail(Oid4vciAuthorizationSelectionError.MISSING_ENDPOINT, "Authorization server has no token endpoint")
        }
        if (Oid4vciAuthorizationGrant.AUTHORIZATION_CODE in requiredGrants && selected.authorizationEndpoint.isNullOrBlank()) {
            fail(Oid4vciAuthorizationSelectionError.MISSING_ENDPOINT, "Authorization server has no authorization endpoint")
        }
    }

    private fun fail(error: Oid4vciAuthorizationSelectionError, message: String): Nothing =
        throw Oid4vciAuthorizationSelectionException(error, message)
}
