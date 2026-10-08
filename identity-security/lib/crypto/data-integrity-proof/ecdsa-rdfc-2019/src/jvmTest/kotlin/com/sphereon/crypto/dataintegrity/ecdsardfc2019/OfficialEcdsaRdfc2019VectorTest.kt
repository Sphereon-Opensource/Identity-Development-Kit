/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.crypto.dataintegrity.ecdsardfc2019

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.decodeFromBase64Url
import com.sphereon.core.api.decodeFromHex
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.Multibase
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.crypto.core.jose.JwaKeyType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.sign.SimpleSignatureService
import com.sphereon.crypto.dataintegrity.model.DataIntegrityProof
import com.sphereon.crypto.dataintegrity.model.ProofPurpose
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolution
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolutionPolicy
import com.sphereon.crypto.dataintegrity.resolution.VerificationMethodResolver
import com.sphereon.jsonld.LinkedDataDocument
import com.sphereon.jsonld.loader.LinkedDataDocumentLoader
import com.sphereon.jsonld.processor.JsonLdProcessor
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.ECFieldFp
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Production RDFC and verifier checks against W3C Recommendation Appendix A.1/A.3. */
class OfficialEcdsaRdfc2019VectorTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun pinnedResourcesMatchSha256ManifestUsingRawBytes() {
        assertManifestMatchesResources()
    }

    @Test
    fun p256RecommendationVectorMatchesHashesAndSignature() = runTest { verifyVector("p256", JwaCurve.P_256) }

    @Test
    fun p384RecommendationVectorMatchesHashesAndSignature() = runTest { verifyVector("p384", JwaCurve.P_384) }

    private suspend fun verifyVector(name: String, curve: JwaCurve) {
        val vector = loadVector()[name]!!.jsonObject
        val document = vector["unsecuredDocument"]!!.jsonObject
        val proof = json.decodeFromJsonElement<DataIntegrityProof>(vector["proof"]!!)
        val processor = JsonLdProcessor(offlineContextLoader)
        val canonicalDocumentHash = com.sphereon.crypto.core.generic.hash(
            EcdsaRdfc2019Cryptosuite.canonicalDocumentBytes(processor, document, curve),
            EcdsaRdfc2019Cryptosuite.digestForCurve(curve),
        )
        val canonicalProofHash = com.sphereon.crypto.core.generic.hash(
            EcdsaRdfc2019Cryptosuite.canonicalProofConfigBytes(processor, document, proof, curve),
            EcdsaRdfc2019Cryptosuite.digestForCurve(curve),
        )
        if (name == "p256") {
            assertEquals(
                "<did:example:abcdefgh> <https://www.w3.org/ns/credentials/examples#alumniOf> \"The School of Examples\" .\n" +
                    "<urn:uuid:58172aac-d8ba-11ed-83dd-0b3aef56cc33> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/2018/credentials#VerifiableCredential> .\n" +
                    "<urn:uuid:58172aac-d8ba-11ed-83dd-0b3aef56cc33> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <https://www.w3.org/ns/credentials/examples#AlumniCredential> .\n" +
                    "<urn:uuid:58172aac-d8ba-11ed-83dd-0b3aef56cc33> <https://schema.org/description> \"A minimum viable example of an Alumni Credential.\" .\n" +
                    "<urn:uuid:58172aac-d8ba-11ed-83dd-0b3aef56cc33> <https://schema.org/name> \"Alumni Credential\" .\n" +
                    "<urn:uuid:58172aac-d8ba-11ed-83dd-0b3aef56cc33> <https://www.w3.org/2018/credentials#credentialSubject> <did:example:abcdefgh> .\n" +
                    "<urn:uuid:58172aac-d8ba-11ed-83dd-0b3aef56cc33> <https://www.w3.org/2018/credentials#issuer> <https://vc.example/issuers/5678> .\n" +
                    "<urn:uuid:58172aac-d8ba-11ed-83dd-0b3aef56cc33> <https://www.w3.org/2018/credentials#validFrom> \"2023-01-01T00:00:00Z\"^^<http://www.w3.org/2001/XMLSchema#dateTime> .\n",
                EcdsaRdfc2019Cryptosuite.canonicalDocumentBytes(processor, document, curve).decodeToString(),
            )
        }
        assertContentEquals(vector["documentHashHex"]!!.toString().trim('"').decodeFromHex(), canonicalDocumentHash)
        assertContentEquals(vector["proofConfigHashHex"]!!.toString().trim('"').decodeFromHex(), canonicalProofHash)
        assertContentEquals(vector["hashDataHex"]!!.toString().trim('"').decodeFromHex(), EcdsaRdfc2019Cryptosuite.hashData(processor, document, proof.copy(proofValue = ""), curve))
        assertContentEquals(vector["signatureHex"]!!.toString().trim('"').decodeFromHex(), EcdsaRdfc2019Cryptosuite.decodeProofValue(proof.proofValue, curve))

        val jwk = publicJwk(vector["publicKeyMultibase"].toString().trim('"'), curve)
        val result = EcdsaRdfc2019Verifier(
            verificationMethodResolver = StaticResolver(proof.verificationMethod, jwk),
            signatureService = JcaEcdsaSignatureService,
            linkedDataDocumentLoader = offlineContextLoader,
        ).verifyProof(document, proof, VerificationMethodResolutionPolicy.empty())
        assertTrue(result.value.verified, "W3C $name proof must verify: ${result.value.errors}")
        assertEquals(document, result.value.verifiedDocument)
    }

    private fun loadVector(): JsonObject = OfficialEcdsaRdfc2019VectorTest::class.java
        .getResourceAsStream("/official-vc-di-ecdsa/vectors.json")!!
        .bufferedReader().use { json.parseToJsonElement(it.readText()).jsonObject }

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
        checkNotNull(javaClass.getResourceAsStream("/official-vc-di-ecdsa/$name")) {
            "missing pinned official resource: $name"
        }.use { it.readBytes() }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun publicJwk(multibase: String, curve: JwaCurve): Jwk {
        val encoded = Multibase.decode(multibase)
        val prefixLength = 2
        require(encoded[0] == (if (curve == JwaCurve.P_256) 0x80 else 0x81).toByte() && encoded[1] == 0x24.toByte())
        val point = encoded.copyOfRange(prefixLength, encoded.size)
        val coordinateLength = if (curve == JwaCurve.P_256) 32 else 48
        require(point.size == coordinateLength + 1)
        val params = ecParameters(curve)
        val field = params.curve.field as ECFieldFp
        val x = BigInteger(1, point.copyOfRange(1, point.size))
        val rhs = x.modPow(BigInteger.valueOf(3), field.p).add(params.curve.a.multiply(x)).add(params.curve.b).mod(field.p)
        var y = rhs.modPow(field.p.add(BigInteger.ONE).shiftRight(2), field.p)
        if (y.testBit(0) != ((point[0].toInt() and 1) == 1)) y = field.p.subtract(y)
        val expectedPoint = ECPoint(x, y)
        KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(expectedPoint, params))
        return Jwk(
            kty = JwaKeyType.EC,
            crv = curve,
            x = toBase64Url(x.toFixedBytes(coordinateLength)),
            y = toBase64Url(y.toFixedBytes(coordinateLength)),
        )
    }

    private fun ecParameters(curve: JwaCurve): java.security.spec.ECParameterSpec {
        val parameters = AlgorithmParameters.getInstance("EC")
        parameters.init(ECGenParameterSpec(if (curve == JwaCurve.P_256) "secp256r1" else "secp384r1"))
        return parameters.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
    }

    private fun toBase64Url(bytes: ByteArray): String = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private val offlineContextLoader = LinkedDataDocumentLoader { iri ->
        when (iri) {
            CREDENTIALS_V2 -> Ok(LinkedDataDocument(iri, credentialsContext))
            EXAMPLES_V2 -> Ok(LinkedDataDocument(iri, examplesContext))
            else -> error("unexpected remote context in pinned vector: $iri")
        }
    }

    private val credentialsContext = json.parseToJsonElement(
        """{"@context":{"@version":"1.1","id":"@id","type":"@type","VerifiableCredential":"https://www.w3.org/2018/credentials#VerifiableCredential","credentialSubject":{"@id":"https://www.w3.org/2018/credentials#credentialSubject","@type":"@id"},"issuer":{"@id":"https://www.w3.org/2018/credentials#issuer","@type":"@id"},"validFrom":{"@id":"https://www.w3.org/2018/credentials#validFrom","@type":"http://www.w3.org/2001/XMLSchema#dateTime"},"name":"https://schema.org/name","description":"https://schema.org/description","DataIntegrityProof":"https://w3id.org/security#DataIntegrityProof","cryptosuite":{"@id":"https://w3id.org/security#cryptosuite","@type":"https://w3id.org/security#cryptosuiteString"},"created":{"@id":"http://purl.org/dc/terms/created","@type":"http://www.w3.org/2001/XMLSchema#dateTime"},"proofPurpose":{"@id":"https://w3id.org/security#proofPurpose","@type":"@vocab"},"assertionMethod":{"@id":"https://w3id.org/security#assertionMethod","@type":"@id"},"verificationMethod":{"@id":"https://w3id.org/security#verificationMethod","@type":"@id"}}}""",
    )
    private val examplesContext = json.parseToJsonElement(
        """{"@context":{"@version":"1.1","AlumniCredential":"https://www.w3.org/ns/credentials/examples#AlumniCredential","alumniOf":"https://www.w3.org/ns/credentials/examples#alumniOf"}}""",
    )

    private companion object {
        const val CREDENTIALS_V2 = "https://www.w3.org/ns/credentials/v2"
        const val EXAMPLES_V2 = "https://www.w3.org/ns/credentials/examples/v2"
    }
}

private fun BigInteger.toFixedBytes(size: Int): ByteArray {
    val source = toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
    require(source.size <= size)
    return ByteArray(size - source.size) + source
}

private class StaticResolver(private val reference: String, private val key: Jwk) : VerificationMethodResolver {
    override suspend fun resolve(reference: String): IdkResult<VerificationMethodResolution, com.sphereon.core.api.error.IdkErrorType> {
        check(reference == this.reference)
        return Ok(VerificationMethodResolution(reference, key, "did:key:controller", setOf(ProofPurpose.ASSERTION_METHOD))).asResult()
    }
}

private object JcaEcdsaSignatureService : SimpleSignatureService {
    override suspend fun createRawSignature(keyInfo: KeyInfoType<*>, input: ByteArray, requireX5Chain: Boolean): ByteArray = error("not used")

    override suspend fun isValidRawSignature(keyInfo: KeyInfoType<*>, input: ByteArray, signature: ByteArray): Boolean {
        val key = keyInfo.key as? JwkType ?: return false
        val curve = key.crv ?: return false
        val coordinateLength = if (curve == JwaCurve.P_256) 32 else if (curve == JwaCurve.P_384) 48 else return false
        val params = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec(if (curve == JwaCurve.P_256) "secp256r1" else "secp384r1")) }.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(BigInteger(1, key.x!!.decodeFromBase64Url()), BigInteger(1, key.y!!.decodeFromBase64Url())), params))
        val der = rawToDer(signature, coordinateLength)
        return Signature.getInstance(if (curve == JwaCurve.P_256) "SHA256withECDSA" else "SHA384withECDSA").run { initVerify(publicKey); update(input); verify(der) }
    }

    private fun rawToDer(raw: ByteArray, size: Int): ByteArray {
        require(raw.size == size * 2)
        fun integer(value: ByteArray): ByteArray {
            var firstNonZero = 0
            while (firstNonZero < value.size - 1 && value[firstNonZero] == 0.toByte()) {
                firstNonZero++
            }
            val stripped = value.copyOfRange(firstNonZero, value.size)
            return if (stripped[0].toInt() and 0x80 != 0) byteArrayOf(0) + stripped else stripped
        }
        val r = integer(raw.copyOfRange(0, size)); val s = integer(raw.copyOfRange(size, size * 2))
        val body = byteArrayOf(0x02, r.size.toByte()) + r + byteArrayOf(0x02, s.size.toByte()) + s
        return byteArrayOf(0x30, body.size.toByte()) + body
    }
}
