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

package com.sphereon.catalog.eu.impl.digest

import com.sphereon.catalog.eu.EuCatalogueConstants
import com.sphereon.catalog.eu.model.EntryReference
import com.sphereon.core.compat.xml.c14n.ExclusiveC14N
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import nl.adaptivity.xmlutil.DomWriter
import nl.adaptivity.xmlutil.dom2.Document
import nl.adaptivity.xmlutil.dom2.Element
import nl.adaptivity.xmlutil.dom2.length
import nl.adaptivity.xmlutil.writeCurrent
import nl.adaptivity.xmlutil.xmlStreaming

/**
 * Outcome of checking one entry file against the `ds:Reference` of its index.
 *
 * @property rawBytesDigestMatches diagnostic only: whether SHA-512 over the raw file bytes equals the declared digest.
 * A file can match here and still be invalid, because the declared digest is over the canonical form.
 */
class EntryDigestResult(
    val valid: Boolean,
    val digestMethod: String,
    val expected: ByteArray,
    val computed: ByteArray?,
    val rawBytesDigestMatches: Boolean,
    val error: String? = null,
)

/**
 * Verifies the digest that binds an entry file to a signed main catalogue file. The digest is SHA-512 over the
 * exclusive canonical form of the entry document. Both the digest method URI of the catalogue specification and the
 * standard xmlenc SHA-512 URI are accepted.
 */
interface CatalogueEntryDigestVerifier {
    fun verify(
        reference: EntryReference,
        entryBytes: ByteArray,
    ): EntryDigestResult
}

/**
 * The entry digest: SHA-512 over the exclusive canonical form of an entry document, or over the raw bytes when the
 * reference has no transforms. The verifier and the publisher both use this code, so a published entry verifies with
 * exactly the digest it was bound with.
 */
object CatalogueEntryDigest {
    const val ENVELOPED_TRANSFORM = "http://www.w3.org/2000/09/xmldsig#enveloped-signature"
    private val SUPPORTED_TRANSFORMS = setOf(EuCatalogueConstants.EXC_C14N_TRANSFORM, ENVELOPED_TRANSFORM)

    /** @return the transforms this implementation cannot apply, empty when [transforms] can be evaluated. */
    fun unsupportedTransforms(transforms: List<String>): List<String> = transforms.filter { it !in SUPPORTED_TRANSFORMS }

    /**
     * @throws Exception when [entryBytes] is not well-formed XML and [transforms] needs canonicalization.
     */
    fun compute(
        entryBytes: ByteArray,
        transforms: List<String>,
    ): ByteArray =
        if (transforms.isEmpty()) {
            hash(entryBytes, DigestAlg.SHA512)
        } else {
            hash(canonicalize(entryBytes, envelopedTransform = ENVELOPED_TRANSFORM in transforms), DigestAlg.SHA512)
        }

    private fun canonicalize(
        bytes: ByteArray,
        envelopedTransform: Boolean,
    ): ByteArray {
        val document = parse(bytes.decodeToString().removePrefix("﻿"))
        val root = document.getDocumentElement() ?: throw IllegalArgumentException("No root element")
        val signature =
            if (envelopedTransform) {
                root.getElementsByTagNameNS(EuCatalogueConstants.XMLDSIG_NAMESPACE, "Signature").let {
                    if (it.length > 0) it[0] as? Element else null
                }
            } else {
                null
            }
        return ExclusiveC14N.canonicalize(root, emptySet(), signature)
    }

    @Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
    private fun parse(xml: String): Document {
        val reader = xmlStreaming.newReader(xml)
        val writer = DomWriter()
        while (reader.hasNext()) {
            reader.next()
            reader.writeCurrent(writer)
        }
        return writer.target
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<CatalogueEntryDigestVerifier>())
class DefaultCatalogueEntryDigestVerifier : CatalogueEntryDigestVerifier {
    override fun verify(
        reference: EntryReference,
        entryBytes: ByteArray,
    ): EntryDigestResult {
        val method = reference.digestMethod
        val rawDigest = hash(entryBytes, DigestAlg.SHA512)
        val rawMatches = rawDigest.contentEquals(reference.digestValue)

        fun failure(message: String) = EntryDigestResult(false, method, reference.digestValue, null, rawMatches, message)

        if (method !in EuCatalogueConstants.ACCEPTED_DIGEST_METHODS) {
            return failure("Unsupported digest method $method")
        }
        val unsupported = CatalogueEntryDigest.unsupportedTransforms(reference.transforms)
        if (unsupported.isNotEmpty()) {
            return failure("Unsupported transform ${unsupported.first()}")
        }
        val computed =
            try {
                CatalogueEntryDigest.compute(entryBytes, reference.transforms)
            } catch (e: Exception) {
                return failure("The entry file is not well-formed XML: ${e.message}")
            }
        val valid = computed.contentEquals(reference.digestValue)
        return EntryDigestResult(valid, method, reference.digestValue, computed, rawMatches, if (valid) null else "Digest mismatch")
    }
}
