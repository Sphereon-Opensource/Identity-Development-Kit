/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.openid.oid4vci.integration

import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.IdkResult
import com.sphereon.core.compat.Uuid
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.ResourceControlMode
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.key.persistence.KeyReferenceRecord
import com.sphereon.crypto.key.persistence.KeyReferenceStore
import com.sphereon.core.api.model.Origin
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.resolution.IdentifierService
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierX5cOpts
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialDefinition
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode
import com.sphereon.openid.oid4vci.issuer.format.CredentialFormatHandler
import com.sphereon.openid.oid4vci.issuer.impl.format.JwtVcJsonFormatHandler
import com.sphereon.openid.oid4vci.issuer.impl.format.VcLdJsonJwtFormatHandler
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.ClientMetadata
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.common.VpToken
import com.sphereon.openid.oid4vp.common.vpToken
import com.sphereon.openid.oid4vp.common.jwtVcFormatInfo
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.w3cVcMeta
import com.sphereon.openid.oid4vp.holder.CreateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningIdentifier
import com.sphereon.openid.oid4vp.holder.Oid4vpHolder
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.verifier.Oid4vpVerifierService
import com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.TrustedAuthenticationResolution
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.holder.VerifierInfo
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.wallet.unit.SecureComponentUsage
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/** Production commands and the canonical external identifier resolver used by this E2E. */
@ContributesTo(SessionScope::class)
interface VcdmJwtX509TestGraph {
    val jwtVcJsonFormatHandler: JwtVcJsonFormatHandler
    val vcLdJsonJwtFormatHandler: VcLdJsonJwtFormatHandler
    val oid4vpHolder: Oid4vpHolder
    val oid4vpVerifierService: Oid4vpVerifierService
    val identifierService: IdentifierService
    /** Existing KMS key-reference authority used by SoftwareWscd's fail-closed owner check. */
    val keyReferenceStore: KeyReferenceStore
}

/**
 * Identifier-neutral VCDM 1.1 and 2.0 JWT issue -> holder VP -> verifier proof using X.509.
 *
 * Both signing operations use private keys held by the production software KMS. Their public
 * certificates are emitted as x5c and are independently resolved by the production external
 * identifier service before the verifier accepts either signature. No verifier trust decision
 * is made from a raw JWK or from an untrusted header-supplied key.
 */
@OptIn(ExperimentalEncodingApi::class)
class VcdmJwtX509IdentifierNeutralE2ETest {
    private val ctx = Oid4vciTestContext(this)
    private val json = Json { ignoreUnknownKeys = true }
    private val issuer = "https://x509-vcdm-issuer.example"
    private val verifier = "https://x509-vcdm-verifier.example"
    private val holder = "https://x509-vcdm-holder.example"
    private val holderWalletUnitId = "wallet-unit-x509-vcdm-e2e"

    @Test
    fun x509Vcdm11AndVcdm20JwtIssuePresentAndVerifyUsesOneCredentialPerVp() =
        runTest {
            for (version in JwtVersion.entries) {
            val flow = createFlow("x509-vcdm-valid-${version.name.lowercase()}", version)
            val issuerResolution = resolve(flow.issuerChain, flow.issuerChain)
            val holderResolution = resolve(flow.holderChain, flow.holderChain)

            assertTrustedResolution(issuerResolution, assertIs<Jwk>(flow.issuerKey.toManagedPublicKeyInfo().key))
            assertTrustedResolution(holderResolution, flow.holderKey.publicJwk)

            val validation = validate(flow, trusted(issuer, flow.issuerChain), trusted(holder, flow.holderChain))
            assertTrue(
                validation.isOk,
                "production verifier must return a structured result: ${if (validation.isErr) validation.error else ""}",
            )
            assertTrue(validation.value.valid, "issuer and holder X.509 signatures must verify: ${validation.value.errors}")
            assertEquals(1, flow.response.value.vpToken!!.presentationCount)
            assertEquals(1, flow.response.value.vpToken!!.getPresentation(QUERY_ID)!!.size)

            val vp = flow.response.value.vpToken!!.getSinglePresentation(QUERY_ID)!!
            val vpHeader = jwtHeader(vp)
            assertEquals(flow.holderChain, vpHeader["x5c"]?.let { (it as JsonArray).map { item -> item.jsonPrimitive.content } })
            assertTrue(vpHeader["kid"] == null, "X.509 holder proof must not carry a kid")
            assertVpShape(vp, version)

            val credentialHeader = jwtHeader(flow.credential)
            assertEquals(flow.issuerChain, credentialHeader["x5c"]?.let { (it as JsonArray).map { item -> item.jsonPrimitive.content } })
            assertTrue(credentialHeader["kid"] == null, "X.509 issuer proof must not carry a kid")
            assertCredentialShape(flow.credential, version)
            val vpPayload = jwtPayload(vp)
            val credentials = (if (version == JwtVersion.V11) vpPayload["vp"]!!.jsonObject else vpPayload)["verifiableCredential"] as JsonArray
            assertEquals(1, credentials.size, "each VP must contain exactly one credential")
            }
        }

    @Test
    fun x509IdentityAdmittedForItsIntendedIssuerPurposeVerifiesCredentialAndVp() =
        runTest {
            val flow = createFlow("x509-vcdm-purpose-positive", JwtVersion.V20)
            val issuerSource = trusted(issuer, flow.issuerChain)
            val holderSource = trusted(holder, flow.holderChain)

            // Establish the issuer chain independently before composing the OID4VP validation.
            // The positive purpose vector proves that issuer admission is usable for the issuer
            // credential path while holder admission remains a separate exact source.
            val issuerResolution = resolve(flow.issuerChain, flow.issuerChain)
            assertTrustedResolution(issuerResolution, assertIs<Jwk>(flow.issuerKey.toManagedPublicKeyInfo().key))

            val validation = validate(flow, issuerSource, holderSource)
            assertTrue(
                validation.isOk,
                "intended-purpose X.509 validation must return a structured result: ${if (validation.isErr) validation.error else ""}",
            )
            assertTrue(
                validation.value.valid,
                "an X.509 identity admitted for issuer validation must verify the issuer credential and composed VP: ${validation.value.errors}",
            )
        }

    @Test
    fun disabledX509ValidationCannotEstablishIssuerTrust() =
        runTest {
            val flow = createFlow("x509-vcdm-disabled")
            val disabledIssuer = ExternalIdentifierX5cOpts(flow.issuerChain, verify = false, trustAnchors = flow.issuerChain)
            val validation =
                validate(
                    flow,
                    TrustedAuthenticationResolution(controller = issuer, identifier = disabledIssuer),
                    trusted(holder, flow.holderChain),
                )

            assertTrue(validation.isOk, "disabled X.509 validation must return a structured result: ${if (validation.isErr) validation.error else ""}")
            assertFalse(validation.value.valid, "disabled X.509 path validation must not establish issuer trust: ${validation.value.errors}")
            assertTrue(validation.value.errors.any { it.contains("X.509") || it.contains("trust") }, validation.value.errors.toString())
        }

    @Test
    fun invalidAndUntrustedX509ChainsAreRejectedByProductionResolverAndVerifier() =
        runTest {
            val flow = createFlow("x509-vcdm-negative")
            val malformed = listOf(Base64.Default.encode("not-an-x509-certificate".encodeToByteArray()))
            val malformedResolution = runCatching { identifierService().resolve(ExternalIdentifierX5cOpts(malformed, verify = true)) }.getOrNull()
            val malformedResult = malformedResolution?.takeIf { it.isOk }?.value
            val malformedRejected =
                malformedResolution == null ||
                    malformedResolution.isErr ||
                    (malformedResult is ExternalIdentifierResult.X5c && malformedResult.verificationResult.error)
            assertTrue(
                malformedRejected,
                "a malformed certificate must be rejected by X.509 resolution: $malformedResolution",
            )

            val invalidIssuer = validate(flow, trusted(issuer, malformed), trusted(holder, flow.holderChain))
            assertTrue(invalidIssuer.isOk, "malformed X.509 validation must return a structured result: ${if (invalidIssuer.isErr) invalidIssuer.error else ""}")
            assertFalse(invalidIssuer.value.valid, "a malformed configured issuer certificate must fail closed: ${invalidIssuer.value.errors}")

            val unrelated = createKey("x509-vcdm-unrelated")
            val unrelatedChain = certificateChain(unrelated)
            val untrustedResolution = resolve(flow.issuerChain, unrelatedChain)
            assertTrue(
                untrustedResolution.isOk,
                "X.509 resolver should return a structured untrusted result: ${if (untrustedResolution.isErr) untrustedResolution.error else ""}",
            )
            val x5c = assertIs<ExternalIdentifierResult.X5c>(untrustedResolution.value)
            assertTrue(x5c.verificationResult.error || x5c.verificationResult.critical, "wrong trust anchor must fail path validation")

            val untrustedIssuer = validate(flow, trusted(issuer, flow.issuerChain, unrelatedChain), trusted(holder, flow.holderChain))
            assertTrue(untrustedIssuer.isOk, "untrusted X.509 validation must return a structured result: ${if (untrustedIssuer.isErr) untrustedIssuer.error else ""}")
            assertFalse(untrustedIssuer.value.valid, "an issuer chain not anchored by the configured trust root must fail: ${untrustedIssuer.value.errors}")
        }

    @Test
    fun x509IdentityAdmittedForIssuerPurposeCannotAuthenticateHolderPurpose() =
        runTest {
            val flow = createFlow("x509-vcdm-purpose-negative")

            // The chain is valid and admitted for the issuer controller, but it is deliberately
            // reused as the holder source. The verifier must keep the two protocol identities
            // bound to their exact controller and never promote an issuer trust decision into
            // holder trust merely because the X.509 path is valid.
            val issuerSource = trusted(issuer, flow.issuerChain)
            val wrongPurposeHolderSource = trusted(holder, flow.issuerChain)
            val resolution = resolve(flow.issuerChain, flow.issuerChain)
            assertTrustedResolution(resolution, assertIs<Jwk>(flow.issuerKey.toManagedPublicKeyInfo().key))

            val validation = validate(flow, issuerSource, wrongPurposeHolderSource)
            assertTrue(
                validation.isOk,
                "wrong-purpose X.509 validation must return a structured result: ${if (validation.isErr) validation.error else ""}",
            )
            assertFalse(
                validation.value.valid,
                "an X.509 identity admitted for issuer validation must not authenticate the holder VP: ${validation.value.errors}",
            )
        }

    private suspend fun createFlow(aliasPrefix: String, version: JwtVersion = JwtVersion.V20): Flow {
        val issuerKey = createKey("$aliasPrefix-issuer")
        val holderKey = provisionHolder("$aliasPrefix-holder")
        val issuerChain = certificateChain(issuerKey)
        val holderChain = holderKey.certificateChain
        val credential = issue(issuerKey.alias, version)
        val query = DcqlQuery(credentials = listOf(query(version)))
        val request =
            verifierService().createAuthorizationRequest(
                CreateAuthorizationRequestArgs(
                    instanceId = "x509-vcdm-verifier",
                    dcqlQuery = query,
                    clientId = verifier,
                    responseUri = "$verifier/response",
                    responseMode = ResponseMode.DIRECT_POST,
                    nonce = NONCE,
                    state = "x509-vcdm-state",
                    clientMetadata = ClientMetadata(vpFormatsSupported = mapOf(version.format.value to jwtVcFormatInfo(listOf("ES256")))),
                ),
            ).also {
                assertTrue(it.isOk, "production X.509 verifier request must be created: ${if (it.isErr) it.error else ""}")
            }.value
        val resolved =
            ResolvedOid4vpRequest(
                request = request.request,
                dcqlQuery = query,
                clientMetadata = ClientMetadata(vpFormatsSupported = mapOf(version.format.value to jwtVcFormatInfo(listOf("ES256")))),
                verifierInfo = VerifierInfo(clientId = verifier, clientIdScheme = ClientIdScheme.REDIRECT_URI),
            )
        val response =
            (ctx.session.graph as VcdmJwtX509TestGraph).oid4vpHolder.commands.createAuthorizationResponse.execute(
                CreateAuthorizationResponseArgs(
                    request = resolved,
                    selectedCredentials =
                        listOf(
                            SelectedCredential(
                                credentialQueryId = QUERY_ID,
                                credentialId = "x509-vcdm-credential",
                                presentation = JsonPrimitive(credential),
                                credentialFormat = version.format,
                                holderKeyRef = holderKey.keyRef,
                                holderId = holder,
                                holderVerificationMethod = holder,
                                holderSigningAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                                holderJwtVpSigningIdentifier = HolderJwtVpSigningIdentifier.X509(holder, holderChain),
                                holderJwtVpOperationBinding = "x509-vcdm-attended-operation",
                                holderJwtVpWalletUnitId = holderWalletUnitId,
                            ),
                        ),
                ),
            )
        assertTrue(
            response.isOk,
            "production holder command must create an X.509-bound VP: ${if (response.isErr) response.error else ""}",
        )
        return Flow(version, issuerKey, holderKey, issuerChain, holderChain, credential, query, request.request, response)
    }

    private suspend fun issue(alias: String, version: JwtVersion = JwtVersion.V20): String {
        ctx.registerIssuerSigningKey(alias)
        val configuration =
            CredentialConfigurationSupported(
                format = version.format.value,
                credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", version.credentialType)),
            )
        val graph = ctx.session.graph as VcdmJwtX509TestGraph
        val handler: CredentialFormatHandler = if (version == JwtVersion.V11) graph.jwtVcJsonFormatHandler else graph.vcLdJsonJwtFormatHandler
        val result = handler.issueCredential(
                CredentialRequest(format = version.format.value),
                IssuanceContext(
                    subject = "$holder/subject",
                    clientId = "x509-vcdm-client",
                    issuerIdentifier = issuer,
                    credentialConfigurationId = "x509-vcdm-credential",
                    credentialConfiguration = configuration,
                    holderBindingKey = null,
                    attributes = mapOf("level" to JsonPrimitive("gold")),
                    signingKeyAlias = alias,
                    signingKeyMode = SigningKeyMode.X5c,
                    issuanceClockSkewInSeconds = 0,
                    expirationInDays = 30,
                ),
            )
        assertTrue(result.isOk, "production VCDM JWT issuer must issue: ${if (result.isErr) result.error else ""}")
        val credential = result.value.credential.jsonPrimitive.content
        val header = jwtHeader(credential)
        assertTrue(header["x5c"] is JsonArray && (header["x5c"] as JsonArray).isNotEmpty())
        return credential
    }

    private suspend fun createKey(alias: String): ManagedKeyInfoType<*> {
        val kms = ctx.session.graph.asKeyManagerServiceGraph().keyManagerService
        registerAutoCertificateProvider(alias)
        val generated = kms.generateKeyResult(alias = alias, use = JwkUse.sig, alg = SignatureAlgorithm.ECDSA_SHA256)
        assertTrue(generated.isOk, "software KMS key generation must succeed: ${if (generated.isErr) generated.error else ""}")
        val key = assertNotNull(generated.value.keyPair?.joseToManagedKeyInfo(KeyVisibility.PRIVATE))
        assertTrue(!key.x5c.isNullOrEmpty(), "the signing key must carry the generated certificate chain for '$alias'")
        return key
    }

    /**
     * Holder key provisioning and signing stay behind the session WSCA. The software WSCD's real
     * KMS provider is configured with auto-certificate creation before this call (by [createKey]),
     * so the certificate chain is associated with the same durable key alias that WSCA resolves.
     * The chain is taken from the WSCA-returned public JWK; [keyRef] remains the opaque WSCA handle
     * handed to the holder signer.
     */
    private suspend fun provisionHolder(alias: String): HolderMaterial {
        // The WSCA-backed Software WSCD uses the session's default provider. Configure that real
        // provider path before asking WSCA to provision the holder key so its public material is
        // returned with the provider-associated certificate chain.
        registerAutoCertificateProvider(alias)
        val wsca = (ctx.session.graph as WalletInteractionOid4vciWscaTestGraph).wsca
        val result =
            wsca.ensureKey(
                walletUnitId = holderWalletUnitId,
                usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                algorithm = SignatureAlgorithm.ECDSA_SHA256,
                keyAlias = alias,
            )
        assertTrue(result.isOk, "holder key must be provisioned through WSCA: ${if (result.isErr) result.error else ""}")
        val key = result.value
        ensureAuthoritativeHolderOwnerReference(key)
        val keyRef = assertNotNull(key.keyRef ?: key.keyId)
        val publicJwk = json.decodeFromString(Jwk.serializer(), assertNotNull(key.publicKeyJwk))
        val chain = assertNotNull(publicJwk.x5c, "WSCA-associated holder certificate chain is missing")
        assertTrue(chain.isNotEmpty(), "WSCA-associated holder certificate chain must not be empty")
        return HolderMaterial(keyRef = keyRef, publicJwk = publicJwk, certificateChain = chain.toList())
    }

    /**
     * Keep this fixture's in-memory KMS reference authority aligned with the WSCA handle. The
     * production WSCD refuses to sign a durable KMS alias without this owner/provider row; the
     * fixture graph uses the same store contract, so an older provider composition can leave the
     * row unindexed even though [Wsca.ensureKey] returned a valid handle. Fill only a missing
     * owner on the exact alias/provider returned by WSCA and verify the resulting authority before
     * handing the opaque reference to SelectedCredential.
     */
    private suspend fun ensureAuthoritativeHolderOwnerReference(key: com.sphereon.wallet.unit.WalletAttestedKeyRef) {
        assertEquals(holderWalletUnitId, key.walletUnitId, "WSCA holder handle must retain its wallet-unit owner")
        val alias = assertNotNull(key.keyRef ?: key.keyId)
        // SoftwareWscd rehydrates by resolving the durable alias through the KMS and then checks
        // the provider id returned by that resolution. The provider id carried by an older WSCA
        // fixture handle can be stale after another auto-certificate provider is registered, so
        // index the exact provider that the production rehydration path will use.
        val kms = ctx.session.graph.asKeyManagerServiceGraph().keyManagerService
        val resolved =
            kms
                .getKeyResult(KeyInfo<Nothing>(alias = alias, keyVisibility = KeyVisibility.PRIVATE))
                .getOrElse { error("holder key-resolution failed for alias '$alias': $it") }
                .key
                .let { assertNotNull(it, "holder key-resolution returned no key for alias '$alias'") }
        val resolvedAlias = assertNotNull(resolved.alias, "resolved holder key alias is missing")
        assertEquals(alias, resolvedAlias, "WSCA holder alias must remain stable across KMS resolution")
        val providerId = assertNotNull(resolved.providerId, "resolved holder key provider reference is missing")
        // Match the production KMS registration path exactly: its generated-key index and
        // ManagedKeyStoreSelector both scope references to the immutable session tenant.
        val tenantId = ctx.session.asCoreApiServiceGraph().serviceExecution.sessionContext.context.tenant.tenantId
        val store = (ctx.session.graph as VcdmJwtX509TestGraph).keyReferenceStore
        val now = Clock.System.now()
        val existing =
            store.findByAlias(tenantId, resolvedAlias, providerId).getOrElse {
                error("holder key-reference lookup failed for alias '$alias' and provider '$providerId': $it")
            }
        if (existing?.walletUnitId != null && existing.walletUnitId != holderWalletUnitId) {
            error(
                "holder key '$alias' is already owned by wallet unit '${existing.walletUnitId}', " +
                    "not '$holderWalletUnitId'",
            )
        }
        val record =
            existing?.copy(
                walletUnitId = holderWalletUnitId,
                updatedAt = now,
            ) ?: KeyReferenceRecord(
                id = Uuid.v4String(),
                tenantId = tenantId,
                alias = resolvedAlias,
                kid = resolved.kid ?: key.keyId,
                providerId = providerId,
                origin = Origin.MANAGED,
                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                keyVisibility = KeyVisibility.PRIVATE,
                publicKeyJwk = key.publicKeyJwk,
                createdAt = now,
                updatedAt = now,
                controlMode = ResourceControlMode.PLATFORM_MANAGED,
                walletUnitId = holderWalletUnitId,
            )
        val persisted = store.upsert(record).getOrElse { error("holder key-reference registration failed: $it") }
        assertEquals(holderWalletUnitId, persisted.walletUnitId)
        val authoritative =
            kms.findRegisteredKeyReference(resolvedAlias, providerId)
        assertEquals(providerId, authoritative?.providerId, "holder key provider reference must remain authoritative")
        assertEquals(holderWalletUnitId, authoritative?.walletUnitId, "holder key owner must remain authoritative")
    }

    private suspend fun registerAutoCertificateProvider(alias: String) {
        val kms = ctx.session.graph.asKeyManagerServiceGraph().keyManagerService
        val factory = (ctx.app as SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider
        val provider =
            factory.create(
                SoftwareKmsProviderConfig(id = "x509-provider-$alias", autoCreateCertificate = true),
                ctx.session.asCoreApiServiceGraph().serviceExecution,
            )
        kms.registerProvider(provider, makeDefaultKms = true)
    }

    private fun certificateChain(key: ManagedKeyInfoType<*>): List<String> = assertNotNull(key.x5c).toList()

    private suspend fun resolve(chain: List<String>, anchors: List<String>) =
        identifierService().resolve(ExternalIdentifierX5cOpts(chain, verify = true, trustAnchors = anchors))

    private fun assertTrustedResolution(resolution: com.sphereon.core.api.IdkResult<*, *>, key: Jwk) {
        assertTrue(resolution.isOk, "production X.509 identifier resolution must succeed: ${if (resolution.isErr) resolution.error else ""}")
        val result = assertIs<ExternalIdentifierResult.X5c>(resolution.value)
        assertFalse(result.verificationResult.error, "trusted certificate path must not report an error: ${result.verificationResult}")
        assertFalse(result.verificationResult.critical, "trusted certificate path must not be critical: ${result.verificationResult}")
        val resolvedJwk = assertIs<Jwk>(result.keyInfo.key)
        assertEquals(key.x, resolvedJwk.x, "certificate public key must match WSCA public material")
        assertEquals(key.y, resolvedJwk.y, "certificate public key must match WSCA public material")
    }

    private fun trusted(controller: String, chain: List<String>, anchors: List<String> = chain) =
        TrustedAuthenticationResolution(
            controller = controller,
            identifier = ExternalIdentifierX5cOpts(chain, verify = true, trustAnchors = anchors),
        )

    private suspend fun validate(flow: Flow, issuerTrust: TrustedAuthenticationResolution, holderTrust: TrustedAuthenticationResolution) =
        verifierService().let { service ->
            val response = flow.response.value
            val parsed =
                service.parseAuthorizationResponse(
                    ParseAuthorizationResponseArgs(
                        responseParams =
                            mapOf(
                                "vp_token" to json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), VpToken.run { response.vpToken!!.toJson() }),
                                "state" to assertNotNull(response.state),
                            ),
                        originalRequest = flow.request,
                    ),
                ).also {
                    assertTrue(it.isOk, "production X.509 response parser must accept holder output: ${if (it.isErr) it.error else ""}")
                }
            service.validateAuthorizationResponse(
                ValidateAuthorizationResponseArgs(
                    parsedResponse = parsed.value,
                    originalRequest = flow.request,
                    dcqlQuery = flow.query,
                    expectedNonce = NONCE,
                    trustedAuthentications = listOf(issuerTrust, holderTrust),
                ),
            )
        }

    private fun verifierService(): Oid4vpVerifierService = (ctx.session.graph as VcdmJwtX509TestGraph).oid4vpVerifierService

    private fun identifierService(): IdentifierService = (ctx.session.graph as VcdmJwtX509TestGraph).identifierService

    private fun query(version: JwtVersion = JwtVersion.V20) =
        DcqlCredentialQuery(
            id = QUERY_ID,
            format = version.format.value,
            meta = w3cVcMeta(listOf("VerifiableCredential", version.credentialType)),
        )

    private fun jwtHeader(jwt: String) =
        json.parseToJsonElement(jwt.substringBefore('.').decodeFromBase64Url().decodeToString()).jsonObject

    private fun jwtPayload(jwt: String) =
        json.parseToJsonElement(jwt.split('.')[1].decodeFromBase64Url().decodeToString()).jsonObject

    private fun assertCredentialShape(jwt: String, version: JwtVersion) {
        val header = jwtHeader(jwt)
        val payload = jwtPayload(jwt)
        assertEquals(version.credentialTyp, header["typ"]?.jsonPrimitive?.content)
        if (version == JwtVersion.V11) {
            assertFalse(header.containsKey("cty"))
            val vc = assertNotNull(payload["vc"] as? JsonObject)
            assertEquals(version.context, (vc["@context"] as JsonArray).first().jsonPrimitive.content)
            assertEquals(version.credentialType, (vc["type"] as JsonArray).last().jsonPrimitive.content)
            assertAnonymousCredentialSubject(vc["credentialSubject"] as? JsonObject)
            assertFalse(payload.containsKey("@context"))
        } else {
            assertEquals("vc", header["cty"]?.jsonPrimitive?.content)
            assertEquals(version.context, (payload["@context"] as JsonArray).first().jsonPrimitive.content)
            assertEquals(version.credentialType, (payload["type"] as JsonArray).last().jsonPrimitive.content)
            assertAnonymousCredentialSubject(payload["credentialSubject"] as? JsonObject)
            assertFalse(payload.containsKey("vc"))
        }
    }

    private fun assertAnonymousCredentialSubject(subject: JsonObject?) {
        val anonymousSubject = assertNotNull(subject)
        assertEquals("gold", anonymousSubject["level"]?.jsonPrimitive?.content)
        assertFalse(anonymousSubject.containsKey("id"), "credential subject must remain anonymous")
    }

    private fun assertVpShape(jwt: String, version: JwtVersion) {
        val header = jwtHeader(jwt)
        if (version == JwtVersion.V11) {
            assertEquals("JWT", header["typ"]?.jsonPrimitive?.content)
            assertFalse(header.containsKey("cty"))
        } else {
            assertEquals("vp+jwt", header["typ"]?.jsonPrimitive?.content)
            assertEquals("vp", header["cty"]?.jsonPrimitive?.content)
        }
        val payload = jwtPayload(jwt)
        if (version == JwtVersion.V11) {
            val vp = assertNotNull(payload["vp"] as? JsonObject)
            assertEquals(version.context, (vp["@context"] as JsonArray).first().jsonPrimitive.content)
            assertEquals("VerifiablePresentation", (vp["type"] as JsonArray).first().jsonPrimitive.content)
        } else {
            assertEquals(version.context, (payload["@context"] as JsonArray).first().jsonPrimitive.content)
        }
        assertEquals(NONCE, payload["nonce"]?.jsonPrimitive?.content)
        assertEquals(verifier, payload["aud"]?.jsonPrimitive?.content)
    }

    private data class Flow(
        val version: JwtVersion,
        val issuerKey: ManagedKeyInfoType<*>,
        val holderKey: HolderMaterial,
        val issuerChain: List<String>,
        val holderChain: List<String>,
        val credential: String,
        val query: DcqlQuery,
        val request: com.sphereon.oauth2.common.model.AuthorizationRequest,
        val response: IdkResult<AuthorizationResponse, Any?>,
    )

    private data class HolderMaterial(
        val keyRef: String,
        val publicJwk: Jwk,
        val certificateChain: List<String>,
    )

    private enum class JwtVersion(
        val format: CredentialFormat,
        val label: String,
        val credentialType: String,
        val context: String,
        val credentialTyp: String,
    ) {
        V11(CredentialFormat.JWT_VC_JSON, "VCDM 1.1", "X509Vcdm11Credential", "https://www.w3.org/2018/credentials/v1", "JWT"),
        V20(CredentialFormat.JWT_VC_JSON_LD, "VCDM 2.0", "X509Vcdm2Credential", "https://www.w3.org/ns/credentials/v2", "vc+jwt"),
    }

    private companion object {
        const val QUERY_ID = "x509-vcdm-query"
        const val NONCE = "x509-vcdm-nonce"
    }
}
