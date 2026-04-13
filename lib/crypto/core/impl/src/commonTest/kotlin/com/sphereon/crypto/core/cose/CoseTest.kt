/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.crypto.core.cose

import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private val certificatePemEC = """
-----BEGIN CERTIFICATE-----
MIIB3DCCAYMCFA6bjsh9CB8NbtINaWK8WNgBMx2iMAoGCCqGSM49BAMCMHExCzAJ
BgNVBAYTAk5MMRYwFAYDVQQIDA1Ob3J0aCBIb2xsYW5kMRIwEAYDVQQHDAlBbXN0
ZXJkYW0xDjAMBgNVBAoMBU15T3JnMQswCQYDVQQLDAJJRDEZMBcGA1UEAwwQdGVz
dC5leGFtcGxlLmNvbTAeFw0yNTA1MDEwOTE5NDFaFw0yNjA1MDEwOTE5NDFaMHEx
CzAJBgNVBAYTAk5MMRYwFAYDVQQIDA1Ob3J0aCBIb2xsYW5kMRIwEAYDVQQHDAlB
bXN0ZXJkYW0xDjAMBgNVBAoMBU15T3JnMQswCQYDVQQLDAJJRDEZMBcGA1UEAwwQ
dGVzdC5leGFtcGxlLmNvbTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABP7W2xjU
4raapzyctjNDkRLGHP7RgAtVqAHRnS5LWz2oXhgKHyhCcwlLrfCOCEIHta+gajwz
2mxZ8j6ix1SNXvkwCgYIKoZIzj0EAwIDRwAwRAIgF9E2jWW+qMnmL3qpB5VvJ/8J
e/K96UVYWQ2T23OA1SYCIAaD8LNo+RgwA0rE7wKKOrogIfQUy+qFPVKjmcDTcHln
-----END CERTIFICATE-----
    """.trimIndent()

class CoseTest {
    @Test
    fun shouldCreateECCoseKeyJsonFromX509CertificatePem(): TestResult = runTest {
        val coseKeyJson = CoseKeyJson.fromX509CertificatePem(certificatePemEC)

        assertNotNull(coseKeyJson)
        assertEquals(CoseKeyTypeEnum.EC2,coseKeyJson.kty)
        assertNotNull(coseKeyJson.x)
        assertNotNull(coseKeyJson.y)
        assertNotNull(coseKeyJson.crv)
        assertEquals(1,coseKeyJson.x5chain?.size)
    }

    @Test
    fun shouldGetCoseKeyJsonX509CertificateAsPem(): TestResult = runTest {
        val coseKeyJson = CoseKeyJson.fromX509CertificatePem(certificatePemEC)
        assertNotNull(coseKeyJson)

        val pem = coseKeyJson.getX509CertificatePem()
        assertNotNull(pem)

        assertEquals(pem.replace("\n", ""), certificatePemEC.replace("\n", ""))
    }

    @Test
    fun shouldConvertECCoseKeyJsonpublicKeyPem(): TestResult = runTest {
        val coseKeyJson = CoseKeyJson.fromX509CertificatePem(certificatePemEC)
        assertNotNull(coseKeyJson)

        val pem = coseKeyJson.publicKeyPem()
        assertNotNull(pem)
    }

    @Test
    fun shouldCreateECCoseKeyTypeFromX509CertificatePem(): TestResult = runTest {
        val coseKey = CoseKey.fromX509CertificatePem(certificatePemEC)

        assertNotNull(coseKey)
        assertEquals(CoseKeyTypeEnum.EC2,CoseKeyTypeEnum.fromValue(coseKey.kty.value))
        assertNotNull(coseKey.x)
        assertNotNull(coseKey.y)
        assertNotNull(coseKey.crv)
        assertEquals(1,coseKey.x5chain?.asList?.size)
    }

    @Test
    fun shouldGetCoseKeyTypeX509CertificateAsPem(): TestResult = runTest {
        val coseKey = CoseKey.fromX509CertificatePem(certificatePemEC)
        assertNotNull(coseKey)

        val pem = coseKey.getX509CertificatePem()
        assertNotNull(pem)

        assertEquals(pem.replace("\n", ""), certificatePemEC.replace("\n", ""))
    }

    @Test
    fun shouldConvertECCoseKeyTypepublicKeyPem(): TestResult = runTest {
        val coseKey = CoseKey.fromX509CertificatePem(certificatePemEC)
        assertNotNull(coseKey)

        val pem = coseKey.publicKeyPem()
        assertNotNull(pem)
    }
}
