/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwtService
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.core.api.error.sourceAs
import com.sphereon.openid.oid4vp.verifier.TrustedAuthenticationResolution
import com.sphereon.openid.oid4vp.verifier.VerifyHolderBindingArgs
import com.sphereon.openid.oid4vc.common.PresentationFormat
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.vcdm.VcdmClassifier
import com.sphereon.openid.oid4vc.common.vcdm.VcdmDocumentKind
import com.sphereon.openid.oid4vc.common.vcdm.VcdmError
import com.sphereon.openid.oid4vc.common.vcdm.VcdmProfiles
import dev.whyoleg.cryptography.CryptographyProvider
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Production JOSE verification against the pinned W3C VC-JOSE-COSE VCDM 2 vectors.
 *
 * The issuer/holder key is passed as verifier-admitted public JWKS material. No key is taken
 * from a compact JWT header, and the tests intentionally keep the upstream payloads unchanged
 * (including their historical dates and string-valued iat claims).
 */
class OfficialW3cVcJoseCoseVerifierTest {
    private val app = createOid4vpRpJvmTestAppGraph(this)
    private val userContext = app.userContextManager.getAnonymous()
    private val session =
        userContext.sessionContextManager.createOrGetFromId(
            "official-w3c-vc-jose-cose-verifier-test",
            principalType = com.sphereon.di.context.PrincipalType.USER,
        )
    private lateinit var jwtService: JwtService

    @BeforeTest
    fun setUp() {
        val factory = app as SoftwareKmsProviderFactoryImpl.Graph
        val provider =
            factory.softwareKmsProvider.create(
                SoftwareKmsProviderConfig(
                    id = "official-w3c-vc-jose-cose-verifier-test-provider",
                    cryptographyProvider = CryptographyProvider.Default.name,
                ),
                session.asCoreApiServiceGraph().serviceExecution,
            )
        val keyManager: KeyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManager.registerProvider(provider, makeDefaultKms = true)
        jwtService = (session.graph as JwtServiceImpl.Graph).jwtService
    }

    @Test
    fun `official basic credential has valid crypto but is rejected for string iat semantics`() = runTest {
        // The pinned upstream artifact is cryptographically valid, but its historical payload
        // uses an ISO-8601 string for `iat`. JWT NumericDate validation must reject that claim;
        // this assertion deliberately keeps semantic validity separate from signature validity.
        val classification = VcdmClassifier.classifyCompactJws(fixture("credential-jose-minimal.txt"))
        assertTrue(classification.isErr)
        val claimError = assertIs<VcdmError.InvalidJwtClaim>(classification.error.sourceAs())
        assertEquals("iat", claimError.claim)

        val validation = verify("credential-jose-minimal.txt", "vm-p256.json")

        assertTrue(validation.isValid, "official ES256 VC signature must verify: ${validation.errorMessages}")
        assertEquals(true, validation.cryptoVerified)
        assertTrue(validation.trustEstablished, "public JWK admission must establish trust")
        assertEquals("https://example.issuer/vc-jose-cose", validation.parsedPayload["issuer"]?.jsonPrimitive?.content)
        assertTrue(validation.parsedPayload["iat"]?.jsonPrimitive?.isString == true)
    }

    @Test
    fun `official full credential is accepted by production VCDM 2 document classifier`() {
        val document = Json.parseToJsonElement(rawFixture("credential-full.json").decodeToString()).jsonObject
        val classification = VcdmClassifier.classifyDocument(document).getOrThrow()

        assertEquals(VcdmDocumentKind.CREDENTIAL, classification.kind)
        assertTrue(VcdmProfiles.v2_0.validateCredential(classification.json).valid)
    }

    @Test
    fun `official invalid signature is rejected after real key trust establishment`() = runTest {
        val validation = verify("credential-jose-bad-signature.txt", "vm-ed25519.json")

        assertTrue(validation.trustEstablished, "the admitted Ed25519 key should be selected")
        assertEquals(false, validation.cryptoVerified, "signature mismatch must fail crypto verification")
        assertFalse(validation.isValid)
    }

    @Test
    fun `official issuer match verifies and production classifier preserves issuer consistency`() = runTest {
        val validation = verify("credential-issuer-match-signed.txt", "vm-ed25519.json")

        assertTrue(validation.isValid, "official EdDSA VC must verify: ${validation.errorMessages}")
        assertEquals(true, validation.cryptoVerified)
        assertTrue(validation.trustEstablished)
        assertEquals(
            validation.parsedPayload["issuer"]?.jsonPrimitive?.content,
            validation.parsedPayload["iss"]?.jsonPrimitive?.content,
        )

        // The signed upstream payload has the known string-iat anomaly. Rebuild only the
        // unsigned classifier input with NumericDate claims so the issuer/iss rule is actually
        // exercised rather than masked by temporal validation.
        val classification = VcdmClassifier.classifyCompactJws(withNumericDates("credential-issuer-match-signed.txt"))
            .getOrThrow()
        assertEquals(
            classification.document.json["issuer"]?.jsonPrimitive?.content,
            classification.rawPayload["iss"]?.jsonPrimitive?.content,
        )
        assertTrue(VcdmProfiles.v2_0.validateCredential(classification.document.json).valid)
    }

    @Test
    fun `official ES512 P521 credential verifies with admitted production key`() = runTest {
        val validation = verify("credential-jose-unknown-extensions.txt", "vm-p521.json")

        assertTrue(validation.isValid, "official ES512 VC must verify: ${validation.errorMessages}")
        assertEquals(true, validation.cryptoVerified)
        assertTrue(validation.trustEstablished)

        // This upstream signed vector also has string iat. Do not use it as unknown-extension
        // semantic evidence; that conformance check is performed with a NumericDate payload below.
        val classification = VcdmClassifier.classifyCompactJws(fixture("credential-jose-unknown-extensions.txt"))
        val claimError = assertIs<VcdmError.InvalidJwtClaim>(classification.error.sourceAs())
        assertEquals("iat", claimError.claim)
    }

    @Test
    fun `unknown extension members are ignored when known JWT claims are valid`() {
        val classification = VcdmClassifier.classifyCompactJws(generatedUnknownExtensionJws()).getOrThrow()

        assertEquals(VcdmDocumentKind.CREDENTIAL, classification.document.kind)
        assertEquals(CredentialFormat.JWT_VC_JSON_LD, classification.credentialFormat)
        assertEquals(null, classification.presentationFormat)
        assertTrue(VcdmProfiles.v2_0.validateCredential(classification.document.json).valid)
        assertTrue(classification.document.json.containsKey("badExtension"))
        assertTrue(classification.document.json.containsKey("anotherBadOne"))
        assertEquals("1700000000", classification.rawPayload["iat"]?.jsonPrimitive?.content)
        assertEquals("1893456000", classification.rawPayload["exp"]?.jsonPrimitive?.content)
    }

    @Test
    fun `official forbidden vc and vp claims are rejected by production classifier after crypto`() = runTest {
        val validation = verify("credential-jose-vc-vp-claims.txt", "vm-ed25519.json")

        assertTrue(validation.isValid, "the official forbidden-claim vector must have valid crypto")
        assertEquals(true, validation.cryptoVerified)
        assertTrue(validation.trustEstablished)

        val classification = VcdmClassifier.classifyCompactJws(fixture("credential-jose-vc-vp-claims.txt"))
        assertTrue(classification.isErr)
        assertIs<VcdmError.ContradictoryDocumentShape>(classification.error.sourceAs())
    }

    @Test
    fun `official multiple VP verifies with production JOSE crypto and retains all credential envelopes`() = runTest {
        val classification = VcdmClassifier.classifyCompactJws(fixture("presentation-jose-multiple.txt")).getOrThrow()
        assertEquals(VcdmDocumentKind.PRESENTATION, classification.document.kind)
        assertEquals(null, classification.credentialFormat)
        assertEquals(PresentationFormat.JWT_VP_JSON, classification.presentationFormat)
        assertTrue(VcdmProfiles.v2_0.validatePresentation(classification.document.json).valid)

        val validation = verify("presentation-jose-multiple.txt", "vm-p384.json")

        assertTrue(validation.isValid, "official ES384 VP must verify: ${validation.errorMessages}")
        assertEquals(true, validation.cryptoVerified)
        assertTrue(validation.trustEstablished)
        val children = validation.parsedPayload["verifiableCredential"]
        assertEquals(3, children?.jsonArray?.size, "the official VP must retain all three credential envelopes")
        assertTrue(children!!.jsonArray.all { it.jsonObject["id"]?.jsonPrimitive?.content?.startsWith("data:application/") == true })
    }

    @Test
    fun `official invalid JOSE media type is rejected by production holder-binding boundary`() = runTest {
        val result =
            sessionOid4vpVerifier().commands.verifyHolderBinding.execute(
                VerifyHolderBindingArgs(
                    presentation = fixture("presentation-jose-bad-media-type.txt"),
                    presentationFormat = PresentationFormat.JWT_VP_JSON,
                    expectedNonce = "unused",
                    expectedAudience = "unused",
                    trustedAuthentications = listOf(holderAuthentication("vm-ed25519.json")),
                ),
            )

        assertTrue(result.isErr, "invalid VCDM 2.0 JOSE media types must fail classification before holder binding")
        assertTrue(result.error.message.defaultMessage.contains("classification", ignoreCase = true))
    }

    @Test
    fun `official bad credential envelope is rejected by production holder-binding boundary`() = runTest {
        val validation = verify("presentation-jose-bad-credential.txt", "vm-ed25519.json")
        assertTrue(validation.isValid, "the outer VP signature must verify before child validation")
        assertEquals(true, validation.cryptoVerified)
        assertTrue(validation.trustEstablished)

        val result =
            sessionOid4vpVerifier().commands.verifyHolderBinding.execute(
                VerifyHolderBindingArgs(
                    presentation = fixture("presentation-jose-bad-credential.txt"),
                    presentationFormat = PresentationFormat.JWT_VP_JSON,
                    expectedNonce = "unused",
                    expectedAudience = "unused",
                    trustedAuthentications = listOf(holderAuthentication("vm-ed25519.json")),
                ),
            )

        assertTrue(result.isErr, "an invalid enveloped credential must fail the production VP boundary")
        assertTrue(result.error.message.defaultMessage.contains("profile", ignoreCase = true))
    }

    @Test
    fun `official unsecured credential and presentation classify as structural VCDM documents`() {
        val credential = Json.parseToJsonElement(rawFixture("credential-minimal.json").decodeToString()).jsonObject
        val credentialClassification = VcdmClassifier.classifyDocument(credential).getOrThrow()
        assertEquals(VcdmDocumentKind.CREDENTIAL, credentialClassification.kind)
        assertTrue(VcdmProfiles.v2_0.validateCredential(credentialClassification.json).valid)

        val presentation = Json.parseToJsonElement(rawFixture("presentation-single.json").decodeToString()).jsonObject
        val presentationClassification = VcdmClassifier.classifyDocument(presentation).getOrThrow()
        assertEquals(VcdmDocumentKind.PRESENTATION, presentationClassification.kind)
        assertTrue(VcdmProfiles.v2_0.validatePresentation(presentationClassification.json).valid)
    }

    @Test
    fun `vendored official vectors match raw byte checksums`() {
        rawFixture("SHA256SUMS").decodeToString().lineSequence().filter(String::isNotBlank).forEach { line ->
            val fields = line.trim().split(Regex("\\s+"), limit = 2)
            assertEquals(2, fields.size)
            assertEquals(fields[0].lowercase(), sha256(rawFixture(fields[1])))
        }
    }

    private suspend fun verify(vector: String, verificationMethod: String) =
        jwtService.verifyJws(
            VerifyJwsArgs(
                jws = JwsCompact(fixture(vector)),
                trustedJwks = trustedJwks(verificationMethod),
            ),
        ).getOrThrow()

    private fun sessionOid4vpVerifier() =
        (session.graph as Oid4VpVerifierServiceImpl.Graph).oid4vpVerifierService

    private fun holderAuthentication(verificationMethod: String): TrustedAuthenticationResolution =
        TrustedAuthenticationResolution(
            controller = "https://example.issuer/vc-jose-cose",
            trustedJwks = trustedJwks(verificationMethod),
        )

    private fun trustedJwks(verificationMethod: String): JsonObject =
        buildJsonObject {
            put("keys", JsonArray(listOf(publicJwk(verificationMethod))))
        }

    private fun publicJwk(verificationMethod: String): JsonObject =
        Json.parseToJsonElement(fixture(verificationMethod)).jsonObject["publicKeyJwk"]!!.jsonObject

    private fun fixture(name: String): String =
        rawFixture(name).decodeToString().trim()

    private fun withNumericDates(name: String): String {
        val parts = fixture(name).split('.')
        val payload = decodeJsonSegment(parts[1])
        val rewritten = buildJsonObject {
            payload.forEach { (key, value) -> put(key, value) }
            put("iat", 1700000000)
            put("exp", 1893456000)
        }
        return "${parts[0]}.${encodeJsonSegment(rewritten)}.AA"
    }

    private fun generatedUnknownExtensionJws(): String {
        val sourceParts = fixture("credential-jose-unknown-extensions.txt").split('.')
        val base = Json.parseToJsonElement(rawFixture("credential-unknown-extensions.json").decodeToString()).jsonObject
        val payload = buildJsonObject {
            base.forEach { (key, value) -> put(key, value) }
            put("iss", "https://example.issuer/vc-jose-cose")
            put("jti", "http://university.example/credentials/1872")
            put("iat", 1700000000)
            put("exp", 1893456000)
        }
        return "${sourceParts[0]}.${encodeJsonSegment(payload)}.AA"
    }

    private fun decodeJsonSegment(segment: String): JsonObject =
        Json.parseToJsonElement(
            Base64.getUrlDecoder().decode(segment).decodeToString(),
        ).jsonObject

    private fun encodeJsonSegment(value: JsonObject): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toString().encodeToByteArray())

    private fun rawFixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/w3c-vc-jose-cose-vcdm2/$name")) {
            "missing pinned W3C VC-JOSE-COSE fixture: $name"
        }.use { it.readBytes() }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
}
