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

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.catalog.eu.impl

import com.sphereon.catalog.eu.EuCatalogueConstants
import com.sphereon.catalog.eu.impl.chain.DirectValidationRequest
import com.sphereon.catalog.eu.impl.chain.EuCatalogueChainValidator
import com.sphereon.catalog.eu.impl.fetch.CatalogueFetcher
import com.sphereon.catalog.eu.impl.publish.CatalogueSigningKey
import com.sphereon.catalog.eu.impl.publish.PublishedCatalogue
import com.sphereon.catalog.eu.impl.testutil.CatalogueFixtures
import com.sphereon.catalog.eu.impl.testutil.CatalogueXsd
import com.sphereon.catalog.eu.impl.testutil.CatalogueTestContext
import com.sphereon.catalog.eu.impl.testutil.MapCatalogueHttpClient
import com.sphereon.catalog.eu.impl.testutil.newKmsSigningKey
import com.sphereon.catalog.eu.model.AttributeEntry
import com.sphereon.catalog.eu.model.AttributeNamespace
import com.sphereon.catalog.eu.model.CatalogueFinding
import com.sphereon.catalog.eu.model.CatalogueKind
import com.sphereon.catalog.eu.model.CatalogueOfAttributes
import com.sphereon.catalog.eu.model.CatalogueOfSchemes
import com.sphereon.catalog.eu.model.EaaSchemeEntry
import com.sphereon.catalog.eu.model.FindingSeverity
import com.sphereon.catalog.eu.parser.DefaultEuCatalogueXmlParser
import com.sphereon.catalog.eu.serializer.CatalogueEntryPaths
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CatalogueXmlPublisherTest {
    private val context = CatalogueTestContext("catalogue-publisher", this)
    private val parser = DefaultEuCatalogueXmlParser()
    private val signingTime = Instant.parse("2026-09-29T10:00:00Z")

    private val base = "https://catalogue.example.org/custom/"
    private val ns = "eu.europa.ec.eudi.pid.1"

    private fun attribute(name: String) = parser.parseAttributeEntry(CatalogueFixtures.bytes("synthetic/attributes/$ns/$name.xml")).value.value

    private fun coaModel(): CatalogueOfAttributes {
        val fixture = parser.parseCoa(CatalogueFixtures.bytes("synthetic/coa-populated.xml")).value.value
        return fixture.copy(
            info = fixture.info.copy(identifier = "urn:example:custom-attributes", sequenceNumber = 3),
            namespaces = listOf(AttributeNamespace(ns)),
            hasSignature = false,
        )
    }

    private fun cosModel(): CatalogueOfSchemes {
        val fixture = parser.parseCos(CatalogueFixtures.bytes("synthetic/cos-populated.xml")).value.value
        return fixture.copy(info = fixture.info.copy(identifier = "urn:example:custom-schemes", sequenceNumber = 7), schemes = emptyList(), hasSignature = false)
    }

    private fun scheme() = parser.parseSchemeEntry(CatalogueFixtures.bytes("synthetic/schemes/eu-pid.xml")).value.value

    private fun serve(
        coa: PublishedCatalogue?,
        cos: PublishedCatalogue?,
    ): MapCatalogueHttpClient {
        val server = MapCatalogueHttpClient()
        coa?.let {
            server.files[base + "coa.xml"] = it.mainXml
            it.entryFiles.forEach { file -> server.files[base + file.path] = file.bytes }
        }
        cos?.let {
            server.files[base + "cos.xml"] = it.mainXml
            it.entryFiles.forEach { file -> server.files[base + file.path] = file.bytes }
        }
        return server
    }

    private fun validator(server: MapCatalogueHttpClient) = EuCatalogueChainValidator(CatalogueFetcher(server), context.signatureVerifier, context.digestVerifier)

    private fun errors(findings: List<CatalogueFinding>) = findings.filter { it.severity == FindingSeverity.ERROR }.map { it.code to it.message }

    @Test
    fun attributesPublishedWithAnRsaKmsKeyVerifyThroughTheChainValidator() =
        runTest {
            val key = context.newKmsSigningKey(SignatureAlgorithm.RSA_SHA512, "catalogue rsa signer")
            val entries = listOf(ns to attribute("family_name"), ns to attribute("birth_date"))
            val result = context.publisher.publishAttributes(coaModel(), entries, key.signingKey, signingTime)
            assertTrue(result.isOk, "publish failed: ${if (result.isErr) result.error.message else ""}")
            val published = result.value

            assertEquals(CatalogueKind.COA, published.kind)
            assertEquals(3, published.sequenceNumber)
            assertEquals(
                setOf("attributes/$ns/family_name.xml", "attributes/$ns/birth_date.xml"),
                published.entryFiles.map { it.path }.toSet(),
            )
            assertEquals(signingTime, published.signingTime)
            assertTrue(published.signerCertificates.single().contentEquals(key.certificateDer))

            val outcome =
                validator(serve(published, null))
                    .validateDirect(DirectValidationRequest(coaUrl = base + "coa.xml", signerCertificates = listOf(key.certificateDer)))
            assertTrue(outcome.isOk, "direct validation failed: ${if (outcome.isErr) outcome.error.reason else ""}")
            assertEquals(emptyList(), errors(outcome.value.findings))
            assertNotNull(outcome.value.coa)
            assertEquals(entries.map { it.second }.toSet(), outcome.value.attributeEntries.toSet())
            assertEquals(3, outcome.value.coa!!.info.sequenceNumber)
            assertEquals(1, outcome.value.signerEvidence.size)
        }

    @Test
    fun theSignatureIsXadesBaselineBWithRsaSha512AndTheSignerVerifierAcceptsIt() =
        runTest {
            val key = context.newKmsSigningKey(SignatureAlgorithm.RSA_SHA512, "catalogue rsa signer")
            val published = context.publisher.publishAttributes(coaModel(), listOf(ns to attribute("family_name")), key.signingKey, signingTime).value
            val xml = published.mainXml.decodeToString()
            assertTrue(xml.contains("http://www.w3.org/2001/04/xmldsig-more#rsa-sha512"))
            assertTrue(xml.contains("<xades:SigningCertificateV2>"))
            assertTrue(xml.contains("<xades:SigningTime>2026-09-29T10:00:00Z</xades:SigningTime>"))
            assertTrue(xml.contains(EuCatalogueConstants.EXC_C14N_TRANSFORM))

            val verified = context.signatureVerifier.verify(published.mainXml, listOf(key.certificateDer))
            assertTrue(verified.valid, verified.errors.toString())
            assertTrue(verified.cryptographicallyValid)
            assertTrue(verified.signerAuthorised)
            assertEquals(signingTime, verified.signingTime)

            val other = context.newKmsSigningKey(SignatureAlgorithm.RSA_SHA512, "another signer")
            val unauthorised = context.signatureVerifier.verify(published.mainXml, listOf(other.certificateDer))
            assertTrue(!unauthorised.valid)
        }

    @Test
    fun anEcdsaKeyIsSignedWithTheEcdsaEquivalent() =
        runTest {
            val key = context.newKmsSigningKey(SignatureAlgorithm.ECDSA_SHA256, "catalogue ec signer")
            val published = context.publisher.publishAttributes(coaModel(), listOf(ns to attribute("birth_date")), key.signingKey, signingTime)
            assertTrue(published.isOk, "publish failed: ${if (published.isErr) published.error.message else ""}")
            assertTrue(published.value.mainXml.decodeToString().contains("http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256"))
            val verified = context.signatureVerifier.verify(published.value.mainXml, listOf(key.certificateDer))
            assertTrue(verified.valid, verified.errors.toString())
        }

    @Test
    fun schemesPublishedWithAKmsKeyVerifyThroughTheChainValidator() =
        runTest {
            val key = context.newKmsSigningKey(SignatureAlgorithm.RSA_SHA512, "catalogue scheme signer")
            val entry: EaaSchemeEntry = scheme()
            val result = context.publisher.publishSchemes(cosModel(), listOf(entry), key.signingKey, signingTime)
            assertTrue(result.isOk, "publish failed: ${if (result.isErr) result.error.message else ""}")
            val published = result.value
            assertEquals(CatalogueKind.COS, published.kind)
            assertEquals(listOf(CatalogueEntryPaths.schemeEntry(entry.name)), published.entryFiles.map { it.path })

            val outcome =
                validator(serve(null, published))
                    .validateDirect(DirectValidationRequest(cosUrl = base + "cos.xml", signerCertificates = listOf(key.certificateDer)))
            assertTrue(outcome.isOk, "direct validation failed: ${if (outcome.isErr) outcome.error.reason else ""}")
            assertEquals(emptyList(), errors(outcome.value.findings))
            assertNotNull(outcome.value.cos)
            assertEquals(listOf(entry), outcome.value.schemeEntries)
            assertEquals(7, outcome.value.cos!!.info.sequenceNumber)
        }

    @Test
    fun theSignedMainFileParsesBackToTheAuthoredModel() =
        runTest {
            val key = context.newKmsSigningKey(SignatureAlgorithm.RSA_SHA512, "catalogue rsa signer")
            val model = coaModel()
            val published = context.publisher.publishAttributes(model, listOf(ns to attribute("family_name"), ns to attribute("birth_date")), key.signingKey, signingTime).value
            val coa = parser.parseCoa(published.mainXml).value.value
            assertTrue(coa.hasSignature)
            assertEquals(model.info, coa.info)
            assertEquals(listOf(ns), coa.namespaces.map { it.identifier })
            assertEquals(setOf("family_name", "birth_date"), coa.namespaces.single().entries.map { it.attributeIdentifier }.toSet())
            for (file in published.entryFiles) {
                val reparsed: AttributeEntry = parser.parseAttributeEntry(file.bytes).value.value
                assertEquals(reparsed, attribute(reparsed.attributeIdentifier))
            }
        }

    @Test
    fun aTamperedPublishedEntryFileFailsTheDigestCheck() =
        runTest {
            val key = context.newKmsSigningKey(SignatureAlgorithm.RSA_SHA512, "catalogue rsa signer")
            val published = context.publisher.publishAttributes(coaModel(), listOf(ns to attribute("family_name")), key.signingKey, signingTime).value
            val server = serve(published, null)
            val entryUrl = base + published.entryFiles.single().path
            server.files[entryUrl] = server.files.getValue(entryUrl).decodeToString().replace("Family name", "Family nome").encodeToByteArray()
            val outcome = validator(server).validateDirect(DirectValidationRequest(coaUrl = base + "coa.xml", signerCertificates = listOf(key.certificateDer))).value
            assertTrue(outcome.coa == null)
            assertTrue(errors(outcome.findings).isNotEmpty())
        }

    @Test
    fun aKeyThatCannotBeResolvedFailsClearly() =
        runTest {
            val missing = CatalogueSigningKey(KeyInfo<KeyType>(alias = "no-such-key-alias"))
            val result = context.publisher.publishAttributes(coaModel(), listOf(ns to attribute("family_name")), missing, signingTime)
            assertTrue(result.isErr)
            assertTrue(result.error.code.startsWith("CATALOGUE_SIGNING"), result.error.code)
        }

    @Test
    fun aDocumentIsNeverSignedTwice() =
        runTest {
            val key = context.newKmsSigningKey(SignatureAlgorithm.RSA_SHA512, "catalogue rsa signer")
            val published = context.publisher.publishAttributes(coaModel(), listOf(ns to attribute("family_name")), key.signingKey, signingTime).value
            assertTrue(context.signer.sign(published.mainXml, key.signingKey, signingTime).isErr)
        }

    @Test
    fun everyPublishedFileIsValidAgainstTheOfficialSchemas() =
        runTest {
            val xsd = CatalogueXsd.fromFixtures()
            val key = context.newKmsSigningKey(SignatureAlgorithm.RSA_SHA512, "catalogue xsd signer")
            val attributes = context.publisher.publishAttributes(coaModel(), listOf(ns to attribute("family_name"), ns to attribute("birth_date")), key.signingKey, signingTime).value
            assertEquals(emptyList(), xsd.errors(xsd.coa, attributes.mainXml))
            assertEquals(2, attributes.entryFiles.size)
            attributes.entryFiles.forEach { assertEquals(emptyList(), xsd.errors(xsd.attribute, it.bytes), it.path) }

            val schemes = context.publisher.publishSchemes(cosModel(), listOf(scheme()), key.signingKey, signingTime).value
            assertEquals(emptyList(), xsd.errors(xsd.cos, schemes.mainXml))
            schemes.entryFiles.forEach { assertEquals(emptyList(), xsd.errors(xsd.scheme, it.bytes), it.path) }
        }

    @Test
    fun theSignedFixtureSetsAreSchemaValidToo() {
        val xsd = CatalogueXsd.fromFixtures()
        val synthetic = com.sphereon.catalog.eu.impl.testutil.SyntheticCatalogueSet(context.digestVerifier)
        assertEquals(emptyList(), xsd.errors(xsd.coa, synthetic.coa))
        assertEquals(emptyList(), xsd.errors(xsd.cos, synthetic.cos))
        assertEquals(emptyList(), xsd.errors(xsd.attribute, synthetic.familyName))
        assertEquals(emptyList(), xsd.errors(xsd.scheme, synthetic.scheme))
    }

    @Test
    fun aDuplicateEntryIsRefused() =
        runTest {
            val key = context.newKmsSigningKey(SignatureAlgorithm.RSA_SHA512, "catalogue rsa signer")
            val result = context.publisher.publishAttributes(coaModel(), listOf(ns to attribute("family_name"), ns to attribute("family_name")), key.signingKey, signingTime)
            assertTrue(result.isErr)
        }

    private fun jdkVerifies(
        xml: ByteArray,
        certificateDer: ByteArray,
    ): Boolean {
        val document =
            javax.xml.parsers.DocumentBuilderFactory
                .newInstance()
                .apply { isNamespaceAware = true }
                .newDocumentBuilder()
                .parse(java.io.ByteArrayInputStream(xml))
        val certificate = java.security.cert.CertificateFactory.getInstance("X.509").generateCertificate(java.io.ByteArrayInputStream(certificateDer))
        val signature = document.getElementsByTagNameNS("http://www.w3.org/2000/09/xmldsig#", "Signature").item(0)
        val signedProperties = document.getElementsByTagNameNS("http://uri.etsi.org/01903/v1.3.2#", "SignedProperties").item(0) as org.w3c.dom.Element
        val context = javax.xml.crypto.dsig.dom.DOMValidateContext(certificate.publicKey, signature)
        context.setIdAttributeNS(signedProperties, null, "Id")
        val factory = javax.xml.crypto.dsig.XMLSignatureFactory.getInstance("DOM")
        return factory.unmarshalXMLSignature(context).validate(context)
    }

    @Test
    fun anEcdsaSignedCatalogueVerifiesWithTheJdkXmlDsigVerifier() =
        runTest {
            for (algorithm in listOf(SignatureAlgorithm.ECDSA_SHA256, SignatureAlgorithm.ECDSA_SHA384)) {
                val key = context.newKmsSigningKey(algorithm, "catalogue ec jdk signer")
                val published = context.publisher.publishAttributes(coaModel(), listOf(ns to attribute("birth_date")), key.signingKey, signingTime)
                assertTrue(published.isOk, "publish failed: ${if (published.isErr) published.error.message else ""}")
                // The JDK verifier rejects a signature value that is not the XMLDSig r||s encoding.
                assertTrue(jdkVerifies(published.value.mainXml, key.certificateDer), "JDK XMLDSig rejected the $algorithm signature")
            }
        }

    @Test
    fun anRsaSignedCatalogueVerifiesWithTheJdkXmlDsigVerifier() =
        runTest {
            val key = context.newKmsSigningKey(SignatureAlgorithm.RSA_SHA512, "catalogue rsa jdk signer")
            val published = context.publisher.publishAttributes(coaModel(), listOf(ns to attribute("birth_date")), key.signingKey, signingTime).value
            assertTrue(jdkVerifies(published.mainXml, key.certificateDer))
        }

    @Test
    fun aCertificateThatDoesNotBelongToTheKmsKeyIsRefused() =
        runTest {
            val key = context.newKmsSigningKey(SignatureAlgorithm.RSA_SHA512, "catalogue key")
            val other = context.newKmsSigningKey(SignatureAlgorithm.RSA_SHA512, "another key")
            val mismatched = CatalogueSigningKey(key.signingKey.keyInfo, listOf(other.certificateDer), SignatureAlgorithm.RSA_SHA512)
            val result = context.publisher.publishAttributes(coaModel(), listOf(ns to attribute("family_name")), mismatched, signingTime)
            assertTrue(result.isErr)
            assertEquals("CATALOGUE_SIGNING_CERTIFICATE_MISMATCH", result.error.code)
        }

    @Test
    fun anAlgorithmThatDoesNotMatchTheKeyTypeIsRefused() =
        runTest {
            val key = context.newKmsSigningKey(SignatureAlgorithm.ECDSA_SHA256, "catalogue ec key")
            val wrong = CatalogueSigningKey(key.signingKey.keyInfo, listOf(key.certificateDer), SignatureAlgorithm.RSA_SHA512)
            val result = context.publisher.publishAttributes(coaModel(), listOf(ns to attribute("family_name")), wrong, signingTime)
            assertTrue(result.isErr)
            assertEquals("CATALOGUE_SIGNING_ALGORITHM_MISMATCH", result.error.code)
        }

    @Test
    fun identifiersThatWouldProduceDotSegmentsAreRefusedAtPublishTime() =
        runTest {
            val key = context.newKmsSigningKey(SignatureAlgorithm.RSA_SHA512, "catalogue rsa signer")
            for (bad in listOf(".", "..", "")) {
                val namespace = context.publisher.publishAttributes(coaModel(), listOf(bad to attribute("family_name")), key.signingKey, signingTime)
                assertTrue(namespace.isErr, "namespace '$bad' must be refused")
                val attributeId = context.publisher.publishAttributes(coaModel(), listOf(ns to attribute("family_name").copy(attributeIdentifier = bad)), key.signingKey, signingTime)
                assertTrue(attributeId.isErr, "attribute identifier '$bad' must be refused")
                val schemeName = context.publisher.publishSchemes(cosModel(), listOf(scheme().copy(name = bad)), key.signingKey, signingTime)
                assertTrue(schemeName.isErr, "scheme name '$bad' must be refused")
            }
        }
}
