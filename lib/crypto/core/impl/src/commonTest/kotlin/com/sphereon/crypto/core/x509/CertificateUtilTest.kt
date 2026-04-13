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

import com.sphereon.crypto.core.interop.x509CertificateChainFromPem
import com.sphereon.crypto.core.interop.x509CertificateChainToX5c
import com.sphereon.crypto.core.interop.x509CertificateFromDer
import com.sphereon.crypto.core.interop.x509CertificateFromPem
import com.sphereon.crypto.core.x509.BEGIN_CERTIFICATE_PEM_HEADER
import com.sphereon.crypto.core.x509.END_CERTIFICATE_PEM_FOOTER
import com.sphereon.crypto.core.x509.KeyUsageFlag
import com.sphereon.crypto.core.x509.certificateFromPem
import com.sphereon.crypto.core.x509.toX500
import com.sphereon.crypto.core.x509.wrapX509CertificatePem
import org.kotlincrypto.hash.sha1.SHA1
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CertificateUtilTest {
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

    private val testPemChain =
        """
-----BEGIN CERTIFICATE-----
MIID+zCCAeMCFGpZprAAGLBe7EwzJtczI10bzmO4MA0GCSqGSIb3DQEBCwUAMDkx
FTATBgNVBAMMDFRlc3QgUm9vdCBDQTETMBEGA1UECgwKTXkgQ29tcGFueTELMAkG
A1UEBhMCVVMwHhcNMjUwNDIyMTIwNzA1WhcNMjYwNDIyMTIwNzA1WjA7MRQwEgYD
VQQDDAt0ZXN0LWNsaWVudDEWMBQGA1UECgwNTXkgQ2xpZW50IE9yZzELMAkGA1UE
BhMCVVMwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDKH88PNb9/+pL1
FtuixCTHeXNIxU4iwU4F3GQsWGvM83ZPTyQsmne/0MBhKg55yhl93SgRLnlx6Gxt
ce8qBWEVkeJwzSgTG+0Nfyx57WBTz8WO9as9dsR0ee+Ay60dHv0xASEoxJwoyXdP
gHhAuIXHba7t1yhCnBrQBGxbCmvyTdRff9JmDa1wDrLNQ02JZBri1mOAV3oHxnjw
MlGDNyHGtqu1pkZ+NkJ8r1YxrtJMbkPNbn3W6WLxy4EcTuyz3geaWFA8+00U6OUl
+Nxdbr2jVRRJepiBGbBr40BKnTNnzDRX8euLppyqDru8TDRkUTKMhASTLrWAkNFv
lRxOuirpAgMBAAEwDQYJKoZIhvcNAQELBQADggIBAI2xqwCLx0i5dCYANJ9ScK0V
acWAk104jr3f+ymGYjbRnoH1n3+E9uPjLsSUZFcj0ohc6dkNsUzbL95/b6Zmrgt2
vFyxa13Kw0EzR0iPvRzOs2EZUPOiNT7dhoX8M6ulHCIH+qW7tviyq33QwxpKi2A2
7AQnaozhb9o06f5jhYNYF9CSlzSL5TibsM19GMtFMp3lwCHBy0TcYx5MFd9B9lDy
kB4Q/q9HoK6xdLb5j78UU7weDJjzATELE2nKuK7qSmM2b6pEl31t8VGhOCtBhd53
uYK4U2RVvBV/nYuQTeaDKi7Kz+Pr2z4oQA3RNG+3Yh4zdrWNMjhkXFsRoBTAlkCa
1jkNQp6vsXg5GafHPt9tZ6MbqO6PR52aXIPKEuWhY1+ry7jm5LLAhALRCpyvZrUl
/aUzbfe3RWFKE0+girYLIAavvmHSHstIKL1ZcF5E3zS6HO8otapbIjaok837MMmZ
3hZEoHqhmXRYpi6h+c9A3tr83rY8NSJCuPIp+Gt4j2sj+3Nc1voV8WEJ1Hkr30IQ
/akdPZw1DVcNCvbZHEvynw81c0Gl40ORaZJ1JD+NHv/oHYg4KFyYVcO/5Dg0+hgs
TOZ7uJOSWHiO7W3EOEVm4gaclmR0YYkTSFmGyifr0+WLzLr/k3pOX2uGAVlx88VE
4Vh9YF/MO1y4Gw4oIf0U
-----END CERTIFICATE-----
-----BEGIN CERTIFICATE-----
MIIE+TCCAuECFGAsUUW/e7N0/+8074MJpSjqucJ+MA0GCSqGSIb3DQEBCwUAMDkx
FTATBgNVBAMMDFRlc3QgUm9vdCBDQTETMBEGA1UECgwKTXkgQ29tcGFueTELMAkG
A1UEBhMCVVMwHhcNMjUwNDIyMTEyMjEyWhcNMjYwNDIyMTEyMjEyWjA5MRUwEwYD
VQQDDAxUZXN0IFJvb3QgQ0ExEzARBgNVBAoMCk15IENvbXBhbnkxCzAJBgNVBAYT
AlVTMIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA9ocLBg0gXJws7DRi
ndzw/YGT6k34oIenIFo8hqXdf1XR0Ncz6aItTVR8OQZ9zXoSmobwR1jFFGeLyHyz
FDCIAYMHomzpGJGQPogzQqD19AgfIxxX2gNwR4yXU+5b2PFyaWtg348luPBuWAeO
4HfRxrN53aCVxSh1zBF2kvZSCstof04s+gCzsoGfuCs4AkE4DOrn0e3Pu/kf/+BE
lqm4hS88KMOs8uwT41aNVmR6vJ8gNYjXjbv4uxnJKY1+W7GTmvTbZW75BuBVOnOP
GDPhoEd5aRmDvCMKazAZRErpKYH586XmvxaQfS4AD8OfDsdrXT34Rv3zQRQw2mZy
X3V52/RMlV4sK17EdDya8omqYuDYmuzQpbY2D64ij+QRn1HndOmhXdegGwWPKrjL
ovMsi5R9wH/zMaN67crthb61+wB3vGiKknf8MpajJI3FHeWyrwnt0zMjbfO8aKyn
5Q2fzh8Z2jvt0FenqjUK8yineaKP5X1gvIc67tb1gqe4oS4bb0zwv9ebT4l+bcvl
u4nBVFtoeV6Izk1RuJA48pjIyZjWxeC9PfX/yz5HBABTysa6E9J/WVPvyuuma6fb
RJ9INmdHmsYYYt9OEHXyr0pjDx5fRdY0zM9qQj2+5K1PbC38Lr6lliznj7NNWO0x
lFMqNDazTBXniR0fV8AeRilQU3ECAwEAATANBgkqhkiG9w0BAQsFAAOCAgEAXCNN
DKOhejtymjy8gXQa/fl6fH4K4OLeJ4IAk5GUNu1yKMfoyXD4QXp70E5Q87DKaysx
x9LXjcTnaF2YU0h+YVEMdQRu3HUic+qrjgeJChfXiUsnbpmicuJqe0XI9Og9bGyQ
ke05Ia0vEbYcWF+kqauSOy99CkTXsn/GymUh/K0ZAuJMFs0fftRQQS5hatJ0Muig
s4ZemopMp5/aVtOC85Bi077M488V7C0zCKuBO/8bLFGM2eHVekzoqLlfKxvEEbgG
CSuqL6eTHycIQ+f5pd21+UKk7GSMy/J9Gxaw/G2bZMGnbRN0HRh1auCxOtp1xwrm
4i2KXMJAQCgHvMMfvWIregkJ2eCUPeh1vyE1LSyHDuZSrODRO9+aV7SgqEK5BccO
CGJdSu3A+Q6l8fpyz6qSXQu9OGdn7XGkGGoA/M0+vK/bf9cSFwNQ4yt9vyDaETAt
VliKkgLYsFuCkaalRzFTPFpnpoY7tnotlzL+A6BXsLfWcFkZalX40tbZEGN16Ngb
w1hs5GftEhpEDGWObW55jNwnUu78sahZ4hQN/4fWvN8dWsZ/I/1KTmTKDlCN8efv
OcoCWG47pw1oR9TYBij2NoX2LcM+pBIjM51F7yarSVrhAtjk7cYooOFBPqtJbsmS
INYu69YIwTpPO4RKcaXVys9MLz1MhcHvEWwGp14=
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
    fun testCertificateChainToBase64() {
        val chain = x509CertificateChainFromPem(testPemChain)
        assertNotNull(chain)

        val x5c = x509CertificateChainToX5c(chain)
        assertNotNull(x5c)
        assertEquals(2, x5c.size)
        assertEquals(
            "MIID+zCCAeMCFGpZprAAGLBe7EwzJtczI10bzmO4MA0GCSqGSIb3DQEBCwUAMDkxFTATBgNVBAMMDFRlc3QgUm9vdCBDQTETMBEGA1UECgwKTXkgQ29tcGFueTELMAkGA1UEBhMCVVMwHhcNMjUwNDIyMTIwNzA1WhcNMjYwNDIyMTIwNzA1WjA7MRQwEgYDVQQDDAt0ZXN0LWNsaWVudDEWMBQGA1UECgwNTXkgQ2xpZW50IE9yZzELMAkGA1UEBhMCVVMwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDKH88PNb9/+pL1FtuixCTHeXNIxU4iwU4F3GQsWGvM83ZPTyQsmne/0MBhKg55yhl93SgRLnlx6Gxtce8qBWEVkeJwzSgTG+0Nfyx57WBTz8WO9as9dsR0ee+Ay60dHv0xASEoxJwoyXdPgHhAuIXHba7t1yhCnBrQBGxbCmvyTdRff9JmDa1wDrLNQ02JZBri1mOAV3oHxnjwMlGDNyHGtqu1pkZ+NkJ8r1YxrtJMbkPNbn3W6WLxy4EcTuyz3geaWFA8+00U6OUl+Nxdbr2jVRRJepiBGbBr40BKnTNnzDRX8euLppyqDru8TDRkUTKMhASTLrWAkNFvlRxOuirpAgMBAAEwDQYJKoZIhvcNAQELBQADggIBAI2xqwCLx0i5dCYANJ9ScK0VacWAk104jr3f+ymGYjbRnoH1n3+E9uPjLsSUZFcj0ohc6dkNsUzbL95/b6Zmrgt2vFyxa13Kw0EzR0iPvRzOs2EZUPOiNT7dhoX8M6ulHCIH+qW7tviyq33QwxpKi2A27AQnaozhb9o06f5jhYNYF9CSlzSL5TibsM19GMtFMp3lwCHBy0TcYx5MFd9B9lDykB4Q/q9HoK6xdLb5j78UU7weDJjzATELE2nKuK7qSmM2b6pEl31t8VGhOCtBhd53uYK4U2RVvBV/nYuQTeaDKi7Kz+Pr2z4oQA3RNG+3Yh4zdrWNMjhkXFsRoBTAlkCa1jkNQp6vsXg5GafHPt9tZ6MbqO6PR52aXIPKEuWhY1+ry7jm5LLAhALRCpyvZrUl/aUzbfe3RWFKE0+girYLIAavvmHSHstIKL1ZcF5E3zS6HO8otapbIjaok837MMmZ3hZEoHqhmXRYpi6h+c9A3tr83rY8NSJCuPIp+Gt4j2sj+3Nc1voV8WEJ1Hkr30IQ/akdPZw1DVcNCvbZHEvynw81c0Gl40ORaZJ1JD+NHv/oHYg4KFyYVcO/5Dg0+hgsTOZ7uJOSWHiO7W3EOEVm4gaclmR0YYkTSFmGyifr0+WLzLr/k3pOX2uGAVlx88VE4Vh9YF/MO1y4Gw4oIf0U",
            x5c[0],
        )
        assertEquals(
            "MIIE+TCCAuECFGAsUUW/e7N0/+8074MJpSjqucJ+MA0GCSqGSIb3DQEBCwUAMDkxFTATBgNVBAMMDFRlc3QgUm9vdCBDQTETMBEGA1UECgwKTXkgQ29tcGFueTELMAkGA1UEBhMCVVMwHhcNMjUwNDIyMTEyMjEyWhcNMjYwNDIyMTEyMjEyWjA5MRUwEwYDVQQDDAxUZXN0IFJvb3QgQ0ExEzARBgNVBAoMCk15IENvbXBhbnkxCzAJBgNVBAYTAlVTMIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEA9ocLBg0gXJws7DRindzw/YGT6k34oIenIFo8hqXdf1XR0Ncz6aItTVR8OQZ9zXoSmobwR1jFFGeLyHyzFDCIAYMHomzpGJGQPogzQqD19AgfIxxX2gNwR4yXU+5b2PFyaWtg348luPBuWAeO4HfRxrN53aCVxSh1zBF2kvZSCstof04s+gCzsoGfuCs4AkE4DOrn0e3Pu/kf/+BElqm4hS88KMOs8uwT41aNVmR6vJ8gNYjXjbv4uxnJKY1+W7GTmvTbZW75BuBVOnOPGDPhoEd5aRmDvCMKazAZRErpKYH586XmvxaQfS4AD8OfDsdrXT34Rv3zQRQw2mZyX3V52/RMlV4sK17EdDya8omqYuDYmuzQpbY2D64ij+QRn1HndOmhXdegGwWPKrjLovMsi5R9wH/zMaN67crthb61+wB3vGiKknf8MpajJI3FHeWyrwnt0zMjbfO8aKyn5Q2fzh8Z2jvt0FenqjUK8yineaKP5X1gvIc67tb1gqe4oS4bb0zwv9ebT4l+bcvlu4nBVFtoeV6Izk1RuJA48pjIyZjWxeC9PfX/yz5HBABTysa6E9J/WVPvyuuma6fbRJ9INmdHmsYYYt9OEHXyr0pjDx5fRdY0zM9qQj2+5K1PbC38Lr6lliznj7NNWO0xlFMqNDazTBXniR0fV8AeRilQU3ECAwEAATANBgkqhkiG9w0BAQsFAAOCAgEAXCNNDKOhejtymjy8gXQa/fl6fH4K4OLeJ4IAk5GUNu1yKMfoyXD4QXp70E5Q87DKaysxx9LXjcTnaF2YU0h+YVEMdQRu3HUic+qrjgeJChfXiUsnbpmicuJqe0XI9Og9bGyQke05Ia0vEbYcWF+kqauSOy99CkTXsn/GymUh/K0ZAuJMFs0fftRQQS5hatJ0Muigs4ZemopMp5/aVtOC85Bi077M488V7C0zCKuBO/8bLFGM2eHVekzoqLlfKxvEEbgGCSuqL6eTHycIQ+f5pd21+UKk7GSMy/J9Gxaw/G2bZMGnbRN0HRh1auCxOtp1xwrm4i2KXMJAQCgHvMMfvWIregkJ2eCUPeh1vyE1LSyHDuZSrODRO9+aV7SgqEK5BccOCGJdSu3A+Q6l8fpyz6qSXQu9OGdn7XGkGGoA/M0+vK/bf9cSFwNQ4yt9vyDaETAtVliKkgLYsFuCkaalRzFTPFpnpoY7tnotlzL+A6BXsLfWcFkZalX40tbZEGN16Ngbw1hs5GftEhpEDGWObW55jNwnUu78sahZ4hQN/4fWvN8dWsZ/I/1KTmTKDlCN8efvOcoCWG47pw1oR9TYBij2NoX2LcM+pBIjM51F7yarSVrhAtjk7cYooOFBPqtJbsmSINYu69YIwTpPO4RKcaXVys9MLz1MhcHvEWwGp14=",
            x5c[1],
        )
    }

    @Test
    fun testWrapCertificatePem() {
        val pemBody = testPem.replace(BEGIN_CERTIFICATE_PEM_HEADER, "").replace(END_CERTIFICATE_PEM_FOOTER, "")

        val pem = wrapX509CertificatePem(pemBody)
        assertEquals(pem.replace("\n", ""), testPem.replace("\n", ""))
    }
}
