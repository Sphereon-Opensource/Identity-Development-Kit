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

import com.sphereon.core.compat.JsExportCompat
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
@JsExportCompat
data class PutOptions(
    val overwrite: Boolean = true,
    val digestAlgorithm: DigestAlg? = null,
    /** Exact current ETag, or `*` to require that the blob already exists. */
    val ifMatch: String? = null,
    /** Reject the write when this ETag exists. `*` implements create-only semantics. */
    val ifNoneMatch: String? = null,
    /** Backend-neutral numeric revision that must match before an update is applied. */
    val expectedRevision: Long? = null,
) {
    init {
        require(ifMatch == null || ifNoneMatch == null) { "ifMatch and ifNoneMatch are mutually exclusive" }
        require(expectedRevision == null || expectedRevision >= 0) { "expectedRevision must not be negative" }
    }

    companion object {
        val DEFAULT = PutOptions()
        val NO_OVERWRITE = PutOptions(overwrite = false)
    }
}
