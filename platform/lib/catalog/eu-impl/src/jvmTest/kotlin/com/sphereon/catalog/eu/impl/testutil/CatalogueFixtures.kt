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

package com.sphereon.catalog.eu.impl.testutil

import com.sphereon.catalog.eu.impl.digest.CatalogueEntryDigestVerifier
import com.sphereon.catalog.eu.impl.fetch.CatalogueHttpClient
import com.sphereon.catalog.eu.error.CatalogError
import com.sphereon.catalog.eu.error.CatalogErrorCode
import com.sphereon.catalog.eu.model.EntryReference
import com.sphereon.core.api.IdkErrorResult
import com.sphereon.core.api.IdkOkResult
import com.sphereon.core.api.IdkResult
import java.io.File

/**
 * Reads the fixtures owned by the eu-public module.
 */
object CatalogueFixtures {
    private val root = File("../eu-public/src/commonTest/resources/eu-catalogues")

    fun bytes(path: String): ByteArray {
        val file = File(root, path)
        require(file.exists()) { "Fixture not found: ${file.absolutePath}" }
        return file.readBytes()
    }

    fun text(path: String): String = bytes(path).decodeToString()

    const val LOC_URL = "https://trust.tech.ec.europa.eu/catalogues/tools/eu-loc.xml"
    const val COA_URL = "https://trust.tech.ec.europa.eu/catalogues/eu-catalogue-of-attributes.xml"
    const val COS_URL = "https://trust.tech.ec.europa.eu/catalogues/eu-catalogue-of-schemes.xml"
    const val FAMILY_NAME_URL = "https://trust.tech.ec.europa.eu/catalogues/attributes/eu.europa.ec.eudi.pid.1/family_name.xml"
    const val BIRTH_DATE_URL = "https://trust.tech.ec.europa.eu/catalogues/attributes/eu.europa.ec.eudi.pid.1/birth_date.xml"
    const val SCHEME_URL = "https://trust.tech.ec.europa.eu/catalogues/schemes/eu-pid.xml"
}

/**
 * An in-memory catalogue server.
 */
class MapCatalogueHttpClient(
    val files: MutableMap<String, ByteArray> = mutableMapOf(),
) : CatalogueHttpClient {
    val requested = mutableListOf<String>()

    override suspend fun get(url: String): IdkResult<ByteArray, CatalogError> {
        requested.add(url)
        val bytes = files[url] ?: return IdkErrorResult(CatalogError(CatalogErrorCode.FETCH_FAILED, "HTTP 404", url))
        return IdkOkResult(bytes)
    }
}

/**
 * A complete signed synthetic catalogue set built from the unsigned eu-public fixtures: entry digests are computed
 * over the canonical form of each entry file and written into the indexes, then every main file is signed.
 */
class SyntheticCatalogueSet(
    digestVerifier: CatalogueEntryDigestVerifier,
    val locSigner: SyntheticCatalogueSigner = SyntheticCatalogueSigner(),
    val coaSigner: SyntheticCatalogueSigner = SyntheticCatalogueSigner(),
    val cosSigner: SyntheticCatalogueSigner = SyntheticCatalogueSigner(),
    familyName: ByteArray = CatalogueFixtures.bytes("synthetic/attributes/eu.europa.ec.eudi.pid.1/family_name.xml"),
    birthDate: ByteArray = CatalogueFixtures.bytes("synthetic/attributes/eu.europa.ec.eudi.pid.1/birth_date.xml"),
    scheme: ByteArray = CatalogueFixtures.bytes("synthetic/schemes/eu-pid.xml"),
    coaSequence: Long? = null,
    coaPointerSigner: SyntheticCatalogueSigner = coaSigner,
    coaIdentifier: String? = null,
) {
    val familyName = familyName
    val birthDate = birthDate
    val scheme = scheme
    val coa: ByteArray
    val cos: ByteArray
    val loc: ByteArray

    init {
        fun digest(bytes: ByteArray): String {
            val probe = EntryReference("x", listOf(TRANSFORM), METHOD, ByteArray(0))
            return java.util.Base64
                .getEncoder()
                .encodeToString(digestVerifier.verify(probe, bytes).computed)
        }

        fun withDigest(
            xml: String,
            uri: String,
            bytes: ByteArray,
        ): String {
            val pattern = Regex("(URI=\"" + Regex.escape(uri) + "\".*?<ds:DigestValue>)[^<]*(</ds:DigestValue>)", RegexOption.DOT_MATCHES_ALL)
            check(pattern.containsMatchIn(xml)) { "No reference to $uri" }
            return pattern.replaceFirst(xml, "$1" + digest(bytes) + "$2")
        }

        var coaXml = CatalogueFixtures.text("synthetic/coa-populated.xml")
        coaXml = withDigest(coaXml, "attributes/eu.europa.ec.eudi.pid.1/family_name.xml", familyName)
        coaXml = withDigest(coaXml, "attributes/eu.europa.ec.eudi.pid.1/birth_date.xml", birthDate)
        if (coaSequence != null) {
            coaXml = coaXml.replace("<CatalogueSequenceNumber>2</CatalogueSequenceNumber>", "<CatalogueSequenceNumber>$coaSequence</CatalogueSequenceNumber>")
        }
        if (coaIdentifier != null) {
            coaXml = coaXml.replace("<CatalogueIdentifier>http://data.europa.eu/c9v/EUCatalogueOfAttributes</CatalogueIdentifier>", "<CatalogueIdentifier>$coaIdentifier</CatalogueIdentifier>")
        }
        val cosXml = withDigest(CatalogueFixtures.text("synthetic/cos-populated.xml"), "schemes/eu-pid.xml", scheme)
        coa = coaSigner.sign(coaXml.encodeToByteArray())
        cos = cosSigner.sign(cosXml.encodeToByteArray())

        var locXml = CatalogueFixtures.text("live/loc.xml")
        val pointers = Regex("<OtherLoTEPointer>.*?</OtherLoTEPointer>", RegexOption.DOT_MATCHES_ALL).findAll(locXml).toList()
        check(pointers.size == 3)
        val signers = listOf(locSigner, coaPointerSigner, cosSigner)
        val rewritten =
            pointers.mapIndexed { i, m ->
                Regex("<X509Certificate>.*?</X509Certificate>", RegexOption.DOT_MATCHES_ALL)
                    .replace(m.value) { "<X509Certificate>" + signers[i].certificateBase64 + "</X509Certificate>" }
            }
        pointers.zip(rewritten).reversed().forEach { (m, r) -> locXml = locXml.replaceRange(m.range, r) }
        loc = locSigner.sign(locXml.encodeToByteArray())
    }

    fun server(): MapCatalogueHttpClient =
        MapCatalogueHttpClient(
            mutableMapOf(
                CatalogueFixtures.LOC_URL to loc,
                CatalogueFixtures.COA_URL to coa,
                CatalogueFixtures.COS_URL to cos,
                CatalogueFixtures.FAMILY_NAME_URL to familyName,
                CatalogueFixtures.BIRTH_DATE_URL to birthDate,
                CatalogueFixtures.SCHEME_URL to scheme,
            ),
        )

    companion object {
        const val TRANSFORM = "http://www.w3.org/2001/10/xml-exc-c14n#"
        const val METHOD = "https://www.w3.org/TR/xmlenc-core1/#sec-SHA512"
    }
}
