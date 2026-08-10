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

package com.sphereon.crypto.core

import com.sphereon.core.compat.LocalDateTimeKMP
import com.sphereon.crypto.core.x509.X509VerificationRequest
import com.sphereon.crypto.core.x509.X509VerifyServiceJvmAdapter
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private val testPemChain =
    arrayOf(
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
        """.trimIndent(),
        """
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
        """.trimIndent(),
    )

class X509CertificateServiceJvmTest {
    val app = createJvmCryptoTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("test", principalType = com.sphereon.di.context.PrincipalType.USER).graph

//    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun shouldValidateCert(): TestResult =
        runTest {
            println("Running verifyCertChainJvm test")
            val x509 = (session as CryptoServices.Graph).cryptoServices.x509
            DefaultCallbacks.setX509Default(X509VerifyServiceJvmAdapter())
            val result =
                x509.verifyCertificateChain(
                    X509VerificationRequest(
                        chainPEM = testPemChain,
                        trustedCerts = arrayOf(testPemChain[1]),
                        verificationProfile = null,
                        verificationTime = LocalDateTimeKMP.fromString("2025-06-02T12:22:22"),
                    ),
                )
            assertFalse(result.error)
            assertEquals("2025-06-02T12:22:22", result.verificationTime.toString())
        }
}
