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
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Options for listing blobs within a store.
 */
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("ListOptions", exact = true)
@JsExportCompat
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
