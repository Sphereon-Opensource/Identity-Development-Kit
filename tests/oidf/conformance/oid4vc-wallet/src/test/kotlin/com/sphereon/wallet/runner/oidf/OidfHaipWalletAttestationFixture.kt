/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.wallet.runner.oidf

import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.jose.JwaCurve
import com.sphereon.wallet.runner.HeadlessWalletRunnerBootstrap
import com.sphereon.wallet.unit.SecureComponentUsage
import com.sphereon.wallet.unit.WalletProviderAttestationSignerRef
import kotlinx.serialization.json.Json
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.util.Base64
import java.util.Date

/**
 * HAIP wallet-attestation material owned by the SUT harness. The suite plan trusts this fixture CA,
 * while each ephemeral real wallet WSCA gets its own CA-signed leaf for the non-extractable signer
 * key it actually uses. This configures the external test trust input; it does not alter suite test
 * behavior or bypass wallet-provider signing.
 */
internal class OidfHaipWalletAttestationFixture {
    private val caKeyPair: KeyPair = generateRsaKeyPair()
    private val caCertificate: X509Certificate = createCaCertificate(caKeyPair)

    val trustAnchorPem: String = caCertificate.toPem()

    suspend fun provision(
        bootstrap: HeadlessWalletRunnerBootstrap,
        walletUnitId: String,
        profileId: String,
    ) {
        val signerKey =
            bootstrap
                .wsca(profileId)
                .ensureKey(
                    walletUnitId = walletUnitId,
                    usage = SecureComponentUsage.WALLET_ATTESTATION,
                    algorithm = SignatureAlgorithm.ECDSA_SHA256,
                    keyAlias = SIGNER_ALIAS,
                ).getOrElse { error("HAIP wallet-attestation signer provisioning failed: ${it.code}") }
        val publicJwk =
            signerKey.publicKeyJwk
                ?.let { Json.decodeFromString(Jwk.serializer(), it) }
                ?: error("HAIP wallet-attestation signer does not expose its public JWK")
        val leaf = createLeafCertificate(ecPublicKey(publicJwk))
        bootstrap.setHaipAttestationSigner(
            signer =
                WalletProviderAttestationSignerRef(
                    signerId = SIGNER_ALIAS,
                    issuer = ATTESTER_ISSUER,
                    keyId = signerKey.keyRef ?: signerKey.keyId,
                    signingAlgorithm = "ES256",
                    signerProfile = "LOCAL_WSCD",
                    certificateChain =
                        listOf(
                            Base64.getEncoder().encodeToString(leaf.encoded),
                        ),
                ),
            profileId = profileId,
        )
    }

    private fun createLeafCertificate(publicKey: PublicKey): X509Certificate {
        val now = Date(System.currentTimeMillis() - CLOCK_SKEW_MS)
        val expiry = Date(now.time + VALIDITY_MS)
        val issuer = X500Name(caCertificate.subjectX500Principal.name)
        val subject = X500Name("CN=VDX OIDF HAIP Wallet Attester")
        val builder =
            JcaX509v3CertificateBuilder(
                issuer,
                BigInteger(96, SecureRandom()),
                now,
                expiry,
                subject,
                publicKey,
            )
        val extensions = JcaX509ExtensionUtils()
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        builder.addExtension(Extension.authorityKeyIdentifier, false, extensions.createAuthorityKeyIdentifier(caCertificate))
        builder.addExtension(Extension.subjectKeyIdentifier, false, extensions.createSubjectKeyIdentifier(publicKey))
        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider(BC).build(caKeyPair.private)
        return JcaX509CertificateConverter().setProvider(BC).getCertificate(builder.build(signer))
    }

    private companion object {
        const val BC = "BC"
        const val SIGNER_ALIAS = "oidf-haip-wallet-provider-signing-x5c"
        const val ATTESTER_ISSUER = "https://client-attester.example.org/"
        const val CLOCK_SKEW_MS = 60_000L
        const val VALIDITY_MS = 24L * 60L * 60L * 1_000L

        init {
            if (Security.getProvider(BC) == null) Security.addProvider(BouncyCastleProvider())
        }

        fun generateRsaKeyPair(): KeyPair =
            KeyPairGenerator
                .getInstance("RSA", BC)
                .apply { initialize(2048, SecureRandom()) }
                .generateKeyPair()

        fun createCaCertificate(keyPair: KeyPair): X509Certificate {
            val now = Date(System.currentTimeMillis() - CLOCK_SKEW_MS)
            val expiry = Date(now.time + VALIDITY_MS)
            val subject = X500Name("CN=VDX OIDF HAIP Wallet Attestation CA")
            val builder =
                JcaX509v3CertificateBuilder(
                    subject,
                    BigInteger(96, SecureRandom()),
                    now,
                    expiry,
                    subject,
                    keyPair.public,
                )
            val extensions = JcaX509ExtensionUtils()
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
            builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
            builder.addExtension(Extension.subjectKeyIdentifier, false, extensions.createSubjectKeyIdentifier(keyPair.public))
            val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider(BC).build(keyPair.private)
            return JcaX509CertificateConverter().setProvider(BC).getCertificate(builder.build(signer))
        }

        fun ecPublicKey(jwk: Jwk): PublicKey {
            require(jwk.crv == JwaCurve.P_256) { "HAIP attester must use P-256, got ${jwk.crv}" }
            val parameters = AlgorithmParameters.getInstance("EC")
            parameters.init(ECGenParameterSpec("secp256r1"))
            val spec = parameters.getParameterSpec(java.security.spec.ECParameterSpec::class.java)
            val point =
                ECPoint(
                    BigInteger(1, Base64.getUrlDecoder().decode(requireNotNull(jwk.x))),
                    BigInteger(1, Base64.getUrlDecoder().decode(requireNotNull(jwk.y))),
                )
            return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, spec))
        }

        fun X509Certificate.toPem(): String {
            val encoded = Base64.getMimeEncoder(64, "\n".encodeToByteArray()).encodeToString(this.encoded)
            return "-----BEGIN CERTIFICATE-----\n$encoded\n-----END CERTIFICATE-----\n"
        }
    }
}
