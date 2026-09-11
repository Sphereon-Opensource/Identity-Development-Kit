@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.common.model.RequestedCredentialResponseEncryption
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationGrant
import com.sphereon.openid.oid4vci.issuer.authorization.Oid4vciAuthorizationPolicySnapshot
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerInstanceIdProvider
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerSpecProfile
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSessionStatus
import com.sphereon.openid.oid4vci.issuer.store.CredentialRequestIdentity
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class IssuanceAuthorizationSnapshotTest {
    @Test
    fun opaqueCredentialIdentifierProvidesOnlyAnIssuanceSessionLookupHint() {
        val sessionId = "ff6b2102-c658-4c96-9d6e-41ad809d5b0f"

        assertEquals(
            sessionId,
            unverifiedIssuerState(
                accessToken = "opaque-access-token",
                credentialIdentifier = "urn:vdx:oid4vci:credential:$sessionId:3aa839e4-03fd-40d0-880f-59b523f46051",
            ),
        )
        assertEquals(
            null,
            unverifiedIssuerState(
                accessToken = "opaque-access-token",
                credentialIdentifier = "urn:vdx:oid4vci:credential:not-a-uuid:3aa839e4-03fd-40d0-880f-59b523f46051",
            ),
        )
    }

    @Test
    fun walletInitiatedRequestRequiresCanonicalIssuerUuidSelector() {
        assertTrue(resolveCredentialRequestIssuerInstanceId(null, instanceProvider(null)).isErr)
        assertTrue(resolveCredentialRequestIssuerInstanceId(null, instanceProvider("default")).isErr)

        val selected = uuid(7).toString()
        val resolved = resolveCredentialRequestIssuerInstanceId(null, instanceProvider(selected))
        assertTrue(resolved.isOk)
        assertEquals(selected, resolved.value)
    }

    @Test
    fun offerLinkedRequestRemainsPinnedWhenAmbientRoutingChanges() {
        var ambient = uuid(7).toString()
        val provider =
            object : Oid4vciIssuerInstanceIdProvider {
                override fun currentInstanceId(): String? = ambient
            }
        val issuanceSession = session()

        assertEquals(ISSUER_ID.toString(), resolveCredentialRequestIssuerInstanceId(issuanceSession, provider).value)
        ambient = uuid(8).toString()
        assertEquals(ISSUER_ID.toString(), resolveCredentialRequestIssuerInstanceId(issuanceSession, provider).value)
    }

    @Test
    fun walletInitiatedIdentityConsumesItsSnapshottedAuthorizationDecision() {
        val identity = CredentialRequestIdentity(
            protocolSessionId = "wallet-session-1",
            instanceId = ISSUER_ID.toString(),
            authorizationPolicySnapshot = snapshot().copy(
                profileRevision = 31,
                authorizationServerRevision = 37,
                bindingRevision = 41,
            ),
        )
        val result = validateWalletInitiatedAuthorizationSnapshot(identity, CONFIG_ID, CredentialRequest())
        assertTrue(result.isOk)
        assertEquals(31, result.value.profileRevision)
        assertEquals(37, result.value.authorizationServerRevision)
        assertEquals(41, result.value.bindingRevision)
    }

    @Test
    fun authorizationCodeAndPreAuthorizedCodeExecutionRequireTheirSnapshottedGrant() {
        assertTrue(validateIssuanceAuthorizationSnapshot(session(), CONFIG_ID, CredentialRequest()).isOk)
        assertTrue(
            validateIssuanceAuthorizationSnapshot(
                session(preAuthCode = "pre-auth", grants = setOf(Oid4vciAuthorizationGrant.PRE_AUTHORIZED_CODE)),
                CONFIG_ID,
                CredentialRequest(),
            ).isOk,
        )

        val mismatched = validateIssuanceAuthorizationSnapshot(
            session(preAuthCode = "pre-auth"),
            CONFIG_ID,
            CredentialRequest(),
        )
        assertTrue(mismatched.isErr)
    }

    @Test
    fun missingOrCrossIssuerSnapshotFailsClosed() {
        assertTrue(validateIssuanceAuthorizationSnapshot(session(snapshot = null), CONFIG_ID, CredentialRequest()).isErr)
        val wrongIssuer = snapshot().copy(issuerId = uuid(9))
        assertTrue(validateIssuanceAuthorizationSnapshot(session(snapshot = wrongIssuer), CONFIG_ID, CredentialRequest()).isErr)
    }

    @Test
    fun credentialMustBelongToTheImmutableOfferSelection() {
        val result = validateIssuanceAuthorizationSnapshot(session(), "DifferentCredential", CredentialRequest())
        assertTrue(result.isErr)
    }

    @Test
    fun validatedTokenMustComeFromTheSnapshottedAuthorizationServer() {
        val selected = snapshot()
        assertTrue(validateAuthorizationServerSnapshot(selected, tokenContext()).isOk)
        assertTrue(
            validateAuthorizationServerSnapshot(
                selected,
                tokenContext(authorizationServerId = uuid(8).toString()),
            ).isErr,
        )
        assertTrue(
            validateAuthorizationServerSnapshot(
                selected,
                tokenContext(authorizationServerIssuer = "https://other-as.example"),
            ).isErr,
        )
    }

    @Test
    fun finalProfileRequiresAlgAndRejectsCompression() {
        val missingAlg = validateIssuanceAuthorizationSnapshot(
            session(),
            CONFIG_ID,
            CredentialRequest(credentialResponseEncryption = encryption(alg = null)),
        )
        assertTrue(missingAlg.isErr)

        val compression = validateIssuanceAuthorizationSnapshot(
            session(),
            CONFIG_ID,
            CredentialRequest(credentialResponseEncryption = encryption(alg = "ECDH-ES", zip = "DEF")),
        )
        assertTrue(compression.isErr)
    }

    @Test
    fun pinnedPreviewProfileAllowsOptionalAlgAndCompression() {
        val result = validateIssuanceAuthorizationSnapshot(
            session(snapshot = snapshot(profile = Oid4vciIssuerSpecProfile.OID4VCI_1_1_DRAFT_2A1F0513)),
            CONFIG_ID,
            CredentialRequest(credentialResponseEncryption = encryption(alg = null, zip = "DEF")),
        )
        assertTrue(result.isOk, result.errorOrNull()?.message?.defaultMessage)
        assertEquals(Oid4vciIssuerSpecProfile.OID4VCI_1_1_DRAFT_2A1F0513, result.value.profile)
    }

    private fun session(
        preAuthCode: String? = null,
        grants: Set<Oid4vciAuthorizationGrant> = setOf(Oid4vciAuthorizationGrant.AUTHORIZATION_CODE),
        snapshot: Oid4vciAuthorizationPolicySnapshot? = snapshot(grants = grants),
    ) = IssuanceSession(
        sessionId = "session-1",
        instanceId = ISSUER_ID.toString(),
        issuerId = "https://issuer.example",
        credentialConfigurationIds = listOf(CONFIG_ID),
        issuerState = "issuer-state",
        status = IssuanceSessionStatus.TOKEN_REQUESTED,
        preAuthCode = preAuthCode,
        authorizationPolicySnapshot = snapshot,
        createdAt = 1,
        expiresAt = 2,
    )

    private fun snapshot(
        grants: Set<Oid4vciAuthorizationGrant> = setOf(Oid4vciAuthorizationGrant.AUTHORIZATION_CODE),
        profile: Oid4vciIssuerSpecProfile = Oid4vciIssuerSpecProfile.OID4VCI_1_0_FINAL,
    ) = Oid4vciAuthorizationPolicySnapshot(
        issuerId = ISSUER_ID,
        authorizationServerId = uuid(2),
        authorizationServerIssuer = "https://as.example",
        applicableGrants = grants,
        profile = profile,
        profileRevision = 3,
        bindingRevision = 4,
    )

    private fun encryption(alg: String?, zip: String? = null) = RequestedCredentialResponseEncryption(
        jwk = buildJsonObject {
            put("kty", "EC")
            put("crv", "P-256")
            put("x", "x")
            put("y", "y")
        },
        alg = alg,
        enc = "A256GCM",
        zip = zip,
    )

    private fun tokenContext(
        authorizationServerId: String = uuid(2).toString(),
        authorizationServerIssuer: String = "https://as.example",
    ) = ValidatedTokenContext(
        subject = "subject",
        clientId = "wallet",
        scope = "credential",
        credentialConfigurationIds = listOf(CONFIG_ID),
        authorizationServerId = authorizationServerId,
        authorizationServerIssuer = authorizationServerIssuer,
    )

    private fun uuid(value: Int): Uuid = Uuid.parse("00000000-0000-4000-8000-${value.toString().padStart(12, '0')}")

    private fun instanceProvider(instanceId: String?) =
        object : Oid4vciIssuerInstanceIdProvider {
            override fun currentInstanceId(): String? = instanceId
        }

    private companion object {
        const val CONFIG_ID = "EmployeeCredential"
        val ISSUER_ID: Uuid = Uuid.parse("00000000-0000-4000-8000-000000000001")
    }
}
