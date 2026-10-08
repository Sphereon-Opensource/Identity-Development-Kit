@file:OptIn(kotlin.uuid.ExperimentalUuidApi::class)

package com.sphereon.openid.oid4vci.issuer.impl.config

import com.sphereon.openid.oid4vci.issuer.authorization.*
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerSpecProfile
import kotlin.uuid.Uuid

internal class TestOid4vciAuthorizationPolicyProvider(
    private val issuerIdentifier: String = "https://as.example.com",
) : Oid4vciIssuerAuthorizationPolicyProvider {
    override suspend fun resolve(tenantId: String, issuerInstanceId: String) = Oid4vciIssuerAuthorizationPolicy(
        tenantId = tenantId,
        issuerId = Uuid.parse("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
        issuerCapabilityId = Uuid.parse("cccccccc-cccc-4ccc-8ccc-cccccccccccc"),
        authorizationServers = listOf(
            Oid4vciBoundAuthorizationServer(
                id = Uuid.parse("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"),
                tenantId = tenantId,
                issuerIdentifier = issuerIdentifier,
                enabled = true,
                default = true,
                lifecycle = Oid4vciAuthorizationServerLifecycle.ACTIVE,
                deployment = Oid4vciAuthorizationServerDeployment.HOSTED,
                credentialIssuancePurpose = true,
                allowedGrants = setOf(Oid4vciAuthorizationGrant.AUTHORIZATION_CODE),
                authorizationEndpoint = "$issuerIdentifier/authorize",
                tokenEndpoint = "$issuerIdentifier/token",
                discoveryCurrent = true,
                bindingRevision = 0,
            ),
        ),
        profile = Oid4vciIssuerSpecProfile.OID4VCI_1_0_FINAL,
        profileRevision = 0,
    )
}
