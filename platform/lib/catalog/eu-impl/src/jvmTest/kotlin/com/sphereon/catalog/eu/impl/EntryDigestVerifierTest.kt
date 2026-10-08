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

import com.sphereon.catalog.eu.EuCatalogueConstants
import com.sphereon.catalog.eu.impl.testutil.CatalogueFixtures
import com.sphereon.catalog.eu.impl.testutil.CatalogueTestContext
import com.sphereon.catalog.eu.impl.testutil.SyntheticCatalogueSigner
import com.sphereon.catalog.eu.model.EntryReference
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EntryDigestVerifierTest {
    private val verifier = CatalogueTestContext("entry-digest", this).digestVerifier
    private val entry = CatalogueFixtures.bytes("synthetic/attributes/eu.europa.ec.eudi.pid.1/family_name.xml")

    private fun reference(
        digest: ByteArray,
        method: String = EuCatalogueConstants.DIGEST_SHA512_SPEC,
        transforms: List<String> = listOf(EuCatalogueConstants.EXC_C14N_TRANSFORM),
    ) = EntryReference("attributes/x.xml", transforms, method, digest)

    private fun computed(bytes: ByteArray = entry): ByteArray {
        val probe = verifier.verify(reference(ByteArray(64)), bytes)
        return assertNotNull(probe.computed)
    }

    @Test
    fun computedDigestEqualsTheJdkExclusiveCanonicalizationDigest() {
        // The JDK signer digests the whole document with exc-c14n and SHA-512 as the first reference.
        val signed = SyntheticCatalogueSigner().sign(entry).decodeToString()
        val jdkDigest = Regex("<ds:DigestValue>([^<]*)</ds:DigestValue>").find(signed)!!.groupValues[1].replace("&#13;", "").filterNot { it.isWhitespace() }
        assertEquals(jdkDigest, Base64.getEncoder().encodeToString(computed()))
    }

    @Test
    fun matchingDigestIsAcceptedWithTheSpecMethodUri() {
        val result = verifier.verify(reference(computed(), EuCatalogueConstants.DIGEST_SHA512_SPEC), entry)
        assertTrue(result.valid, result.error)
    }

    @Test
    fun matchingDigestIsAcceptedWithTheStandardXmlencMethodUri() {
        val result = verifier.verify(reference(computed(), EuCatalogueConstants.DIGEST_SHA512_XMLENC), entry)
        assertTrue(result.valid, result.error)
    }

    @Test
    fun unsupportedDigestMethodIsRejected() {
        val result = verifier.verify(reference(computed(), "http://www.w3.org/2001/04/xmlenc#sha256"), entry)
        assertFalse(result.valid)
        assertTrue(result.error!!.contains("Unsupported digest method"))
    }

    @Test
    fun modifiedEntryIsADigestMismatch() {
        val digest = computed()
        val modified = entry.decodeToString().replace("family_name", "family_nome").encodeToByteArray()
        val result = verifier.verify(reference(digest), modified)
        assertFalse(result.valid)
        assertFalse(result.rawBytesDigestMatches)
    }

    @Test
    fun whitespaceOutsideTheRootDoesNotChangeTheCanonicalDigest() {
        val digest = computed()
        val reformatted = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n\n" + entry.decodeToString().substringAfter("?>").trim() + "\n\n").encodeToByteArray()
        assertTrue(verifier.verify(reference(digest), reformatted).valid)
    }

    @Test
    fun rawBytesDigestIsOnlyADiagnostic() {
        val raw = MessageDigest.getInstance("SHA-512").digest(entry)
        val result = verifier.verify(reference(raw), entry)
        assertFalse(result.valid)
        assertTrue(result.rawBytesDigestMatches)
    }

    @Test
    fun noTransformsMeansRawByteDigest() {
        val raw = MessageDigest.getInstance("SHA-512").digest(entry)
        assertTrue(verifier.verify(reference(raw, transforms = emptyList()), entry).valid)
    }

    @Test
    fun unknownTransformIsRejected() {
        val result = verifier.verify(reference(computed(), transforms = listOf("http://example.org/unknown")), entry)
        assertFalse(result.valid)
        assertTrue(result.error!!.contains("Unsupported transform"))
    }

    @Test
    fun malformedXmlIsRejected() {
        val result = verifier.verify(reference(computed()), "<a><b></a>".encodeToByteArray())
        assertFalse(result.valid)
    }
}
