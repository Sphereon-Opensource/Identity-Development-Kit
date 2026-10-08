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

package com.sphereon.catalog.eu.impl.publish

import com.sphereon.catalog.eu.EuCatalogueConstants
import com.sphereon.catalog.eu.error.CatalogError
import com.sphereon.catalog.eu.impl.digest.CatalogueEntryDigest
import com.sphereon.catalog.eu.model.AttributeEntry
import com.sphereon.catalog.eu.model.AttributeNamespace
import com.sphereon.catalog.eu.model.CatalogueKind
import com.sphereon.catalog.eu.model.CatalogueOfAttributes
import com.sphereon.catalog.eu.model.CatalogueOfSchemes
import com.sphereon.catalog.eu.model.EaaSchemeEntry
import com.sphereon.catalog.eu.model.EntryReference
import com.sphereon.catalog.eu.model.NamespaceEntry
import com.sphereon.catalog.eu.model.SchemeEntryReference
import com.sphereon.catalog.eu.parser.DefaultEuCatalogueXmlParser
import com.sphereon.catalog.eu.parser.EuCatalogueXmlParser
import com.sphereon.catalog.eu.serializer.CatalogueEntryPaths
import com.sphereon.catalog.eu.serializer.DefaultEuCatalogueXmlSerializer
import com.sphereon.catalog.eu.serializer.EuCatalogueXmlSerializer
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.ErrorCategory
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.time.Clock
import kotlin.time.Instant

class PublishedEntryFile(
    /** Path relative to the main file, percent-encoded per segment; equal to the URI of the `ds:Reference`. */
    val path: String,
    val bytes: ByteArray,
)

/**
 * A signed catalogue ready to be stored and served: the main file with its enveloped signature and the entry files it
 * binds by digest.
 */
class PublishedCatalogue(
    val kind: CatalogueKind,
    val catalogueIdentifier: String,
    val sequenceNumber: Long,
    val mainXml: ByteArray,
    val mainSha256: String,
    val entryFiles: List<PublishedEntryFile>,
    val signerCertificates: List<ByteArray>,
    /** Lowercase hex SHA-256 of the leaf signer certificate. */
    val signerCertificateSha256: String?,
    val signingTime: Instant,
)

/**
 * Builds the published form of an authored catalogue: serializes every entry file, binds each to the main file with a
 * `ds:Reference` (exclusive canonicalization, SHA-512), writes the main file and adds the enveloped XAdES signature.
 * The index of the given catalogue is rebuilt from the entries, so any references in it are ignored.
 */
interface CatalogueXmlPublisher {
    suspend fun publishAttributes(
        catalogue: CatalogueOfAttributes,
        entries: List<Pair<String, AttributeEntry>>,
        key: CatalogueSigningKey,
        signingTime: Instant = Clock.System.now(),
    ): IdkResult<PublishedCatalogue, IdkError>

    suspend fun publishSchemes(
        catalogue: CatalogueOfSchemes,
        entries: List<EaaSchemeEntry>,
        key: CatalogueSigningKey,
        signingTime: Instant = Clock.System.now(),
    ): IdkResult<PublishedCatalogue, IdkError>
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CatalogueXmlPublisher>())
class DefaultCatalogueXmlPublisher(
    private val signer: CatalogueXmlSigner,
) : CatalogueXmlPublisher {
    private val serializer: EuCatalogueXmlSerializer = DefaultEuCatalogueXmlSerializer()
    private val parser: EuCatalogueXmlParser = DefaultEuCatalogueXmlParser()

    override suspend fun publishAttributes(
        catalogue: CatalogueOfAttributes,
        entries: List<Pair<String, AttributeEntry>>,
        key: CatalogueSigningKey,
        signingTime: Instant,
    ): IdkResult<PublishedCatalogue, IdkError> {
        val duplicate = entries.groupBy { it.first to it.second.attributeIdentifier }.entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) return Err(invalid("Attribute ${duplicate.key.first}/${duplicate.key.second} is listed twice"))

        entries.firstOrNull { (namespace, entry) -> !CatalogueEntryPaths.isSafeSegment(namespace) || !CatalogueEntryPaths.isSafeSegment(entry.attributeIdentifier) }?.let { (namespace, entry) ->
            return Err(invalid("Attribute '$namespace'/'${entry.attributeIdentifier}' cannot be published: identifiers must not be empty, '.' or '..'"))
        }
        catalogue.namespaces.firstOrNull { !CatalogueEntryPaths.isSafeSegment(it.identifier) }?.let {
            return Err(invalid("Namespace '${it.identifier}' cannot be published: identifiers must not be empty, '.' or '..'"))
        }

        val files = mutableListOf<PublishedEntryFile>()
        val indexed = mutableMapOf<String, MutableList<NamespaceEntry>>()
        for ((namespace, entry) in entries) {
            val bytes = serializer.serializeAttributeEntry(entry).getOrElse { return Err(it.asError()) }.encodeToByteArray()
            val path = CatalogueEntryPaths.attributeEntry(namespace, entry.attributeIdentifier)
            files += PublishedEntryFile(path, bytes)
            indexed.getOrPut(namespace) { mutableListOf() } +=
                NamespaceEntry(entry.attributeIdentifier, entry.registrationIdentifier, reference(path, bytes).getOrElse { return Err(it) })
        }
        val declared = catalogue.namespaces.map { it.identifier }
        val namespaces = (declared + indexed.keys.filter { it !in declared }).map { AttributeNamespace(it, indexed[it].orEmpty()) }
        val main = serializer.serializeCoa(catalogue.copy(namespaces = namespaces, hasSignature = false)).getOrElse { return Err(it.asError()) }
        return finish(CatalogueKind.COA, catalogue.info.identifier, catalogue.info.sequenceNumber, main, files, key, signingTime)
    }

    override suspend fun publishSchemes(
        catalogue: CatalogueOfSchemes,
        entries: List<EaaSchemeEntry>,
        key: CatalogueSigningKey,
        signingTime: Instant,
    ): IdkResult<PublishedCatalogue, IdkError> {
        val duplicate = entries.groupBy { it.name }.entries.firstOrNull { it.value.size > 1 }
        if (duplicate != null) return Err(invalid("Scheme ${duplicate.key} is listed twice"))

        entries.firstOrNull { !CatalogueEntryPaths.isSafeSegment(it.name) }?.let {
            return Err(invalid("Scheme '${it.name}' cannot be published: scheme names must not be empty, '.' or '..'"))
        }

        val files = mutableListOf<PublishedEntryFile>()
        val references = mutableListOf<SchemeEntryReference>()
        for (entry in entries) {
            val bytes = serializer.serializeSchemeEntry(entry).getOrElse { return Err(it.asError()) }.encodeToByteArray()
            val path = CatalogueEntryPaths.schemeEntry(entry.name)
            files += PublishedEntryFile(path, bytes)
            references += SchemeEntryReference(entry.name, entry.identifier, entry.registrationIdentifier, reference(path, bytes).getOrElse { return Err(it) })
        }
        val main = serializer.serializeCos(catalogue.copy(schemes = references, hasSignature = false)).getOrElse { return Err(it.asError()) }
        return finish(CatalogueKind.COS, catalogue.info.identifier, catalogue.info.sequenceNumber, main, files, key, signingTime)
    }

    private suspend fun finish(
        kind: CatalogueKind,
        identifier: String,
        sequenceNumber: Long,
        mainXml: String,
        files: List<PublishedEntryFile>,
        key: CatalogueSigningKey,
        signingTime: Instant,
    ): IdkResult<PublishedCatalogue, IdkError> {
        val signed = signer.sign(mainXml.encodeToByteArray(), key, signingTime).getOrElse { return Err(it) }
        val parsedOk =
            when (kind) {
                CatalogueKind.COA -> parser.parseCoa(signed.xml).let { it.isOk && it.value.value.hasSignature }
                else -> parser.parseCos(signed.xml).let { it.isOk && it.value.value.hasSignature }
            }
        if (!parsedOk) return Err(IdkError.fromString("The signed $kind document does not parse back as a signed catalogue", "CATALOGUE_SERIALIZATION_FAILED"))
        return Ok(
            PublishedCatalogue(
                kind = kind,
                catalogueIdentifier = identifier,
                sequenceNumber = sequenceNumber,
                mainXml = signed.xml,
                mainSha256 = sha256Hex(signed.xml),
                entryFiles = files,
                signerCertificates = signed.signerCertificates,
                signerCertificateSha256 = signed.signerCertificates.firstOrNull()?.let { sha256Hex(it) },
                signingTime = signed.signingTime,
            ),
        )
    }

    private fun reference(
        path: String,
        bytes: ByteArray,
    ): IdkResult<EntryReference, IdkError> {
        val transforms = listOf(EuCatalogueConstants.EXC_C14N_TRANSFORM)
        val digest =
            try {
                CatalogueEntryDigest.compute(bytes, transforms)
            } catch (e: Exception) {
                return Err(IdkError.fromString("Entry file $path could not be canonicalized: ${e.message}", "CATALOGUE_SERIALIZATION_FAILED", e))
            }
        return Ok(EntryReference(path, transforms, EuCatalogueConstants.DIGEST_SHA512_SPEC, digest))
    }

    private fun invalid(message: String) = IdkError.fromString(message, "CATALOGUE_INVALID", category = ErrorCategory.VALIDATION)

    private fun CatalogError.asError() =
        IdkError.fromString("Catalogue serialization failed: $reason${path?.let { " ($it)" }.orEmpty()}", code, category = ErrorCategory.VALIDATION)

    private fun sha256Hex(bytes: ByteArray): String {
        val digits = "0123456789abcdef"
        val out = StringBuilder()
        for (b in hash(bytes, DigestAlg.SHA256)) {
            val v = b.toInt() and 0xFF
            out.append(digits[v shr 4]).append(digits[v and 0x0F])
        }
        return out.toString()
    }
}
