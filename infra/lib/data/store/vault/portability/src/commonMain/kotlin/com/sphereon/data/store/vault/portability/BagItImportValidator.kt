/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.data.store.vault.portability

import doist.x.normalize.Form
import doist.x.normalize.normalize
import kotlinx.serialization.Serializable
import kotlin.coroutines.cancellation.CancellationException

@Serializable
data class VaultImportLimits(
    val maxObjectCount: Long = 100_000,
    val maxPathDepth: Int = 64,
    val maxEntryUncompressedBytes: Long = 2L * 1024 * 1024 * 1024,
    val maxTotalUncompressedBytes: Long = 20L * 1024 * 1024 * 1024,
    val maxDecompressionRatio: Double = 100.0,
    val maxTagFileBytes: Long = 4L * 1024 * 1024,
) {
    init {
        require(maxObjectCount > 0) { "maxObjectCount must be positive" }
        require(maxPathDepth > 0) { "maxPathDepth must be positive" }
        require(maxEntryUncompressedBytes > 0) { "maxEntryUncompressedBytes must be positive" }
        require(maxTotalUncompressedBytes >= maxEntryUncompressedBytes) { "total limit must cover one entry" }
        require(maxDecompressionRatio >= 1.0) { "maxDecompressionRatio must be at least 1" }
        require(maxTagFileBytes > 0) { "maxTagFileBytes must be positive" }
    }
}

class BagItImportValidator(
    private val checksumProvider: VaultChecksumProvider = Sha256VaultChecksumProvider(),
    private val limits: VaultImportLimits = VaultImportLimits(),
    private val excludedPathSegments: Set<String> = VaultExportExclusionPolicy.DEFAULT_EXCLUDED_SEGMENTS,
    private val structuralProfile: BagItStructuralProfile? = null,
    private val signedManifestVerifier: SignedVaultManifestVerifier? = null,
    private val requireSignedManifestVerification: Boolean = false,
) {
    suspend fun verify(archive: VaultArchiveSource): VaultPackageVerificationResult {
        val issues = mutableListOf<VaultVerificationIssue>()
        val seenExact = mutableSetOf<String>()
        val seenCollision = mutableMapOf<String, String>()
        val tagBytes = mutableMapOf<String, ByteArray>()
        val tagDigests = mutableMapOf<String, String>()
        val payloads = mutableMapOf<String, ObservedPayload>()
        var totalBytes = 0L
        var objectCount = 0L

        try {
            while (true) {
            val entry =
                try {
                    archive.nextEntry()
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    issues += issue(VaultVerificationIssueCode.IO_ERROR, null, "Cannot read archive: ${error.message}")
                    break
                } ?: break

            validatePath(entry.path, seenExact, seenCollision, issues)
            if (issues.any { it.path == entry.path }) {
                entry.content?.closeQuietly()
                continue
            }

            if (VaultPortablePath.depth(entry.path) > limits.maxPathDepth) {
                issues += issue(VaultVerificationIssueCode.PATH_DEPTH_LIMIT, entry.path, "Path exceeds maximum depth")
                entry.content?.closeQuietly()
                continue
            }
            if (entry.isDirectory) {
                entry.content?.closeQuietly()
                continue
            }

            objectCount++
            if (objectCount > limits.maxObjectCount) {
                issues += issue(VaultVerificationIssueCode.OBJECT_COUNT_LIMIT, entry.path, "Archive exceeds object-count limit")
                entry.content?.closeQuietly()
                break
            }
            if (!checkDeclaredSizes(entry, issues)) {
                entry.content?.closeQuietly()
                continue
            }

            val source = requireNotNull(entry.content)
            try {
                when {
                    entry.path in TAG_PATHS -> {
                        val bytes = readBounded(source, limits.maxTagFileBytes)
                        tagBytes[entry.path] = bytes
                        tagDigests[entry.path] = checksumBytes(bytes)
                        totalBytes = checkedAdd(totalBytes, bytes.size.toLong())
                    }
                    entry.path.startsWith(DATA_PREFIX) -> {
                        val payloadPath = entry.path.removePrefix(DATA_PREFIX)
                        val excluded = payloadPath.split('/').firstOrNull { it.lowercase() in excludedPathSegments }
                        if (excluded != null) {
                            issues += issue(VaultVerificationIssueCode.EXCLUDED_CONTENT, entry.path, "Excluded payload path segment '$excluded'")
                            continue
                        }
                        val (digest, size) = checksum(source, checksumProvider, limits.maxEntryUncompressedBytes)
                        if (entry.uncompressedSize != null && entry.uncompressedSize != size) {
                            issues += issue(VaultVerificationIssueCode.SIZE_MISMATCH, entry.path, "Declared and streamed entry sizes differ")
                        }
                        payloads[entry.path] = ObservedPayload(digest, size)
                        totalBytes = checkedAdd(totalBytes, size)
                    }
                    else -> issues += issue(VaultVerificationIssueCode.RESERVED_PATH, entry.path, "Unexpected file outside BagIt data and tag paths")
                }
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                issues += issue(VaultVerificationIssueCode.ENTRY_SIZE_LIMIT, entry.path, error.message ?: "Entry read failed")
            } finally {
                source.closeQuietly()
            }

            if (totalBytes > limits.maxTotalUncompressedBytes) {
                issues += issue(VaultVerificationIssueCode.TOTAL_SIZE_LIMIT, entry.path, "Archive exceeds total uncompressed-size limit")
                break
            }
            }
        } finally {
            try {
                archive.close()
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                issues += issue(VaultVerificationIssueCode.IO_ERROR, null, "Cannot close archive: ${error.message}")
            }
        }

        verifyBagDeclaration(tagBytes, issues)
        val manifest = parseManifest(tagBytes[PAYLOAD_MANIFEST], issues, PAYLOAD_MANIFEST)
        val tagManifest = parseManifest(tagBytes[TAG_MANIFEST], issues, TAG_MANIFEST)
        var verifiedCount = 0L
        var verifiedBytes = 0L

        manifest.forEach { (path, expected) ->
            val observed = payloads[path]
            when {
                observed == null -> issues += issue(VaultVerificationIssueCode.MISSING_PAYLOAD, path, "Manifest payload is missing")
                observed.sha256 != expected -> issues += issue(VaultVerificationIssueCode.CHECKSUM_MISMATCH, path, "Payload checksum mismatch")
                else -> {
                    verifiedCount++
                    verifiedBytes = checkedAdd(verifiedBytes, observed.sizeBytes)
                }
            }
        }
        payloads.keys.filterNot { it in manifest }.forEach { path ->
            issues += issue(VaultVerificationIssueCode.UNLISTED_PAYLOAD, path, "Payload is absent from manifest-sha256.txt")
        }
        tagManifest.forEach { (path, expected) ->
            if (path == TAG_MANIFEST) {
                issues += issue(VaultVerificationIssueCode.MALFORMED_MANIFEST, path, "Tag manifest must not checksum itself")
            } else if (tagDigests[path] != expected) {
                issues += issue(VaultVerificationIssueCode.TAG_CHECKSUM_MISMATCH, path, "Tag-file checksum mismatch")
            }
        }
        REQUIRED_TAG_MANIFEST_PATHS.filterNot { it in tagManifest }.forEach { path ->
            issues += issue(VaultVerificationIssueCode.MISSING_TAG_MANIFEST_ENTRY, path, "Required tag-manifest entry is missing")
        }
        structuralProfile?.let { profile ->
            verifyStructuralProfile(profile, tagBytes, tagManifest, manifest, payloads, issues)
        }

        return VaultPackageVerificationResult(issues.isEmpty(), issues.toList(), verifiedCount, verifiedBytes)
    }

    private suspend fun verifyStructuralProfile(
        profile: BagItStructuralProfile,
        tagBytes: Map<String, ByteArray>,
        tagManifest: Map<String, String>,
        payloadManifest: Map<String, String>,
        payloads: Map<String, ObservedPayload>,
        issues: MutableList<VaultVerificationIssue>,
    ) {
        val bagInfo = tagBytes["bag-info.txt"]?.let { decodeUtf8(it, "bag-info.txt", issues) }
        val profileValues =
            bagInfo
                ?.lineSequence()
                ?.filter { it.isNotEmpty() }
                ?.mapNotNull { line ->
                    val separator = line.indexOf(": ")
                    if (separator <= 0) null else line.substring(0, separator) to line.substring(separator + 2)
                }
                ?.filter { (name, _) -> name == profile.profileTagName }
                ?.map { it.second }
                ?.toList()
                .orEmpty()
        if (profileValues != listOf(profile.profileTagValue)) {
            issues +=
                issue(
                    VaultVerificationIssueCode.INVALID_PROFILE,
                    "bag-info.txt",
                    "BagIt profile marker must be exactly '${profile.profileTagName}: ${profile.profileTagValue}'",
                )
        }

        profile.requiredTagPaths.forEach { path ->
            val bytes = tagBytes[path]
            if (bytes == null || path !in tagManifest) {
                issues += issue(VaultVerificationIssueCode.MISSING_PROFILE_ENTRY, path, "Required profile tag or tag-manifest coverage is missing")
            } else if (bytes.isEmpty()) {
                issues += issue(VaultVerificationIssueCode.INVALID_SIGNED_MANIFEST, path, "Signed vault manifest must not be empty")
            } else if (path == VaultBagItProfile.SIGNED_VAULT_MANIFEST_TAG) {
                if (signedManifestVerifier == null && requireSignedManifestVerification) {
                    issues +=
                        issue(
                            VaultVerificationIssueCode.SIGNATURE_VERIFIER_REQUIRED,
                            path,
                            "A signed-vault-manifest verifier is required for Vault BagIt profile verification",
                        )
                } else if (signedManifestVerifier != null) {
                    val verified =
                        try {
                            signedManifestVerifier.verify(bytes.copyOf())
                        } catch (error: Throwable) {
                            if (error is CancellationException) throw error
                            false
                        }
                    if (!verified) {
                        issues += issue(VaultVerificationIssueCode.INVALID_SIGNED_MANIFEST, path, "Signed vault manifest verification failed")
                    }
                }
            }
        }
        profile.requiredPayloadPaths.forEach { path ->
            val observed = payloads[path]
            if (path !in payloadManifest || observed == null) {
                issues += issue(VaultVerificationIssueCode.MISSING_PROFILE_ENTRY, path, "Required profile payload or payload-manifest coverage is missing")
            } else if (observed.sizeBytes == 0L) {
                issues += issue(VaultVerificationIssueCode.INVALID_PROFILE, path, "Required profile payload must not be empty")
            }
        }
    }

    private fun validatePath(
        path: String,
        seenExact: MutableSet<String>,
        seenCollision: MutableMap<String, String>,
        issues: MutableList<VaultVerificationIssue>,
    ) {
        if (!seenExact.add(path)) {
            issues += issue(VaultVerificationIssueCode.DUPLICATE_PATH, path, "Duplicate archive path")
            return
        }
        val collisionKey = VaultPortablePath.collisionKey(path)
        val previous = seenCollision[collisionKey]
        if (previous != null) {
            val code =
                if (previous.lowercase() == path.lowercase()) {
                    VaultVerificationIssueCode.CASE_COLLISION
                } else {
                    VaultVerificationIssueCode.UNICODE_COLLISION
                }
            issues += issue(code, path, "Path collides with '$previous'")
            return
        }
        seenCollision[collisionKey] = path

        val validation = VaultPortablePath.validate(path, requireCanonicalUnicode = true)
        if (!validation.valid) {
            val code =
                when (validation.problem) {
                    PortablePathProblem.TRAVERSAL -> VaultVerificationIssueCode.PATH_TRAVERSAL
                    PortablePathProblem.RESERVED_NAME, PortablePathProblem.TRAILING_DOT_OR_SPACE -> VaultVerificationIssueCode.RESERVED_PATH
                    else -> VaultVerificationIssueCode.INVALID_PATH
                }
            issues += issue(code, path, "Invalid archive path: ${validation.problem}")
        }
    }

    private fun checkDeclaredSizes(
        entry: VaultArchiveEntry,
        issues: MutableList<VaultVerificationIssue>,
    ): Boolean {
        val uncompressed = entry.uncompressedSize
        val compressed = entry.compressedSize
        if (uncompressed != null && uncompressed > limits.maxEntryUncompressedBytes) {
            issues += issue(VaultVerificationIssueCode.ENTRY_SIZE_LIMIT, entry.path, "Declared entry size exceeds limit")
            return false
        }
        if (compressed != null && uncompressed != null && uncompressed > 0) {
            val ratio = if (compressed == 0L) Double.POSITIVE_INFINITY else uncompressed.toDouble() / compressed.toDouble()
            if (ratio > limits.maxDecompressionRatio) {
                issues += issue(VaultVerificationIssueCode.DECOMPRESSION_RATIO_LIMIT, entry.path, "Declared decompression ratio exceeds limit")
                return false
            }
        }
        return true
    }

    private fun verifyBagDeclaration(
        tagBytes: Map<String, ByteArray>,
        issues: MutableList<VaultVerificationIssue>,
    ) {
        val bytes = tagBytes[BAGIT_DECLARATION]
        if (bytes == null) {
            issues += issue(VaultVerificationIssueCode.MISSING_BAGIT_DECLARATION, BAGIT_DECLARATION, "bagit.txt is missing")
            return
        }
        val expected = "BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n"
        val actual = decodeUtf8(bytes, BAGIT_DECLARATION, issues) ?: return
        if (actual != expected) {
            issues += issue(VaultVerificationIssueCode.INVALID_BAGIT_DECLARATION, BAGIT_DECLARATION, "Unsupported or non-canonical BagIt declaration")
        }
    }

    private fun parseManifest(
        bytes: ByteArray?,
        issues: MutableList<VaultVerificationIssue>,
        manifestPath: String,
    ): Map<String, String> {
        if (bytes == null) {
            if (manifestPath == PAYLOAD_MANIFEST) {
                issues += issue(VaultVerificationIssueCode.MISSING_PAYLOAD_MANIFEST, manifestPath, "Payload manifest is missing")
            } else if (manifestPath == TAG_MANIFEST) {
                issues += issue(VaultVerificationIssueCode.MISSING_TAG_MANIFEST, manifestPath, "Tag manifest is missing")
            }
            return emptyMap()
        }
        val text = decodeUtf8(bytes, manifestPath, issues) ?: return emptyMap()
        val entries = linkedMapOf<String, String>()
        val collisionKeys = mutableMapOf<String, String>()
        text.split('\n').filter { it.isNotEmpty() }.forEach { line ->
            val match = MANIFEST_LINE.matchEntire(line)
            if (match == null) {
                issues += issue(VaultVerificationIssueCode.MALFORMED_MANIFEST, manifestPath, "Malformed SHA-256 manifest line")
                return@forEach
            }
            val digest = match.groupValues[1]
            val path = match.groupValues[2]
            if (!validateManifestPath(path, manifestPath, issues)) return@forEach
            val collisionKey = VaultPortablePath.collisionKey(path)
            val previousCollision = collisionKeys[collisionKey]
            if (previousCollision != null && previousCollision != path) {
                val code =
                    if (previousCollision.lowercase() == path.lowercase()) {
                        VaultVerificationIssueCode.CASE_COLLISION
                    } else {
                        VaultVerificationIssueCode.UNICODE_COLLISION
                    }
                issues += issue(code, path, "Manifest path collides with '$previousCollision'")
                return@forEach
            }
            collisionKeys[collisionKey] = path
            if (entries.put(path, digest) != null) {
                issues += issue(VaultVerificationIssueCode.MALFORMED_MANIFEST, path, "Duplicate manifest path")
            }
        }
        return entries
    }

    private fun validateManifestPath(
        path: String,
        manifestPath: String,
        issues: MutableList<VaultVerificationIssue>,
    ): Boolean {
        val validation = VaultPortablePath.validate(path)
        if (!validation.valid) {
            val code =
                when (validation.problem) {
                    PortablePathProblem.TRAVERSAL -> VaultVerificationIssueCode.PATH_TRAVERSAL
                    PortablePathProblem.RESERVED_NAME, PortablePathProblem.TRAILING_DOT_OR_SPACE -> VaultVerificationIssueCode.RESERVED_PATH
                    else -> VaultVerificationIssueCode.INVALID_PATH
                }
            issues += issue(code, path, "Invalid path in $manifestPath: ${validation.problem}")
            return false
        }
        if (manifestPath == PAYLOAD_MANIFEST && (!path.startsWith(DATA_PREFIX) || path.length == DATA_PREFIX.length)) {
            issues += issue(VaultVerificationIssueCode.MALFORMED_MANIFEST, path, "Payload manifest path must be below data/")
            return false
        }
        if (manifestPath == TAG_MANIFEST && path !in ALLOWED_TAG_MANIFEST_PATHS && path != TAG_MANIFEST) {
            issues += issue(VaultVerificationIssueCode.MALFORMED_MANIFEST, path, "Unexpected tag-manifest path")
            return false
        }
        return true
    }

    private fun decodeUtf8(
        bytes: ByteArray,
        path: String,
        issues: MutableList<VaultVerificationIssue>,
    ): String? =
        try {
            bytes.decodeToString(throwOnInvalidSequence = true)
        } catch (error: Throwable) {
            issues += issue(VaultVerificationIssueCode.MALFORMED_MANIFEST, path, "Tag file is not valid UTF-8")
            null
        }

    private suspend fun readBounded(
        source: VaultByteSource,
        limit: Long,
    ): ByteArray {
        val chunks = mutableListOf<ByteArray>()
        var size = 0L
        while (true) {
            val chunk = source.read() ?: break
            require(chunk.isNotEmpty()) { "Source returned an empty non-EOF chunk" }
            size = checkedAdd(size, chunk.size.toLong())
            require(size <= limit) { "Tag file exceeds configured limit" }
            chunks += chunk
        }
        val result = ByteArray(size.toInt())
        var offset = 0
        chunks.forEach { chunk ->
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }

    private fun checksumBytes(bytes: ByteArray): String {
        val session = checksumProvider.newSession()
        session.update(bytes)
        return session.finishHex()
    }

    private fun issue(
        code: VaultVerificationIssueCode,
        path: String?,
        message: String,
    ) = VaultVerificationIssue(code, path, message)

    private suspend fun VaultByteSource.closeQuietly() {
        try {
            close()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            // The primary validation issue is retained. Archive-level close still runs below.
        }
    }

    private data class ObservedPayload(
        val sha256: String,
        val sizeBytes: Long,
    )

    companion object {
        const val BAGIT_DECLARATION = "bagit.txt"
        const val PAYLOAD_MANIFEST = "manifest-sha256.txt"
        const val TAG_MANIFEST = "tagmanifest-sha256.txt"
        const val DATA_PREFIX = "data/"
        val TAG_PATHS = setOf(BAGIT_DECLARATION, "bag-info.txt", PAYLOAD_MANIFEST, TAG_MANIFEST, VaultBagItProfile.SIGNED_VAULT_MANIFEST_TAG)
        val REQUIRED_TAG_MANIFEST_PATHS = setOf(BAGIT_DECLARATION, "bag-info.txt", PAYLOAD_MANIFEST)
        val ALLOWED_TAG_MANIFEST_PATHS = REQUIRED_TAG_MANIFEST_PATHS + VaultBagItProfile.SIGNED_VAULT_MANIFEST_TAG
        val MANIFEST_LINE = Regex("^([0-9a-f]{64})  (.+)$")
    }
}
