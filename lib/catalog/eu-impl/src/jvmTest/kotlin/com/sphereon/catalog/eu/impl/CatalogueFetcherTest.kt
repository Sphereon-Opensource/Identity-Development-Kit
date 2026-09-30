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

import com.sphereon.catalog.eu.error.CatalogErrorCode
import com.sphereon.catalog.eu.impl.fetch.CatalogueFetcher
import com.sphereon.catalog.eu.impl.testutil.MapCatalogueHttpClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogueFetcherTest {
    private val main = "https://catalogue.example.org/catalogues/coa.xml?x=1"

    private fun resolve(path: String) = CatalogueFetcher.resolveEntryUrl(main, path)

    @Test
    fun entryPathIsResolvedRelativeToTheMainFileDirectory() {
        assertEquals("https://catalogue.example.org/catalogues/attributes/ns/a.xml", resolve("attributes/ns/a.xml").value)
    }

    @Test
    fun segmentsArePercentEncoded() {
        assertEquals(
            "https://catalogue.example.org/catalogues/a%20b/caf%C3%A9%2Bx.xml",
            resolve("a b/café+x.xml").value,
        )
    }

    @Test
    fun existingEscapesAreNotEncodedTwice() {
        assertEquals("https://catalogue.example.org/catalogues/a%20b.xml", resolve("a%20b.xml").value)
    }

    @Test
    fun dotSegmentsAndAbsoluteReferencesAreRefused() {
        for (path in listOf("../x.xml", "/x.xml", "//evil.example/x.xml", "https://evil.example/x.xml", "a/../../x.xml", "a?b=c", "a#f", "", "a//b.xml")) {
            val result = resolve(path)
            assertTrue(result.isErr, "expected $path to be refused")
            assertEquals(CatalogErrorCode.INVALID_ENTRY_PATH, result.error.errorCode)
        }
    }

    @Test
    fun percentEncodedTraversalAndSeparatorsAreRefused() {
        for (path in listOf("%2e%2e/x.xml", "%2E%2E/x.xml", "a/%2e%2e/%2e%2e/x.xml", ".%2e/x.xml", "a%2Fb.xml", "a%2fb.xml", "a%5Cb.xml", "%2e%2e%2fx.xml", "a%00b.xml", "a%zzb.xml", "a%2", "%C3%28.xml")) {
            val result = resolve(path)
            assertTrue(result.isErr, "expected $path to be refused")
            assertEquals(CatalogErrorCode.INVALID_ENTRY_PATH, result.error.errorCode)
        }
    }

    @Test
    fun encodedDotSegmentIsTreatedAsADotSegment() {
        assertEquals("https://catalogue.example.org/catalogues/x.xml", resolve("%2e/x.xml").value)
    }

    @Test
    fun fetchReturnsTheExactBytes() =
        runTest {
            val bytes = byteArrayOf(0x3c, 0x61, 0x2f, 0x3e, 0x0d, 0x0a)
            val server = MapCatalogueHttpClient(mutableMapOf("https://catalogue.example.org/catalogues/attributes/ns/a.xml" to bytes))
            val fetched = CatalogueFetcher(server).fetchEntry(main, "attributes/ns/a.xml")
            assertTrue(fetched.isOk)
            assertEquals(bytes.toList(), fetched.value.bytes.toList())
        }

    @Test
    fun nonHttpUrlAndOversizeResponsesFail() =
        runTest {
            val server = MapCatalogueHttpClient(mutableMapOf("https://catalogue.example.org/big.xml" to ByteArray(20)))
            val fetcher = CatalogueFetcher(server, maxBytes = 10)
            assertTrue(fetcher.fetch("file:///etc/passwd").isErr)
            assertTrue(fetcher.fetch("https://catalogue.example.org/big.xml").isErr)
            assertTrue(fetcher.fetch("https://catalogue.example.org/missing.xml").isErr)
        }
}
