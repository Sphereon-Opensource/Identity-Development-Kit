/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.crypto.dataintegrity.eddsajcs2022

import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromHex
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.generic.Multibase
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.sign.SimpleSignatureService
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolution
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolutionPolicy
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Verifies the signed W3C VC-DI-EDDSA Appendix B.3 vector with the production verifier. */
class OfficialEddsaJcs2022VectorTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun pinnedResourcesMatchSha256ManifestUsingRawBytes() {
        assertManifestMatchesResources()
    }

    @Test
    fun appendixB3VectorMatchesHashAndVerifiesWithProductionVerifier() = runTest {
        val vector = loadVector()
        val document = vector["unsecuredDocument"]!!.jsonObject
        val proof = json.decodeFromJsonElement<DataIntegrityProof>(vector["proof"]!!)
        val expectedHash = vector["hashDataHex"]!!.toString().trim('"').decodeFromHex()
        val expectedSignature = vector["signatureHex"]!!.toString().trim('"').decodeFromHex()

        assertContentEquals(expectedHash, EddsaJcs2022Cryptosuite.hashData(document, proof.copy(proofValue = "")))
        assertContentEquals(expectedSignature, EddsaJcs2022Cryptosuite.decodeProofValue(proof.proofValue))

        val result = EddsaJcs2022Verifier(
            verificationMethodResolver = StaticResolver(proof.verificationMethod, publicJwk(vector["publicKeyMultibase"]!!.toString().trim('"'))),
            signatureService = JcaEd25519SignatureService,
        ).verifyProof(document, proof, VerificationMethodResolutionPolicy.empty())

        assertTrue(result.value.verified, "official Appendix B.3 proof must verify: ${result.value.errors}")
        assertEquals(document, result.value.verifiedDocument)
    }

    private fun loadVector(): JsonObject =
        OfficialEddsaJcs2022VectorTest::class.java
            .getResourceAsStream("/official-vc-di-eddsa/spec-example-jcs-2022.json")!!
            .bufferedReader()
            .use { json.parseToJsonElement(it.readText()).jsonObject }

    private fun assertManifestMatchesResources() {
        val entries = resourceBytes("SHA256SUMS.txt").decodeToString().lineSequence()
            .filter(String::isNotBlank)
            .toList()
        assertTrue(entries.isNotEmpty(), "SHA256SUMS.txt must contain at least one resource entry")
        entries.forEach { rawLine ->
            val line = rawLine.removeSuffix("\r")
            val separator = line.indexOf("  ")
            require(separator > 0) { "malformed SHA256SUMS.txt entry: $rawLine" }
            val expected = line.substring(0, separator)
            val name = line.substring(separator + 2)
            require(expected.length == 64 && expected.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                "malformed SHA256SUMS.txt digest for $name"
            }
            val actual = MessageDigest.getInstance("SHA-256").digest(resourceBytes(name)).toHex()
            assertEquals(expected.lowercase(), actual, "pinned official resource hash mismatch: $name")
        }
    }

    private fun resourceBytes(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/official-vc-di-eddsa/$name")) {
            "missing pinned official resource: $name"
        }.use { it.readBytes() }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun publicJwk(multibase: String): Jwk {
        val encoded = Multibase.decode(multibase)
        require(encoded.size == 34 && encoded[0] == 0xed.toByte() && encoded[1] == 0x01.toByte())
        return Jwk(kty = JwaKeyType.OKP, crv = JwaCurve.Ed25519, x = encoded.copyOfRange(2, 34).encodeToBase64Url())
    }
}

private class StaticResolver(
    private val reference: String,
    private val key: Jwk,
) : VerificationMethodResolver {
    override suspend fun resolve(reference: String): com.sphereon.core.api.IdkResult<VerificationMethodResolution, IdkErrorType> {
        check(reference == this.reference)
        return Ok(
            VerificationMethodResolution(
                reference = reference,
                key = key,
                controller = "did:key:controller",
                authorizedProofPurposes = setOf(com.sphereon.crypto.dataintegrity.model.ProofPurpose.ASSERTION_METHOD),
            ),
        ).asResult()
    }
}

/** JDK Ed25519 is used only as the test transport for the production verifier seam. */
private object JcaEd25519SignatureService : SimpleSignatureService {
    override suspend fun createRawSignature(keyInfo: KeyInfoType<*>, input: ByteArray, requireX5Chain: Boolean): ByteArray =
        error("verification vector test does not sign")

    override suspend fun isValidRawSignature(keyInfo: KeyInfoType<*>, input: ByteArray, signature: ByteArray): Boolean {
        val jwk = keyInfo.key as? JwkType ?: return false
        val rawPublic = jwk.x?.decodeFromBase64Url() ?: return false
        val spki = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00) + rawPublic
        val publicKey = KeyFactory.getInstance("Ed25519").generatePublic(X509EncodedKeySpec(spki))
        return Signature.getInstance("Ed25519").run {
            initVerify(publicKey)
            update(input)
            verify(signature)
        }
    }

}
