package com.sphereon.data.store.blob

import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Result of a blob list operation with pagination support.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ListResult", exact = true)
data class ListResult(
    val descriptors: List<BlobDescriptor>,
    val nextPageToken: String? = null,
    val commonPrefixes: List<String> = emptyList(),
) {
    val hasMore: Boolean get() = nextPageToken != null
}
