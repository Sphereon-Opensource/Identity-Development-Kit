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

import kotlinx.serialization.Serializable

@Serializable
enum class VaultExportContentClass {
    PORTABLE_DATA,
    PRIVATE_KEY,
    PIN_MATERIAL,
    LIVE_ACCESS_TOKEN,
    LIVE_REFRESH_TOKEN,
    SESSION_COOKIE,
    KMS_RECOVERY_DATA,
    UNRELATED_GRANT_TOPOLOGY,
}

data class VaultExportExclusionPolicy(
    val excludedClasses: Set<VaultExportContentClass> = DEFAULT_EXCLUDED_CLASSES,
    val excludedPathSegments: Set<String> = DEFAULT_EXCLUDED_SEGMENTS,
) {
    fun reasonExcluded(payload: BagItPayload): String? {
        if (payload.contentClass in excludedClasses) return "content class ${payload.contentClass}"
        val excluded = payload.path.split('/').firstOrNull { it.lowercase() in excludedPathSegments }
        return excluded?.let { "excluded path segment '$it'" }
    }

    companion object {
        val DEFAULT_EXCLUDED_CLASSES = VaultExportContentClass.entries.filterNot { it == VaultExportContentClass.PORTABLE_DATA }.toSet()
        val DEFAULT_EXCLUDED_SEGMENTS = setOf(".secrets", ".internal", ".kms", "secrets", "tokens", "sessions", "private-keys", "pins")
        val DEFAULT = VaultExportExclusionPolicy()
    }
}

interface BagItPayload {
    /** Relative path inside the BagIt data directory, without the `data/` prefix. */
    val path: String
    val contentClass: VaultExportContentClass
    val content: ReplayableVaultContent
}

@Serializable
data class BagItManifestEntry(
    val path: String,
    val sha256: String,
    val sizeBytes: Long,
)

data class BagItLogicalEntry(
    val path: String,
    val sizeBytes: Long,
    val content: ReplayableVaultContent,
)

internal data class BagItReservedTagEntry(
    val path: String,
    val content: ReplayableVaultContent,
)

data class BagItLogicalPackage(
    val entries: List<BagItLogicalEntry>,
    val payloadManifest: List<BagItManifestEntry>,
    val payloadBytes: Long,
) {
    init {
        require(payloadBytes >= 0) { "payloadBytes must be non-negative" }
        require(entries.map { it.path } == entries.map { it.path }.sortedWith(VaultPortablePath.canonicalUtf8Comparator())) {
            "BagIt entries must use canonical UTF-8 ordering"
        }
    }

    fun entry(path: String): BagItLogicalEntry? = entries.firstOrNull { it.path == path }
}

class BagItPackageBuilder(
    private val checksumProvider: VaultChecksumProvider = Sha256VaultChecksumProvider(),
    private val exclusionPolicy: VaultExportExclusionPolicy = VaultExportExclusionPolicy.DEFAULT,
) {
    init {
        require(checksumProvider.algorithm == "sha256") { "Vault BagIt profile requires SHA-256" }
    }

    suspend fun build(
        payloads: List<BagItPayload>,
        bagInfo: Map<String, String> = emptyMap(),
    ): BagItLogicalPackage = build(payloads, bagInfo, emptyList())

    internal suspend fun build(
        payloads: List<BagItPayload>,
        bagInfo: Map<String, String>,
        reservedTags: List<BagItReservedTagEntry>,
    ): BagItLogicalPackage {
        validatePayloadPaths(payloads)
        validateReservedTags(reservedTags)

        val sortedPayloads = payloads.sortedWith { left, right -> VaultPortablePath.canonicalUtf8Comparator().compare(left.path, right.path) }
        val manifest = mutableListOf<BagItManifestEntry>()
        var payloadBytes = 0L

        for (payload in sortedPayloads) {
            exclusionPolicy.reasonExcluded(payload)?.let { throw VaultPortabilityError.ExcludedContent(payload.path, it) }
            val source = payload.content.open()
            val (digest, actualSize) =
                try {
                    checksum(source, checksumProvider)
                } finally {
                    source.close()
                }
            if (actualSize != payload.content.sizeBytes) {
                throw VaultPortabilityError.InvalidPayload(
                    "Payload '${payload.path}' declared ${payload.content.sizeBytes} bytes but streamed $actualSize",
                )
            }
            payloadBytes = checkedAdd(payloadBytes, actualSize)
            manifest += BagItManifestEntry("data/${payload.path}", digest, actualSize)
        }

        val bagitBytes = "BagIt-Version: 1.0\nTag-File-Character-Encoding: UTF-8\n".encodeToByteArray()
        val bagInfoBytes = buildBagInfo(payloadBytes, payloads.size, bagInfo).encodeToByteArray()
        val manifestBytes =
            manifest.joinToString(separator = "", transform = { "${it.sha256}  ${it.path}\n" }).encodeToByteArray()

        val generatedTagEntries =
            listOf(
                "bagit.txt" to bagitBytes,
                "bag-info.txt" to bagInfoBytes,
                "manifest-sha256.txt" to manifestBytes,
            )
        val reservedTagDigests = mutableListOf<Pair<String, String>>()
        for (entry in reservedTags) {
            val source = entry.content.open()
            val (digest, actualSize) =
                try {
                    checksum(source, checksumProvider)
                } finally {
                    source.close()
                }
            if (actualSize != entry.content.sizeBytes) {
                throw VaultPortabilityError.InvalidPayload(
                    "Reserved tag '${entry.path}' declared ${entry.content.sizeBytes} bytes but streamed $actualSize",
                )
            }
            reservedTagDigests += digest to entry.path
        }
        val tagManifestBytes =
            (generatedTagEntries.map { (path, bytes) -> checksumBytes(bytes) to path } + reservedTagDigests)
                .sortedWith { left, right -> VaultPortablePath.canonicalUtf8Comparator().compare(left.second, right.second) }
                .joinToString(separator = "", transform = { "${it.first}  ${it.second}\n" })
                .encodeToByteArray()

        val logicalEntries =
            buildList {
                generatedTagEntries.forEach { (path, bytes) -> add(BagItLogicalEntry(path, bytes.size.toLong(), ByteArrayReplayableContent(bytes))) }
                reservedTags.forEach { add(BagItLogicalEntry(it.path, it.content.sizeBytes, it.content)) }
                add(BagItLogicalEntry("tagmanifest-sha256.txt", tagManifestBytes.size.toLong(), ByteArrayReplayableContent(tagManifestBytes)))
                sortedPayloads.forEach { add(BagItLogicalEntry("data/${it.path}", it.content.sizeBytes, it.content)) }
            }.sortedWith { left, right -> VaultPortablePath.canonicalUtf8Comparator().compare(left.path, right.path) }

        return BagItLogicalPackage(logicalEntries, manifest, payloadBytes)
    }

    private fun validatePayloadPaths(payloads: List<BagItPayload>) {
        val exact = mutableSetOf<String>()
        val collisionKeys = mutableMapOf<String, String>()
        for (payload in payloads) {
            val validation = VaultPortablePath.validate(payload.path)
            if (!validation.valid) throw VaultPortabilityError.InvalidPath(payload.path, requireNotNull(validation.problem))
            if (!exact.add(payload.path)) throw VaultPortabilityError.InvalidPayload("Duplicate payload path '${payload.path}'")

            val key = VaultPortablePath.collisionKey(payload.path)
            val previous = collisionKeys[key]
            if (previous != null && previous != payload.path) {
                throw VaultPortabilityError.InvalidPayload("Portable path collision between '$previous' and '${payload.path}'")
            }
            collisionKeys[key] = payload.path
        }
    }

    private fun validateReservedTags(entries: List<BagItReservedTagEntry>) {
        val exact = mutableSetOf<String>()
        val collisionKeys = mutableMapOf<String, String>()
        for (entry in entries) {
            val validation = VaultPortablePath.validate(entry.path)
            if (!validation.valid) throw VaultPortabilityError.InvalidPath(entry.path, requireNotNull(validation.problem))
            require('/' !in entry.path) { "Reserved BagIt tags must be top-level files" }
            require(entry.path !in GENERATED_TAG_PATHS) { "Reserved tag '${entry.path}' collides with a generated BagIt tag" }
            if (!exact.add(entry.path)) throw VaultPortabilityError.InvalidPayload("Duplicate reserved tag '${entry.path}'")
            val key = VaultPortablePath.collisionKey(entry.path)
            val previous = collisionKeys[key]
            if (previous != null && previous != entry.path) {
                throw VaultPortabilityError.InvalidPayload("Portable tag collision between '$previous' and '${entry.path}'")
            }
            collisionKeys[key] = entry.path
        }
    }

    private fun buildBagInfo(
        payloadBytes: Long,
        payloadCount: Int,
        supplied: Map<String, String>,
    ): String {
        val tags = linkedMapOf("Payload-Oxum" to "$payloadBytes.$payloadCount")
        supplied.forEach { (name, value) ->
            require(name.isNotBlank() && ':' !in name && '\n' !in name && '\r' !in name) { "Invalid BagIt tag name" }
            require('\n' !in value && '\r' !in value) { "BagIt tag values must be single-line" }
            require(!name.equals("Payload-Oxum", ignoreCase = true)) { "Payload-Oxum is generated" }
            tags[name] = value
        }
        return tags.entries
            .sortedWith { left, right -> VaultPortablePath.canonicalUtf8Comparator().compare(left.key, right.key) }
            .joinToString(separator = "", transform = { "${it.key}: ${it.value}\n" })
    }

    private fun checksumBytes(bytes: ByteArray): String {
        val session = checksumProvider.newSession()
        session.update(bytes)
        return session.finishHex()
    }

    private companion object {
        val GENERATED_TAG_PATHS = setOf("bagit.txt", "bag-info.txt", "manifest-sha256.txt", "tagmanifest-sha256.txt")
    }
}

private class ByteArrayReplayableContent(
    bytes: ByteArray,
) : ReplayableVaultContent {
    private val value = bytes.copyOf()
    override val sizeBytes: Long = value.size.toLong()

    override suspend fun open(): VaultByteSource =
        object : VaultByteSource {
            private var offset = 0

            override suspend fun read(maxBytes: Int): ByteArray? {
                require(maxBytes > 0) { "maxBytes must be positive" }
                if (offset >= value.size) return null
                val end = minOf(value.size, offset + maxBytes)
                return value.copyOfRange(offset, end).also { offset = end }
            }
        }
}
