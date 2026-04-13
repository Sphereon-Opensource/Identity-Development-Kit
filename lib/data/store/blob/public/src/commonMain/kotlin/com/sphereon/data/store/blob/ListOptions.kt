package com.sphereon.data.store.blob

import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Options for listing blobs within a store.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ListOptions", exact = true)
data class ListOptions(
    val prefix: String? = null,
    val delimiter: String? = "/",
    val maxResults: Int = 1000,
    val pageToken: String? = null,
    val recursive: Boolean = false,
) {
    companion object {
        val DEFAULT = ListOptions()
    }
}
