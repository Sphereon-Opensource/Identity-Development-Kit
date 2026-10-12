package com.sphereon.trust.etsi.signature.jades

import com.sphereon.trust.core.TrustDiagnosticReasonCodes
import com.sphereon.trust.etsi.testutil.EtsiTestContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PSSParameterSpec
import java.util.Base64
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Real signatures use a separately issued RSA-3072 leaf pinned explicitly by the caller. */
class JAdESProtectedAlgorithmTest {
    private val validator = EtsiTestContext("jades-protected-algorithm", this).jadesValidator

    @Test
    fun verifiesRs256WithRsa3072PinnedLeaf() = runTest {
        val bytes = signed("RS256")
        val result = validator.validate(bytes, options = pinnedOptions())
        assertTrue(result.signatureValid, result.errors.toString())
        assertTrue(result.valid, result.errors.toString())
        assertContentEquals(certificate.encoded, result.signingCertificate)
        assertContentEquals(bytes, result.serializedData)
    }

    @Test
    fun verifiesPs256WithTheSameRsa3072PinnedLeaf() = runTest {
        val result = validator.validate(signed("PS256"), options = pinnedOptions())
        assertTrue(result.signatureValid, result.errors.toString())
        assertTrue(result.valid, result.errors.toString())
    }

    @Test
    fun rejectsPssSignatureMislabeledAsRs256() = runTest {
        val result = validator.validate(signed("RS256", "PS256"), options = pinnedOptions())
        assertFalse(result.signatureValid)
        assertFalse(result.valid)
    }

    @Test
    fun rejectsPkcs1SignatureMislabeledAsPs256() = runTest {
        val result = validator.validate(signed("PS256", "RS256"), options = pinnedOptions())
        assertFalse(result.signatureValid)
        assertFalse(result.valid)
    }

    @Test
    fun rejectsEcAlgorithmWithRsaCertificate() = runTest {
        val result = validator.validate(signed("ES256", "RS256"), options = pinnedOptions())
        assertFalse(result.signatureValid)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("certificate key type") }, result.errors.toString())
    }

    @Test
    fun rejectsMissingUnsupportedOrSymmetricProtectedAlgorithm() = runTest {
        for (algorithm in listOf(null, "none", "HS256", "RSA-OAEP", "unregistered")) {
            val result = validator.validate(signed(algorithm, "RS256"), options = pinnedOptions())
            assertFalse(result.signatureValid, "algorithm=$algorithm")
            assertFalse(result.valid, "algorithm=$algorithm")
        }
    }

    @Test
    fun cryptographicSuccessStillRequiresConfiguredSignerTrust() = runTest {
        val result = validator.validate(signed("RS256"), options = JAdESValidationOptions(requireEtsiHeaders = true))
        assertTrue(result.signatureValid, result.errors.toString())
        assertFalse(result.valid)
        assertTrue(TrustDiagnosticReasonCodes.SIGNER_ROOT_NOT_CONFIGURED in result.reasonCodes)
    }

    private fun pinnedOptions() = JAdESValidationOptions(
        requireEtsiHeaders = true,
        validateSigningCertificate = true,
        validateCertificateChain = true,
        trustedCertificates = listOf(certificate.encoded),
    )

    private fun signed(protectedAlgorithm: String?, signingAlgorithm: String = requireNotNull(protectedAlgorithm)): ByteArray {
        val headers = linkedMapOf(
            "iat" to JsonPrimitive(System.currentTimeMillis() / 1000),
            "x5c" to JsonArray(listOf(JsonPrimitive(Base64.getEncoder().encodeToString(certificate.encoded)))),
        )
        if (protectedAlgorithm != null) headers["alg"] = JsonPrimitive(protectedAlgorithm)
        val input = "${encode(JsonObject(headers).toString().toByteArray())}.${encode("{\"test\":true}".toByteArray())}"
        val signer = Signature.getInstance(if (signingAlgorithm == "PS256") "RSASSA-PSS" else "SHA256withRSA")
        if (signingAlgorithm == "PS256") signer.setParameter(PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1))
        signer.initSign(keyPair.private)
        signer.update(input.toByteArray())
        return "$input.${encode(signer.sign())}".toByteArray()
    }

    private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private companion object {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(3072) }.generateKeyPair()
        val certificate = run {
            val issuer = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
            val now = System.currentTimeMillis()
            val builder = JcaX509v3CertificateBuilder(
                X500Name("CN=JAdES test issuer"), BigInteger.ONE,
                Date(now - 60_000), Date(now + 86_400_000),
                X500Name("C=NL,O=Test signer,CN=JAdES leaf"), keyPair.public,
            ).addExtension(Extension.basicConstraints, true, BasicConstraints(false))
            JcaX509CertificateConverter().getCertificate(builder.build(JcaContentSignerBuilder("SHA256withRSA").build(issuer.private)))
        }
    }
}
