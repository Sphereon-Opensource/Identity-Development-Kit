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

package com.sphereon.data.store.blob

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
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
    suspend fun canDelete(
        info: BlobInfo,
        metadata: BlobMetadata,
    ): IdkResult<Boolean, IdkError>

    /**
     * Apply retention hints to a blob descriptor.
     */
    suspend fun applyRetention(descriptor: BlobDescriptor): IdkResult<BlobDescriptor, IdkError>
}
