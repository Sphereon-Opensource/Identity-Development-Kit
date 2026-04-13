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

@file:OptIn(ExperimentalStdlibApi::class)

import at.asitplus.awesn1.crypto.pki.X509Certificate
import com.sphereon.crypto.core.createTestHttpClient
import com.sphereon.crypto.core.interop.x509CertificateFromDer
import com.sphereon.crypto.core.interop.x509CertificateFromPem
import com.sphereon.crypto.core.isBrowserEnv
import com.sphereon.crypto.core.x509.KeyUsageFlag
import com.sphereon.crypto.core.x509.certificateFromPem
import com.sphereon.crypto.core.x509.downloadCertificateChain
import com.sphereon.crypto.core.x509.toX500
import io.ktor.client.HttpClient
import kotlinx.coroutines.test.runTest
import org.kotlincrypto.hash.sha1.SHA1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class X509CertificateTest {
    private val testPem =
        """
-----BEGIN CERTIFICATE-----
MIID6jCCAtKgAwIBAgIUZDoXRc6UwR/4DSF119w7ZYM2UrQwDQYJKoZIhvcNAQEL
BQAwfjELMAkGA1UEBhMCTkwxFjAUBgNVBAgMDU5vcnRoIEhvbGxhbmQxEjAQBgNV
BAcMCUFtc3RlcmRhbTEZMBcGA1UECgwQU3BoZXJlb24gSUQgVGVjaDERMA8GA1UE
CwwISWRlbnRpdHkxFTATBgNVBAMMDHNwaGVyZW9uLmNvbTAeFw0yNTA0MjIxMTUw
NDhaFw0zNTA0MjAxMTUwNDhaMH4xCzAJBgNVBAYTAk5MMRYwFAYDVQQIDA1Ob3J0
aCBIb2xsYW5kMRIwEAYDVQQHDAlBbXN0ZXJkYW0xGTAXBgNVBAoMEFNwaGVyZW9u
IElEIFRlY2gxETAPBgNVBAsMCElkZW50aXR5MRUwEwYDVQQDDAxzcGhlcmVvbi5j
b20wggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDTPZOWQJQCJRMh1eSB
HS9LurbxQMgwYHgiip0ioMz3vxNEB/fJ7yt03StY/h3IHNfxfxQ3VSzzqzWlaAmF
LrOHMOIlwXS8WikUViB1i/IaLd5R5g+eYTA9ReclNnT3+0+1oZIyZ5dHKtWZUIgR
37ieH0ZvLWydWr7MpqUkSk5yCsl0CMSvbGdjf+eDTgmiYiNVH4HkB3TYahwOjptE
rib7qwNWP7doXx22WjMxbsnsPdx8I5VtbMlZtrzMbLxKf74HptrNd6ZzQ/0OXDH1
MkYGu4E/bbVHCJERUXbVnwIDKpoArhSBoE8iroiYuW3aRqsJJuzPinsPp7Q+Uvrn
vrExAgMBAAGjYDBeMB0GA1UdDgQWBBQpQI0OZ367uIV3MtEQQCt0kvyQRTAfBgNV
HSMEGDAWgBQpQI0OZ367uIV3MtEQQCt0kvyQRTAPBgNVHRMBAf8EBTADAQH/MAsG
A1UdDwQEAwIDODANBgkqhkiG9w0BAQsFAAOCAQEAgWlUg9cgIyzM2fyu5yRcAheY
pQm7dKFDxdzuy2YzNiWXCRAhsT3YDxiTyoBBOCdrA2eYRqkLlFzA1aFVcRhCPobM
ENgyEeVN7SGlLRZ1b0XbmViVafuG+WCP4Lh9Q14accpsEcOAM98coVAoskrs6HgR
441AzVJJEaf7NqLv7SjHw/T4meS4mdB+wyLQnrZwRPfPO63kVDZiuiM04OwAhbk5
3kMxqLt0TV63iimNnjLhmqP+ktXhueWKHqPbwEO4wdkVkQx/W8Y5sk/l/1STsdYE
AoC+D6BQFQ4qD5dRu4JbNaH78Fhw7wEoDplA6k9R2t39KuqlPJHAanRDsVN1gQ==
-----END CERTIFICATE-----
        """.trimIndent()

    private val googleCertPem =
        """
-----BEGIN CERTIFICATE-----
MIIOIjCCDQqgAwIBAgIRALKeQ7KseVzjCZouh4o/WMEwDQYJKoZIhvcNAQELBQAw
OzELMAkGA1UEBhMCVVMxHjAcBgNVBAoTFUdvb2dsZSBUcnVzdCBTZXJ2aWNlczEM
MAoGA1UEAxMDV1IyMB4XDTI1MDMzMTA4NTQyOVoXDTI1MDYyMzA4NTQyOFowFzEV
MBMGA1UEAwwMKi5nb29nbGUuY29tMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE
FNM8rWYymTC9WTSTBI7zYTCWYpCFkhpwlYr3jgi1ALWWXO5p+P0c2i9q8W5E5OIg
1G31fuqPh1Ww3iM8a8quS6OCDA4wggwKMA4GA1UdDwEB/wQEAwIHgDATBgNVHSUE
DDAKBggrBgEFBQcDATAMBgNVHRMBAf8EAjAAMB0GA1UdDgQWBBQ45+Buil/L8S42
YN5naVXXukd70jAfBgNVHSMEGDAWgBTeGx7teRXUPjckwyG77DQ5bUKyMDBYBggr
BgEFBQcBAQRMMEowIQYIKwYBBQUHMAGGFWh0dHA6Ly9vLnBraS5nb29nL3dyMjAl
BggrBgEFBQcwAoYZaHR0cDovL2kucGtpLmdvb2cvd3IyLmNydDCCCeQGA1UdEQSC
CdswggnXggwqLmdvb2dsZS5jb22CFiouYXBwZW5naW5lLmdvb2dsZS5jb22CCSou
YmRuLmRldoIVKi5vcmlnaW4tdGVzdC5iZG4uZGV2ghIqLmNsb3VkLmdvb2dsZS5j
b22CGCouY3Jvd2Rzb3VyY2UuZ29vZ2xlLmNvbYIYKi5kYXRhY29tcHV0ZS5nb29n
bGUuY29tggsqLmdvb2dsZS5jYYILKi5nb29nbGUuY2yCDiouZ29vZ2xlLmNvLmlu
gg4qLmdvb2dsZS5jby5qcIIOKi5nb29nbGUuY28udWuCDyouZ29vZ2xlLmNvbS5h
coIPKi5nb29nbGUuY29tLmF1gg8qLmdvb2dsZS5jb20uYnKCDyouZ29vZ2xlLmNv
bS5jb4IPKi5nb29nbGUuY29tLm14gg8qLmdvb2dsZS5jb20udHKCDyouZ29vZ2xl
LmNvbS52boILKi5nb29nbGUuZGWCCyouZ29vZ2xlLmVzggsqLmdvb2dsZS5mcoIL
Ki5nb29nbGUuaHWCCyouZ29vZ2xlLml0ggsqLmdvb2dsZS5ubIILKi5nb29nbGUu
cGyCCyouZ29vZ2xlLnB0gg8qLmdvb2dsZWFwaXMuY26CESouZ29vZ2xldmlkZW8u
Y29tggwqLmdzdGF0aWMuY26CECouZ3N0YXRpYy1jbi5jb22CD2dvb2dsZWNuYXBw
cy5jboIRKi5nb29nbGVjbmFwcHMuY26CEWdvb2dsZWFwcHMtY24uY29tghMqLmdv
b2dsZWFwcHMtY24uY29tggxna2VjbmFwcHMuY26CDiouZ2tlY25hcHBzLmNughJn
b29nbGVkb3dubG9hZHMuY26CFCouZ29vZ2xlZG93bmxvYWRzLmNughByZWNhcHRj
aGEubmV0LmNughIqLnJlY2FwdGNoYS5uZXQuY26CEHJlY2FwdGNoYS1jbi5uZXSC
EioucmVjYXB0Y2hhLWNuLm5ldIILd2lkZXZpbmUuY26CDSoud2lkZXZpbmUuY26C
EWFtcHByb2plY3Qub3JnLmNughMqLmFtcHByb2plY3Qub3JnLmNughFhbXBwcm9q
ZWN0Lm5ldC5jboITKi5hbXBwcm9qZWN0Lm5ldC5jboIXZ29vZ2xlLWFuYWx5dGlj
cy1jbi5jb22CGSouZ29vZ2xlLWFuYWx5dGljcy1jbi5jb22CF2dvb2dsZWFkc2Vy
dmljZXMtY24uY29tghkqLmdvb2dsZWFkc2VydmljZXMtY24uY29tghFnb29nbGV2
YWRzLWNuLmNvbYITKi5nb29nbGV2YWRzLWNuLmNvbYIRZ29vZ2xlYXBpcy1jbi5j
b22CEyouZ29vZ2xlYXBpcy1jbi5jb22CFWdvb2dsZW9wdGltaXplLWNuLmNvbYIX
Ki5nb29nbGVvcHRpbWl6ZS1jbi5jb22CEmRvdWJsZWNsaWNrLWNuLm5ldIIUKi5k
b3VibGVjbGljay1jbi5uZXSCGCouZmxzLmRvdWJsZWNsaWNrLWNuLm5ldIIWKi5n
LmRvdWJsZWNsaWNrLWNuLm5ldIIOZG91YmxlY2xpY2suY26CECouZG91YmxlY2xp
Y2suY26CFCouZmxzLmRvdWJsZWNsaWNrLmNughIqLmcuZG91YmxlY2xpY2suY26C
EWRhcnRzZWFyY2gtY24ubmV0ghMqLmRhcnRzZWFyY2gtY24ubmV0gh1nb29nbGV0
cmF2ZWxhZHNlcnZpY2VzLWNuLmNvbYIfKi5nb29nbGV0cmF2ZWxhZHNlcnZpY2Vz
LWNuLmNvbYIYZ29vZ2xldGFnc2VydmljZXMtY24uY29tghoqLmdvb2dsZXRhZ3Nl
cnZpY2VzLWNuLmNvbYIXZ29vZ2xldGFnbWFuYWdlci1jbi5jb22CGSouZ29vZ2xl
dGFnbWFuYWdlci1jbi5jb22CGGdvb2dsZXN5bmRpY2F0aW9uLWNuLmNvbYIaKi5n
b29nbGVzeW5kaWNhdGlvbi1jbi5jb22CJCouc2FmZWZyYW1lLmdvb2dsZXN5bmRp
Y2F0aW9uLWNuLmNvbYIWYXBwLW1lYXN1cmVtZW50LWNuLmNvbYIYKi5hcHAtbWVh
c3VyZW1lbnQtY24uY29tggtndnQxLWNuLmNvbYINKi5ndnQxLWNuLmNvbYILZ3Z0
Mi1jbi5jb22CDSouZ3Z0Mi1jbi5jb22CCzJtZG4tY24ubmV0gg0qLjJtZG4tY24u
bmV0ghRnb29nbGVmbGlnaHRzLWNuLm5ldIIWKi5nb29nbGVmbGlnaHRzLWNuLm5l
dIIMYWRtb2ItY24uY29tgg4qLmFkbW9iLWNuLmNvbYIUZ29vZ2xlc2FuZGJveC1j
bi5jb22CFiouZ29vZ2xlc2FuZGJveC1jbi5jb22CHiouc2FmZW51cC5nb29nbGVz
YW5kYm94LWNuLmNvbYINKi5nc3RhdGljLmNvbYIUKi5tZXRyaWMuZ3N0YXRpYy5j
b22CCiouZ3Z0MS5jb22CESouZ2NwY2RuLmd2dDEuY29tggoqLmd2dDIuY29tgg4q
LmdjcC5ndnQyLmNvbYIQKi51cmwuZ29vZ2xlLmNvbYIWKi55b3V0dWJlLW5vY29v
a2llLmNvbYILKi55dGltZy5jb22CC2FuZHJvaWQuY29tgg0qLmFuZHJvaWQuY29t
ghMqLmZsYXNoLmFuZHJvaWQuY29tggRnLmNuggYqLmcuY26CBGcuY2+CBiouZy5j
b4IGZ29vLmdsggp3d3cuZ29vLmdsghRnb29nbGUtYW5hbHl0aWNzLmNvbYIWKi5n
b29nbGUtYW5hbHl0aWNzLmNvbYIKZ29vZ2xlLmNvbYISZ29vZ2xlY29tbWVyY2Uu
Y29tghQqLmdvb2dsZWNvbW1lcmNlLmNvbYIIZ2dwaHQuY26CCiouZ2dwaHQuY26C
CnVyY2hpbi5jb22CDCoudXJjaGluLmNvbYIIeW91dHUuYmWCC3lvdXR1YmUuY29t
gg0qLnlvdXR1YmUuY29tghFtdXNpYy55b3V0dWJlLmNvbYITKi5tdXNpYy55b3V0
dWJlLmNvbYIUeW91dHViZWVkdWNhdGlvbi5jb22CFioueW91dHViZWVkdWNhdGlv
bi5jb22CD3lvdXR1YmVraWRzLmNvbYIRKi55b3V0dWJla2lkcy5jb22CBXl0LmJl
ggcqLnl0LmJlghphbmRyb2lkLmNsaWVudHMuZ29vZ2xlLmNvbYITKi5hbmRyb2lk
Lmdvb2dsZS5jboISKi5jaHJvbWUuZ29vZ2xlLmNughYqLmRldmVsb3BlcnMuZ29v
Z2xlLmNughUqLmFpc3R1ZGlvLmdvb2dsZS5jb20wEwYDVR0gBAwwCjAIBgZngQwB
AgEwNgYDVR0fBC8wLTAroCmgJ4YlaHR0cDovL2MucGtpLmdvb2cvd3IyL0dTeVQx
TjRQQnJnLmNybDCCAQQGCisGAQQB1nkCBAIEgfUEgfIA8AB2AM8RVu7VLnyv84db
2Wkum+kacWdKsBfsrAHSW3fOzDsIAAABleuf1dQAAAQDAEcwRQIhAO0kBGU0B+j4
NXdW9m4Y1r4Z02Ij/SLSjBE4GBcGfrSUAiBQKWu2ZmElwImvnal9gfCzqIsY87zA
nCuYOW/qIdZnFQB2AH1ZHhLheCp7HGFnfF79+NCHXBSgTpWeuQMv2Q6MLnm4AAAB
leuf1cYAAAQDAEcwRQIgdaahxweOTK0lT4A9EL+1xo4wdBOA6fEDhb+CKUi3HAEC
IQDL6dLM5kkyMyQvCi5lP4L5WPir9eLeuaZIQDmMWeNF+TANBgkqhkiG9w0BAQsF
AAOCAQEAWcqoWSUgkEhWug8K9XJJ/EQFdX6Y6CS3Bd8moT7FwvgAAPQN3QO12/nz
s7Z8X5KfrhuwxtnGom1TGDWq0K0YDOzKMAFnPocAGYgUfgcA9h5n3YUq8bN1B6Wg
D3PQ+UEl1eVoZxuuMZjfpyv8ZJtwi9dA1k1D7ze5v+D/Bx5NuRy8cGIktXk4x3ym
Mx0Xp6IYFnFPJCFnTjT0cZEcQgspQnPXWhJ0jW/DeP4HmEIeJIv4B6raK2h/xbAx
q/M0GDyO2E/8GcQE7f1l0dyuSqnw4a5A1rH/LMBjtly2DICE9qVJG++A0B8o2Eo7
eTCn+mxYcL+QkJA3DCokSdxVOB7yBw==
-----END CERTIFICATE-----
        """.trimIndent()

    @Test
    fun testx509CertificateDTOFromPem() {
        val cert = x509CertificateFromPem(testPem)

        assertNotNull(cert)
        assertEquals("643A1745CE94C11FF80D2175D7DC3B65833652B4", cert.tbsCertificate.serialNumber.toHexString(format = HexFormat.UpperCase))
        assertEquals("C=NL,ST=North Holland,L=Amsterdam,O=Sphereon ID Tech,OU=Identity,CN=sphereon.com", cert.tbsCertificate.issuerName.toX500())
        assertEquals("C=NL,ST=North Holland,L=Amsterdam,O=Sphereon ID Tech,OU=Identity,CN=sphereon.com", cert.tbsCertificate.subjectName.toX500())
    }

    @Test
    fun testToCertificateDTO() {
        val certDTO = certificateFromPem(testPem)

        assertNotNull(certDTO)
        assertEquals("643A1745CE94C11FF80D2175D7DC3B65833652B4", certDTO.serialNumber)
        assertEquals("C=NL,ST=North Holland,L=Amsterdam,O=Sphereon ID Tech,OU=Identity,CN=sphereon.com", certDTO.issuerDN)
        assertEquals("C=NL,ST=North Holland,L=Amsterdam,O=Sphereon ID Tech,OU=Identity,CN=sphereon.com", certDTO.subjectDN)

        // Verify validity period
        assertNotNull(certDTO.notBefore)
        assertNotNull(certDTO.notAfter)

        // Verify key usage
        val keyUsage = certDTO.keyUsage
        assertNotNull(keyUsage)
        assertFalse(keyUsage.has(KeyUsageFlag.CRL_SIGN))
        assertFalse(keyUsage.has(KeyUsageFlag.KEY_CERT_SIGN))
        assertFalse(keyUsage.has(KeyUsageFlag.DIGITAL_SIGNATURE))
        assertTrue(keyUsage.has(KeyUsageFlag.KEY_ENCIPHERMENT))
        assertTrue(keyUsage.has(KeyUsageFlag.DATA_ENCIPHERMENT))
        assertTrue(keyUsage.has(KeyUsageFlag.KEY_AGREEMENT))
    }

    @Test
    fun testFingerprint() {
        val certDTO = certificateFromPem(testPem)
        val x509 = x509CertificateFromPem(testPem)
        val derBytes = x509.encodeToTlv().derEncoded

        assertNotNull(derBytes)
        val expectedFingerprint = SHA1().digest(derBytes).toHexString(format = HexFormat.UpperCase)
        assertEquals(expectedFingerprint, certDTO.fingerPrint)
    }

    @Test
    fun testDerConversion() {
        val x509 = x509CertificateFromPem(testPem)
        val derBytes = x509.encodeToTlv().derEncoded

        assertNotNull(derBytes)

        val fromDer = x509CertificateFromDer(derBytes)
        assertEquals(x509.tbsCertificate.serialNumber.toHexString(format = HexFormat.UpperCase), fromDer.tbsCertificate.serialNumber.toHexString(format = HexFormat.UpperCase))
        assertEquals(x509.tbsCertificate.issuerName.toX500(), fromDer.tbsCertificate.issuerName.toX500())
    }

    @Test
    fun testDownloadExtraCertificates() =
        runTest {
            if (isBrowserEnv()) {
                println("Skipping testDownloadExtraCertificates in browser environment due to CORS issues")
                return@runTest
            }

            // 1. Load the Google certificate
            val googleCert = x509CertificateFromPem(googleCertPem)
            assertNotNull(googleCert)

            // 2. Google's certificate should have an AIA extension with a URL to the issuer certificate
            val downloadedCerts = googleCert.downloadCertificateChain(httpClient = createTestHttpClient())

            // 3. Verify the result
            assertTrue(downloadedCerts.isNotEmpty(), "Should download at least one certificate")

            // 4. Log some details about the downloaded certificates
            for ((index, cert) in downloadedCerts.withIndex()) {
                println("Certificate #${index + 1}:")
                println("  Subject: ${cert.tbsCertificate.subjectName.toX500()}")
                println("  Issuer: ${cert.tbsCertificate.issuerName.toX500()}")
                println("  Serial: ${cert.tbsCertificate.serialNumber.toHexString(format = HexFormat.UpperCase)}")
                println()
            }

            // 5. Verify the downloaded certificate is the expected issuer
            val issuerDN = googleCert.tbsCertificate.issuerName.toX500()
            val foundIssuer =
                downloadedCerts.any {
                    it.tbsCertificate.subjectName.toX500() == issuerDN
                }

            assertTrue(
                foundIssuer,
                "The downloaded certificates should include the issuer certificate with DN: $issuerDN",
            )
        }

    @Test
    fun testCertificateChainConstruction() =
        runTest {
            if (isBrowserEnv()) {
                println("Skipping testCertificateChainConstruction in browser environment due to CORS issues")
                return@runTest
            }

            // 1. Load the Google certificate
            val googleCert = x509CertificateFromPem(googleCertPem)

            // 2. Download the certificate chain
            val downloadedCerts = googleCert.downloadCertificateChain(HttpClient())
            assertTrue(downloadedCerts.isNotEmpty())

            // 3. Verify we can build a proper certificate chain
            val chain = buildCertificateChain(googleCert, downloadedCerts)

            // 4. Verify the chain properties
            assertTrue(chain.size >= 2, "Chain should include at least the leaf and issuer certificates")

            // 5. Verify the chain order (leaf -> intermediate -> root)
            assertEquals(googleCert, chain.first(), "First certificate should be the original certificate")

            // 6. Print the chain for verification
            println("Certificate Chain:")
            for ((index, cert) in chain.withIndex()) {
                println("$index: ${cert.tbsCertificate.subjectName.toX500()}")
                println("   Issued by: ${cert.tbsCertificate.issuerName.toX500()}")
            }
        }

    /**
     * Builds a certificate chain from a leaf certificate and a pool of potential issuer certificates.
     * The resulting chain starts with the leaf certificate and ends with the root certificate.
     */
    private fun buildCertificateChain(
        leafCert: X509Certificate,
        certPool: List<X509Certificate>,
    ): List<X509Certificate> {
        val chain = mutableListOf(leafCert)
        var current = leafCert

        while (true) {
            // Find issuer certificate (where subject of issuer matches issuer of current)
            val issuer =
                certPool.find {
                    it.tbsCertificate.subjectName.toX500() == current.tbsCertificate.issuerName.toX500()
                } ?: break

            // Add issuer to chain
            chain.add(issuer)

            // If issuer is self-signed (root), we're done
            if (issuer.tbsCertificate.subjectName.toX500() ==
                issuer.tbsCertificate.issuerName.toX500()
            ) {
                break
            }

            // Move up the chain
            current = issuer
        }

        return chain
    }
}
