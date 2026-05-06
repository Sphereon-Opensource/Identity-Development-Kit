/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.oauth2.oidf.op

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Self-contained certificate generator for the OIDF mTLS conformance fixture. Mirrors the
 * Bouncy-Castle helper at `lib/data/link/http/client/impl/src/jvmTest` but is replicated here
 * so the OIDF harness module stays IDK-pure (no test-classpath dependency on a sibling library
 * test source set, which would force `checkIdkPurity` to follow that classpath).
 *
 * RSA-2048 throughout because the OIDF AS already advertises RS256 for ID token signing, so
 * the same key type doubles for `self_signed_tls_client_auth` JWK publication. The CA, server,
 * and per-client certs all chain to a single in-memory CA generated when the fixture starts.
 */
internal object OidfOpMtlsCerts {
    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    fun generateRsaKeyPair(keySize: Int = 2048): KeyPair =
        KeyPairGenerator
            .getInstance("RSA", "BC")
            .apply { initialize(keySize, SecureRandom()) }
            .generateKeyPair()

    fun createSelfSignedCa(
        subjectDn: String,
        keyPair: KeyPair,
    ): X509Certificate {
        val now = Date()
        val expiry = Date(now.time + 365L * 24L * 3600L * 1000L)
        val subject = X500Name(subjectDn)
        val serial = BigInteger(64, SecureRandom())
        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(keyPair.private)
        val builder = JcaX509v3CertificateBuilder(subject, serial, now, expiry, subject, keyPair.public)

        val ext = JcaX509ExtensionUtils()
        builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        builder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign))
        builder.addExtension(Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(keyPair.public))

        return JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
    }

    fun createSignedCertificate(
        subjectDn: String,
        subjectPublicKey: PublicKey,
        issuerCert: X509Certificate,
        issuerPrivateKey: PrivateKey,
        addLocalhostSans: Boolean,
    ): X509Certificate {
        val now = Date()
        val expiry = Date(now.time + 365L * 24L * 3600L * 1000L)
        val subject = X500Name(subjectDn)
        val issuer = X500Name(issuerCert.subjectX500Principal.name)
        val serial = BigInteger(64, SecureRandom())
        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(issuerPrivateKey)
        val builder = JcaX509v3CertificateBuilder(issuer, serial, now, expiry, subject, subjectPublicKey)

        val ext = JcaX509ExtensionUtils()
        builder.addExtension(Extension.basicConstraints, false, BasicConstraints(false))
        builder.addExtension(Extension.authorityKeyIdentifier, false, ext.createAuthorityKeyIdentifier(issuerCert.publicKey))
        builder.addExtension(Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(subjectPublicKey))
        builder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment),
        )

        if (addLocalhostSans) {
            val sans =
                GeneralNames(
                    arrayOf(
                        GeneralName(GeneralName.dNSName, "localhost"),
                        GeneralName(GeneralName.iPAddress, "127.0.0.1"),
                    ),
                )
            builder.addExtension(Extension.subjectAlternativeName, false, sans)
        }

        return JcaX509CertificateConverter().setProvider("BC").getCertificate(builder.build(signer))
    }
}
