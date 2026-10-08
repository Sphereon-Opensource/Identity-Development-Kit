@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.openid.oid4vci.issuer.authorization

import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerSpecProfile
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@Serializable
enum class Oid4vciAuthorizationGrant {
    AUTHORIZATION_CODE,
    PRE_AUTHORIZED_CODE,
}

enum class Oid4vciAuthorizationServerLifecycle {
    DRAFT,
    ACTIVE,
    SUSPENDED,
    DECOMMISSIONED,
}

@Serializable
enum class Oid4vciAuthorizationServerDeployment {
    HOSTED,
    EXTERNAL,
}

data class Oid4vciBoundAuthorizationServer(
    val id: Uuid,
    val tenantId: String,
    val issuerIdentifier: String,
    val enabled: Boolean,
    val default: Boolean,
    val lifecycle: Oid4vciAuthorizationServerLifecycle,
    val deployment: Oid4vciAuthorizationServerDeployment,
    val credentialIssuancePurpose: Boolean,
    val allowedGrants: Set<Oid4vciAuthorizationGrant>,
    val authorizationEndpoint: String?,
    val tokenEndpoint: String?,
    /** Hosted runtime projection key (normally the resource slug). Never inferred from defaults. */
    val runtimeServerKey: String? = null,
    /** Discovery-pinned JWKS URI used for external JWT access-token verification. */
    val jwksUri: String? = null,
    val discoveryCurrent: Boolean,
    val revision: Long = 0,
    val bindingRevision: Long,
)

data class Oid4vciIssuerAuthorizationPolicy(
    val tenantId: String,
    val issuerId: Uuid,
    val issuerCapabilityId: Uuid,
    val authorizationServers: List<Oid4vciBoundAuthorizationServer>,
    val profile: Oid4vciIssuerSpecProfile,
    val profileRevision: Long,
)

data class Oid4vciCredentialAuthorizationSelection(
    val credentialConfigurationId: String,
    val authorizationServerId: Uuid? = null,
    val allowedGrants: Set<Oid4vciAuthorizationGrant>? = null,
)

data class Oid4vciAuthorizationSelectionRequest(
    val credentialSelections: List<Oid4vciCredentialAuthorizationSelection>,
    val templateAuthorizationServerId: Uuid? = null,
    val templateAllowedGrants: Set<Oid4vciAuthorizationGrant>? = null,
    val requiredGrants: Set<Oid4vciAuthorizationGrant>,
)

@Serializable
data class Oid4vciAuthorizationPolicySnapshot(
    val issuerId: Uuid,
    val authorizationServerId: Uuid,
    val authorizationServerIssuer: String,
    val authorizationServerDeployment: Oid4vciAuthorizationServerDeployment = Oid4vciAuthorizationServerDeployment.HOSTED,
    val authorizationServerRuntimeKey: String? = null,
    val authorizationServerTokenEndpoint: String = "",
    val authorizationServerJwksUri: String? = null,
    val grantAuthorizationServer: String? = null,
    val applicableGrants: Set<Oid4vciAuthorizationGrant>,
    val profile: Oid4vciIssuerSpecProfile,
    val profileRevision: Long,
    val authorizationServerRevision: Long = 0,
    val bindingRevision: Long,
)

enum class Oid4vciAuthorizationSelectionError {
    MISSING_DEFAULT,
    MULTIPLE_DEFAULTS,
    UNKNOWN_AUTHORIZATION_SERVER,
    DISABLED_AUTHORIZATION_SERVER,
    INACTIVE_AUTHORIZATION_SERVER,
    CROSS_TENANT_AUTHORIZATION_SERVER,
    INCOMPATIBLE_PURPOSE,
    STALE_DISCOVERY,
    UNSUPPORTED_GRANT,
    MISSING_ENDPOINT,
    MIXED_AUTHORIZATION_SERVERS,
}

class Oid4vciAuthorizationSelectionException(
    val error: Oid4vciAuthorizationSelectionError,
    message: String,
) : IllegalStateException(message)

/** Runtime selection boundary shared with enterprise composition without depending on issuer impl. */
interface Oid4vciAuthorizationServerSelection {
    fun select(
        policy: Oid4vciIssuerAuthorizationPolicy,
        request: Oid4vciAuthorizationSelectionRequest,
    ): Oid4vciAuthorizationPolicySnapshot
}

/** Deployment boundary implemented by EDK/VDX, never by the generic wire protocol itself. */
interface Oid4vciIssuerAuthorizationPolicyProvider {
    suspend fun resolve(tenantId: String, issuerInstanceId: String): Oid4vciIssuerAuthorizationPolicy
}
