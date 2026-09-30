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

package com.sphereon.trust.etsi.signature

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.x509.X509VerificationRequestType
import com.sphereon.crypto.core.x509.X509VerificationResult
import com.sphereon.crypto.core.x509.X509VerificationResultType
import com.sphereon.crypto.core.x509.X509VerifyService
import com.sphereon.trust.etsi.testutil.EtsiTestContext
import com.sphereon.trust.etsi.testutil.SyntheticTrustListSigner
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
 * XML signature wrapping against the trusted-list / LOTL / LoTE gate: a signature that is cryptographically valid and
 * made by the pinned signer must still be rejected when it does not cover the whole list.
 */
class TrustListSignatureWrappingTest {
    private val context = EtsiTestContext("trust-list-signature-wrapping", this)
    private val signer = SyntheticTrustListSigner()
    private val options = XmlSignatureVerificationOptions(trustedRoots = listOf(signer.certificateDer))

    private val verifier: XmlSignatureVerifier =
        XmlUtilSignatureVerifier(
            keyManagerService = context.keyManagerService,
            x509VerifyService = RejectingX509VerifyService,
            execution = context.session.asCoreApiServiceGraph().serviceExecution,
        )

    private val benign = "<TrustServiceStatusList><Entry>benign</Entry></TrustServiceStatusList>".encodeToByteArray()
    private val forged = "<TrustServiceStatusList><Entry>forged</Entry></TrustServiceStatusList>".encodeToByteArray()

    private fun parse(xml: ByteArray): Document =
        DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(ByteArrayInputStream(xml))

    private fun serialize(document: Document): ByteArray {
        val out = ByteArrayOutputStream()
        TransformerFactory.newInstance().newTransformer().transform(DOMSource(document), StreamResult(out))
        return out.toByteArray().decodeToString().replace("\r\n", "\n").encodeToByteArray()
    }

    private fun signatureOf(document: Document): Element = document.getElementsByTagNameNS(SyntheticTrustListSigner.DSIG, "Signature").item(0) as Element

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

    private suspend fun verify(xml: ByteArray) = verifier.verifyFromBytes(xml, options)

    @Test
    fun aFullySignedListIsAccepted() =
        runTest {
            val result = verify(signer.sign(benign))
            assertTrue(result.valid, result.errorMessage)
        }

    @Test
    fun aSignatureThatOnlyCoversItsSignedPropertiesIsRejectedWhenTransplanted() =
        runTest {
            val decoy = signer.sign(benign, coverDocument = false)
            assertFalse(verify(decoy).valid, "a signature without a document reference must not be accepted")

            val result = verify(transplant(decoy, forged))
            assertFalse(result.valid)
            assertTrue(result.errorMessage.orEmpty().contains("URI=\"\""), result.errorMessage)
        }

    @Test
    fun aTransplantedIdReferenceToANonRootElementIsRejected() =
        runTest {
            val original = "<TrustServiceStatusList><Entry Id=\"part\">benign</Entry></TrustServiceStatusList>".encodeToByteArray()
            val signed = signer.sign(original, documentReferenceUri = "#part")
            val attackerDocument =
                "<TrustServiceStatusList><Entry Id=\"part\">benign</Entry><Entry>forged</Entry></TrustServiceStatusList>".encodeToByteArray()
            val result = verify(transplant(signed, attackerDocument))
            assertFalse(result.valid)
            assertTrue(result.errorMessage.orEmpty().contains("URI=\"\""), result.errorMessage)
        }

    @Test
    fun theOriginalSignedDocumentNestedUnderANewRootIsRejected() =
        runTest {
            val signed = signer.sign(benign).decodeToString().substringAfter("?>")
            val wrapped = "<Wrapper><Entry>forged</Entry>$signed</Wrapper>".encodeToByteArray()
            val result = verify(wrapped)
            assertFalse(result.valid)
        }

    @Test
    fun aSignatureThatIsNotAChildOfTheRootIsRejected() =
        runTest {
            val signed = signer.sign(benign)
            val nested = transplant(signed, "<TrustServiceStatusList><Wrapper/></TrustServiceStatusList>".encodeToByteArray(), parentName = "Wrapper")
            val result = verify(nested)
            assertFalse(result.valid)
        }

    @Test
    fun aSecondSignatureInTheDocumentIsRejected() =
        runTest {
            val signed = signer.sign(benign)
            val result = verify(transplant(signed, signed))
            assertFalse(result.valid)
            assertTrue(result.errorMessage.orEmpty().contains("exactly one"), result.errorMessage)
        }

    @Test
    fun duplicateIdAttributesAreRejected() =
        runTest {
            val duplicated = "<TrustServiceStatusList><A Id=\"x\"/><B Id=\"x\"/></TrustServiceStatusList>".encodeToByteArray()
            val result = verify(signer.sign(duplicated))
            assertFalse(result.valid)
            assertTrue(result.errorMessage.orEmpty().contains("duplicate Id"), result.errorMessage)
        }

    @Test
    fun aReferenceToTheRootElementByIdIsAccepted() =
        runTest {
            val withId = "<TrustServiceStatusList Id=\"doc1\"><Entry>benign</Entry></TrustServiceStatusList>".encodeToByteArray()
            val result = verify(signer.sign(withId, documentReferenceUri = "#doc1"))
            assertTrue(result.valid, result.errorMessage)
        }

    @Test
    fun signedPropertiesThatAreNotReferencedAreRejected() =
        runTest {
            val result = verify(signer.sign(benign, coverSignedProperties = false))
            assertFalse(result.valid)
            assertTrue(result.errorMessage.orEmpty().contains("SignedProperties"), result.errorMessage)
        }

    private object RejectingX509VerifyService : X509VerifyService {
        override suspend fun verifyCertificateChain(req: X509VerificationRequestType): X509VerificationResultType =
            X509VerificationResult(
                certificateChain = emptyArray(),
                critical = false,
                message = "test chain rejected",
                error = true,
            )

        override fun setTrustedCerts(trustedCerts: Array<String>?): X509VerifyService = this

        override fun getTrustedCerts(): Array<String>? = null
    }
}
