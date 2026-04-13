package com.sphereon.data.store.blob

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Retention policy service interface.
 *
 * IDK defines the interface only. EDK provides a jurisdiction-aware implementation.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("RetentionPolicyService", exact = true)
interface RetentionPolicyService {

    /**
     * Check if a blob may be deleted according to retention policies.
     */
    suspend fun canDelete(info: BlobInfo, metadata: BlobMetadata): IdkResult<Boolean, IdkError>

    /**
     * Apply retention hints to a blob descriptor.
     */
    suspend fun applyRetention(descriptor: BlobDescriptor): IdkResult<BlobDescriptor, IdkError>
}
