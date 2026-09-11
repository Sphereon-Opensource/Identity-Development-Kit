/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.openid.oid4vci.integration

import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.model.AuthorizationRequest
import com.sphereon.oauth2.common.model.AuthorizationResponse
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported
import com.sphereon.openid.oid4vci.common.model.CredentialDefinition
import com.sphereon.openid.oid4vci.common.model.CredentialRequest
import com.sphereon.openid.oid4vci.issuer.format.CredentialFormatHandler
import com.sphereon.openid.oid4vci.issuer.format.IssuanceContext
import com.sphereon.openid.oid4vci.issuer.format.SigningKeyMode
import com.sphereon.openid.oid4vci.issuer.impl.format.JwtVcJsonFormatHandler
import com.sphereon.openid.oid4vci.issuer.impl.format.VcLdJsonJwtFormatHandler
import com.sphereon.openid.oid4vp.common.ClientMetadata
import com.sphereon.openid.oid4vp.common.ResponseMode
import com.sphereon.openid.oid4vp.common.VpToken
import com.sphereon.openid.oid4vp.common.buildOid4vpAuthorizationResponse
import com.sphereon.openid.oid4vp.common.jwtVcFormatInfo
import com.sphereon.openid.oid4vp.common.vpTokenOf
import com.sphereon.openid.oid4vp.common.vpToken
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.w3cVcMeta
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningIdentifier
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningRequest
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.verifier.Oid4vpVerifierService
import com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.TrustedAuthenticationResolution
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseArgs
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.interaction.protocol.oid4vp.SecureComponentOid4vpJwtVpSigningProvider
import dev.zacsweers.metro.ContributesTo
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Production JWT handlers are exposed only for this issuer/verifier integration fixture. */
@ContributesTo(SessionScope::class)
interface VcdmJwtNestedIssuerTestGraph {
    val jwtVcJsonFormatHandler: JwtVcJsonFormatHandler
    val vcLdJsonJwtFormatHandler: VcLdJsonJwtFormatHandler
}

/**
 * Compact-JWT VCDM integration coverage for multi-child and nested presentations.
 *
 * Every issuer and holder proof is created by the production JWT/KMS or WSCA path. The outer
 * holder JWT is deliberately assembled here only to cover the protocol shape that the producer
 * must accept/verify; producer behavior remains one VP per selected credential.
 */
class VcdmJwtNestedPresentationE2ETest {
    private val ctx = Oid4vciTestContext(this)
    private val json = Json { ignoreUnknownKeys = true }
    private val issuer = "https://nested-vcdm-issuer.example"
    private val verifier = "https://nested-vcdm-verifier.example"
    private val holder = "https://nested-vcdm-holder.example"

    @Test
    fun compactVcdm11HolderVpWithMultipleIssuerSecuredChildrenVerifies() =
        runTest {
            val first = issue(CredentialFormat.JWT_VC_JSON, "nested-v1-issuer-a")
            val second = issue(CredentialFormat.JWT_VC_JSON, "nested-v1-issuer-b")
            val holderKey = provisionHolder("nested-v1-holder")
            val query = query(CredentialFormat.JWT_VC_JSON, "vcdm11-multiple")
            val request = createRequest(query, "nested-v1-nonce", "nested-v1-state", CredentialFormat.JWT_VC_JSON)
            val vp = signVp(vcdm11VpPayload("nested-v1-nonce", listOf(first.credential, second.credential)), holderKey, vcdm11 = true)
            val validation = validate(request.request, query, response(queryId = query.credentials.single().id, vp = vp, state = request.request.state), trusted(listOf(first, second), listOf(holderKey)))

            assertTrue(validation.isOk, "verifier must return a structured result")
            assertTrue(validation.value.valid, "all issuer children and the holder VP must verify: ${validation.value.errors}")
            assertEquals(2, vcdmChildren(vp).size)
        }

    @Test
    fun compactVcdm20NestedVpInVpWithEnvelopedCredentialVerifies() = runTest {
        val child = issue(CredentialFormat.JWT_VC_JSON_LD, "nested-v2-issuer")
        val innerHolder = provisionHolder("nested-v2-inner-holder")
        val outerHolder = provisionHolder("nested-v2-outer-holder")
        val query = query(CredentialFormat.JWT_VC_JSON_LD, "vcdm20-nested")
        val request = createRequest(query, "nested-v2-nonce", "nested-v2-state", CredentialFormat.JWT_VC_JSON_LD)
        val inner = signVp(
            vcdm20VpPayload("nested-v2-nonce", verifier, listOf(vcdm2Envelope(VCDM2_ENVELOPED_VC_TYPE, child.credential))),
            innerHolder,
            vcdm11 = false,
        )
        val outer = signVp(
            vcdm20VpPayload("nested-v2-nonce", verifier, listOf(vcdm2Envelope(VCDM2_ENVELOPED_VP_TYPE, inner))),
            outerHolder,
            vcdm11 = false,
        )

        val validation = validate(
            request.request,
            query,
            response(query.credentials.single().id, outer, request.request.state),
            trusted(listOf(child), listOf(innerHolder, outerHolder)),
        )

        assertTrue(validation.isOk)
        assertTrue(validation.value.valid, "nested VCDM 2.0 VP must verify: ${validation.value.errors}")
    }

    @Test
    fun compactVcdm20NestedVpRejectsTamperedChildAndUnresolvedIssuer() = runTest {
        val child = issue(CredentialFormat.JWT_VC_JSON_LD, "nested-v2-tamper-issuer")
        val innerHolder = provisionHolder("nested-v2-tamper-inner")
        val outerHolder = provisionHolder("nested-v2-tamper-outer")
        val query = query(CredentialFormat.JWT_VC_JSON_LD, "vcdm20-tampered")
        val request = createRequest(query, "nested-v2-tamper-nonce", "nested-v2-tamper-state", CredentialFormat.JWT_VC_JSON_LD)
        val inner = signVp(
            vcdm20VpPayload("nested-v2-tamper-nonce", verifier, listOf(vcdm2Envelope(VCDM2_ENVELOPED_VC_TYPE, tamperSignature(child.credential)))),
            innerHolder,
            vcdm11 = false,
        )
        val outer = signVp(
            vcdm20VpPayload("nested-v2-tamper-nonce", verifier, listOf(vcdm2Envelope(VCDM2_ENVELOPED_VP_TYPE, inner))),
            outerHolder,
            vcdm11 = false,
        )

        val tampered = validate(request.request, query, response(query.credentials.single().id, outer, request.request.state), trusted(listOf(child), listOf(innerHolder, outerHolder)))
        assertTrue(tampered.isOk)
        assertFalse(tampered.value.valid, "tampered issuer child must fail closed")

        val unresolved = validate(request.request, query, response(query.credentials.single().id, outer, request.request.state), trusted(emptyList(), listOf(innerHolder, outerHolder)))
        assertTrue(unresolved.isOk)
        assertFalse(unresolved.value.valid, "unresolved issuer child trust must fail closed")
    }

    @Test
    fun compactVcdm20NestedVpRejectsWrongNestedNonceAudienceDuplicateAndExcessiveDepth() = runTest {
        val child = issue(CredentialFormat.JWT_VC_JSON_LD, "nested-v2-bound-issuer")
        val query = query(CredentialFormat.JWT_VC_JSON_LD, "vcdm20-bounds")

        val innerHolder = provisionHolder("nested-v2-bound-inner")
        val outerHolder = provisionHolder("nested-v2-bound-outer")
        val request = createRequest(query, "nested-v2-bound-nonce", "nested-v2-bound-state", CredentialFormat.JWT_VC_JSON_LD)
        val wrongNonceInner = signVp(
            vcdm20VpPayload("wrong-nested-nonce", verifier, listOf(vcdm2Envelope(VCDM2_ENVELOPED_VC_TYPE, child.credential))),
            innerHolder,
            vcdm11 = false,
        )
        val wrongNonceOuter = signVp(
            vcdm20VpPayload("nested-v2-bound-nonce", verifier, listOf(vcdm2Envelope(VCDM2_ENVELOPED_VP_TYPE, wrongNonceInner))),
            outerHolder,
            vcdm11 = false,
        )
        val wrongNonce = validate(request.request, query, response(query.credentials.single().id, wrongNonceOuter, request.request.state), trusted(listOf(child), listOf(innerHolder, outerHolder)))
        assertTrue(wrongNonce.isOk)
        assertFalse(wrongNonce.value.valid, "wrong nested nonce must fail closed")

        val wrongAudienceInner = signVp(
            vcdm20VpPayload("nested-v2-bound-nonce", "https://wrong-audience.example", listOf(vcdm2Envelope(VCDM2_ENVELOPED_VC_TYPE, child.credential))),
            innerHolder,
            vcdm11 = false,
        )
        val wrongAudienceOuter = signVp(
            vcdm20VpPayload("nested-v2-bound-nonce", verifier, listOf(vcdm2Envelope(VCDM2_ENVELOPED_VP_TYPE, wrongAudienceInner))),
            outerHolder,
            vcdm11 = false,
        )
        val wrongAudience = validate(request.request, query, response(query.credentials.single().id, wrongAudienceOuter, request.request.state), trusted(listOf(child), listOf(innerHolder, outerHolder)))
        assertTrue(wrongAudience.isOk)
        assertFalse(wrongAudience.value.valid, "wrong nested audience must fail closed")

        val duplicateInner = signVp(
            vcdm20VpPayload(
                "nested-v2-bound-nonce",
                verifier,
                listOf(
                    vcdm2Envelope(VCDM2_ENVELOPED_VC_TYPE, child.credential),
                    vcdm2Envelope(VCDM2_ENVELOPED_VC_TYPE, child.credential),
                ),
            ),
            innerHolder,
            vcdm11 = false,
        )
        val duplicateOuter = signVp(
            vcdm20VpPayload("nested-v2-bound-nonce", verifier, listOf(vcdm2Envelope(VCDM2_ENVELOPED_VP_TYPE, duplicateInner))),
            outerHolder,
            vcdm11 = false,
        )
        val duplicate = validate(request.request, query, response(query.credentials.single().id, duplicateOuter, request.request.state), trusted(listOf(child), listOf(innerHolder, outerHolder)))
        assertTrue(duplicate.isOk)
        assertFalse(duplicate.value.valid, "duplicate nested child must fail closed")

        // Base VP plus five nested VP envelopes: the production bound is four nested levels.
        val depthHolders = (0..5).map { provisionHolder("nested-v2-depth-$it") }
        var excessive = signVp(
            vcdm20VpPayload("nested-v2-bound-nonce", verifier, listOf(vcdm2Envelope(VCDM2_ENVELOPED_VC_TYPE, child.credential))),
            depthHolders.first(),
            vcdm11 = false,
        )
        depthHolders.drop(1).forEach { holderKey ->
            excessive = signVp(
                vcdm20VpPayload("nested-v2-bound-nonce", verifier, listOf(vcdm2Envelope(VCDM2_ENVELOPED_VP_TYPE, excessive))),
                holderKey,
                vcdm11 = false,
            )
        }
        val tooDeep = validate(request.request, query, response(query.credentials.single().id, excessive, request.request.state), trusted(listOf(child), depthHolders))
        assertTrue(tooDeep.isOk)
        assertFalse(tooDeep.value.valid, "excessive nested VP depth must fail closed")
    }

    private suspend fun createRequest(query: DcqlQuery, nonce: String, state: String, format: CredentialFormat) =
        verifierService().createAuthorizationRequest(
            CreateAuthorizationRequestArgs(
                instanceId = "nested-vcdm-verifier",
                dcqlQuery = query,
                clientId = verifier,
                responseUri = "$verifier/response",
                responseMode = ResponseMode.DIRECT_POST,
                nonce = nonce,
                state = state,
                clientMetadata = ClientMetadata(
                    vpFormatsSupported = mapOf(format.value to jwtVcFormatInfo(listOf("ES256"))),
                ),
            ),
        ).also { assertTrue(it.isOk, "authorization request creation must succeed: ${if (it.isErr) it.error else ""}") }.value

    private suspend fun validate(
        request: AuthorizationRequest,
        query: DcqlQuery,
        response: AuthorizationResponse,
        trusted: List<TrustedAuthenticationResolution>,
    ) = verifierService().let { service ->
        val parsed = service.parseAuthorizationResponse(
            ParseAuthorizationResponseArgs(
                responseParams = mapOf(
                    "vp_token" to json.encodeToString(JsonElement.serializer(), VpToken.run { response.vpToken!!.toJson() }),
                    "state" to assertNotNull(response.state),
                ),
                originalRequest = request,
            ),
        ).also { assertTrue(it.isOk, "response parser must accept the response: ${if (it.isErr) it.error else ""}") }
        service.validateAuthorizationResponse(
            ValidateAuthorizationResponseArgs(
                parsedResponse = parsed.value,
                originalRequest = request,
                dcqlQuery = query,
                expectedNonce = request.nonce ?: error("test request nonce missing"),
                trustedAuthentications = trusted,
            ),
        )
    }

    private fun response(queryId: String, vp: String, state: String?): AuthorizationResponse =
        buildOid4vpAuthorizationResponse {
            vpToken(vpTokenOf(queryId to vp))
            state(state ?: error("state must be present"))
        }

    private suspend fun issue(format: CredentialFormat, alias: String): IssuedCredential {
        val key = generateKey(alias)
        ctx.registerIssuerSigningKey(alias)
        val configuration = CredentialConfigurationSupported(
            format = format.value,
            credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", "NestedVcdmCredential")),
        )
        val handler: CredentialFormatHandler = if (format == CredentialFormat.JWT_VC_JSON) {
            (ctx.session.graph as VcdmJwtNestedIssuerTestGraph).jwtVcJsonFormatHandler
        } else {
            (ctx.session.graph as VcdmJwtNestedIssuerTestGraph).vcLdJsonJwtFormatHandler
        }
        val result = handler.issueCredential(
            CredentialRequest(format = format.value),
            IssuanceContext(
                subject = "$holder/subject",
                clientId = "nested-vcdm-client",
                issuerIdentifier = issuer,
                credentialConfigurationId = "nested-${format.value}",
                credentialConfiguration = configuration,
                holderBindingKey = null,
                attributes = mapOf("level" to JsonPrimitive("gold")),
                signingKeyAlias = alias,
                signingKeyMode = SigningKeyMode.JwkThumbprint,
                issuanceClockSkewInSeconds = 0,
                expirationInDays = 30,
            ),
        )
        assertTrue(result.isOk, "production issuer must issue ${format.value}: ${if (result.isErr) result.error else ""}")
        return IssuedCredential(result.value.credential.jsonPrimitive.content, key)
    }

    private suspend fun generateKey(alias: String): ManagedKeyInfoType<*> {
        val result = ctx.session.graph.asKeyManagerServiceGraph().keyManagerService.generateKeyResult(
            alias = alias,
            use = JwkUse.sig,
            alg = SignatureAlgorithm.ECDSA_SHA256,
        )
        assertTrue(result.isOk, "software KMS key generation must succeed")
        return assertNotNull(result.value.keyPair?.joseToManagedKeyInfo(KeyVisibility.PRIVATE))
    }

    private suspend fun provisionHolder(alias: String): HolderMaterial {
        val result = (ctx.session.graph as WalletInteractionOid4vciWscaTestGraph).wsca.ensureKey(
            walletUnitId = HOLDER_WALLET_UNIT_ID,
            usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
            algorithm = SignatureAlgorithm.ECDSA_SHA256,
            keyAlias = alias,
        )
        assertTrue(result.isOk, "WSCA holder key provisioning must succeed: ${if (result.isErr) result.error else ""}")
        val key = result.value
        val publicJwk = json.decodeFromString(Jwk.serializer(), assertNotNull(key.publicKeyJwk))
        return HolderMaterial(
            keyReference = key.keyRef ?: key.keyId,
            kid = "$holder/keys/$alias",
            publicJwk = publicJwk.copy(kid = "$holder/keys/$alias"),
        )
    }

    private suspend fun signVp(payload: JsonObject, holderKey: HolderMaterial, vcdm11: Boolean): String {
        val result = SecureComponentOid4vpJwtVpSigningProvider(
            (ctx.session.graph as WalletInteractionOid4vciWscaTestGraph).wsca,
        ).sign(
            HolderJwtVpSigningRequest(
                walletUnitId = HOLDER_WALLET_UNIT_ID,
                payload = payload,
                keyReference = holderKey.keyReference,
                signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                identifier = HolderJwtVpSigningIdentifier.JwksKid(holderKey.kid),
                protectedHeader = buildJsonObject {
                    put("typ", if (vcdm11) "JWT" else "vp+jwt")
                    if (!vcdm11) put("cty", "vp")
                },
                operationBinding = "nested-vp-signing",
            ),
        )
        assertTrue(result.isOk, "production WSCA holder signer must sign VP: ${if (result.isErr) result.error else ""}")
        return result.value.compactJws
    }

    private fun vcdm11VpPayload(nonce: String, children: List<String>) = buildJsonObject {
        put("iss", holder)
        put("aud", verifier)
        put("nonce", nonce)
        put("iat", Clock.System.now().epochSeconds)
        putJsonObject("vp") {
            putJsonArray("@context") { add(JsonPrimitive(VCDM11_CONTEXT)) }
            putJsonArray("type") { add(JsonPrimitive("VerifiablePresentation")) }
            put("holder", holder)
            putJsonArray("verifiableCredential") { children.forEach { add(JsonPrimitive(it)) } }
        }
    }

    private fun vcdm20VpPayload(nonce: String, audience: String, children: List<JsonObject>) = buildJsonObject {
        put("@context", VCDM20_CONTEXT)
        put("type", "VerifiablePresentation")
        put("holder", holder)
        putJsonArray("verifiableCredential") { children.forEach(::add) }
        put("iss", holder)
        put("aud", audience)
        put("nonce", nonce)
        put("iat", Clock.System.now().epochSeconds)
    }

    private fun vcdm2Envelope(type: String, compactJwt: String) = buildJsonObject {
        put("@context", VCDM20_CONTEXT)
        put("id", if (type == VCDM2_ENVELOPED_VP_TYPE) "$VCDM2_VP_DATA_URI_PREFIX$compactJwt" else "$VCDM2_VC_DATA_URI_PREFIX$compactJwt")
        put("type", type)
    }

    private fun tamperSignature(jwt: String): String {
        val parts = jwt.split('.')
        assertEquals(3, parts.size)
        val signature = parts[2].toCharArray()
        signature[0] = if (signature[0] == 'A') 'B' else 'A'
        return "${parts[0]}.${parts[1]}.${signature.concatToString()}"
    }

    private fun query(format: CredentialFormat, id: String) = DcqlQuery(
        credentials = listOf(
            DcqlCredentialQuery(
                id = id,
                format = format.value,
                meta = w3cVcMeta(listOf("VerifiableCredential", "NestedVcdmCredential")),
            ),
        ),
    )

    private fun trusted(credentials: List<IssuedCredential>, holders: List<HolderMaterial>): List<TrustedAuthenticationResolution> = buildList {
        if (credentials.isNotEmpty()) {
            add(
                TrustedAuthenticationResolution(
                    controller = issuer,
                    trustedJwks = JsonObject(mapOf("keys" to JsonArray(credentials.map(::publicJwk)))),
                ),
            )
        }
        add(
            TrustedAuthenticationResolution(
                controller = holder,
                trustedJwks = JsonObject(mapOf("keys" to JsonArray(holders.map { it.publicJwk.toJsonObject() }))),
            ),
        )
    }

    private fun publicJwk(credential: IssuedCredential): JsonObject {
        val header = json.parseToJsonElement(credential.jwt.substringBefore('.').decodeFromBase64Url().decodeToString()).jsonObject
        val kid = assertNotNull(header["kid"]?.jsonPrimitive?.content)
        return assertNotNull(credential.issuerKey.toManagedPublicKeyInfo().key as? Jwk).copy(kid = kid).toJsonObject() as JsonObject
    }

    private fun vcdmChildren(vp: String): List<JsonElement> {
        val payload = json.parseToJsonElement(vp.split('.')[1].decodeFromBase64Url().decodeToString()).jsonObject
        return payload["vp"]!!.jsonObject["verifiableCredential"]!!.let { (it as JsonArray).toList() }
    }

    private fun verifierService(): Oid4vpVerifierService = (ctx.session.graph as Oid4vpPresentationTestGraph).oid4vpVerifierService

    private data class IssuedCredential(val credential: String, val issuerKey: ManagedKeyInfoType<*>) {
        val jwt: String get() = credential
    }

    private data class HolderMaterial(val keyReference: String, val kid: String, val publicJwk: Jwk)

    private companion object {
        const val VCDM11_CONTEXT = "https://www.w3.org/2018/credentials/v1"
        const val VCDM20_CONTEXT = "https://www.w3.org/ns/credentials/v2"
        const val VCDM2_VC_DATA_URI_PREFIX = "data:application/vc+jwt,"
        const val VCDM2_VP_DATA_URI_PREFIX = "data:application/vp+jwt,"
        const val VCDM2_ENVELOPED_VC_TYPE = "EnvelopedVerifiableCredential"
        const val VCDM2_ENVELOPED_VP_TYPE = "EnvelopedVerifiablePresentation"
        const val HOLDER_WALLET_UNIT_ID = "wallet-unit-nested-vcdm-jwt-e2e"
    }
}
