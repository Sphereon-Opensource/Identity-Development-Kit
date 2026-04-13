package com.sphereon.data.store.blob

import com.sphereon.crypto.core.generic.DigestAlg
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Options for blob put operations.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("PutOptions", exact = true)
data class PutOptions(
    val overwrite: Boolean = true,
    val digestAlgorithm: DigestAlg? = null,
    val ifNoneMatch: String? = null,
) {
    companion object {
        val DEFAULT = PutOptions()
        val NO_OVERWRITE = PutOptions(overwrite = false)
    }
}
