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

package com.sphereon.crypto.core.jose

import com.sphereon.cbor.CborUInt
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeTo
import com.sphereon.crypto.core.cose.CoseCurve
import com.sphereon.crypto.core.cose.CoseKey
import com.sphereon.crypto.core.cose.CoseKeyCborCodecImpl
import com.sphereon.crypto.core.cose.CoseKeyTypeEnum
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val HEX_ENCODED_CBOR_KEY =
    "a5010202582b6c663872734d5371454f5138626d664f4c44526873414e5878667a5a4678725f64634c6e52496a787671452001215820bb11cddd6e9e869d1559729a30d89ed49f3631524215961271abbbe28d7b731f225820dbd639132e2ee561965b830530a6a024f1098888f313550515921184c86acac3"

private val coseKeyCodec = CoseKeyCborCodecImpl()

private val certificatePemRSA =
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

private val certificatePemEC =
    """
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

class JWKTest {
    @Test
    fun shouldConvertECJWKToCoseKey(): TestResult =
        runTest {
            val jwk =
                Jwk(
                    generateKid = true,
                    kty = JwaKeyType.EC,
                    crv = JwaCurve.P_256,
                    x = "uxHN3W6ehp0VWXKaMNie1J82MVJCFZYScau74o17cx8",
                    y = "29Y5Ey4u5WGWW4MFMKagJPEJiIjzE1UFFZIRhMhqysM",
                )
            val coseKey = jwk.jwkToCoseKey()
            assertEquals(CborUInt(CoseKeyTypeEnum.EC2.value.toLong()), coseKey.kty)
            assertEquals(CborUInt(CoseCurve.P_256.value.toLong()), coseKey.crv)
            assertContentEquals(
                byteArrayOf(
                    -69,
                    17,
                    -51,
                    -35,
                    110,
                    -98,
                    -122,
                    -99,
                    21,
                    89,
                    114,
                    -102,
                    48,
                    -40,
                    -98,
                    -44,
                    -97,
                    54,
                    49,
                    82,
                    66,
                    21,
                    -106,
                    18,
                    113,
                    -85,
                    -69,
                    -30,
                    -115,
                    123,
                    115,
                    31,
                ),
                coseKey.x!!.value,
            )
            assertEquals("bb11cddd6e9e869d1559729a30d89ed49f3631524215961271abbbe28d7b731f", coseKey.x!!.encodeValueTo(Encoding.HEX))
            assertEquals(jwk.x, coseKey.x!!.encodeValueTo(Encoding.BASE64URL))

            assertContentEquals(
                byteArrayOf(
                    -37,
                    -42,
                    57,
                    19,
                    46,
                    46,
                    -27,
                    97,
                    -106,
                    91,
                    -125,
                    5,
                    48,
                    -90,
                    -96,
                    36,
                    -15,
                    9,
                    -120,
                    -120,
                    -13,
                    19,
                    85,
                    5,
                    21,
                    -110,
                    17,
                    -124,
                    -56,
                    106,
                    -54,
                    -61,
                ),
                coseKey.y!!.value,
            )
            assertEquals("dbd639132e2ee561965b830530a6a024f1098888f313550515921184c86acac3", coseKey.y!!.encodeValueTo(Encoding.HEX))
            assertEquals(jwk.y, coseKey.y!!.encodeValueTo(Encoding.BASE64URL))
            assertEquals(
                HEX_ENCODED_CBOR_KEY,
                coseKeyCodec.encode(coseKey).getOrThrow().encodeTo(Encoding.HEX),
            )
        }

    @Test
    fun shouldConvertECJWKToCoseKeyAndBack(): TestResult =
        runTest {
            val jwk =
                Jwk(
                    generateKid = false,
                    kty = JwaKeyType.EC,
                    crv = JwaCurve.P_256,
                    x = "uxHN3W6ehp0VWXKaMNie1J82MVJCFZYScau74o17cx8",
                    y = "29Y5Ey4u5WGWW4MFMKagJPEJiIjzE1UFFZIRhMhqysM",
                )
            assertEquals(jwk, jwk.jwkToCoseKey().cborToJwk())
        }

    @Test
    fun shouldConvertECCoseKeyToJWKandBack(): TestResult =
        runTest {
            val cborKey = coseKeyCodec.decode(HEX_ENCODED_CBOR_KEY.decodeFrom(Encoding.HEX)).getOrThrow().value
            assertEquals(cborKey, cborKey.cborToJwk().jwkToCoseKey())
        }

    @Test
    fun shouldHaveSomeFunWithConversion(): TestResult =
        runTest {
            val cborKey = coseKeyCodec.decode(HEX_ENCODED_CBOR_KEY.decodeFrom(Encoding.HEX)).getOrThrow().value
            assertEquals(
                cborKey,
                cborKey
                    .cborToJwk()
                    .jwkToCoseKeyJson()
                    .jsonToJwk()
                    .jwkToCoseKey()
                    .cborToJwk()
                    .jwkToCoseKey(),
            )
        }

    @Test
    fun shouldCreateRSAJWKFromX509CertificatePem(): TestResult =
        runTest {
            val jwk = Jwk.fromX509CertificatePem(certificatePemRSA)

            assertNotNull(jwk)
            assertEquals(JwaKeyType.RSA, jwk.kty)
            assertNotNull(jwk.e)
            assertNotNull(jwk.n)
            assertEquals(1, jwk.x5c?.size)
        }

    @Test
    fun shouldCreateECJWKFromX509CertificatePem(): TestResult =
        runTest {
            val jwk = Jwk.fromX509CertificatePem(certificatePemEC)

            assertNotNull(jwk)
            assertEquals(JwaKeyType.EC, jwk.kty)
            assertNotNull(jwk.x)
            assertNotNull(jwk.y)
            assertNotNull(jwk.crv)
            assertEquals(1, jwk.x5c?.size)
        }

    @Test
    fun shouldgetX509CertificatePem(): TestResult =
        runTest {
            val jwk = Jwk.fromX509CertificatePem(certificatePemEC)
            assertNotNull(jwk)

            val pem = jwk.getX509CertificatePem()
            assertNotNull(pem)

            assertEquals(pem.replace("\n", ""), certificatePemEC.replace("\n", ""))
        }

    @Test
    fun shouldConvertECJWKpublicKeyPem(): TestResult =
        runTest {
            val jwk = Jwk.fromX509CertificatePem(certificatePemEC)
            assertNotNull(jwk)

            val pem = jwk.publicKeyPem()
            assertNotNull(pem)
        }

    @Test
    fun shouldConvertRSAJWKpublicKeyPem(): TestResult =
        runTest {
            val jwk = Jwk.fromX509CertificatePem(certificatePemRSA)
            assertNotNull(jwk)

            val pem = jwk.publicKeyPem()
            assertNotNull(pem)
        }

    @Test
    fun shouldConvertRSAJWKToCoseKey(): TestResult =
        runTest {
            // RSA JWK from certificate (public key only)
            val jwkType = Jwk.fromX509CertificatePem(certificatePemRSA)
            assertNotNull(jwkType)
            assertEquals(JwaKeyType.RSA, jwkType.kty)

            // Convert to Jwk to use conversion methods
            val jwk = Jwk.from(jwkType)
            val coseKey = jwk.jwkToCoseKey()
            assertEquals(CborUInt(CoseKeyTypeEnum.RSA.value.toLong()), coseKey.kty)
            assertNotNull(coseKey.n, "COSE key should have n parameter")
            assertNotNull(coseKey.rsaE, "COSE key should have e parameter")

            // Verify the values are properly converted
            assertEquals(jwk.n, coseKey.n!!.encodeValueTo(Encoding.BASE64URL))
            assertEquals(jwk.e, coseKey.rsaE!!.encodeValueTo(Encoding.BASE64URL))
        }

    @Test
    fun shouldConvertRSAJWKToCoseKeyAndBack(): TestResult =
        runTest {
            // Create RSA JWK from certificate (no private key params)
            val jwkType = Jwk.fromX509CertificatePem(certificatePemRSA)
            assertNotNull(jwkType)
            val jwk = Jwk.from(jwkType)

            // Convert JWK -> COSE -> JWK
            val roundTripped = jwk.jwkToCoseKey().cborToJwk()

            // Verify key type and essential parameters are preserved
            assertEquals(jwk.kty, roundTripped.kty)
            assertEquals(jwk.n, roundTripped.n)
            assertEquals(jwk.e, roundTripped.e)
        }

    @Test
    fun shouldConvertRSAJWKWithPrivateKeyToCoseKeyAndBack(): TestResult =
        runTest {
            // Create RSA JWK with private key parameters (from RFC 7517 Appendix A.2)
            val jwk =
                Jwk
                    .Builder()
                    .withKty(JwaKeyType.RSA)
                    .withN(
                        "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
                    ).withE("AQAB")
                    .withD(
                        "X4cTteJY_gn4FYPsXB8rdXix5vwsg1FLN5E3EaG6RJoVH-HLLKD9M7dx5oo7GURknchnrRweUkC7hT5fJLM0WbFAKNLWY2vv7B6NqXSzUvxT0_YSfqijwp3RTzlBaCxWp4doFk5N2o8Gy_nHNKroADIkJ46pRUohsXywbReAdYaMwFs9tv8d_cPVY3i07a3t8MN6TNwm0dSawm9v47UiCl3Sk5ZiG7xojPLu4sbg1U2jx4IBTNBznbJSzFHK66jT8bgkuqsk0GjskDJk19Z4qwjwbsnn4j2WBii3RL-Us2lGVkY8fkFzme1z0HbIkfz0Y6mqnOYtqc0X4jfcKoAC8Q",
                    ).withP(
                        "83i-7IvMGXoMXCskv73TKr8637FiO7Z27zv8oj6pbWUQyLPQBQxtPVnwD20R-60eTDmD2ujnMt5PoqMrm8RfmNhVWDtjjMmCMjOpSXicFHj7XOuVIYQyqVWlWEh6dN36GVZYk93N8Bc9vY41xy8B9RzzOGVQzXvNEvn7O0nVbfs",
                    ).withQ(
                        "3dfOR9cuYq-0S-mkFLzgItgMEfFzB2q3hWehMuG0oCuqnb3vobLyumqjVZQO1dIrdwgTnCdpYzBcOfW5r370AFXjiWft_NGEiovonizhKpo9VVS78TzFgxkIdrecRezsZ-1kYd_s1qDbxtkDEgfAITAG9LUnADun4vIcb6yelxk",
                    ).withDP(
                        "G4sPXkc6Ya9y8oJW9_ILj4xuppu0lzi_H7VTkS8xj5SdX3coE0oimYwxIi2emTAue0UOa5dpgFGyBJ4c8tQ2VF402XRugKDTP8akYhFo5tAA77Qe_NmtuYZc3C3m3I24G2GvR5sSDxUyAN2zq8Lfn9EUms6rY3Ob8YeiKkTiBj0",
                    ).withDQ(
                        "s9lAH9fggBsoFR8Oac2R_E2gw282rT2kGOAhvIllETE1efrA6huUUvMfBcMpn8lqeW6vzznYY5SSQF7pMdC_agI3nG8Ibp1BUb0JUiraRNqUfLhcQb_d9GF4Dh7e74WbRsobRonujTYN1xCaP6TO61jvWrX-L18txXw494Q_cgk",
                    ).withQInv(
                        "GyM_p6JrXySiz1toFgKbWV-JdI3jQ4ypu9rbMWx3rQJBfmt0FoYzgUIZEVFEcOqwemRN81zoDAaa-Bk0KWNGDjJHZDdDmFhW3AN7lI-puxk_mHZGJ11rxyR8O55XLSe3SPmRfKwZI6yU24ZxvQKFYItdldUKGzO6Ia6zTKhAVRU",
                    ).withKid("2011-04-29", false)
                    .build()

            // Convert JWK -> COSE -> JWK
            val roundTripped = jwk.jwkToCoseKey().cborToJwk()

            // Verify all parameters are preserved (string comparison is safe here since
            // all test values are already in canonical base64url form or will be decoded)
            assertEquals(jwk.kty, roundTripped.kty, "kty mismatch")
            assertEquals(jwk.n, roundTripped.n, "n mismatch")
            assertEquals(jwk.e, roundTripped.e, "e mismatch")
            assertEquals(jwk.d, roundTripped.d, "d mismatch")
            assertEquals(jwk.p, roundTripped.p, "p mismatch")
            assertEquals(jwk.q, roundTripped.q, "q mismatch")
            assertEquals(jwk.dP, roundTripped.dP, "dP mismatch")
            assertEquals(jwk.dQ, roundTripped.dQ, "dQ mismatch")
            assertEquals(jwk.qInv, roundTripped.qInv, "qInv mismatch")
        }

    @Test
    fun shouldConvertRSAJWKToCoseKeyJsonAndBack(): TestResult =
        runTest {
            // Create RSA JWK from certificate
            val jwkType = Jwk.fromX509CertificatePem(certificatePemRSA)
            assertNotNull(jwkType)
            val jwk = Jwk.from(jwkType)

            // Convert through COSE JSON representation
            val coseKeyJson = jwk.jwkToCoseKeyJson()
            assertEquals(CoseKeyTypeEnum.RSA, coseKeyJson.kty)
            assertNotNull(coseKeyJson.n, "COSE JSON key should have n parameter")
            assertNotNull(coseKeyJson.rsaE, "COSE JSON key should have e parameter")

            // Convert back to JWK
            val roundTripped = coseKeyJson.jsonToJwk()
            assertEquals(jwk.kty, roundTripped.kty)
            assertEquals(jwk.n, roundTripped.n)
            assertEquals(jwk.e, roundTripped.e)
        }
}
