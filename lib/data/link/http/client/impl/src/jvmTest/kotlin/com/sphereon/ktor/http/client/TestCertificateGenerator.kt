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
 *
 */

import com.sphereon.crypto.core.x509.Certificate
import com.sphereon.crypto.core.x509.certificateFromDer
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date

object TestCertificateGenerator {
    init {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(
                org.bouncycastle.jce.provider
                    .BouncyCastleProvider(),
            )
        }
    }

    fun generateKeyPair(
        algorithm: String = "EC",
        curveNameOrKeySize: String = "secp256r1",
    ): KeyPair {
        val kpg = KeyPairGenerator.getInstance(algorithm, "BC")
        if (algorithm.equals("EC", ignoreCase = true)) {
            kpg.initialize(ECGenParameterSpec(curveNameOrKeySize))
        } else { // RSA
            kpg.initialize(curveNameOrKeySize.toInt())
        }
        return kpg.generateKeyPair()
    }

    fun createSelfSignedCertificate(
        subjectDNStr: String,
        keyPair: KeyPair,
        daysValidity: Int = 365,
        sigAlgName: String = "SHA256withECDSA",
    ): X509Certificate {
        val now = Date()
        val expiryDate = Date(now.time + daysValidity * 24L * 3600L * 1000L)
        val subjectDN = X500Name(subjectDNStr)
        val serialNumber = BigInteger(64, SecureRandom())

        val contentSigner = JcaContentSignerBuilder(sigAlgName).setProvider("BC").build(keyPair.private)
        val certBuilder = JcaX509v3CertificateBuilder(subjectDN, serialNumber, now, expiryDate, subjectDN, keyPair.public)

        // Basic Constraints for CA: true
        val bcUtils = JcaX509ExtensionUtils()
        certBuilder.addExtension(
            Extension.basicConstraints,
            true,
            org.bouncycastle.asn1.x509
                .BasicConstraints(true),
        )
        certBuilder.addExtension(
            Extension.keyUsage,
            true,
            org.bouncycastle.asn1.x509
                .KeyUsage(org.bouncycastle.asn1.x509.KeyUsage.keyCertSign or org.bouncycastle.asn1.x509.KeyUsage.cRLSign),
        )
        val ski = bcUtils.createSubjectKeyIdentifier(keyPair.public)
        certBuilder.addExtension(Extension.subjectKeyIdentifier, false, ski)

        val san =
            GeneralNames(
                arrayOf(
                    GeneralName(GeneralName.iPAddress, "127.0.0.1"),
                    GeneralName(GeneralName.dNSName, "localhost"),
                ),
            )
        certBuilder.addExtension(Extension.subjectAlternativeName, false, san)

        return JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(contentSigner))
    }

    fun createSignedCertificate(
        subjectDNStr: String,
        subjectPublicKey: PublicKey,
        issuerCert: X509Certificate, // Issuer's certificate
        issuerPrivateKey: PrivateKey, // Issuer's private key
        daysValidity: Int = 365,
        isCA: Boolean = false,
        sigAlgName: String = "SHA256withECDSA", // Ensure this matches issuer's key type
    ): X509Certificate {
        val now = Date()
        val expiryDate = Date(now.time + daysValidity * 24L * 3600L * 1000L)
        val subjectDN = X500Name(subjectDNStr)
        val issuerDN = X500Name(issuerCert.subjectX500Principal.name) // Use X500Name for consistency
        val serialNumber = BigInteger(64, SecureRandom())

        val contentSigner = JcaContentSignerBuilder(sigAlgName).setProvider("BC").build(issuerPrivateKey)
        val certBuilder = JcaX509v3CertificateBuilder(issuerDN, serialNumber, now, expiryDate, subjectDN, subjectPublicKey)

        val bcUtils = JcaX509ExtensionUtils()
        certBuilder.addExtension(
            Extension.basicConstraints,
            isCA,
            org.bouncycastle.asn1.x509
                .BasicConstraints(isCA),
        )
        certBuilder.addExtension(Extension.authorityKeyIdentifier, false, bcUtils.createAuthorityKeyIdentifier(issuerCert.publicKey))
        certBuilder.addExtension(Extension.subjectKeyIdentifier, false, bcUtils.createSubjectKeyIdentifier(subjectPublicKey))
        if (!isCA) { // Key usage for end-entity cert
            certBuilder.addExtension(
                Extension.keyUsage,
                true,
                org.bouncycastle.asn1.x509
                    .KeyUsage(org.bouncycastle.asn1.x509.KeyUsage.digitalSignature or org.bouncycastle.asn1.x509.KeyUsage.keyEncipherment),
            )
        }

        // --- Add Subject Alternative Names (SANs) ---
        // This is crucial for hostname verification, especially for servers.
        // The client will verify the hostname (e.g., "127.0.0.1" or "localhost") against these SANs.
        val sanList = mutableListOf<GeneralName>()
        sanList.add(GeneralName(GeneralName.iPAddress, "127.0.0.1")) // For connecting via IP
        sanList.add(GeneralName(GeneralName.dNSName, "localhost")) // For connecting via DNS name
        // If your subjectDNStr for the server contains a common name (CN) that is also
        // a hostname, you might want to parse it and add it as a dNSName SAN as well.
        // For example: if subjectDNStr = "CN=myserver.example.com,O=MyOrg", add "myserver.example.com"
        // For simplicity here, we are focusing on the typical test server names.

        val subjectAltNames = GeneralNames(sanList.toTypedArray())
        certBuilder.addExtension(Extension.subjectAlternativeName, false, subjectAltNames)
        // --- End of SANs addition ---

        return JcaX509CertificateConverter().setProvider("BC").getCertificate(certBuilder.build(contentSigner))
    }

    fun X509Certificate.toSureCertificate(): Certificate = certificateFromDer(this.encoded)
}
