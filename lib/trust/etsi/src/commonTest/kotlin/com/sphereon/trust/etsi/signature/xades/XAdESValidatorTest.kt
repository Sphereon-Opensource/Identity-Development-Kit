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

package com.sphereon.trust.etsi.signature.xades

import com.sphereon.trust.etsi.testutil.EtsiTestContext
import com.sphereon.trust.etsi.testutil.FIDES_TL_URL
import com.sphereon.trust.etsi.testutil.ensureDomAvailable
import com.sphereon.trust.etsi.testutil.readTestResource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class XAdESValidatorTest {
    private val testContext = EtsiTestContext("xades-test", this)
    private val validator: XAdESValidator = testContext.xadesValidator

    @Test
    fun shouldReportNoSignatureWhenAbsent() =
        runTest {
            ensureDomAvailable()
            val xml = """<Root xmlns="urn:test"><Content>No signature here</Content></Root>"""
            val result = validator.validate(xml.encodeToByteArray())

            assertFalse(result.signaturePresent)
            assertFalse(result.signatureValid)
            assertFalse(result.xadesPresent)
            assertTrue(result.errors.isNotEmpty())
            assertTrue(result.errors.any { "No XML signature found" in it })
        }

    @Test
    fun shouldValidateFidesTlSignature() =
        runTest {
            ensureDomAvailable()
            val fidesTl = testContext.fetchUrl(FIDES_TL_URL)
            val result =
                validator.validate(
                    fidesTl.encodeToByteArray(),
                    XAdESValidationOptions(validateReferences = false),
                )
            assertTrue(result.signaturePresent, "Signature should be present in FIDES-TL.xml")
            assertTrue(result.signatureValid, "FIDES-TL signature should be valid (no longer double-base64)")
            assertTrue(result.valid, "Overall validation should pass")
            assertNotNull(result.signingCertificate, "Signing certificate should be extracted")
        }

    @Test
    fun shouldValidateEuLotlSignature() =
        runTest {
            ensureDomAvailable()

            val euLotl = readTestResource("eu-lotl/eu-lotl.xml")
            val result =
                validator.validate(
                    euLotl.encodeToByteArray(),
                    XAdESValidationOptions(validateReferences = false),
                )

            assertTrue(result.signaturePresent, "Signature should be present in eu-lotl.xml")
            assertTrue(result.signatureValid, "Cryptographic signature should be valid for eu-lotl.xml. Errors: ${result.errors}")
            assertNotNull(result.signingCertificate, "Signing certificate should be extracted")
        }

    @Test
    fun shouldExtractXAdESPropertiesFromFidesTl() =
        runTest {
            ensureDomAvailable()

            val fidesTl = testContext.fetchUrl(FIDES_TL_URL)
            val result =
                validator.validate(
                    fidesTl.encodeToByteArray(),
                    XAdESValidationOptions(validateReferences = false),
                )

            assertTrue(result.signaturePresent)
            assertTrue(result.xadesPresent, "FIDES-TL should have XAdES QualifyingProperties")
            assertNotNull(result.qualifyingProperties)
            assertNotNull(result.signingTime, "FIDES-TL should have a signing time")
        }

    @Test
    fun shouldDetectTamperedContent() =
        runTest {
            ensureDomAvailable()

            val fidesTl = testContext.fetchUrl(FIDES_TL_URL)
            // Tamper with the content to invalidate the signature
            val tampered = fidesTl.replace("FIDES Labs", "TAMPERED Labs")
            val result =
                validator.validate(
                    tampered.encodeToByteArray(),
                    XAdESValidationOptions(validateReferences = true),
                )

            assertTrue(result.signaturePresent)
            // Reference digests should fail since content was tampered
            assertFalse(result.referencesValid, "Tampered content should invalidate references")
        }

    @Test
    fun shouldReportMissingCertificate() =
        runTest {
            ensureDomAvailable()
            val xml = """<Root xmlns="urn:test">
    <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
        <ds:SignedInfo>
            <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
            <ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"/>
            <ds:Reference URI="">
                <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"/>
                <ds:DigestValue>dGVzdA==</ds:DigestValue>
            </ds:Reference>
        </ds:SignedInfo>
        <ds:SignatureValue>dGVzdA==</ds:SignatureValue>
    </ds:Signature>
</Root>"""
            val result =
                validator.validate(
                    xml.encodeToByteArray(),
                    XAdESValidationOptions(validateReferences = false),
                )

            assertFalse(result.valid)
            assertTrue(result.errors.any { "No X.509 certificate" in it })
        }

    @Test
    fun shouldReportMissingSignedInfo() =
        runTest {
            ensureDomAvailable()
            val xml = """<Root xmlns="urn:test">
    <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
        <ds:SignatureValue>dGVzdA==</ds:SignatureValue>
        <ds:KeyInfo>
            <ds:X509Data>
                <ds:X509Certificate>AAAA</ds:X509Certificate>
            </ds:X509Data>
        </ds:KeyInfo>
    </ds:Signature>
</Root>"""
            val result =
                validator.validate(
                    xml.encodeToByteArray(),
                    XAdESValidationOptions(validateReferences = false),
                )

            assertFalse(result.valid)
            assertTrue(result.errors.any { "No SignedInfo" in it })
        }

    @Test
    fun shouldRequireXAdESPropertiesWhenOptionSet() =
        runTest {
            ensureDomAvailable()
            // XML with a signature but no XAdES QualifyingProperties
            val xml = """<Root xmlns="urn:test">
    <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
        <ds:SignedInfo>
            <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#"/>
            <ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha256"/>
            <ds:Reference URI="">
                <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256"/>
                <ds:DigestValue>dGVzdA==</ds:DigestValue>
            </ds:Reference>
        </ds:SignedInfo>
        <ds:SignatureValue>dGVzdA==</ds:SignatureValue>
        <ds:KeyInfo>
            <ds:X509Data>
                <ds:X509Certificate>AAAA</ds:X509Certificate>
            </ds:X509Data>
        </ds:KeyInfo>
    </ds:Signature>
</Root>"""
            val result =
                validator.validate(
                    xml.encodeToByteArray(),
                    XAdESValidationOptions(validateReferences = false, requireXAdESProperties = true),
                )

            assertTrue(result.signaturePresent)
            assertFalse(result.xadesPresent)
            assertFalse(result.valid)
            assertTrue(result.errors.any { "XAdES QualifyingProperties required" in it })
        }

    @Test
    fun shouldReturnCertificateChainFromFidesTl() =
        runTest {
            ensureDomAvailable()

            val fidesTl = testContext.fetchUrl(FIDES_TL_URL)
            val result =
                validator.validate(
                    fidesTl.encodeToByteArray(),
                    XAdESValidationOptions(validateReferences = false),
                )

            assertNotNull(result.signingCertificate)
            assertNotNull(result.certificateChain)
            assertTrue(result.certificateChain!!.isNotEmpty())
            assertTrue(result.signingCertificate!!.contentEquals(result.certificateChain!!.first()))
        }

    @Test
    fun shouldValidateFidesTlWithXAdESProperties() =
        runTest {
            ensureDomAvailable()

            // Online FIDES-TL has valid signatures. XAdES property extraction and
            // signing certificate digest validation should succeed alongside signature validation.
            val fidesTl = testContext.fetchUrl(FIDES_TL_URL)
            val result =
                validator.validate(
                    fidesTl.encodeToByteArray(),
                    XAdESValidationOptions(
                        validateReferences = false,
                        validateSigningCertificate = true,
                        requireXAdESProperties = true,
                    ),
                )

            assertTrue(result.signaturePresent)
            assertTrue(result.signatureValid, "Signature should be valid")
            assertTrue(result.valid, "Overall validation should pass")

            // XAdES properties should be fully extracted
            assertTrue(result.xadesPresent)
            assertNotNull(result.signingTime)
            assertNotNull(result.signingCertificate)
            assertNotNull(result.certificateChain)
            assertEquals(true, result.signingCertificateValid, "Signing certificate digest should match")
        }

    @Test
    fun shouldFullyValidateEuLotl() =
        runTest {
            ensureDomAvailable()

            val euLotl = readTestResource("eu-lotl/eu-lotl.xml")
            val result =
                validator.validate(
                    euLotl.encodeToByteArray(),
                    XAdESValidationOptions(
                        validateReferences = true,
                        validateSigningCertificate = true,
                    ),
                )

            assertTrue(result.signaturePresent)
            assertTrue(result.signatureValid, "Cryptographic signature verification failed. Errors: ${result.errors}")
            assertTrue(result.referencesValid, "Reference digests should all be valid. Errors: ${result.errors}")
            assertNotNull(result.signingCertificate)
        }

    @Test
    fun shouldHandleInvalidXml() =
        runTest {
            ensureDomAvailable()
            val result = validator.validate("not valid xml".encodeToByteArray())

            assertFalse(result.valid)
            assertTrue(result.errors.isNotEmpty())
        }

    @Test
    fun shouldValidateSigningCertificateDigestFromFidesTl() =
        runTest {
            ensureDomAvailable()

            // Signing certificate digest validation compares the hash of the actual X.509 certificate
            // against the digest declared in XAdES SigningCertificateV2.
            val fidesTl = testContext.fetchUrl(FIDES_TL_URL)
            val result =
                validator.validate(
                    fidesTl.encodeToByteArray(),
                    XAdESValidationOptions(validateReferences = false, validateSigningCertificate = true),
                )

            assertTrue(result.signaturePresent)
            assertTrue(result.xadesPresent, "FIDES-TL should have XAdES properties")
            assertNotNull(
                result.qualifyingProperties
                    ?.signedProperties
                    ?.signedSignatureProperties
                    ?.signingCertificateV2,
                "FIDES-TL should have SigningCertificateV2",
            )
            assertNotNull(result.signingCertificateValid, "Should have validated signing certificate")
            assertEquals(true, result.signingCertificateValid, "Signing certificate digest should match")
        }
}
