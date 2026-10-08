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

package com.sphereon.data.store.blob.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.blob.BlobDescriptor
import com.sphereon.data.store.blob.BlobInfo
import com.sphereon.data.store.blob.BlobMetadata
import com.sphereon.data.store.blob.RetentionPolicyService
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Default retention policy that always allows deletion and passes descriptors through unchanged.
 *
 * EDK replaces this via `@ContributesBinding(replaces = [DefaultRetentionPolicyService::class])`
 * with a jurisdiction-aware implementation that enforces:
 * - Per-document-type retention periods (e.g., exam results kept for 7 years)
 * - Legal hold (documents under legal hold cannot be deleted regardless of retention)
 * - Tenant-specific retention rules
 * - OKD destruction notification lifecycle
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<RetentionPolicyService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("DefaultRetentionPolicyService", exact = true)
class DefaultRetentionPolicyService : RetentionPolicyService {
    override suspend fun canDelete(
        info: BlobInfo,
        metadata: BlobMetadata,
    ): IdkResult<Boolean, IdkError> = Ok(true)

    override suspend fun applyRetention(descriptor: BlobDescriptor): IdkResult<BlobDescriptor, IdkError> = Ok(descriptor)
}
