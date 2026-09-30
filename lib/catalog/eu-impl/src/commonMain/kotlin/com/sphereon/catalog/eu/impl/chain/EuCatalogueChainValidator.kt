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

package com.sphereon.catalog.eu.impl.chain

import com.sphereon.catalog.eu.EuCatalogueConstants
import com.sphereon.catalog.eu.error.CatalogError
import com.sphereon.catalog.eu.error.CatalogErrorCode
import com.sphereon.catalog.eu.impl.digest.CatalogueEntryDigestVerifier
import com.sphereon.catalog.eu.impl.fetch.CatalogueFetcher
import com.sphereon.catalog.eu.impl.fetch.FetchedFile
import com.sphereon.catalog.eu.impl.signature.CatalogueSignatureResult
import com.sphereon.catalog.eu.impl.signature.CatalogueSignatureVerifier
import com.sphereon.catalog.eu.model.AttributeEntry
import com.sphereon.catalog.eu.model.CatalogueFinding
import com.sphereon.catalog.eu.model.CatalogueKind
import com.sphereon.catalog.eu.model.CatalogueOfAttributes
import com.sphereon.catalog.eu.model.CatalogueOfSchemes
import com.sphereon.catalog.eu.model.CataloguePointer
import com.sphereon.catalog.eu.model.EaaSchemeEntry
import com.sphereon.catalog.eu.model.EntryReference
import com.sphereon.catalog.eu.model.FindingSeverity
import com.sphereon.catalog.eu.model.ListOfCatalogues
import com.sphereon.catalog.eu.model.SignerEvidence
import com.sphereon.catalog.eu.model.VerifiedCatalogueSet
import com.sphereon.catalog.eu.parser.DefaultEuCatalogueXmlParser
import com.sphereon.catalog.eu.parser.EuCatalogueXmlParser
import com.sphereon.catalog.eu.validation.CatalogueConformanceValidator
import com.sphereon.catalog.eu.validation.CatalogueFindingCodes
import com.sphereon.catalog.eu.validation.CatalogueProfile
import com.sphereon.catalog.eu.validation.LocProfileValidator
import com.sphereon.core.api.Encoding
import com.sphereon.core.api.IdkErrorResult
import com.sphereon.core.api.IdkOkResult
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.decodeFrom
import com.sphereon.core.api.encodeTo
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Finding codes raised by the chain validator, next to the codes of the conformance and LoC profile validators.
 */
/** Prefix of the digest that identifies a list of catalogues; see [EuCatalogueChainValidator.locIdentity]. */
const val LOC_IDENTITY_PREFIX = "loc:sha256:"

object ChainFindingCodes {
    const val SEQUENCE_REGRESSION = "CATALOGUE_SEQUENCE_REGRESSION"
    const val SEQUENCE_CONFLICT = "CATALOGUE_SEQUENCE_CONFLICT"
    const val IDENTIFIER_CHANGED = "CATALOGUE_IDENTIFIER_CHANGED"
    const val DIGEST_MISMATCH = "CATALOGUE_DIGEST_MISMATCH"
    const val SIGNER_NOT_AUTHORISED = "CATALOGUE_SIGNER_NOT_AUTHORISED"
    const val SIGNATURE_INVALID = "CATALOGUE_SIGNATURE_INVALID"
    const val CONFORMANCE_FAILED = "CATALOGUE_CONFORMANCE_FAILED"
    const val UNKNOWN_CRITICAL_EXTENSION = CatalogueFindingCodes.UNKNOWN_CRITICAL_EXTENSION
    const val FETCH_FAILED = "CATALOGUE_FETCH_FAILED"
    const val PARSE_FAILED = "CATALOGUE_PARSE_FAILED"
    const val SIGNER_CERTIFICATE_INVALID = "CATALOGUE_SIGNER_CERTIFICATE_INVALID"
    const val LOC_NEXT_UPDATE_PASSED = CatalogueFindingCodes.LOC_NEXT_UPDATE_PASSED
}

/**
 * What the caller last accepted for one catalogue. [documentSha256] is SHA-256 over the raw main file bytes.
 */
class LastSeenCatalogue(
    val identifier: String?,
    val sequenceNumber: Long,
    val documentSha256: ByteArray? = null,
)

/**
 * @property locSignerCertificates DER certificates that may sign the LoC (the trust anchor of the chain).
 * @property lastSeen previously accepted state per catalogue, used to refuse sequence regressions.
 * @property now overrides the clock for the LoC NextUpdate diagnostic.
 */
class ChainValidationRequest(
    val locUrl: String,
    val locSignerCertificates: List<ByteArray>,
    val lastSeen: Map<CatalogueKind, LastSeenCatalogue> = emptyMap(),
    val coaProfile: CatalogueProfile = CatalogueProfile.EU_COA,
    val cosProfile: CatalogueProfile = CatalogueProfile.EU_COS,
    val now: Instant? = null,
)

/**
 * Validates a catalogue of attributes and/or a catalogue of schemes that are published directly, without a list of
 * catalogues. [signerCertificates] (DER) are the only certificates that may sign either document.
 */
class DirectValidationRequest(
    val coaUrl: String? = null,
    val cosUrl: String? = null,
    val signerCertificates: List<ByteArray>,
    val lastSeen: Map<CatalogueKind, LastSeenCatalogue> = emptyMap(),
    val coaProfile: CatalogueProfile = CatalogueProfile.custom(requireSignature = true),
    val cosProfile: CatalogueProfile = CatalogueProfile.custom(requireSignature = true),
)

/**
 * A main file that passed every check, with the bytes it was verified from.
 */
class ValidatedDocument(
    val kind: CatalogueKind,
    val url: String,
    val identifier: String,
    val sequenceNumber: Long,
    val rawBytes: ByteArray,
    val sha256: ByteArray,
)

class ValidatedEntryFile(
    val kind: CatalogueKind,
    val url: String,
    val rawBytes: ByteArray,
)

/**
 * @property set the accepted catalogues. A catalogue that failed any check is null and its findings are in [VerifiedCatalogueSet.findings].
 * @property documents the accepted main files, including the LoC, keyed by kind.
 */
class ChainValidationOutcome(
    val set: VerifiedCatalogueSet,
    val documents: Map<CatalogueKind, ValidatedDocument>,
    val entryFiles: List<ValidatedEntryFile>,
)

/**
 * Result of [EuCatalogueChainValidator.validateDirect]. A catalogue that failed a check is null and its findings are in [findings].
 */
class DirectValidationOutcome(
    val coa: CatalogueOfAttributes?,
    val cos: CatalogueOfSchemes?,
    val attributeEntries: List<AttributeEntry>,
    val schemeEntries: List<EaaSchemeEntry>,
    val signerEvidence: List<SignerEvidence>,
    val findings: List<CatalogueFinding>,
    val documents: Map<CatalogueKind, ValidatedDocument>,
    val entryFiles: List<ValidatedEntryFile>,
)

/**
 * Validates the EU catalogue chain: LoC (signed by a configured anchor), its pointers, the CoA and CoS each signed by
 * one of their pointer's certificates, and every entry file bound to its index by digest.
 *
 * The LoC itself is all-or-nothing: an unsigned, mis-signed, non-conforming or regressed LoC yields an error result.
 * Each pointed-to catalogue is judged on its own; a catalogue with any error finding (a bad signature, a signer that is
 * not in the pointer, a digest mismatch, an unknown critical extension, a sequence regression) is left out of the set
 * as a whole, entries included.
 */
class EuCatalogueChainValidator(
    private val fetcher: CatalogueFetcher,
    private val signatureVerifier: CatalogueSignatureVerifier,
    private val digestVerifier: CatalogueEntryDigestVerifier,
    private val parser: EuCatalogueXmlParser = DefaultEuCatalogueXmlParser(),
    private val conformance: CatalogueConformanceValidator = CatalogueConformanceValidator(),
    private val locProfile: LocProfileValidator = LocProfileValidator(),
) {
    suspend fun validate(request: ChainValidationRequest): IdkResult<ChainValidationOutcome, CatalogError> {
        val now = request.now ?: Clock.System.now()
        val findings = mutableListOf<CatalogueFinding>()
        val evidence = mutableListOf<SignerEvidence>()
        val documents = mutableMapOf<CatalogueKind, ValidatedDocument>()
        val entryFiles = mutableListOf<ValidatedEntryFile>()

        val locFile = fetcher.fetch(request.locUrl)
        if (locFile.isErr) return IdkErrorResult(locFile.error)
        val locParsed =
            parser.parseLoc(locFile.value.bytes).let {
                if (it.isErr) return IdkErrorResult(it.error)
                it.value
            }
        val loc = locParsed.value

        val locProfileErrors = locProfile.validate(loc, now).filter { it.severity == FindingSeverity.ERROR }
        if (locProfileErrors.any { it.code == ChainFindingCodes.LOC_NEXT_UPDATE_PASSED }) {
            return IdkErrorResult(
                CatalogError(
                    CatalogErrorCode.SCHEMA_VIOLATION,
                    "${ChainFindingCodes.LOC_NEXT_UPDATE_PASSED}: the list of catalogues NextUpdate ${loc.nextUpdate} has passed, an expired list is rejected",
                    request.locUrl,
                ),
            )
        }
        if (locProfileErrors.isNotEmpty()) {
            return IdkErrorResult(
                CatalogError(
                    CatalogErrorCode.SCHEMA_VIOLATION,
                    "The list of catalogues does not conform to its profile: ${locProfileErrors.joinToString { it.code }}",
                    request.locUrl,
                ),
            )
        }
        val locSignature = signatureVerifier.verify(locParsed.rawBytes, request.locSignerCertificates)
        if (!locSignature.valid) {
            return IdkErrorResult(
                CatalogError(
                    CatalogErrorCode.SIGNATURE_INVALID,
                    "The list of catalogues signature was not accepted: ${signatureFailure(locSignature).second}",
                    request.locUrl,
                ),
            )
        }
        val locInfo = loc.lote.listAndSchemeInformation
        val locSha = hash(locParsed.rawBytes, DigestAlg.SHA256)
        request.lastSeen[CatalogueKind.LOC]?.let { last ->
            val regression = sequenceProblem(last, locInfo.sequenceNumber.toLong(), locIdentity(loc), locSha)
            if (regression != null) {
                return IdkErrorResult(CatalogError(CatalogErrorCode.SCHEMA_VIOLATION, "${regression.first}: ${regression.second}", request.locUrl))
            }
        }
        documents[CatalogueKind.LOC] =
            ValidatedDocument(CatalogueKind.LOC, request.locUrl, locIdentity(loc), locInfo.sequenceNumber.toLong(), locParsed.rawBytes, locSha)
        signatureEvidence(CatalogueKind.LOC, locIdentity(loc), locInfo.sequenceNumber.toLong(), locSignature)?.let { evidence.add(it) }

        var coa: CatalogueOfAttributes? = null
        var cos: CatalogueOfSchemes? = null
        val attributeEntries = mutableListOf<AttributeEntry>()
        val schemeEntries = mutableListOf<EaaSchemeEntry>()

        pointer(loc, EuCatalogueConstants.COA_LOTE_TYPE)?.let { pointer ->
            val result = validateCoa(pointer, request, findings)
            if (result != null) {
                coa = result.catalogue
                attributeEntries.addAll(result.entries)
                documents[CatalogueKind.COA] = result.document
                entryFiles.addAll(result.entryFiles)
                evidence.add(result.evidence)
            }
        }
        pointer(loc, EuCatalogueConstants.COS_LOTE_TYPE)?.let { pointer ->
            val result = validateCos(pointer, request, findings)
            if (result != null) {
                cos = result.catalogue
                schemeEntries.addAll(result.entries)
                documents[CatalogueKind.COS] = result.document
                entryFiles.addAll(result.entryFiles)
                evidence.add(result.evidence)
            }
        }

        return IdkOkResult(
            ChainValidationOutcome(
                set = VerifiedCatalogueSet(loc, coa, cos, attributeEntries, schemeEntries, evidence, findings),
                documents = documents,
                entryFiles = entryFiles,
            ),
        )
    }

    /**
     * Validates directly published catalogues. The same signature, conformance, digest and sequence checks apply as
     * for the pointers of a list of catalogues; the signer must be one of [DirectValidationRequest.signerCertificates].
     */
    suspend fun validateDirect(request: DirectValidationRequest): IdkResult<DirectValidationOutcome, CatalogError> {
        if (request.coaUrl == null && request.cosUrl == null) {
            return IdkErrorResult(CatalogError(CatalogErrorCode.FETCH_FAILED, "A catalogue URL is required"))
        }
        val findings = mutableListOf<CatalogueFinding>()
        val evidence = mutableListOf<SignerEvidence>()
        val documents = mutableMapOf<CatalogueKind, ValidatedDocument>()
        val entryFiles = mutableListOf<ValidatedEntryFile>()
        val certificates = request.signerCertificates.map { it.encodeTo(Encoding.BASE64) }
        val chainRequest =
            ChainValidationRequest(
                locUrl = request.coaUrl ?: request.cosUrl!!,
                locSignerCertificates = request.signerCertificates,
                lastSeen = request.lastSeen,
                coaProfile = request.coaProfile,
                cosProfile = request.cosProfile,
            )
        var coa: CatalogueOfAttributes? = null
        var cos: CatalogueOfSchemes? = null
        val attributeEntries = mutableListOf<AttributeEntry>()
        val schemeEntries = mutableListOf<EaaSchemeEntry>()
        request.coaUrl?.let { url ->
            val result = validateCoa(CataloguePointer(loteType = EuCatalogueConstants.COA_LOTE_TYPE, location = url, signerCertificates = certificates), chainRequest, findings)
            if (result != null) {
                coa = result.catalogue
                attributeEntries.addAll(result.entries)
                documents[CatalogueKind.COA] = result.document
                entryFiles.addAll(result.entryFiles)
                evidence.add(result.evidence)
            }
        }
        request.cosUrl?.let { url ->
            val result = validateCos(CataloguePointer(loteType = EuCatalogueConstants.COS_LOTE_TYPE, location = url, signerCertificates = certificates), chainRequest, findings)
            if (result != null) {
                cos = result.catalogue
                schemeEntries.addAll(result.entries)
                documents[CatalogueKind.COS] = result.document
                entryFiles.addAll(result.entryFiles)
                evidence.add(result.evidence)
            }
        }
        return IdkOkResult(DirectValidationOutcome(coa, cos, attributeEntries, schemeEntries, evidence, findings, documents, entryFiles))
    }

    private class CatalogueResult<C, E>(
        val catalogue: C,
        val entries: List<E>,
        val document: ValidatedDocument,
        val entryFiles: List<ValidatedEntryFile>,
        val evidence: SignerEvidence,
    )

    private class MainFile(
        val bytes: ByteArray,
        val sha256: ByteArray,
        val signature: CatalogueSignatureResult,
    )

    private fun pointer(
        loc: ListOfCatalogues,
        type: String,
    ): CataloguePointer? = loc.pointers.firstOrNull { it.loteType == type }

    private suspend fun validateCoa(
        pointer: CataloguePointer,
        request: ChainValidationRequest,
        findings: MutableList<CatalogueFinding>,
    ): CatalogueResult<CatalogueOfAttributes, AttributeEntry>? {
        val kind = CatalogueKind.COA
        val local = mutableListOf<CatalogueFinding>()
        val main = fetchAndVerifyMain(kind, pointer, local) ?: return reject(local, findings)
        val parsed =
            parser.parseCoa(main.bytes).let {
                if (it.isErr) return reject(local + parseFailure(kind, it.error), findings)
                it.value
            }
        val coa = parsed.value
        local.addAll(conformance.validateCoa(coa, request.coaProfile))
        sequenceFindings(kind, coa.info.identifier, coa.info.sequenceNumber, main.sha256, request.lastSeen[kind], local)

        val entries = mutableListOf<AttributeEntry>()
        val files = mutableListOf<ValidatedEntryFile>()
        for (namespace in coa.namespaces) {
            for (indexEntry in namespace.entries) {
                val path = "Namespaces/${namespace.identifier}/${indexEntry.attributeIdentifier}"
                val bytes = fetchEntry(kind, pointer.location, indexEntry.reference, path, local) ?: continue
                val parsedEntry = parser.parseAttributeEntry(bytes.bytes)
                if (parsedEntry.isErr) {
                    local.add(error(ChainFindingCodes.PARSE_FAILED, path, parsedEntry.error.reason))
                    continue
                }
                val entry = parsedEntry.value.value
                local.addAll(conformance.validateAttributeEntry(entry, indexEntry, request.coaProfile).map { it.under(path) })
                entries.add(entry)
                files.add(ValidatedEntryFile(kind, bytes.url, bytes.bytes))
            }
        }
        if (local.any { it.severity == FindingSeverity.ERROR }) return reject(local, findings)
        findings.addAll(local)
        return CatalogueResult(
            coa,
            entries,
            ValidatedDocument(kind, pointer.location, coa.info.identifier, coa.info.sequenceNumber, main.bytes, main.sha256),
            files,
            signatureEvidence(kind, coa.info.identifier, coa.info.sequenceNumber, main.signature)!!,
        )
    }

    private suspend fun validateCos(
        pointer: CataloguePointer,
        request: ChainValidationRequest,
        findings: MutableList<CatalogueFinding>,
    ): CatalogueResult<CatalogueOfSchemes, EaaSchemeEntry>? {
        val kind = CatalogueKind.COS
        val local = mutableListOf<CatalogueFinding>()
        val main = fetchAndVerifyMain(kind, pointer, local) ?: return reject(local, findings)
        val parsed =
            parser.parseCos(main.bytes).let {
                if (it.isErr) return reject(local + parseFailure(kind, it.error), findings)
                it.value
            }
        val cos = parsed.value
        local.addAll(conformance.validateCos(cos, request.cosProfile))
        sequenceFindings(kind, cos.info.identifier, cos.info.sequenceNumber, main.sha256, request.lastSeen[kind], local)

        val entries = mutableListOf<EaaSchemeEntry>()
        val files = mutableListOf<ValidatedEntryFile>()
        for (indexEntry in cos.schemes) {
            val path = "EAASchemeList/${indexEntry.name}"
            val bytes = fetchEntry(kind, pointer.location, indexEntry.reference, path, local) ?: continue
            val parsedEntry = parser.parseSchemeEntry(bytes.bytes)
            if (parsedEntry.isErr) {
                local.add(error(ChainFindingCodes.PARSE_FAILED, path, parsedEntry.error.reason))
                continue
            }
            val entry = parsedEntry.value.value
            local.addAll(conformance.validateSchemeEntry(entry, indexEntry, request.cosProfile).map { it.under(path) })
            entries.add(entry)
            files.add(ValidatedEntryFile(kind, bytes.url, bytes.bytes))
        }
        if (local.any { it.severity == FindingSeverity.ERROR }) return reject(local, findings)
        findings.addAll(local)
        return CatalogueResult(
            cos,
            entries,
            ValidatedDocument(kind, pointer.location, cos.info.identifier, cos.info.sequenceNumber, main.bytes, main.sha256),
            files,
            signatureEvidence(kind, cos.info.identifier, cos.info.sequenceNumber, main.signature)!!,
        )
    }

    private fun <C, E> reject(
        local: List<CatalogueFinding>,
        findings: MutableList<CatalogueFinding>,
    ): CatalogueResult<C, E>? {
        findings.addAll(local)
        return null
    }

    /**
     * Fetches a pointer's main file and verifies its signature against the certificates listed in that pointer.
     */
    private suspend fun fetchAndVerifyMain(
        kind: CatalogueKind,
        pointer: CataloguePointer,
        local: MutableList<CatalogueFinding>,
    ): MainFile? {
        val fetched = fetcher.fetch(pointer.location)
        if (fetched.isErr) {
            local.add(error(ChainFindingCodes.FETCH_FAILED, kind.name, fetched.error.reason))
            return null
        }
        val certificates =
            pointer.signerCertificates.mapNotNull { base64 ->
                try {
                    base64.filterNot { it.isWhitespace() }.decodeFrom(Encoding.BASE64)
                } catch (e: Exception) {
                    local.add(error(ChainFindingCodes.SIGNER_CERTIFICATE_INVALID, kind.name, "A signer certificate of the pointer is not valid base64"))
                    null
                }
            }
        val signature = signatureVerifier.verify(fetched.value.bytes, certificates)
        if (!signature.valid) {
            val (code, reason) = signatureFailure(signature)
            local.add(error(code, kind.name, reason))
            return null
        }
        return MainFile(fetched.value.bytes, hash(fetched.value.bytes, DigestAlg.SHA256), signature)
    }

    private suspend fun fetchEntry(
        kind: CatalogueKind,
        mainUrl: String,
        reference: EntryReference,
        path: String,
        local: MutableList<CatalogueFinding>,
    ): FetchedFile? {
        val fetched = fetcher.fetchEntry(mainUrl, reference.uri)
        if (fetched.isErr) {
            local.add(error(ChainFindingCodes.FETCH_FAILED, path, fetched.error.reason))
            return null
        }
        val digest = digestVerifier.verify(reference, fetched.value.bytes)
        if (!digest.valid) {
            val hint = if (digest.rawBytesDigestMatches) " (the digest matches the raw file bytes, not the canonical form)" else ""
            local.add(error(ChainFindingCodes.DIGEST_MISMATCH, path, "${digest.error ?: "Digest mismatch"}$hint"))
            return null
        }
        return fetched.value
    }

    private fun sequenceFindings(
        kind: CatalogueKind,
        identifier: String,
        sequence: Long,
        sha256: ByteArray,
        last: LastSeenCatalogue?,
        local: MutableList<CatalogueFinding>,
    ) {
        if (last == null) return
        sequenceProblem(last, sequence, identifier, sha256)?.let { (code, message) -> local.add(error(code, kind.name, message)) }
    }

    /**
     * A LoC has no catalogue identifier. Its type is the same for every EU list of catalogues, so the source identity
     * is derived from the scheme operator name plus the scheme name, which stay fixed for one operator's list. The EU list
     * carries both names in every official language, so the identity is their SHA-256 digest: it stays short enough to be
     * stored and indexed as a catalogue identifier.
     */
    internal fun locIdentity(loc: ListOfCatalogues): String {
        val info = loc.lote.listAndSchemeInformation
        val operator = info.schemeOperatorName.joinToString("|") { it.value }
        val name = info.schemeName.joinToString("|") { it.value }
        return LOC_IDENTITY_PREFIX + hash("$operator#$name".encodeToByteArray(), DigestAlg.SHA256).encodeTo(Encoding.HEX)
    }

    private fun sequenceProblem(
        last: LastSeenCatalogue,
        sequence: Long,
        identifier: String?,
        sha256: ByteArray,
    ): Pair<String, String>? =
        when {
            last.identifier != null && identifier != null && last.identifier != identifier ->
                ChainFindingCodes.IDENTIFIER_CHANGED to "The catalogue identifier changed from ${last.identifier} to $identifier"
            sequence < last.sequenceNumber ->
                ChainFindingCodes.SEQUENCE_REGRESSION to "Sequence number $sequence is lower than the last accepted ${last.sequenceNumber}"
            sequence == last.sequenceNumber && last.documentSha256 != null && !last.documentSha256.contentEquals(sha256) ->
                ChainFindingCodes.SEQUENCE_CONFLICT to "Sequence number $sequence was already accepted with different content"
            else -> null
        }

    private fun signatureFailure(result: CatalogueSignatureResult): Pair<String, String> =
        if (result.signaturePresent && result.cryptographicallyValid && !result.signerAuthorised) {
            ChainFindingCodes.SIGNER_NOT_AUTHORISED to "The signing certificate is not one of the certificates authorised for this catalogue"
        } else {
            ChainFindingCodes.SIGNATURE_INVALID to (result.errors.firstOrNull() ?: "The signature is invalid or missing")
        }

    private fun signatureEvidence(
        kind: CatalogueKind,
        identifier: String,
        sequence: Long,
        signature: CatalogueSignatureResult,
    ): SignerEvidence? =
        signature.signerCertificate?.let {
            SignerEvidence(kind, identifier, sequence, it.encodeTo(Encoding.BASE64), signature.signingTime)
        }

    private fun parseFailure(
        kind: CatalogueKind,
        error: CatalogError,
    ) = error(ChainFindingCodes.PARSE_FAILED, kind.name, error.reason)

    private fun error(
        code: String,
        path: String,
        message: String,
    ) = CatalogueFinding(code, FindingSeverity.ERROR, path, message)

    private fun CatalogueFinding.under(prefix: String) = copy(path = "$prefix/$path")
}
