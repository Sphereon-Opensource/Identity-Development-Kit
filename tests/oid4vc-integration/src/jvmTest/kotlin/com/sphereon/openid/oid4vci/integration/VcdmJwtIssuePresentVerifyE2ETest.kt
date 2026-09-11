/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.openid.oid4vci.integration

import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vp.common.ClientMetadata
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialDefinition
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.issuer.format.CredentialFormatHandler
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode
import com.sphereon.openid.oid4vci.issuer.impl.format.JwtVcJsonFormatHandler
import com.sphereon.openid.oid4vci.issuer.impl.format.VcLdJsonJwtFormatHandler
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.common.VpToken
import com.sphereon.openid.oid4vp.common.vpToken
import com.sphereon.openid.oid4vp.common.jwtVcFormatInfo
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.w3cVcMeta
import com.sphereon.openid.oid4vp.holder.CreateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningIdentifier
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.holder.VerifierInfo
import com.sphereon.openid.oid4vp.verifier.TrustedAuthenticationResolution
import com.sphereon.wallet.unit.SecureComponentUsage
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Concrete production issuer handlers used by the VCDM JWT fixture. */
@ContributesTo(SessionScope::class)
interface VcdmJwtIssuerHandlersTestGraph {
    val jwtVcJsonFormatHandler: JwtVcJsonFormatHandler
    val vcLdJsonJwtFormatHandler: VcLdJsonJwtFormatHandler
}

/**
 * Real JWT VCDM issue -> holder VP -> verifier validation proof.
 *
 * The issuer signs through the issuer-side KMS while the holder provisions and signs exclusively
 * through the in-process software WSCA/WSCD boundary. The
 * verifier receives no embedded trust key and therefore exercises the identifier-service path
 * while validating issuer and holder signatures. This test intentionally does not use DID as a
 * prerequisite: the issuer is an HTTPS identifier and the generated JOSE key identifier is
 * resolved by the configured managed-key service.
 */
class VcdmJwtIssuePresentVerifyE2ETest {
    private val ctx = Oid4vciTestContext(this)
    private val json = Json { ignoreUnknownKeys = true }
    private val issuer = "https://vcdm-jwt-issuer.example"
    private val verifier = "https://vcdm-jwt-verifier.example"
    private val holder = "https://vcdm-jwt-holder.example"

    @Test
    fun vcdm11AndVcdm20IssuePresentAndVerifyWithRealJwtProofs() =
        runTest {
            val issuerV11 = issue(CredentialFormat.JWT_VC_JSON, "vcdm11-issuer-key")
            val issuerV20 = issue(vcdm20Format(), "vcdm20-issuer-key")
            val holderKey = provisionHolder("vcdm-holder-key")
            val query = queryFor(issuerV11.format, issuerV20.format)
            val original = createRequest(query, nonce = "vcdm-valid-nonce", state = "vcdm-valid-state")
            val resolved = resolvedRequest(original.request, query)

            val response = createResponse(resolved, issuerV11, issuerV20, holderKey)
            val validation =
                validate(
                    original.request,
                    query,
                    response,
                    expectedNonce = "vcdm-valid-nonce",
                    trustedAuthentications = listOf(issuerAuthentication(issuerV11, issuerV20), holderAuthentication(holderKey)),
                )

            assertTrue(validation.isOk, "real verifier command should return a result")
            assertTrue(validation.value.valid, "issuer and holder JWTs should verify through identifier services: ${validation.value.errors}")
            assertEquals(2, response.vpToken!!.presentationCount)
            assertEquals(1, response.vpToken!!.getPresentation(VC11_QUERY)!!.size)
            assertEquals(1, response.vpToken!!.getPresentation(VC20_QUERY)!!.size)
            assertFalse(
                response.vpToken!!.getSinglePresentation(VC11_QUERY) == issuerV11.credential,
                "holder must emit a separately secured VP for VCDM 1.1",
            )
            assertFalse(
                response.vpToken!!.getSinglePresentation(VC20_QUERY) == issuerV20.credential,
                "holder must emit a separately secured VP for VCDM 2.0",
            )
            assertVpBinding(response.vpToken!!.getSinglePresentation(VC11_QUERY)!!, "vcdm-valid-nonce")
            assertVpBinding(response.vpToken!!.getSinglePresentation(VC20_QUERY)!!, "vcdm-valid-nonce")
        }

    @Test
    fun twoSelectedCredentialsWithDistinctHolderKeysProduceSeparatelySignedSingleCredentialVps() =
        runTest {
            val holderV11Controller = "https://vcdm-jwt-holder-v11.example"
            val holderV20Controller = "https://vcdm-jwt-holder-v20.example"
            val issuerV11 = issue(CredentialFormat.JWT_VC_JSON, "vcdm-distinct-issuer-v11", subject = holderV11Controller)
            val issuerV20 = issue(vcdm20Format(), "vcdm-distinct-issuer-v20", subject = holderV20Controller)
            val holderV11 = provisionHolder("vcdm-distinct-holder-v11", holderV11Controller)
            val holderV20 = provisionHolder("vcdm-distinct-holder-v20", holderV20Controller)
            val query = queryFor(issuerV11.format, issuerV20.format)
            val original = createRequest(query, nonce = "vcdm-distinct-nonce", state = "vcdm-distinct-state")
            val resolved = resolvedRequest(original.request, query)

            val response = createResponse(resolved, issuerV11, issuerV20, holderV11, secondHolder = holderV20)
            val validation =
                validate(
                    original.request,
                    query,
                    response,
                    expectedNonce = "vcdm-distinct-nonce",
                    trustedAuthentications =
                        listOf(
                            issuerAuthentication(issuerV11, issuerV20),
                            holderAuthentication(holderV11),
                            holderAuthentication(holderV20),
                        ),
                )

            assertTrue(validation.isOk, "real verifier command should return a result")
            assertTrue(validation.value.valid, "both distinct holder signatures should verify: ${validation.value.errors}")
            assertEquals(2, response.vpToken!!.presentationCount)
            val vpV11 = assertNotNull(response.vpToken!!.getSinglePresentation(VC11_QUERY))
            val vpV20 = assertNotNull(response.vpToken!!.getSinglePresentation(VC20_QUERY))
            assertEquals(1, countPresentedCredentials(vpV11), "VCDM 1.1 VP must contain one credential")
            assertEquals(1, countPresentedCredentials(vpV20), "VCDM 2.0 VP must contain one credential")
            assertNotEquals(vpV11.substringAfterLast('.'), vpV20.substringAfterLast('.'), "distinct holder keys must produce distinct signatures")
            assertVpBinding(vpV11, "vcdm-distinct-nonce")
            assertVpBinding(vpV20, "vcdm-distinct-nonce")
            assertVpControllerAndKey(vpV11, holderV11)
            assertVpControllerAndKey(vpV20, holderV20)
            assertEquals(holderV11Controller, credentialSubjectId(issuerV11.credential))
            assertEquals(holderV20Controller, credentialSubjectId(issuerV20.credential))
        }

    @Test
    fun tamperedIssuerCredentialFailsAfterHolderSignsTheVp() =
        runTest {
            val issued = issue(CredentialFormat.JWT_VC_JSON, "vcdm-tamper-issuer-key")
            val holderKey = provisionHolder("vcdm-tamper-holder-key")
            val query = queryFor(issued.format, null)
            val original = createRequest(query, nonce = "vcdm-tamper-nonce", state = "vcdm-tamper-state")
            val resolved = resolvedRequest(original.request, query)
            val tampered = issued.copy(credential = tamperSignature(issued.credential))
            val response = createResponse(resolved, tampered, null, holderKey)
            val validation =
                validate(
                    original.request,
                    query,
                    response,
                    expectedNonce = "vcdm-tamper-nonce",
                    trustedAuthentications = listOf(issuerAuthentication(issued), holderAuthentication(holderKey)),
                )

            assertTrue(validation.isOk, "verification should produce a structured invalid result")
            assertFalse(validation.value.valid, "a tampered issuer JWT must fail despite a valid holder VP")
            assertTrue(validation.value.errors.any { it.contains("issuer") || it.contains("signature") })
        }

    @Test
    fun holderVpWithWrongNonceOrAudienceFailsHolderBinding() =
        runTest {
            val issued = issue(CredentialFormat.JWT_VC_JSON, "vcdm-binding-issuer-key")
            val holderKey = provisionHolder("vcdm-binding-holder-key")
            val query = queryFor(issued.format, null)
            val original = createRequest(query, nonce = "vcdm-binding-nonce", state = "vcdm-binding-state")
            val resolved = resolvedRequest(original.request, query)

            val wrongNonceRequest = resolved.copy(request = resolved.request.copy(nonce = "wrong-nonce"))
            val wrongNonceResponse = createResponse(wrongNonceRequest, issued, null, holderKey)
            val trusted = listOf(issuerAuthentication(issued), holderAuthentication(holderKey))
            val wrongNonce = validate(original.request, query, wrongNonceResponse, expectedNonce = "vcdm-binding-nonce", trustedAuthentications = trusted)
            assertTrue(wrongNonce.isOk)
            assertFalse(wrongNonce.value.valid, "a holder VP with a wrong nonce must fail")

            val wrongAudienceRequest =
                resolved.copy(
                    verifierInfo = resolved.verifierInfo.copy(clientId = "https://wrong-audience.example"),
                )
            val wrongAudienceResponse = createResponse(wrongAudienceRequest, issued, null, holderKey)
            val wrongAudience = validate(original.request, query, wrongAudienceResponse, expectedNonce = "vcdm-binding-nonce", trustedAuthentications = trusted)
            assertTrue(wrongAudience.isOk)
            assertFalse(wrongAudience.value.valid, "a holder VP with a wrong audience must fail")
        }

    @Test
    fun wrongExpectedNonceRejectsOtherwiseValidHolderVp() =
        runTest {
            val issued = issue(CredentialFormat.JWT_VC_JSON, "vcdm-expected-nonce-issuer-key")
            val holderKey = provisionHolder("vcdm-expected-nonce-holder-key")
            val query = queryFor(issued.format, null)
            val original = createRequest(query, nonce = "vcdm-expected-nonce", state = "vcdm-expected-nonce-state")
            val resolved = resolvedRequest(original.request, query)
            val response = createResponse(resolved, issued, null, holderKey)

            val validation =
                validate(
                    original.request,
                    query,
                    response,
                    expectedNonce = "wrong-expected-nonce",
                    trustedAuthentications = listOf(issuerAuthentication(issued), holderAuthentication(holderKey)),
                )

            assertTrue(validation.isOk, "wrong expected nonce should produce a structured validation result")
            assertFalse(validation.value.valid, "wrong expected nonce must reject the holder VP")
        }

    @Test
    fun wrongExpectedAudienceRejectsHolderVp() =
        runTest {
            val issued = issue(CredentialFormat.JWT_VC_JSON, "vcdm-expected-audience-issuer-key")
            val holderKey = provisionHolder("vcdm-expected-audience-holder-key")
            val query = queryFor(issued.format, null)
            val original = createRequest(query, nonce = "vcdm-expected-audience-nonce", state = "vcdm-expected-audience-state")
            val resolved = resolvedRequest(original.request, query)
            val wrongAudienceRequest =
                resolved.copy(
                    verifierInfo = resolved.verifierInfo.copy(clientId = "https://wrong-expected-audience.example"),
                )
            val response = createResponse(wrongAudienceRequest, issued, null, holderKey)

            val validation =
                validate(
                    original.request,
                    query,
                    response,
                    expectedNonce = "vcdm-expected-audience-nonce",
                    trustedAuthentications = listOf(issuerAuthentication(issued), holderAuthentication(holderKey)),
                )

            assertTrue(validation.isOk, "wrong audience should produce a structured validation result")
            assertFalse(validation.value.valid, "wrong expected audience must reject the holder VP")
        }

    @Test
    fun statefulVerifierRejectsReplayOfTheSameAuthorizationResponse() =
        runTest {
            val issued = issue(CredentialFormat.JWT_VC_JSON, "vcdm-replay-issuer-key")
            val holderKey = provisionHolder("vcdm-replay-holder-key")
            val query = queryFor(issued.format, null)
            val original = createRequest(query, nonce = "vcdm-replay-nonce", state = "vcdm-replay-state")
            val resolved = resolvedRequest(original.request, query)
            val response = createResponse(resolved, issued, null, holderKey)
            val graph = ctx.session.graph as Oid4vpPresentationTestGraph
            val responseParams =
                mapOf(
                    "vp_token" to json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), VpToken.run { response.vpToken!!.toJson() }),
                    "state" to assertNotNull(response.state),
                )
            val args =
                com.sphereon.openid.oid4vp.verifier.HandleDirectPostResponseArgs(
                    responseParams = responseParams,
                    originalRequest = original.request,
                    dcqlQuery = query,
                    redirectUri = "$verifier/oid4vp/response",
                    trustedAuthentications = listOf(issuerAuthentication(issued), holderAuthentication(holderKey)),
                )

            val first = graph.oid4vpVerifierService.handleDirectPostResponse(args)
            assertTrue(first.isOk, "first stateful response submission should succeed: ${if (first.isErr) first.error else ""}")
            val replay = graph.oid4vpVerifierService.handleDirectPostResponse(args)
            assertTrue(replay.isErr, "replaying the same authorization response must be rejected")
        }

    private suspend fun createResponse(
        request: ResolvedOid4vpRequest,
        first: IssuedCredential,
        second: IssuedCredential?,
        holderKey: HolderMaterial,
        secondHolder: HolderMaterial? = null,
    ): com.sphereon.oauth2.common.model.AuthorizationResponse {
        val result = (ctx.session.graph as Oid4vpPresentationTestGraph).oid4vpHolder.commands.createAuthorizationResponse.execute(
            CreateAuthorizationResponseArgs(
                request = request,
                selectedCredentials =
                    buildList {
                        add(
                            SelectedCredential(
                                credentialQueryId = VC11_QUERY,
                                credentialId = "wallet-vcdm-11",
                                presentation = JsonPrimitive(first.credential),
                                credentialFormat = first.format,
                                holderKeyRef = holderKey.keyRef,
                                holderId = holderKey.controller,
                                holderVerificationMethod = holderKey.kid,
                                holderJwtVpSigningIdentifier =
                                    HolderJwtVpSigningIdentifier.ManagedKid(holderKey.kid),
                                holderSigningAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                                holderJwtVpOperationBinding = "vcdm-jwt-presentation",
                                holderJwtVpWalletUnitId = HOLDER_WALLET_UNIT_ID,
                            ),
                        )
                        second?.let {
                            val credentialHolder = secondHolder ?: holderKey
                            add(
                                SelectedCredential(
                                    credentialQueryId = VC20_QUERY,
                                    credentialId = "wallet-vcdm-20",
                                    presentation = JsonPrimitive(it.credential),
                                    credentialFormat = it.format,
                                    holderKeyRef = credentialHolder.keyRef,
                                    holderId = credentialHolder.controller,
                                    holderVerificationMethod = credentialHolder.kid,
                                    holderJwtVpSigningIdentifier =
                                        HolderJwtVpSigningIdentifier.ManagedKid(credentialHolder.kid),
                                    holderSigningAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                                    holderJwtVpOperationBinding = "vcdm-jwt-presentation",
                                    holderJwtVpWalletUnitId = HOLDER_WALLET_UNIT_ID,
                                ),
                            )
                        }
                    },
            ),
        )
        assertTrue(result.isOk, "production holder response command should succeed: ${if (result.isErr) result.error else ""}")
        return result.value
    }

    private suspend fun validate(
        request: com.sphereon.oauth2.common.model.AuthorizationRequest,
        query: DcqlQuery,
        response: com.sphereon.oauth2.common.model.AuthorizationResponse,
        expectedNonce: String,
        trustedAuthentications: List<TrustedAuthenticationResolution>,
    ) =
        (ctx.session.graph as Oid4vpPresentationTestGraph).oid4vpVerifierService.let { service ->
            val vpToken = assertNotNull(response.vpToken)
            val parsed =
                service.parseAuthorizationResponse(
                    ParseAuthorizationResponseArgs(
                        responseParams =
                            mapOf(
                                "vp_token" to json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), VpToken.run { vpToken.toJson() }),
                                "state" to assertNotNull(response.state),
                            ),
                        originalRequest = request,
                    ),
                )
            assertTrue(parsed.isOk, "production response parser should accept the command output")
            service.validateAuthorizationResponse(
                ValidateAuthorizationResponseArgs(
                    parsedResponse = parsed.value,
                    originalRequest = request,
                    dcqlQuery = query,
                    expectedNonce = expectedNonce,
                    trustedAuthentications = trustedAuthentications,
                ),
            )
        }

    private suspend fun createRequest(query: DcqlQuery, nonce: String, state: String) =
        (ctx.session.graph as Oid4vpPresentationTestGraph).oid4vpVerifierService.createAuthorizationRequest(
            CreateAuthorizationRequestArgs(
                instanceId = "vcdm-jwt-verifier",
                dcqlQuery = query,
                clientId = verifier,
                responseUri = "$verifier/oid4vp/response",
                responseMode = ResponseMode.DIRECT_POST,
                nonce = nonce,
                state = state,
                clientMetadata = jwtClientMetadata(),
            ),
        ).also { assertTrue(it.isOk, "production verifier request command should succeed") }.value

    private fun resolvedRequest(
        request: com.sphereon.oauth2.common.model.AuthorizationRequest,
        query: DcqlQuery,
    ) =
        ResolvedOid4vpRequest(
            request = request,
            dcqlQuery = query,
            clientMetadata = jwtClientMetadata(),
            verifierInfo =
                VerifierInfo(
                    clientId = verifier,
                    clientIdScheme = com.sphereon.openid.oid4vp.common.ClientIdScheme.REDIRECT_URI,
                ),
        )

    private fun jwtClientMetadata() =
        ClientMetadata(
            vpFormatsSupported =
                mapOf(
                    CredentialFormat.JWT_VC_JSON.value to jwtVcFormatInfo(listOf("ES256")),
                    vcdm20Format().value to jwtVcFormatInfo(listOf("ES256")),
                ),
        )

    private fun queryFor(first: CredentialFormat, second: CredentialFormat?): DcqlQuery =
        DcqlQuery(
            credentials =
                buildList {
                    add(
                        DcqlCredentialQuery(
                            id = VC11_QUERY,
                            format = first.value,
                            meta = w3cVcMeta(listOf("VerifiableCredential", "VcdmCredential")),
                        ),
                    )
                    second?.let {
                        add(
                            DcqlCredentialQuery(
                                id = VC20_QUERY,
                                format = it.value,
                                meta = w3cVcMeta(listOf("VerifiableCredential", "VcdmCredential")),
                            ),
                        )
                    }
                },
        )

    private suspend fun issue(
        format: CredentialFormat,
        alias: String,
        subject: String = "https://vcdm-jwt-holder.example/subject",
    ): IssuedCredential {
        val issuerKey = generateKey(alias)
        ctx.registerIssuerSigningKey(alias)
        val configuration =
            CredentialConfigurationSupported(
                format = format.value,
                credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "VcdmCredential")),
            )
        val context =
            IssuanceContext(
                subject = subject,
                clientId = "vcdm-jwt-client",
                issuerIdentifier = issuer,
                credentialConfigurationId = "vcdm-${format.value}",
                credentialConfiguration = configuration,
                holderBindingKey = null,
                attributes = mapOf("level" to JsonPrimitive("gold")),
                signingKeyAlias = alias,
                signingKeyMode = SigningKeyMode.JwkThumbprint,
                issuanceClockSkewInSeconds = 0,
                expirationInDays = 30,
            )
        val handler: CredentialFormatHandler =
            if (format == CredentialFormat.JWT_VC_JSON) {
                (ctx.session.graph as VcdmJwtIssuerHandlersTestGraph).jwtVcJsonFormatHandler
            } else {
                (ctx.session.graph as VcdmJwtIssuerHandlersTestGraph).vcLdJsonJwtFormatHandler
            }
        val result = handler.issueCredential(CredentialRequest(format = format.value), context)
        assertTrue(result.isOk, "production ${format.value} issuer handler should issue: ${if (result.isErr) result.error else ""}")
        return IssuedCredential(format, result.value.credential.jsonPrimitive.content, issuerKey)
    }

    private suspend fun generateKey(alias: String): ManagedKeyInfoType<*> {
        val kms = ctx.session.graph.asKeyManagerServiceGraph().keyManagerService
        val result = kms.generateKeyResult(alias = alias, use = JwkUse.sig, alg = SignatureAlgorithm.ECDSA_SHA256)
        assertTrue(result.isOk, "software KMS key generation must succeed")
        return assertNotNull(result.value.keyPair?.joseToManagedKeyInfo(com.sphereon.crypto.core.KeyVisibility.PRIVATE))
    }

    private suspend fun provisionHolder(alias: String, controller: String = holder): HolderMaterial {
        val result =
            (ctx.session.graph as WalletInteractionOid4vciWscaTestGraph).wsca.ensureKey(
                walletUnitId = HOLDER_WALLET_UNIT_ID,
                usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                algorithm = SignatureAlgorithm.ECDSA_SHA256,
                keyAlias = alias,
            )
        assertTrue(result.isOk, "holder key must be provisioned through WSCA: ${if (result.isErr) result.error else ""}")
        val key = result.value
        val publicJwk = json.decodeFromString(Jwk.serializer(), assertNotNull(key.publicKeyJwk))
        val kid = "$controller/keys/$alias"
        return HolderMaterial(controller = controller, keyRef = key.keyRef ?: key.keyId, kid = kid, publicJwk = publicJwk.copy(kid = kid))
    }

    private fun issuerAuthentication(issued: IssuedCredential): TrustedAuthenticationResolution =
        authentication(issuer, issued.issuerKey, issuerJwtKid(issued.credential))

    private fun issuerAuthentication(vararg issued: IssuedCredential): TrustedAuthenticationResolution =
        TrustedAuthenticationResolution(
            controller = issuer,
            trustedJwks =
                JsonObject(
                    mapOf(
                        "keys" to
                            kotlinx.serialization.json.JsonArray(
                                issued.map { credential ->
                                    assertNotNull(credential.issuerKey.toManagedPublicKeyInfo().key as? Jwk)
                                        .copy(kid = issuerJwtKid(credential.credential))
                                        .toJsonObject()
                                },
                            ),
                    ),
                ),
        )

    private fun issuerJwtKid(jwt: String): String {
        val header = json.parseToJsonElement(jwt.substringBefore('.').decodeFromBase64Url().decodeToString()).jsonObject
        return assertNotNull(header["kid"]?.jsonPrimitive?.content)
    }

    private fun holderAuthentication(holderKey: HolderMaterial): TrustedAuthenticationResolution =
        TrustedAuthenticationResolution(
            controller = holderKey.controller,
            trustedJwks = JsonObject(mapOf("keys" to kotlinx.serialization.json.JsonArray(listOf(holderKey.publicJwk.toJsonObject())))),
        )

    private fun authentication(controller: String, key: ManagedKeyInfoType<*>, kid: String): TrustedAuthenticationResolution {
        val publicJwk = assertNotNull(key.toManagedPublicKeyInfo().key as? Jwk).copy(kid = kid)
        return TrustedAuthenticationResolution(
            controller = controller,
            trustedJwks = JsonObject(mapOf("keys" to kotlinx.serialization.json.JsonArray(listOf(publicJwk.toJsonObject())))),
        )
    }

    private fun tamperSignature(jwt: String): String {
        val parts = jwt.split('.')
        assertEquals(3, parts.size)
        val signature = parts[2].toCharArray()
        signature[0] = if (signature[0] == 'A') 'B' else 'A'
        return "${parts[0]}.${parts[1]}.${signature.concatToString()}"
    }

    private fun assertVpBinding(vp: String, nonce: String) {
        val parts = vp.split('.')
        assertEquals(3, parts.size, "holder VP must be compact JWS")
        val payload = json.parseToJsonElement(parts[1].decodeFromBase64Url().decodeToString()).jsonObject
        assertEquals(nonce, payload["nonce"]?.jsonPrimitive?.content)
        assertEquals(verifier, payload["aud"]?.jsonPrimitive?.content)
    }

    private fun assertVpControllerAndKey(vp: String, holderKey: HolderMaterial) {
        val parts = vp.split('.')
        val header = json.parseToJsonElement(parts[0].decodeFromBase64Url().decodeToString()).jsonObject
        val payload = json.parseToJsonElement(parts[1].decodeFromBase64Url().decodeToString()).jsonObject
        assertEquals(holderKey.kid, header["kid"]?.jsonPrimitive?.content)
        assertEquals(holderKey.controller, payload["iss"]?.jsonPrimitive?.content)
        val presentation = payload["vp"]?.jsonObject ?: payload
        assertEquals(holderKey.controller, presentation["holder"]?.jsonPrimitive?.content)
    }

    private fun countPresentedCredentials(vp: String): Int {
        val payload = json.parseToJsonElement(vp.split('.')[1].decodeFromBase64Url().decodeToString()).jsonObject
        val presentation = payload["vp"]?.jsonObject ?: payload
        return assertNotNull(presentation["verifiableCredential"]?.jsonArray).size
    }

    private fun credentialSubjectId(credential: String): String? {
        val payload = json.parseToJsonElement(credential.split('.')[1].decodeFromBase64Url().decodeToString()).jsonObject
        val vc = payload["vc"]?.jsonObject ?: payload
        return vc["credentialSubject"]?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    private fun vcdm20Format(): CredentialFormat = CredentialFormat.JWT_VC_JSON_LD

    private data class IssuedCredential(
        val format: CredentialFormat,
        val credential: String,
        val issuerKey: ManagedKeyInfoType<*>,
    )

    private data class HolderMaterial(
        val controller: String,
        val keyRef: String,
        val kid: String,
        val publicJwk: Jwk,
    )

    private companion object {
        const val VC11_QUERY = "vcdm11"
        const val VC20_QUERY = "vcdm20"
        const val HOLDER_WALLET_UNIT_ID = "wallet-unit-vcdm-jwt-e2e"
    }
}
