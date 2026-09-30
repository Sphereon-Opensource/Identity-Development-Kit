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
import com.sphereon.catalog.eu.model.CataloguePointer
import com.sphereon.catalog.eu.parser.DefaultEuCatalogueXmlParser
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.decodeFrom
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiveCatalogueSignatureTest {
    private val context = CatalogueTestContext("live-catalogue-signature", this)
    private val verifier = context.signatureVerifier

    private val pointers: List<CataloguePointer> =
        DefaultEuCatalogueXmlParser()
            .parseLoc(CatalogueFixtures.bytes("live/loc.xml"))
            .let { assertTrue(it.isOk); it.value.value.pointers }

    private fun certificates(type: String): List<ByteArray> =
        pointers
            .single { it.loteType == type }
            .signerCertificates
            .map { it.filterNot(Char::isWhitespace).decodeFrom(Encoding.BASE64) }

    @Test
    fun liveLocSignatureVerifiesAgainstItsPointerCertificates() =
        runTest {
            val result = verifier.verify(CatalogueFixtures.bytes("live/loc.xml"), certificates(EuCatalogueConstants.LOC_TYPE))
            assertTrue(result.valid, "errors: ${result.errors}")
            assertTrue(result.signerAuthorised)
            assertNotNull(result.signingTime)
        }

    @Test
    fun liveCoaSignatureVerifiesAgainstItsPointerCertificates() =
        runTest {
            val result = verifier.verify(CatalogueFixtures.bytes("live/coa.xml"), certificates(EuCatalogueConstants.COA_LOTE_TYPE))
            assertTrue(result.valid, "errors: ${result.errors}")
        }

    @Test
    fun liveCosSignatureVerifiesAgainstItsPointerCertificates() =
        runTest {
            val result = verifier.verify(CatalogueFixtures.bytes("live/cos.xml"), certificates(EuCatalogueConstants.COS_LOTE_TYPE))
            assertTrue(result.valid, "errors: ${result.errors}")
        }

    @Test
    fun tamperedLiveCoaFailsVerification() =
        runTest {
            val original = CatalogueFixtures.text("live/coa.xml")
            val tampered = original.replace("EU:Catalogue of attributes", "EU:Catalogue of attributez")
            assertFalse(original == tampered)
            val result = verifier.verify(tampered.encodeToByteArray(), certificates(EuCatalogueConstants.COA_LOTE_TYPE))
            assertFalse(result.valid)
            assertFalse(result.cryptographicallyValid)
        }

    @Test
    fun liveCoaSignedByAnUnlistedCertificateIsNotAuthorised() =
        runTest {
            val stranger = SyntheticCatalogueSigner().certificateDer
            val result = verifier.verify(CatalogueFixtures.bytes("live/coa.xml"), listOf(stranger))
            assertFalse(result.valid)
            assertFalse(result.signerAuthorised)
            assertEquals(true, result.cryptographicallyValid)
        }

    @Test
    fun emptyAuthorisedSignerListIsRejected() =
        runTest {
            val result = verifier.verify(CatalogueFixtures.bytes("live/coa.xml"), emptyList())
            assertFalse(result.valid)
        }
}
