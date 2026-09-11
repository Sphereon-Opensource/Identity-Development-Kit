@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.openid.oid4vci.issuer.impl.authorization

import com.sphereon.openid.oid4vci.issuer.authorization.*
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerSpecProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class Oid4vciAuthorizationServerSelectorTest {
    @Test
    fun inFlightSnapshotDoesNotChangeWhenPolicyIsReplaced() {
        val original = policy(server(firstId, default = true, issuer = "https://as-a.example.com"))
        val snapshot = selector.select(original, request())

        val replaced = original.copy(
            profile = Oid4vciIssuerSpecProfile.OID4VCI_1_1_DRAFT_2A1F0513,
            profileRevision = 8,
            authorizationServers = listOf(server(secondId, default = true, issuer = "https://as-b.example.com")),
        )

        assertEquals(firstId, snapshot.authorizationServerId)
        assertEquals(Oid4vciIssuerSpecProfile.OID4VCI_1_0_FINAL, snapshot.profile)
        assertEquals(7, snapshot.profileRevision)
        assertEquals(secondId, selector.select(replaced, request()).authorizationServerId)
    }
    private val tenantId = "tenant-a"
    private val issuerId = uuid(1)
    private val firstId = uuid(2)
    private val secondId = uuid(3)
    private val selector = Oid4vciAuthorizationServerSelector()

    @Test
    fun templateOverridePrecedesCredentialOverrideAndIssuerDefault() {
        val policy = policy(
            server(firstId, default = true, issuer = "https://as-one.example"),
            server(secondId, issuer = "https://as-two.example"),
        )

        val snapshot = selector.select(
            policy,
            Oid4vciAuthorizationSelectionRequest(
                credentialSelections = listOf(
                    Oid4vciCredentialAuthorizationSelection("Employee", firstId),
                    Oid4vciCredentialAuthorizationSelection("Badge", null),
                ),
                templateAuthorizationServerId = secondId,
                requiredGrants = setOf(Oid4vciAuthorizationGrant.AUTHORIZATION_CODE),
            ),
        )

        assertEquals(secondId, snapshot.authorizationServerId)
        assertEquals("https://as-two.example", snapshot.authorizationServerIssuer)
    }

    @Test
    fun credentialOverridesThatResolveToDifferentServersFailClosed() {
        val policy = policy(
            server(firstId, default = true, issuer = "https://as-one.example"),
            server(secondId, issuer = "https://as-two.example"),
        )

        val failure = assertFailsWith<Oid4vciAuthorizationSelectionException> {
            selector.select(
                policy,
                Oid4vciAuthorizationSelectionRequest(
                    credentialSelections = listOf(
                        Oid4vciCredentialAuthorizationSelection("Employee", firstId),
                        Oid4vciCredentialAuthorizationSelection("Badge", secondId),
                    ),
                    requiredGrants = setOf(Oid4vciAuthorizationGrant.PRE_AUTHORIZED_CODE),
                ),
            )
        }

        assertEquals(Oid4vciAuthorizationSelectionError.MIXED_AUTHORIZATION_SERVERS, failure.error)
    }

    @Test
    fun templateOverrideCannotBypassMissingIssuerDefault() {
        val policy = policy(server(secondId, issuer = "https://as-two.example"))

        val failure = assertFailsWith<Oid4vciAuthorizationSelectionException> {
            selector.select(
                policy,
                Oid4vciAuthorizationSelectionRequest(
                    credentialSelections = listOf(Oid4vciCredentialAuthorizationSelection("Employee")),
                    templateAuthorizationServerId = secondId,
                    requiredGrants = setOf(Oid4vciAuthorizationGrant.PRE_AUTHORIZED_CODE),
                ),
            )
        }

        assertEquals(Oid4vciAuthorizationSelectionError.MISSING_DEFAULT, failure.error)
    }

    @Test
    fun multipleEnabledDefaultsFailBeforeSelection() {
        val policy = policy(
            server(firstId, default = true, issuer = "https://as-one.example"),
            server(secondId, default = true, issuer = "https://as-two.example"),
        )

        val failure = assertFailsWith<Oid4vciAuthorizationSelectionException> {
            selector.select(policy, request())
        }

        assertEquals(Oid4vciAuthorizationSelectionError.MULTIPLE_DEFAULTS, failure.error)
    }

    @Test
    fun authorizationCodeAndPreAuthorizedCodeCapabilitiesAreCheckedIndependently() {
        val policy = policy(
            server(
                firstId,
                default = true,
                issuer = "https://as-one.example",
                grants = setOf(Oid4vciAuthorizationGrant.PRE_AUTHORIZED_CODE),
                authorizationEndpoint = null,
            ),
        )

        val preAuthorized = selector.select(
            policy,
            Oid4vciAuthorizationSelectionRequest(
                credentialSelections = listOf(Oid4vciCredentialAuthorizationSelection("Employee")),
                requiredGrants = setOf(Oid4vciAuthorizationGrant.PRE_AUTHORIZED_CODE),
            ),
        )
        assertEquals(firstId, preAuthorized.authorizationServerId)

        val failure = assertFailsWith<Oid4vciAuthorizationSelectionException> {
            selector.select(
                policy,
                Oid4vciAuthorizationSelectionRequest(
                    credentialSelections = listOf(Oid4vciCredentialAuthorizationSelection("Employee")),
                    requiredGrants = setOf(Oid4vciAuthorizationGrant.AUTHORIZATION_CODE),
                ),
            )
        }
        assertEquals(Oid4vciAuthorizationSelectionError.UNSUPPORTED_GRANT, failure.error)
    }

    @Test
    fun staleExternalDiscoveryFailsBeforeEndpointUse() {
        val policy = policy(
            server(
                firstId,
                default = true,
                issuer = "https://as-one.example",
                deployment = Oid4vciAuthorizationServerDeployment.EXTERNAL,
                discoveryCurrent = false,
            ),
        )

        val failure = assertFailsWith<Oid4vciAuthorizationSelectionException> {
            selector.select(
                policy,
                Oid4vciAuthorizationSelectionRequest(
                    credentialSelections = listOf(Oid4vciCredentialAuthorizationSelection("Employee")),
                    requiredGrants = setOf(Oid4vciAuthorizationGrant.AUTHORIZATION_CODE),
                ),
            )
        }

        assertEquals(Oid4vciAuthorizationSelectionError.STALE_DISCOVERY, failure.error)
    }

    @Test
    fun crossTenantSelectionFailsClosed() {
        val failure = assertSelectionFailure(
            server(firstId, default = true, issuer = "https://as.example", tenant = "tenant-b"),
        )
        assertEquals(Oid4vciAuthorizationSelectionError.CROSS_TENANT_AUTHORIZATION_SERVER, failure.error)
    }

    @Test
    fun disabledSuspendedAndDecommissionedSelectionsFailClosed() {
        val disabled = assertFailsWith<Oid4vciAuthorizationSelectionException> {
            selector.select(
                policy(
                    server(firstId, default = true, issuer = "https://default.example"),
                    server(secondId, issuer = "https://disabled.example", enabled = false),
                ),
                request().copy(
                    credentialSelections = listOf(Oid4vciCredentialAuthorizationSelection("Employee", secondId)),
                ),
            )
        }
        assertEquals(
            Oid4vciAuthorizationSelectionError.DISABLED_AUTHORIZATION_SERVER,
            disabled.error,
        )
        assertEquals(
            Oid4vciAuthorizationSelectionError.INACTIVE_AUTHORIZATION_SERVER,
            assertSelectionFailure(
                server(firstId, default = true, issuer = "https://as.example", lifecycle = Oid4vciAuthorizationServerLifecycle.SUSPENDED),
            ).error,
        )
        assertEquals(
            Oid4vciAuthorizationSelectionError.INACTIVE_AUTHORIZATION_SERVER,
            assertSelectionFailure(
                server(firstId, default = true, issuer = "https://as.example", lifecycle = Oid4vciAuthorizationServerLifecycle.DECOMMISSIONED),
            ).error,
        )
    }

    @Test
    fun incompatiblePurposeAndMissingEndpointsFailClosed() {
        assertEquals(
            Oid4vciAuthorizationSelectionError.INCOMPATIBLE_PURPOSE,
            assertSelectionFailure(server(firstId, default = true, issuer = "https://as.example", credentialIssuancePurpose = false)).error,
        )
        assertEquals(
            Oid4vciAuthorizationSelectionError.MISSING_ENDPOINT,
            assertSelectionFailure(server(firstId, default = true, issuer = "https://as.example", tokenEndpoint = null)).error,
        )
        assertEquals(
            Oid4vciAuthorizationSelectionError.MISSING_ENDPOINT,
            assertSelectionFailure(
                server(firstId, default = true, issuer = "https://as.example", authorizationEndpoint = null),
                setOf(Oid4vciAuthorizationGrant.AUTHORIZATION_CODE),
            ).error,
        )
    }

    @Test
    fun metadataAdvertisesAllEnabledBindingsAndGrantSelectorOnlyForSeveral() {
        val single = policy(server(firstId, default = true, issuer = "https://as-one.example"))
        val singleSnapshot = selector.select(single, request())
        assertEquals(listOf("https://as-one.example"), selector.advertisedAuthorizationServers(single))
        assertNull(selector.grantAuthorizationServer(single, singleSnapshot))

        val several = policy(
            server(firstId, default = true, issuer = "https://as-one.example"),
            server(secondId, issuer = "https://as-two.example"),
        )
        val severalSnapshot = selector.select(several, request())
        assertEquals(
            listOf("https://as-one.example", "https://as-two.example"),
            selector.advertisedAuthorizationServers(several),
        )
        assertEquals("https://as-one.example", selector.grantAuthorizationServer(several, severalSnapshot))
    }

    private fun request() = Oid4vciAuthorizationSelectionRequest(
        credentialSelections = listOf(Oid4vciCredentialAuthorizationSelection("Employee")),
        requiredGrants = setOf(Oid4vciAuthorizationGrant.PRE_AUTHORIZED_CODE),
    )

    private fun assertSelectionFailure(
        server: Oid4vciBoundAuthorizationServer,
        grants: Set<Oid4vciAuthorizationGrant> = setOf(Oid4vciAuthorizationGrant.PRE_AUTHORIZED_CODE),
    ): Oid4vciAuthorizationSelectionException = assertFailsWith {
        selector.select(policy(server), request().copy(requiredGrants = grants))
    }

    private fun policy(vararg servers: Oid4vciBoundAuthorizationServer) = Oid4vciIssuerAuthorizationPolicy(
        tenantId = tenantId,
        issuerId = issuerId,
        issuerCapabilityId = Uuid.parse("dddddddd-dddd-4ddd-8ddd-dddddddddddd"),
        authorizationServers = servers.toList(),
        profile = Oid4vciIssuerSpecProfile.OID4VCI_1_0_FINAL,
        profileRevision = 7,
    )

    private fun server(
        id: Uuid,
        default: Boolean = false,
        issuer: String,
        grants: Set<Oid4vciAuthorizationGrant> = Oid4vciAuthorizationGrant.entries.toSet(),
        deployment: Oid4vciAuthorizationServerDeployment = Oid4vciAuthorizationServerDeployment.HOSTED,
        discoveryCurrent: Boolean = true,
        authorizationEndpoint: String? = "$issuer/authorize",
        tokenEndpoint: String? = "$issuer/token",
        enabled: Boolean = true,
        lifecycle: Oid4vciAuthorizationServerLifecycle = Oid4vciAuthorizationServerLifecycle.ACTIVE,
        tenant: String = tenantId,
        credentialIssuancePurpose: Boolean = true,
    ) = Oid4vciBoundAuthorizationServer(
        id = id,
        tenantId = tenant,
        issuerIdentifier = issuer,
        enabled = enabled,
        default = default,
        lifecycle = lifecycle,
        deployment = deployment,
        credentialIssuancePurpose = credentialIssuancePurpose,
        allowedGrants = grants,
        authorizationEndpoint = authorizationEndpoint,
        tokenEndpoint = tokenEndpoint,
        discoveryCurrent = discoveryCurrent,
        bindingRevision = 1,
    )

    private fun uuid(value: Int): Uuid = Uuid.parse("00000000-0000-4000-8000-${value.toString().padStart(12, '0')}")
}
