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

package com.sphereon.catalog.eu.impl

import com.sphereon.catalog.eu.impl.testutil.CatalogueTestContext
import com.sphereon.catalog.eu.impl.testutil.SyntheticCatalogueSigner
import kotlinx.coroutines.test.runTest
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * XML signature wrapping: a signature that is cryptographically valid and made by the authorised signer must still be
 * rejected when it does not cover the whole catalogue.
 */
class CatalogueSignatureWrappingTest {
    private val context = CatalogueTestContext("catalogue-signature-wrapping", this)
    private val verifier = context.signatureVerifier
    private val signer = SyntheticCatalogueSigner()
    private val authorised = listOf(signer.certificateDer)

    private val benign = "<Catalogue><Entry>benign</Entry></Catalogue>".encodeToByteArray()
    private val forged = "<Catalogue><Entry>forged</Entry></Catalogue>".encodeToByteArray()

    private fun parse(xml: ByteArray): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(ByteArrayInputStream(xml))

    private fun serialize(document: Document): ByteArray {
        val out = ByteArrayOutputStream()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(out))
        return out.toByteArray()
    }

    private fun signatureOf(document: Document): Element = document.getElementsByTagNameNS(SyntheticCatalogueSigner.DSIG, "Signature").item(0) as Element

    /** Copies the ds:Signature of [signed] under [parentName] of [target], or directly under the root when null. */
    private fun transplant(
        signed: ByteArray,
        target: ByteArray,
        parentName: String? = null,
    ): ByteArray {
        val signature = signatureOf(parse(signed))
        val document = parse(target)
        val imported = document.importNode(signature, true)
        val parent = if (parentName == null) document.documentElement else document.getElementsByTagName(parentName).item(0)
        parent.appendChild(imported)
        return serialize(document)
    }

    @Test
    fun aFullySignedDocumentIsAccepted() =
        runTest {
            val result = verifier.verify(signer.sign(benign), authorised)
            assertTrue(result.valid, result.errors.toString())
            assertTrue(result.cryptographicallyValid)
        }

    @Test
    fun aValidSignatureOverAnObjectIsNotAcceptedForAForgedCatalogue() =
        runTest {
            // The signature only references its own xades:SignedProperties inside ds:Object: cryptographically valid, covers no catalogue content.
            val decoy = signer.sign(benign, coverDocument = false)
            val standalone = verifier.verify(decoy, authorised)
            assertFalse(standalone.valid, "a signature without a document reference must not be accepted")

            val wrapped = transplant(decoy, forged)
            val result = verifier.verify(wrapped, authorised)
            assertFalse(result.valid)
            assertFalse(result.cryptographicallyValid)
            assertTrue(result.errors.any { it.contains("URI=\"\"") }, result.errors.toString())
        }

    @Test
    fun aSignatureThatIsNotAChildOfTheRootIsRejected() =
        runTest {
            val signed = signer.sign(benign)
            val nested = transplant(signed, "<Catalogue><Wrapper/></Catalogue>".encodeToByteArray(), parentName = "Wrapper")
            val result = verifier.verify(nested, authorised)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("direct child") }, result.errors.toString())
        }

    @Test
    fun aSecondSignatureInTheDocumentIsRejected() =
        runTest {
            val signed = signer.sign(benign)
            val twice = transplant(signed, signed)
            val result = verifier.verify(twice, authorised)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("exactly one") }, result.errors.toString())
        }

    @Test
    fun duplicateIdAttributesAreRejected() =
        runTest {
            val duplicated = "<Catalogue><A Id=\"x\"/><B Id=\"x\"/></Catalogue>".encodeToByteArray()
            val result = verifier.verify(signer.sign(duplicated), authorised)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("duplicate Id") }, result.errors.toString())
        }

    @Test
    fun aReferenceToTheRootElementByIdIsAccepted() =
        runTest {
            val withId = "<Catalogue Id=\"doc1\"><Entry>benign</Entry></Catalogue>".encodeToByteArray()
            val result = verifier.verify(signer.sign(withId, documentReferenceUri = "#doc1"), authorised)
            assertTrue(result.valid, result.errors.toString())
        }

    @Test
    fun aReferenceToANonRootElementByIdIsRejected() =
        runTest {
            val withId = "<Catalogue><Entry Id=\"part\">benign</Entry><Entry>unsigned</Entry></Catalogue>".encodeToByteArray()
            val result = verifier.verify(signer.sign(withId, documentReferenceUri = "#part"), authorised)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("URI=\"\"") }, result.errors.toString())
        }

    @Test
    fun signedPropertiesThatAreNotReferencedAreRejected() =
        runTest {
            val result = verifier.verify(signer.sign(benign, coverSignedProperties = false), authorised)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("SignedProperties") }, result.errors.toString())
        }

    @Test
    fun aMissingSigningCertificateV2IsAnError() =
        runTest {
            val result = verifier.verify(signer.sign(benign, includeSigningCertificateV2 = false), authorised)
            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("SigningCertificateV2") }, result.errors.toString())
        }
}
