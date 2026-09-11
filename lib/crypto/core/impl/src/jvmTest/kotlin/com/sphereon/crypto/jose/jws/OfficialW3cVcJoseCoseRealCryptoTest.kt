/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.crypto.jose.jws

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.testutil.createCryptoTestAppGraph
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import dev.whyoleg.cryptography.CryptographyProvider
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real-crypto proof for the pinned W3C VC-JOSE-COSE VCDM 2 credential vector.
 *
 * This goes through production [JwtService.verifyJws], with the verifier-admitted public
 * JWKS supplied separately from the compact credential. The vector's `iat` is intentionally
 * left untouched: it is an upstream string claim and remains a known semantic-validation
 * incompatibility for callers that require the JWT NumericDate form. This test proves only
 * the cryptographic and trust result and does not weaken or normalize that semantic rule.
 */
class OfficialW3cVcJoseCoseRealCryptoTest {
    private lateinit var jwtService: JwtService

    private val app = createCryptoTestAppGraph(this)
    private val context = app.userContextManager.getAnonymous()
    private val session =
        context.sessionContextManager.createOrGetFromId(
            "official-w3c-vc-jose-cose-real-crypto-test",
            principalType = com.sphereon.di.context.PrincipalType.USER,
        )

    @BeforeTest
    fun setUp() {
        val config =
            SoftwareKmsProviderConfig(
                id = "official-w3c-vc-jose-cose-real-crypto-test-provider",
                cryptographyProvider = CryptographyProvider.Default.name,
            )
        app as SoftwareKmsProviderFactoryImpl.Graph
        val softwareKmsProvider =
            app.softwareKmsProvider.create(
                config,
                session.asCoreApiServiceGraph().serviceExecution,
            )

        val keyManagerService: KeyManagerService = session.graph.asKeyManagerServiceGraph().keyManagerService
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)
        jwtService = (session.graph as JwtServiceImpl.Graph).jwtService
    }

    @Test
    fun `vendored crypto inputs match pinned logical-content hashes`() {
        val manifest = resourceText("SHA256SUMS")
        manifest.lineSequence().filter(String::isNotBlank).forEach { line ->
            val (expected, name) = line.trim().split(Regex("\\s+"), limit = 2)
            val actual = MessageDigest.getInstance("SHA-256").digest(resourceText(name).trim().encodeToByteArray()).toHex()
            assertEquals(expected, actual, "pinned W3C crypto fixture hash mismatch: $name")
        }
    }

    @Test
    fun `official credential establishes crypto verification and trust through admitted jwks`() =
        runTest {
            val compact = fixture("credential-jose-minimal.txt")
            val trustedJwks = trustedJwks("vm-p256-public-key.json")

            val result =
                jwtService.verifyJws(
                    VerifyJwsArgs(
                        jws = JwsCompact(compact),
                        trustedJwks = trustedJwks,
                    ),
                )

            assertTrue(
                result.isOk,
                "production VerifyJws returned an error: ${if (result.isErr) result.error else ""}",
            )
            val validation = result.getOrNull()
            assertNotNull(validation)
            assertTrue(
                validation.cryptoVerified == true,
                "official W3C signature must be cryptographically verified; errors=${validation.errorMessages}",
            )
            assertTrue(
                validation.trustEstablished,
                "the verifier-admitted public JWKS must establish trust; errors=${validation.errorMessages}",
            )

            // Preserve the upstream semantic input. JWT callers may reject this string `iat`
            // as a NumericDate; this crypto test must not repair or silently reinterpret it.
            assertEquals(
                "2010-01-01T19:23:24Z",
                validation.parsedPayload["iat"]?.jsonPrimitive?.content,
            )
        }

    @Test
    fun `official Ed25519 issuer-match credential verifies with public-only JWK`() =
        runTest {
            val validation = verifyOfficialCredential(
                credential = "credential-issuer-match-signed.txt",
                verificationMethod = "vm-ed25519-public-key.json",
            )

            assertTrue(validation.isValid, "official Ed25519 issuer-match credential must verify: ${validation.errorMessages}")
            assertTrue(validation.trustEstablished, "the admitted Ed25519 JWK must establish trust")
            assertEquals(true, validation.cryptoVerified, "the Ed25519 signature must verify cryptographically")
            assertEquals(
                "https://example.issuer/vc-jose-cose",
                validation.parsedPayload["issuer"]?.jsonPrimitive?.content,
            )
            assertEquals(
                validation.parsedPayload["issuer"]?.jsonPrimitive?.content,
                validation.parsedPayload["iss"]?.jsonPrimitive?.content,
            )
        }

    @Test
    fun `official Ed25519 bad-signature credential is rejected after trust establishment`() =
        runTest {
            val validation = verifyOfficialCredential(
                credential = "credential-jose-bad-signature.txt",
                verificationMethod = "vm-ed25519-public-key.json",
            )

            assertTrue(validation.trustEstablished, "the public Ed25519 JWK should be selected")
            assertEquals(false, validation.cryptoVerified, "the official bad signature must fail cryptographic verification")
            assertTrue(!validation.isValid, "the official bad signature must be rejected")
        }

    private suspend fun verifyOfficialCredential(credential: String, verificationMethod: String): JwsValidationResult {
        val result = jwtService.verifyJws(
            VerifyJwsArgs(
                jws = JwsCompact(fixture(credential)),
                trustedJwks = trustedJwks(verificationMethod),
            ),
        )
        assertTrue(
            result.isOk,
            "verifyJws returned a hard error for $credential: ${if (result.isErr) result.error else ""}",
        )
        return assertNotNull(result.getOrNull(), "verifyJws returned no validation result for $credential")
    }

    private fun publicJwk(name: String): JsonObject {
        val publicJwk = Json.parseToJsonElement(fixture(name)).jsonObject
        assertTrue("d" !in publicJwk, "the verifier fixture must contain public key material only: $name")
        return publicJwk
    }

    private fun trustedJwks(name: String): JsonObject =
        buildJsonObject {
            put("keys", JsonArray(listOf(publicJwk(name))))
        }

    private fun fixture(name: String): String =
        resourceText(name).trim()

    private fun resourceText(name: String): String =
        checkNotNull(javaClass.getResourceAsStream("/w3c-vc-jose-cose-vcdm2/$name")) {
            "missing pinned W3C VC-JOSE-COSE fixture: $name"
        }.bufferedReader().use { it.readText() }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
