/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

@file:OptIn(ExperimentalTime::class)

package com.sphereon.software.registry.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@Serializable
enum class AuthorizationServerDeployment {
    @SerialName("HOSTED") HOSTED,
    @SerialName("EXTERNAL") EXTERNAL,
}

@Serializable
enum class AuthorizationServerLifecycle {
    @SerialName("DRAFT") DRAFT,
    @SerialName("ACTIVE") ACTIVE,
    @SerialName("SUSPENDED") SUSPENDED,
    @SerialName("DECOMMISSIONED") DECOMMISSIONED,
}

@Serializable
enum class AuthorizationServerCapability {
    @SerialName("OAUTH2") OAUTH2,
    @SerialName("OIDC") OIDC,
}

@Serializable
enum class AuthorizationServerPurpose {
    @SerialName("GENERAL") GENERAL,
    @SerialName("CREDENTIAL_ISSUANCE") CREDENTIAL_ISSUANCE,
    @SerialName("WALLET_LOGIN") WALLET_LOGIN,
}

@Serializable
enum class AuthorizationServerUsage {
    @SerialName("OID4VCI_AUTHORIZATION_SERVER") OID4VCI_AUTHORIZATION_SERVER,
    @SerialName("HOSTED_LOGIN_UPSTREAM") HOSTED_LOGIN_UPSTREAM,
}

@Serializable
enum class HostedAuthenticationMode {
    @SerialName("LOCAL_ONLY") LOCAL_ONLY,
    @SerialName("FEDERATED_ONLY") FEDERATED_ONLY,
    @SerialName("HYBRID") HYBRID,
}

@Serializable
enum class AuthorizationServerGrantType {
    @SerialName("authorization_code") AUTHORIZATION_CODE,
    @SerialName("urn:ietf:params:oauth:grant-type:pre-authorized_code") PRE_AUTHORIZED_CODE,
    @SerialName("client_credentials") CLIENT_CREDENTIALS,
    @SerialName("refresh_token") REFRESH_TOKEN,
}

@Serializable
enum class UpstreamClientAuthenticationMethod {
    @SerialName("none") NONE,
    @SerialName("client_secret_basic") CLIENT_SECRET_BASIC,
    @SerialName("client_secret_post") CLIENT_SECRET_POST,
    @SerialName("private_key_jwt") PRIVATE_KEY_JWT,
}

@Serializable
enum class DiscoveryFreshness {
    @SerialName("CURRENT") CURRENT,
    @SerialName("STALE") STALE,
    @SerialName("INVALID") INVALID,
    @SerialName("UNAVAILABLE") UNAVAILABLE,
}

@Serializable
data class AuthorizationServerDiscoverySnapshot(
    val capabilities: Set<AuthorizationServerCapability>,
    val grantTypes: Set<AuthorizationServerGrantType>,
    val tokenEndpointAuthMethodsSupported: Set<UpstreamClientAuthenticationMethod>,
    val issuer: String,
    val authorizationEndpoint: String? = null,
    val tokenEndpoint: String? = null,
    val userinfoEndpoint: String? = null,
    val registrationEndpoint: String? = null,
    val pushedAuthorizationRequestEndpoint: String? = null,
    val jwksUri: String? = null,
    val scopesSupported: Set<String>,
    val idTokenSigningAlgorithms: Set<String>,
    val sourceUrls: List<String>,
    val digest: String,
    val validatedAt: Instant,
    val validUntil: Instant? = null,
    val freshness: DiscoveryFreshness,
) {
    /** OIDC Core requires `openid` even when the recommended discovery field is absent. */
    val effectiveScopesSupported: Set<String>
        get() = if (AuthorizationServerCapability.OIDC in capabilities) scopesSupported + "openid" else scopesSupported

    init {
        require(capabilities.isNotEmpty()) { "Discovery must establish at least one capability" }
        require(AuthorizationServerCapability.OIDC !in capabilities || AuthorizationServerCapability.OAUTH2 in capabilities) {
            "OIDC capability requires OAuth2 capability"
        }
        requireHttpsUrl(issuer, "Discovered issuer")
        require(sourceUrls.isNotEmpty() && sourceUrls.all(::isHttpsUrl)) {
            "Discovery source URLs must be non-empty HTTPS URLs"
        }
        require(userinfoEndpoint == null || isHttpsUrl(userinfoEndpoint)) {
            "Discovered UserInfo endpoint must be an HTTPS URL"
        }
        require(SHA_256.matches(digest)) { "Discovery digest must be a lowercase SHA-256 value" }
        require(validUntil == null || validUntil >= validatedAt) { "Discovery validity limit must not precede its validation timestamp" }
    }
}

/**
 * Portable authorization-server resource contract. VDX persists the commercial resource and EDK
 * projects hosted configuration from it; `oauth2.servers.*` is not lifecycle authority.
 */
@Serializable
data class AuthorizationServerResource(
    val id: String,
    val tenantId: String,
    val slug: String,
    val displayName: String,
    val issuer: String,
    val lifecycle: AuthorizationServerLifecycle,
    val deployment: AuthorizationServerDeployment,
    val authenticationMode: HostedAuthenticationMode? = null,
    val purposes: Set<AuthorizationServerPurpose>,
    val usages: Set<AuthorizationServerUsage>,
    val allowedGrantTypes: Set<AuthorizationServerGrantType>,
    /** Effective capabilities established by hosted configuration or the current external snapshot. */
    val capabilities: Set<AuthorizationServerCapability>,
    /** Intended protocol contract, persisted independently from discovery evidence. */
    val expectedCapabilities: Set<AuthorizationServerCapability>,
    val system: Boolean,
    val defaultForPurposes: Set<AuthorizationServerPurpose>,
    val discovery: AuthorizationServerDiscoverySnapshot? = null,
    val revision: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
) {
    init {
        require(UUID.matches(id)) { "Authorization server id must be a UUID" }
        require(tenantId.isNotBlank()) { "Tenant id is required" }
        require(tenantId != PLATFORM_TENANT_ID) { "The platform tenant built-in authorization server is outside this resource model" }
        require(SLUG.matches(slug)) { "Authorization server slug is invalid" }
        require(displayName.isNotBlank()) { "Authorization server display name is required" }
        requireHttpsUrl(issuer, "Authorization server issuer")
        require(purposes.isNotEmpty()) { "Authorization server must have at least one purpose" }
        require(defaultForPurposes.all { it in purposes }) { "Default purposes must be a subset of resource purposes" }
        require(revision >= 0) { "Authorization server revision cannot be negative" }
        require(updatedAt >= createdAt) { "Authorization server update timestamp cannot precede creation" }
        require(AuthorizationServerCapability.OIDC !in capabilities || AuthorizationServerCapability.OAUTH2 in capabilities) {
            "OIDC capability requires OAuth2 capability"
        }

        when (deployment) {
            AuthorizationServerDeployment.HOSTED -> {
                require(authenticationMode != null) { "Hosted authorization servers require an authentication mode" }
                require(discovery == null) { "Hosted authorization servers do not persist external discovery snapshots" }
                require(usages.isEmpty()) { "Hosted authorization servers do not declare external usages" }
                require(expectedCapabilities.isEmpty()) { "Hosted authorization servers do not declare external expected capabilities" }
                require(capabilities.isNotEmpty()) { "Hosted authorization servers require configured capabilities" }
            }

            AuthorizationServerDeployment.EXTERNAL -> {
                require(expectedCapabilities.isNotEmpty()) { "External authorization servers require expected capabilities" }
                require(AuthorizationServerCapability.OIDC !in expectedCapabilities || AuthorizationServerCapability.OAUTH2 in expectedCapabilities) {
                    "Expected OIDC capability requires OAuth2"
                }
                require(authenticationMode == null) { "External authorization servers cannot declare a hosted authentication mode" }
                val validated = discovery
                require(capabilities == validated?.capabilities.orEmpty()) {
                    "External effective capabilities must equal the persisted discovery snapshot"
                }
                if (validated == null) {
                    require(lifecycle != AuthorizationServerLifecycle.ACTIVE) {
                        "Active external authorization servers require a validated discovery snapshot"
                    }
                    require(usages.isEmpty() && allowedGrantTypes.isEmpty()) {
                        "Unvalidated external authorization servers cannot enable usages or grants"
                    }
                } else {
                    require(validated.issuer == issuer) { "Resource issuer must equal the validated discovery issuer" }
                    require(allowedGrantTypes.all { it in validated.grantTypes }) {
                        "Allowed grant types must be a subset of the last validated discovery snapshot"
                    }
                    if (AuthorizationServerUsage.OID4VCI_AUTHORIZATION_SERVER in usages) {
                        require(AuthorizationServerCapability.OAUTH2 in validated.capabilities) {
                            "OID4VCI authorization usage requires a validated OAuth2 capability"
                        }
                    }
                    if (AuthorizationServerUsage.HOSTED_LOGIN_UPSTREAM in usages) {
                        require(AuthorizationServerCapability.OIDC in validated.capabilities) {
                            "Hosted login usage requires a validated OIDC capability"
                        }
                    }
                }
            }
        }
    }

    fun transitionTo(target: AuthorizationServerLifecycle, at: Instant): AuthorizationServerResource {
        if (target == lifecycle) return this
        check(target in lifecycle.allowedTargets()) {
            "Authorization server lifecycle transition from $lifecycle to $target is not allowed"
        }
        require(at >= updatedAt) { "Lifecycle transition timestamp cannot precede the resource update timestamp" }
        return copy(lifecycle = target, revision = revision + 1, updatedAt = at)
    }
}

@Serializable
data class TypedSecretReference(
    val resourceHandle: String,
    val purpose: SecretReferencePurpose,
) {
    init {
        require(resourceHandle.isNotBlank()) { "Secret resource handle is required" }
    }
}

@Serializable enum class SecretReferencePurpose { OAUTH_CLIENT_SECRET }

@Serializable
data class TypedKmsReference(
    val kmsResourceHandle: String,
    val kmsKeyAlias: String,
    val purpose: KmsReferencePurpose,
) {
    init {
        require(kmsResourceHandle.isNotBlank()) { "KMS resource handle is required" }
        require(kmsKeyAlias.isNotBlank()) { "KMS key alias is required" }
    }
}

@Serializable enum class KmsReferencePurpose { OAUTH_CLIENT_ASSERTION_SIGNING, TOKEN_SIGNING }

@Serializable
data class FederationClientAuthentication(
    val method: UpstreamClientAuthenticationMethod,
    val clientId: String? = null,
    val secretReference: TypedSecretReference? = null,
    val kmsReference: TypedKmsReference? = null,
) {
    init {
        require(clientId == null || clientId.isNotBlank()) { "Federation client id cannot be blank" }
        when (method) {
            UpstreamClientAuthenticationMethod.NONE ->
                require(secretReference == null && kmsReference == null) { "Public upstream clients cannot carry secret or KMS references" }

            UpstreamClientAuthenticationMethod.CLIENT_SECRET_BASIC,
            UpstreamClientAuthenticationMethod.CLIENT_SECRET_POST,
            -> {
                require(secretReference != null) { "Secret-based upstream client authentication requires a typed secret reference" }
                require(kmsReference == null) { "Secret-based upstream client authentication cannot carry a KMS reference" }
            }

            UpstreamClientAuthenticationMethod.PRIVATE_KEY_JWT -> {
                require(kmsReference != null) { "private_key_jwt requires a typed KMS reference" }
                require(kmsReference.purpose == KmsReferencePurpose.OAUTH_CLIENT_ASSERTION_SIGNING) {
                    "private_key_jwt requires an OAuth client assertion signing key"
                }
                require(secretReference == null) { "private_key_jwt cannot carry a secret reference" }
            }
        }
    }
}

@Serializable
enum class FederationBindingStatus {
    @SerialName("UNVALIDATED") UNVALIDATED,
    @SerialName("VALID") VALID,
    @SerialName("INVALID") INVALID,
    @SerialName("DISABLED") DISABLED,
}

@Serializable
data class FederationBinding(
    val id: String,
    val hostedAuthorizationServerId: String,
    val externalAuthorizationServerId: String,
    val order: Int,
    val enabled: Boolean,
    val scopes: Set<String>,
    val claimsMapping: Map<String, String>,
    val clientAuthentication: FederationClientAuthentication,
    val status: FederationBindingStatus,
    val lastValidatedAt: Instant? = null,
    val revision: Long,
) {
    init {
        require(UUID.matches(id)) { "Federation binding id must be a UUID" }
        require(UUID.matches(hostedAuthorizationServerId) && UUID.matches(externalAuthorizationServerId)) {
            "Federation binding endpoints must be UUIDs"
        }
        require(hostedAuthorizationServerId != externalAuthorizationServerId) { "Federation binding endpoints must be distinct" }
        require(order >= 0) { "Federation binding order cannot be negative" }
        require("openid" in scopes) { "Federation binding scopes must include openid" }
        require(scopes.all { it.isNotBlank() }) { "Federation binding scopes cannot contain blanks" }
        require(claimsMapping.all { (source, target) -> source.isNotBlank() && target.isNotBlank() }) {
            "Federation claims mappings cannot contain blank names"
        }
        require(revision >= 0) { "Federation binding revision cannot be negative" }
        if (enabled) {
            require(status == FederationBindingStatus.VALID && lastValidatedAt != null) {
                "Enabled federation binding must have current VALID validation"
            }
        }
    }

    fun validateAgainst(source: AuthorizationServerResource, target: AuthorizationServerResource, at: Instant) {
        require(source.id == hostedAuthorizationServerId && target.id == externalAuthorizationServerId) {
            "Federation binding resource identifiers do not match its endpoints"
        }
        require(source.deployment == AuthorizationServerDeployment.HOSTED) {
            "Federation binding source must be a hosted authorization server"
        }
        require(target.deployment == AuthorizationServerDeployment.EXTERNAL) {
            "Federation binding target must be an external authorization server"
        }
        require(source.tenantId == target.tenantId) {
            "Federation binding resources must belong to the same tenant"
        }
        if (enabled) {
            require(status == FederationBindingStatus.VALID && lastValidatedAt != null) {
                "Enabled federation binding must have current VALID validation"
            }
            require(
                source.lifecycle == AuthorizationServerLifecycle.ACTIVE &&
                    target.lifecycle == AuthorizationServerLifecycle.ACTIVE,
            ) { "Federation binding endpoints must both be active" }
        }
        val discovery = requireNotNull(target.discovery) { "Federation target requires validated discovery" }
        if (enabled) {
            require(lastValidatedAt!! in discovery.validatedAt..at) {
                "Federation binding validation timestamp must match the current discovery window"
            }
            require(
                discovery.freshness == DiscoveryFreshness.CURRENT &&
                    (discovery.validUntil == null || at < discovery.validUntil),
            ) { "Federation target discovery must be fresh" }
        }
        require(AuthorizationServerCapability.OIDC in discovery.capabilities) {
            "Federation binding target must have a validated OIDC capability"
        }
        require(scopes.all { it in discovery.effectiveScopesSupported }) {
            "Federation binding scopes must be a subset of discovered scopes"
        }
        require(AuthorizationServerUsage.HOSTED_LOGIN_UPSTREAM in target.usages) {
            "Federation binding target must allow hosted login usage"
        }
        require(clientAuthentication.method in discovery.tokenEndpointAuthMethodsSupported) {
            "Federation client authentication method was not advertised by the external provider"
        }
    }
}

private const val PLATFORM_TENANT_ID = "platform"
private val UUID = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
private val SLUG = Regex("^[a-z][a-z0-9-]{0,62}$")
private val SHA_256 = Regex("^[0-9a-f]{64}$")

private fun isHttpsUrl(value: String): Boolean =
    value.startsWith("https://") && value.length > "https://".length && !value.contains('#')

private fun requireHttpsUrl(value: String, name: String) {
    require(isHttpsUrl(value)) { "$name must be an HTTPS URL without a fragment" }
}

private fun AuthorizationServerLifecycle.allowedTargets(): Set<AuthorizationServerLifecycle> =
    when (this) {
        AuthorizationServerLifecycle.DRAFT -> setOf(
            AuthorizationServerLifecycle.ACTIVE,
            AuthorizationServerLifecycle.DECOMMISSIONED,
        )

            AuthorizationServerLifecycle.ACTIVE -> setOf(
            AuthorizationServerLifecycle.SUSPENDED,
            AuthorizationServerLifecycle.DECOMMISSIONED,
        )

            AuthorizationServerLifecycle.SUSPENDED -> setOf(
            AuthorizationServerLifecycle.ACTIVE,
            AuthorizationServerLifecycle.DECOMMISSIONED,
        )

        AuthorizationServerLifecycle.DECOMMISSIONED -> emptySet()
    }
