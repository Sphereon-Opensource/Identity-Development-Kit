package com.sphereon.openid.oid4vci.issuer.impl

import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationGrant
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationPolicySnapshot
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerSpecProfile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal fun testAuthorizationSnapshot(
    issuerId: String,
    authorizationServerId: String = "00000000-0000-4000-8000-000000000099",
    grants: Set<Oid4vciAuthorizationGrant> = Oid4vciAuthorizationGrant.entries.toSet(),
): Oid4vciAuthorizationPolicySnapshot = Oid4vciAuthorizationPolicySnapshot(
    issuerId = Uuid.parse(issuerId),
    authorizationServerId = Uuid.parse(authorizationServerId),
    authorizationServerIssuer = "https://as.example.test",
    applicableGrants = grants,
    profile = Oid4vciIssuerSpecProfile.OID4VCI_1_0_FINAL,
    profileRevision = 7,
    authorizationServerRevision = 11,
    bindingRevision = 13,
)
