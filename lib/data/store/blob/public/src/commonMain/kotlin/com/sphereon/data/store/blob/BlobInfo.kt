package com.sphereon.data.store.blob

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlin.experimental.ExperimentalObjCName
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.native.ObjCName

/**
 * Sealed interface for blob references, mirroring the KMS [KeyInfoType] pattern.
 *
 * Functions that accept [BlobInfoType] can receive either an unresolved [BlobInfo] (reference only)
 * or a [ResolvedBlobInfo] (reference + content). Once resolved, subsequent calls in a pipeline
 * skip the store lookup — the resolved blob flows through as-is.
 *
 * ```kotlin
 * suspend fun process(blob: BlobInfoType) {
 *     val resolved = resolver.resolve(blob)  // no-op if already ResolvedBlobInfo
 *     // ... use resolved.data ...
 * }
 * ```
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlobInfoType", exact = true)
sealed interface BlobInfoType {
    val storeId: String?
    val path: String?
    val tenantId: String?
    val contentType: String?
    val metadata: Map<String, String>

    /** Extract the unresolved [BlobInfo] from any variant. */
    fun toBlobInfo(): BlobInfo
}

/**
 * Unresolved blob reference — identifies a blob's location without carrying content.
 *
 * Serializable and safe to pass between layers, services, and over the wire.
 * Mirrors [KeyInfo] with `key = null`: just enough metadata to look up the blob at execution time.
 *
 * @param storeId Configured store instance ID (e.g., "sharepoint", "azure", "documents").
 *   Resolved from default if null.
 * @param path Blob path within the store. Null for server-assigned paths on write.
 * @param tenantId Tenant context. Resolved from session if null.
 * @param contentType Content type hint. Used when writing; informational when reading.
 * @param metadata Custom key-value metadata to attach on write.
 * @param opts Transient runtime options (not serialized). For passing context between layers
 *   without polluting the serialized form, like KeyInfo.opts.
 */
@Serializable
@SerialName("blob-info")
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlobInfo", exact = true)
data class BlobInfo(
    override val storeId: String? = null,
    override val path: String? = null,
    override val tenantId: String? = null,
    override val contentType: String? = null,
    override val metadata: Map<String, String> = emptyMap(),
    @Transient
    val opts: Map<String, String> = emptyMap(),
) : BlobInfoType {

    override fun toBlobInfo(): BlobInfo = this

    /**
     * Returns a [BlobMetadata] from this info's content type and custom metadata.
     */
    fun toBlobMetadata(): BlobMetadata = BlobMetadata(
        contentType = contentType,
        custom = metadata,
    )

    /**
     * Lightweight identity for use as map keys and cache lookups.
     * Like [KeyIdentity] — deterministic equality without transient fields.
     */
    fun toIdentity(): BlobIdentity = BlobIdentity(
        storeId = storeId,
        path = path,
        tenantId = tenantId,
    )

    /** Creates a copy targeting a different path. */
    fun withPath(newPath: String): BlobInfo = copy(path = newPath)

    /** Creates a copy targeting a different store. */
    fun withStore(newStoreId: String): BlobInfo = copy(storeId = newStoreId)

    companion object {
        fun of(storeId: String, path: String) = BlobInfo(storeId = storeId, path = path)

        fun fromDescriptor(
            descriptor: BlobDescriptor,
            tenantId: String? = null,
        ) = BlobInfo(
            storeId = descriptor.storeId,
            path = descriptor.path,
            tenantId = tenantId,
            contentType = descriptor.contentType,
            metadata = descriptor.metadata.custom,
        )

        /**
         * Validates a blob path for security:
         * - Rejects path traversal segments (`..`)
         * - Rejects null bytes
         * - Rejects absolute paths
         * - Rejects backslash separators
         */
        fun validatePath(path: String) {
            require(!path.contains('\u0000')) { "Blob path must not contain null bytes" }
            require(!path.startsWith('/')) { "Blob path must not be absolute" }
            require(!path.contains('\\')) { "Blob path must use '/' separators, not '\\'" }
            val segments = path.split('/')
            require(segments.none { it == ".." }) { "Blob path must not contain '..' traversal segments: $path" }
            require(segments.none { it == "." }) { "Blob path must not contain '.' self-reference segments: $path" }
        }

        /**
         * Sanitizes and normalizes a user-provided path. Returns a safe path string.
         * Throws [IllegalArgumentException] if the path is malicious.
         */
        fun sanitizePath(path: String): String {
            val normalized = path
                .replace('\\', '/')
                .replace(Regex("/+"), "/")
                .trimStart('/')
                .trimEnd('/')
            require(normalized.isNotBlank()) { "Sanitized path must not be blank" }
            validatePath(normalized)
            return normalized
        }
    }
}

/**
 * Resolved blob reference — carries actual content alongside the reference.
 *
 * Like [ResolvedKeyInfo]: once a blob is resolved, it stays resolved through the pipeline.
 * Functions accepting [BlobInfoType] get either variant — if already resolved, skip the lookup.
 *
 * Content is stored as base64 for serialization. Use [data] for decoded bytes,
 * or [fromContent] to construct from raw bytes.
 */
@Serializable
@SerialName("resolved-blob-info")
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolvedBlobInfo", exact = true)
data class ResolvedBlobInfo(
    val info: BlobInfo,
    val dataBase64: String,
    val descriptor: BlobDescriptor,
) : BlobInfoType {

    override val storeId: String? get() = info.storeId ?: descriptor.storeId
    override val path: String? get() = info.path ?: descriptor.path
    override val tenantId: String? get() = info.tenantId
    override val contentType: String? get() = info.contentType ?: descriptor.contentType
    override val metadata: Map<String, String> get() = info.metadata

    override fun toBlobInfo(): BlobInfo = info

    /** Decoded blob content. */
    @OptIn(ExperimentalEncodingApi::class)
    @Transient
    val data: ByteArray = Base64.decode(dataBase64)

    /** Size of the blob in bytes. */
    val sizeBytes: Long get() = descriptor.sizeBytes

    /**
     * Create a target [BlobInfo] pointing to a different store/path,
     * carrying forward content type and metadata from this resolved blob.
     */
    fun retarget(storeId: String, path: String): BlobInfo = BlobInfo(
        storeId = storeId,
        path = path,
        tenantId = info.tenantId,
        contentType = contentType,
        metadata = info.metadata,
    )

    companion object {
        /**
         * Construct from raw bytes (encodes to base64 internally).
         * Use this from resolvers and store operations.
         */
        @OptIn(ExperimentalEncodingApi::class)
        fun fromContent(info: BlobInfo, data: ByteArray, descriptor: BlobDescriptor) = ResolvedBlobInfo(
            info = info,
            dataBase64 = Base64.encode(data),
            descriptor = descriptor,
        )
    }

    // ByteArray-aware equals/hashCode
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ResolvedBlobInfo) return false
        return info == other.info && dataBase64 == other.dataBase64 && descriptor == other.descriptor
    }

    override fun hashCode(): Int {
        var result = info.hashCode()
        result = 31 * result + dataBase64.hashCode()
        result = 31 * result + descriptor.hashCode()
        return result
    }
}

/**
 * Lightweight identity for caching and map keys.
 * Like [KeyIdentity] — deterministic equality without metadata or transient fields.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("BlobIdentity", exact = true)
data class BlobIdentity(
    val storeId: String? = null,
    val path: String? = null,
    val tenantId: String? = null,
) {
    fun hasIdentity(): Boolean = path != null
}
