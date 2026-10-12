package com.sphereon.crypto.core.x509

import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import dev.whyoleg.cryptography.CryptographyProvider
import com.sphereon.crypto.core.testutil.createCryptoTestAppGraph
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.crypto.jose.jws.command.verifyResolvedPublicJwkSignature
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.interop.getPublicKeyJwk
import com.sphereon.crypto.core.jose.JwaAlgorithm
import com.sphereon.crypto.resolution.extern.ExternalIdentifierX5cOpts
import kotlinx.coroutines.test.runTest
import java.util.Base64
import kotlin.test.*

/** Public, independently generated certificates exercise actual JVM PKIX and production JWS verification. */
class ExplicitCertificatePinTest {
    private val at = LocalDateTimeKMP(year = 2027, month = 1, day = 1, hour = 0, minute = 0)
    private fun fixture(name: String) = checkNotNull(javaClass.getResourceAsStream("/explicit-certificate-pin/$name")).bufferedReader().use { it.readText() }.trim()
    private fun x5c(name: String) = certificateChainToX5c(Certificate.chainFromPem(fixture("$name.pem"))).toList()
    private suspend fun verify(leaf: String = "leaf", anchors: Array<String>? = arrayOf(fixture("leaf.pem")), year: Int = 2027, chain: Array<String> = arrayOf(fixture("$leaf.pem"))) =
        X509VerifyServiceImpl().setPlatform(X509VerifyServiceJvmAdapter()).let { service ->
            (service as X509VerifyServiceImpl).verifyCertificateChain(X509VerificationRequest(
                enabled = true, chainPEM = chain, trustedCerts = anchors,
                verificationTime = LocalDateTimeKMP(year = year, month = 1, day = 1, hour = 0, minute = 0),
            ))
        }

    @Test fun explicitNonSelfSignedLeafPinPasses() = runTest {
        val result = verify()
        assertFalse(result.error, result.message)
        assertFalse(result.critical)
        assertNotNull(result.publicKey)
    }
    @Test fun samePublicKeyDifferentCertificateDoesNotPin() = runTest {
        assertTrue(verify(anchors = arrayOf(fixture("same-key-other-cert.pem"))).error)
    }
    @Test fun missingAnchorFails() = runTest { assertTrue(verify(anchors = null).error) }
    @Test fun expiredPinFails() = runTest { assertTrue(verify(year = 2037).error) }
    @Test fun notYetValidPinFails() = runTest { assertTrue(verify(year = 2025).error) }
    @Test fun pinCannotAuthorizeEncryptionOnlyKey() = runTest {
        assertTrue(verify("no-signature", arrayOf(fixture("no-signature.pem"))).error)
    }
    @Test fun certificateSigningOnlyCaPinCannotAuthorizeJwsSigning() = runTest {
        assertTrue(verify("ca-sign-only", arrayOf(fixture("ca-sign-only.pem"))).error)
    }
    @Test fun pinCannotIgnoreCriticalExtension() = runTest {
        assertTrue(verify("unknown-critical", arrayOf(fixture("unknown-critical.pem"))).error)
    }
    @Test fun ordinaryRootChainStillPasses() = runTest {
        assertFalse(verify(anchors = arrayOf(fixture("root.pem"))).error)
    }
    @Test fun pinForNonLeafChainElementDoesNotAuthorizeLeaf() = runTest {
        assertTrue(verify(leaf = "same-key-other-cert", chain = arrayOf(fixture("same-key-other-cert.pem"), fixture("leaf.pem"))).error)
    }

    @Test fun productionRsaX5cAndJwsRequirePinAndValidSignature() = runTest { assertProductionSignature("leaf", "signed.jwt") }
    @Test fun productionEcX5cAndJwsRequirePinAndValidSignature() = runTest { assertProductionSignature("ec-leaf", "ec-signed.jwt") }
    @Test fun productionPs256X5cAndJwsRequirePinAndValidSignature() = runTest { assertProductionSignature("rsa-unconstrained", "rsa-ps256.jwt") }
    @Test fun productionRs256X5cAndJwsRequirePinAndValidSignature() = runTest { assertProductionSignature("rsa-unconstrained", "rsa-rs256.jwt") }
    @Test fun rsaCertificateRetainsOnlyExplicitAlgorithmConstraints() = runTest {
        val certificate = Certificate.fromPem(fixture("rsa-unconstrained.pem"))
        assertNull(certificate.getPublicKeyJwk().alg)
        val constrained = certificate.getPublicKeyJwk(alg = JwaAlgorithm.PS256)
        assertEquals(JwaAlgorithm.PS256, constrained.alg)
        val parts = fixture("rsa-rs256.jwt").split('.')
        assertFailsWith<IllegalArgumentException> {
            verifyResolvedPublicJwkSignature(
                ResolvedKeyInfo.fromKey(constrained), "RS256",
                (parts[0] + "." + parts[1]).encodeToByteArray(), Base64.getUrlDecoder().decode(parts[2]),
            )
        }
    }

    private suspend fun assertProductionSignature(leafName: String, tokenName: String) {
        val app = createCryptoTestAppGraph(this)
        val session = app.userContextManager.getAnonymous().sessionContextManager.createOrGetFromId(
            "explicit-certificate-pin-jws", principalType = com.sphereon.di.context.PrincipalType.USER,
        )
        val provider = (app as SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider.create(
            SoftwareKmsProviderConfig(id = "explicit-certificate-pin-provider", cryptographyProvider = CryptographyProvider.Default.name),
            session.asCoreApiServiceGraph().serviceExecution,
        )
        session.graph.asKeyManagerServiceGraph().keyManagerService.registerProvider(provider, makeDefaultKms = true)
        val jwt = (session.graph as JwtServiceImpl.Graph).jwtService
        val signed = fixture(tokenName)
        suspend fun check(value: String, verify: Boolean, anchors: List<String>) = jwt.verifyJws(VerifyJwsArgs(
            jws = JwsCompact(value), identifier = ExternalIdentifierX5cOpts(
                x5c(leafName), verify = verify, trustAnchors = anchors, verificationTime = at.toString(),
            ),
        )).also { assertTrue(it.isOk, "Production verifier must return its validation result") }.value
        val accepted = check(signed, true, x5c(leafName))
        assertTrue(accepted.trustEstablished)
        assertEquals(true, accepted.cryptoVerified, accepted.errorMessages.take(8).joinToString("; ").take(2048))
        val parts = signed.split('.').toMutableList()
        val signature = Base64.getUrlDecoder().decode(parts[2])
        signature[0] = (signature[0].toInt() xor 1).toByte()
        parts[2] = Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
        val tampered = check(parts.joinToString("."), true, x5c(leafName))
        assertEquals(false, tampered.cryptoVerified)
        assertFalse(tampered.isValid)
        assertFalse(check(signed, false, x5c(leafName)).trustEstablished)
        assertFalse(check(signed, true, x5c("same-key-other-cert")).trustEstablished)
    }
}
