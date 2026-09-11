/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.integration

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.jose.generateJwkThumbprintUri
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkSet
import com.sphereon.crypto.resolution.AdditionalIdentifierLookup
import com.sphereon.crypto.resolution.extern.ExternalIdentifierJwksUrlOpts
import com.sphereon.crypto.resolution.extern.JwksUrlExternalIdentifierResolutionServiceImpl
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
import com.sphereon.openid.oid4vp.holder.Oid4vpHolder
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.SelectedCredential
import com.sphereon.openid.oid4vp.holder.HolderJwtVpSigningIdentifier
import com.sphereon.openid.oid4vp.verifier.CreateAuthorizationRequestArgs
import com.sphereon.openid.oid4vp.verifier.Oid4vpVerifierService
import com.sphereon.openid.oid4vp.verifier.ParseAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.verifier.ValidateAuthorizationResponseArgs
import com.sphereon.openid.oid4vp.holder.VerifierInfo
import com.sphereon.openid.oid4vp.verifier.TrustedAuthenticationResolution
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.wallet.unit.SecureComponentUsage
import dev.zacsweers.metro.ContributesTo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Production verifier and holder services used by the identifier-neutral E2E. */
@ContributesTo(SessionScope::class)
interface VcdmJwksVerifierTestGraph {
    val oid4vpVerifierService: Oid4vpVerifierService
    val oid4vpHolder: Oid4vpHolder
}

/** Concrete production issuer handler used by this test. */
@ContributesTo(SessionScope::class)
interface VcdmJwksIssuerTestGraph {
    val jwtVcJsonFormatHandler: JwtVcJsonFormatHandler
    val vcLdJsonJwtFormatHandler: VcLdJsonJwtFormatHandler
}

/**
 * Identifier-neutral VCDM 1.1 and 2.0 JWT proof using a real configured HTTPS JWKS resolver.
 *
 * The endpoint is intercepted only at the HTTP transport boundary. The issuer JWT is generated
 * and signed by the software KMS; the holder uses the production VP construction command; and
 * the production verifier command validates the holder binding and issuer signature. The
 * issuer JWT contains neither an embedded `jwk` nor `x5c`; the only issuer trust input is the
 * configured JWKS URL and its published public key.
 */
class VcdmJwtJwksIdentifierNeutralE2ETest {
    private val ctx = Oid4vciTestContext(this)
    private val json = Json { ignoreUnknownKeys = true }
    private val issuer = "https://jwks-vcdm-issuer.example"
    private val verifier = "https://jwks-vcdm-verifier.example"
    private val holder = "https://jwks-vcdm-holder.example"
    private val jwksUrl = "$issuer/.well-known/jwks.json"
    private val holderWalletUnitId = "wallet-unit-jwks-vcdm-e2e"

    @Test
    fun vcdm11AndVcdm20IssuePresentAndVerifyUseConfiguredHttpsJwksWithoutDid() =
        runTest {
            for (version in JwtVersion.entries) {
            val issuerKey = generateKey("jwks-vcdm-issuer-key-${version.name.lowercase()}")
            val holderKey = provisionHolder("jwks-vcdm-holder-key-${version.name.lowercase()}")
            val published = publishedJwks(issuerKey)
            val http = StaticJwksHttpClientFactory(jwksUrl, published)
            val verifierService = verifierService()
            val query = DcqlQuery(credentials = listOf(query(version)))
            val request =
                verifierService
                    .createAuthorizationRequest(
                        CreateAuthorizationRequestArgs(
                            instanceId = "jwks-vcdm-verifier",
                            dcqlQuery = query,
                            clientId = verifier,
                            responseUri = "$verifier/response",
                            responseMode = ResponseMode.DIRECT_POST,
                            nonce = "jwks-vcdm-nonce",
                            state = "jwks-vcdm-state",
                            clientMetadata = ClientMetadata(vpFormatsSupported = mapOf(version.format.value to jwtVcFormatInfo(listOf("ES256")))),
                        ),
                    ).also {
                        assertTrue(it.isOk, "production JWKS verifier request must be created: ${if (it.isErr) it.error else ""}")
                    }
                    .value
            val resolved =
                ResolvedOid4vpRequest(
                    request = request.request,
                    dcqlQuery = query,
                    clientMetadata = ClientMetadata(vpFormatsSupported = mapOf(version.format.value to jwtVcFormatInfo(listOf("ES256")))),
                    verifierInfo = VerifierInfo(clientId = verifier, clientIdScheme = ClientIdScheme.REDIRECT_URI),
                )
            val credential = issue(issuerKey.alias, version)
            assertIssuerJwtHasNoEmbeddedKeyMaterial(credential)
            assertCredentialShape(credential, version)
            val response =
                (ctx.session.graph as VcdmJwksVerifierTestGraph).oid4vpHolder.commands.createAuthorizationResponse.execute(
                    CreateAuthorizationResponseArgs(
                        request = resolved,
                        selectedCredentials =
                            listOf(
                                SelectedCredential(
                                    credentialQueryId = QUERY_ID,
                                    credentialId = "jwks-vcdm-credential",
                                    presentation = JsonPrimitive(credential),
                                    credentialFormat = version.format,
                                    holderKeyRef = holderKey.keyRef,
                                    holderId = holder,
                                    holderVerificationMethod = holderKey.kid,
                                    holderSigningAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                                    holderJwtVpSigningIdentifier =
                                        HolderJwtVpSigningIdentifier.JwksKid(holderKey.kid),
                                    holderJwtVpOperationBinding = "jwks-vcdm-attended-operation",
                                    holderJwtVpWalletUnitId = holderWalletUnitId,
                                ),
                            ),
                    ),
                )
            assertTrue(
                response.isOk,
                "production holder VP construction must succeed: ${if (response.isErr) response.error else ""}",
            )

            val parsed =
                verifierService.parseAuthorizationResponse(
                    ParseAuthorizationResponseArgs(
                        responseParams =
                            mapOf(
                                "vp_token" to
                                    json.encodeToString(
                                        JsonElement.serializer(),
                                        VpToken.run { response.value.vpToken!!.toJson() },
                                    ),
                                "state" to assertNotNull(response.value.state),
                            ),
                        originalRequest = request.request,
                    ),
                )
            assertTrue(
                parsed.isOk,
                "production response parser must accept holder output: ${if (parsed.isErr) parsed.error else ""}",
            )

            val issuerAuthentication = resolveJwks(http, issuerJwtKid(credential))
            val validation = verifierService.validateAuthorizationResponse(
                ValidateAuthorizationResponseArgs(
                    parsedResponse = parsed.value,
                    originalRequest = request.request,
                    dcqlQuery = query,
                    expectedNonce = "jwks-vcdm-nonce",
                    trustedAuthentications = listOf(issuerAuthentication, holderAuthentication(holderKey)),
                ),
            )
            assertTrue(
                validation.isOk,
                "production verifier should return structured validation: ${if (validation.isErr) validation.error else ""}",
            )
            assertTrue(validation.value.valid, "issuer and holder signatures must verify: ${validation.value.errors}")
            assertTrue(http.fetches > 0, "issuer key must be fetched through the configured JWKS resolver")
            assertVpBinding(
                assertNotNull(response.value.vpToken!!.getSinglePresentation(QUERY_ID)),
                nonce = "jwks-vcdm-nonce",
            )
            assertVpShape(assertNotNull(response.value.vpToken!!.getSinglePresentation(QUERY_ID)), version)
            }
        }

    @Test
    fun wrongOrUnconfiguredJwksCannotVerifyTheIssuedCredential() =
        runTest {
            val issuerKey = generateKey("jwks-vcdm-negative-issuer-key")
            val holderKey = provisionHolder("jwks-vcdm-negative-holder-key")
            val credential = issue(issuerKey.alias)
            assertIssuerJwtHasNoEmbeddedKeyMaterial(credential)
            val verifierService = verifierService()
            val query = DcqlQuery(credentials = listOf(query()))
            val request =
                verifierService.createAuthorizationRequest(
                    CreateAuthorizationRequestArgs(
                        instanceId = "jwks-vcdm-negative-verifier",
                        dcqlQuery = query,
                        clientId = verifier,
                        responseUri = "$verifier/response",
                        responseMode = ResponseMode.DIRECT_POST,
                        nonce = "jwks-vcdm-negative-nonce",
                        state = "jwks-vcdm-negative-state",
                        clientMetadata = ClientMetadata(vpFormatsSupported = mapOf(JwtVersion.V20.format.value to jwtVcFormatInfo(listOf("ES256")))),
                    ),
                ).also {
                    assertTrue(it.isOk, "production JWKS verifier request must be created: ${if (it.isErr) it.error else ""}")
                }.value
            val resolved =
                ResolvedOid4vpRequest(
                    request = request.request,
                    dcqlQuery = query,
                    clientMetadata = ClientMetadata(vpFormatsSupported = mapOf(JwtVersion.V20.format.value to jwtVcFormatInfo(listOf("ES256")))),
                    verifierInfo = VerifierInfo(clientId = verifier, clientIdScheme = ClientIdScheme.REDIRECT_URI),
                )
            val response =
                (ctx.session.graph as VcdmJwksVerifierTestGraph).oid4vpHolder.commands.createAuthorizationResponse.execute(
                    CreateAuthorizationResponseArgs(
                        request = resolved,
                        selectedCredentials =
                            listOf(
                                SelectedCredential(
                                    credentialQueryId = QUERY_ID,
                                    credentialId = "jwks-vcdm-negative-credential",
                                    presentation = JsonPrimitive(credential),
                                    credentialFormat = CredentialFormat.JWT_VC_JSON_LD,
                                    holderKeyRef = holderKey.keyRef,
                                    holderId = holder,
                                    holderVerificationMethod = holderKey.kid,
                                    holderSigningAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
                                    holderJwtVpSigningIdentifier =
                                        HolderJwtVpSigningIdentifier.JwksKid(holderKey.kid),
                                    holderJwtVpOperationBinding = "jwks-vcdm-attended-operation",
                                    holderJwtVpWalletUnitId = holderWalletUnitId,
                                ),
                            ),
                    ),
                )
            assertTrue(
                response.isOk,
                "production holder VP construction must succeed for negative verification cases: ${if (response.isErr) response.error else ""}",
            )
            val parsed = parse(verifierService, request.request, response.value)

            val unrelatedKey = generateKey("jwks-vcdm-unrelated-key")
            val wrongHttp =
                StaticJwksHttpClientFactory(
                    jwksUrl,
                    publishedJwks(unrelatedKey, kidOverride = issuerJwtKid(credential)),
                )
            val issuerAuthentication = resolveJwks(wrongHttp, issuerJwtKid(credential))
            val wrong = verifierService.validateAuthorizationResponse(
                ValidateAuthorizationResponseArgs(
                    parsedResponse = parsed,
                    originalRequest = request.request,
                    dcqlQuery = query,
                    expectedNonce = "jwks-vcdm-negative-nonce",
                    trustedAuthentications = listOf(issuerAuthentication, holderAuthentication(holderKey)),
                ),
            )
            assertTrue(wrong.isOk, "wrong JWKS verification must return a structured result: ${if (wrong.isErr) wrong.error else ""}")
            assertFalse(wrong.value.valid, "a JWKS containing an unrelated key must fail cryptographic verification: ${wrong.value.errors}")
            assertTrue(wrongHttp.fetches > 0)

            val unconfiguredHttp = StaticJwksHttpClientFactory("https://configured-only.example/jwks", publishedJwks(issuerKey))
            val unconfiguredAuthentication = resolveJwksOrNull(
                unconfiguredHttp,
                endpoint = "https://unconfigured.example/jwks",
                kid = issuerJwtKid(credential),
            )
            assertTrue(unconfiguredAuthentication == null, "unconfigured JWKS resolution must fail closed")
            val unconfigured = verifierService.validateAuthorizationResponse(
                ValidateAuthorizationResponseArgs(
                    parsedResponse = parsed,
                    originalRequest = request.request,
                    dcqlQuery = query,
                    expectedNonce = "jwks-vcdm-negative-nonce",
                    trustedAuthentications = emptyList(),
                ),
            )
            assertTrue(unconfigured.isOk, "unconfigured JWKS verification must return a structured result: ${if (unconfigured.isErr) unconfigured.error else ""}")
            assertFalse(unconfigured.value.valid, "an unconfigured JWKS endpoint must fail closed: ${unconfigured.value.errors}")
            assertTrue(unconfiguredHttp.fetches > 0)
        }

    private suspend fun generateKey(alias: String): ManagedKeyInfoType<*> {
        val kms = ctx.session.graph.asKeyManagerServiceGraph().keyManagerService
        val generated = kms.generateKeyResult(alias = alias, use = JwkUse.sig, alg = SignatureAlgorithm.ECDSA_SHA256)
        assertTrue(generated.isOk, "software KMS key generation must succeed: ${if (generated.isErr) generated.error else ""}")
        return assertNotNull(generated.value.keyPair?.joseToManagedKeyInfo(com.sphereon.crypto.core.KeyVisibility.PRIVATE))
    }

    private suspend fun provisionHolder(alias: String): HolderMaterial {
        val result =
            (ctx.session.graph as WalletInteractionOid4vciWscaTestGraph).wsca.ensureKey(
                walletUnitId = holderWalletUnitId,
                usage = SecureComponentUsage.WALLET_CREDENTIAL_PROOF,
                algorithm = SignatureAlgorithm.ECDSA_SHA256,
                keyAlias = alias,
            )
        assertTrue(result.isOk, "holder key must be provisioned through WSCA: ${if (result.isErr) result.error else ""}")
        val key = result.value
        val publicJwk = json.decodeFromString(Jwk.serializer(), assertNotNull(key.publicKeyJwk))
        val kid = "$holder/keys/$alias"
        return HolderMaterial(keyRef = assertNotNull(key.keyRef ?: key.keyId), kid = kid, publicJwk = publicJwk.copy(kid = kid))
    }

    private suspend fun issue(alias: String, version: JwtVersion = JwtVersion.V20): String {
        ctx.registerIssuerSigningKey(alias)
        val configuration =
            CredentialConfigurationSupported(
                format = version.format.value,
                credentialDefinition = CredentialDefinition(type = listOf("VerifiableCredential", version.credentialType)),
            )
        val graph = ctx.session.graph as VcdmJwksIssuerTestGraph
        val handler: CredentialFormatHandler = if (version == JwtVersion.V11) graph.jwtVcJsonFormatHandler else graph.vcLdJsonJwtFormatHandler
        val result = handler.issueCredential(
                CredentialRequest(format = version.format.value),
                IssuanceContext(
                    subject = "https://jwks-vcdm-holder.example/subject",
                    clientId = "jwks-vcdm-client",
                    issuerIdentifier = issuer,
                    credentialConfigurationId = "jwks-vcdm-credential",
                    credentialConfiguration = configuration,
                    holderBindingKey = null,
                    attributes = mapOf("level" to JsonPrimitive("gold")),
                    signingKeyAlias = alias,
                    signingKeyMode = SigningKeyMode.JwkThumbprint,
                    issuanceClockSkewInSeconds = 0,
                    expirationInDays = 30,
                ),
            )
        assertTrue(result.isOk, "production ${version.label} issuer must issue: ${if (result.isErr) result.error else ""}")
        return result.value.credential.jsonPrimitive.content
    }

    private fun publishedJwks(key: ManagedKeyInfoType<*>, kidOverride: String? = null): String {
        val jwk = assertNotNull(key.toManagedPublicKeyInfo().key as? Jwk)
        val kid = kidOverride ?: generateJwkThumbprintUri(jwk.toPublicKey())
        return json.encodeToString(JwkSet.serializer(), JwkSet(keys = arrayOf(jwk.copy(kid = kid))))
    }

    private fun assertIssuerJwtHasNoEmbeddedKeyMaterial(jwt: String) {
        val header = issuerJwtHeader(jwt)
        assertFalse(header.containsKey("jwk"), "issuer JWT must not carry a token-supplied JWK")
        assertFalse(header.containsKey("jku"), "issuer JWT must not carry a token-supplied JKU")
        assertFalse(header.containsKey("x5c"), "issuer JWT must not carry a token-supplied X.509 chain")
    }

    private fun issuerJwtKid(jwt: String): String = assertNotNull(issuerJwtHeader(jwt)["kid"]?.jsonPrimitive?.content)

    private fun holderAuthentication(holderKey: HolderMaterial): TrustedAuthenticationResolution {
        return TrustedAuthenticationResolution(
            controller = holder,
            trustedJwks = JsonObject(mapOf("keys" to JsonArray(listOf(holderKey.publicJwk.toJsonObject())))),
        )
    }

    private fun issuerJwtHeader(jwt: String) =
        json.parseToJsonElement(jwt.substringBefore('.').decodeFromBase64Url().decodeToString()).jsonObject

    private suspend fun resolveJwks(
        http: StaticJwksHttpClientFactory,
        kid: String,
    ): TrustedAuthenticationResolution {
        return checkNotNull(resolveJwksOrNull(http, jwksUrl, kid))
    }

    private suspend fun resolveJwksOrNull(
        http: StaticJwksHttpClientFactory,
        endpoint: String,
        kid: String,
    ): TrustedAuthenticationResolution? {
        val resolver =
            JwksUrlExternalIdentifierResolutionServiceImpl(
                ctx.session.asCoreApiServiceGraph().serviceExecution,
                http,
            )
        val result =
            resolver.resolve(
                ExternalIdentifierJwksUrlOpts(
                    identifier = endpoint,
                    lookup = AdditionalIdentifierLookup(kid = kid),
                ),
            )
        if (result.isErr) return null
        val resolvedJwks =
            JsonObject(
                mapOf(
                    "keys" to
                        JsonArray(
                            result.value.jwks.map { resolved ->
                                Jwk.from(resolved.key).toJsonObject()
                            },
                        ),
                ),
            )
        return TrustedAuthenticationResolution(
            controller = issuer,
            trustedJwks = resolvedJwks,
        )
    }

    private fun assertVpBinding(vp: String, nonce: String) {
        val payload = json.parseToJsonElement(vp.split('.')[1].decodeFromBase64Url().decodeToString()).jsonObject
        assertEquals(nonce, payload["nonce"]?.jsonPrimitive?.content)
        assertEquals(verifier, payload["aud"]?.jsonPrimitive?.content)
    }

    private fun assertCredentialShape(jwt: String, version: JwtVersion) {
        val header = issuerJwtHeader(jwt)
        val payload = json.parseToJsonElement(jwt.split('.')[1].decodeFromBase64Url().decodeToString()).jsonObject
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
        val header = issuerJwtHeader(jwt)
        if (version == JwtVersion.V11) {
            assertEquals("JWT", header["typ"]?.jsonPrimitive?.content)
            assertFalse(header.containsKey("cty"))
        } else {
            assertEquals("vp+jwt", header["typ"]?.jsonPrimitive?.content)
            assertEquals("vp", header["cty"]?.jsonPrimitive?.content)
        }
        val payload = json.parseToJsonElement(jwt.split('.')[1].decodeFromBase64Url().decodeToString()).jsonObject
        if (version == JwtVersion.V11) {
            val vp = assertNotNull(payload["vp"] as? JsonObject)
            assertEquals(version.context, (vp["@context"] as JsonArray).first().jsonPrimitive.content)
            assertEquals("VerifiablePresentation", (vp["type"] as JsonArray).first().jsonPrimitive.content)
        } else {
            assertEquals(version.context, (payload["@context"] as JsonArray).first().jsonPrimitive.content)
        }
    }

    private fun query(version: JwtVersion = JwtVersion.V20) =
        DcqlCredentialQuery(
            id = QUERY_ID,
            format = version.format.value,
            meta = w3cVcMeta(listOf("VerifiableCredential", version.credentialType)),
        )

    private fun verifierService(): Oid4vpVerifierService = (ctx.session.graph as VcdmJwksVerifierTestGraph).oid4vpVerifierService

    private suspend fun parse(
        verifier: Oid4vpVerifierService,
        request: com.sphereon.oauth2.common.model.AuthorizationRequest,
        response: com.sphereon.oauth2.common.model.AuthorizationResponse,
    ) =
        verifier.parseAuthorizationResponse(
            ParseAuthorizationResponseArgs(
                responseParams =
                    mapOf(
                        "vp_token" to
                            json.encodeToString(
                                JsonElement.serializer(),
                                VpToken.run { response.vpToken!!.toJson() },
                            ),
                        "state" to assertNotNull(response.state),
                    ),
                originalRequest = request,
            ),
        ).also {
            assertTrue(it.isOk, "production JWKS response parser must accept holder output: ${if (it.isErr) it.error else ""}")
        }.value

    private class StaticJwksHttpClientFactory(
        private val servedUrl: String,
        val jwksJson: String,
    ) : HttpClientFactory {
        var fetches: Int = 0

        override fun createClient(options: HttpClientOptions): HttpClient =
            HttpClient(
                MockEngine { request ->
                    fetches++
                    if (request.method == HttpMethod.Get && request.url.toString() == servedUrl) {
                        respond(
                            content = jwksJson,
                            status = HttpStatusCode.OK,
                            headers = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString())),
                        )
                    } else {
                        respond("not configured", HttpStatusCode.NotFound)
                    }
                },
            )

        override fun isSupportedOptions(options: HttpClientOptions): Boolean = true

        override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(HttpClientEngineType.CIO)

        override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
    }

    private data class HolderMaterial(
        val keyRef: String,
        val kid: String,
        val publicJwk: Jwk,
    )

    private enum class JwtVersion(
        val format: CredentialFormat,
        val label: String,
        val credentialType: String,
        val context: String,
        val credentialTyp: String,
    ) {
        V11(CredentialFormat.JWT_VC_JSON, "VCDM 1.1", "JwksVcdm11Credential", "https://www.w3.org/2018/credentials/v1", "JWT"),
        V20(CredentialFormat.JWT_VC_JSON_LD, "VCDM 2.0", "JwksVcdm2Credential", "https://www.w3.org/ns/credentials/v2", "vc+jwt"),
    }

    private companion object {
        const val QUERY_ID = "jwks-vcdm-query"
    }
}
