package com.sphereon.data.store.blob.command

import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.ListOptions
import com.sphereon.data.store.blob.MetadataSearchQuery
import com.sphereon.data.store.blob.PutOptions
import kotlinx.serialization.Serializable

// -- Standard blob operations --

@Serializable
data class BlobPutInput(
    val target: BlobInfo,
    val dataBase64: String,
    val options: PutOptions = PutOptions.DEFAULT,
)

@Serializable
data class BlobGetInput(
    val info: BlobInfo,
)

@Serializable
data class BlobDeleteInput(
    val info: BlobInfo,
)

@Serializable
data class BlobStatInput(
    val info: BlobInfo,
)

@Serializable
data class BlobListInput(
    val info: BlobInfo,
    val options: ListOptions = ListOptions.DEFAULT,
)

@Serializable
data class BlobCopyInput(
    val source: BlobInfo,
    val destination: BlobInfo,
)

@Serializable
data class BlobMoveInput(
    val source: BlobInfo,
    val destination: BlobInfo,
)

// -- CAS operations --

@Serializable
data class CasStoreInput(
    val info: BlobInfo,
    val dataBase64: String,
    val algorithm: DigestAlg = DigestAlg.SHA256,
)

@Serializable
data class CasGetInput(
    val info: BlobInfo,
    val addressMultibase: String,
)

@Serializable
data class CasVerifyInput(
    val info: BlobInfo,
    val addressMultibase: String,
)

// -- Metadata search --

@Serializable
data class MetadataSearchInput(
    val info: BlobInfo,
    val query: MetadataSearchQuery,
)

// -- Serializable output wrappers --

@Serializable
data class BlobGetOutput(
    val dataBase64: String,
    val path: String,
    val storeId: String,
    val sizeBytes: Long,
    val contentType: String? = null,
)

@Serializable
data class BlobDeleteOutput(
    val deleted: Boolean,
)

@Serializable
data class CasVerifyOutput(
    val valid: Boolean,
)
