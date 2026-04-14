/*
 * © 2026 Sphereon International B.V.
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
 *
 */

package com.sphereon.did.capabilities

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic
import kotlin.native.ObjCName

/**
 * Metadata about a DID method.
 *
 * Provides descriptive information about the DID method.
 *
 * @property description Human-readable description of the method
 * @property specificationUrl URL to the method specification
 * @property version Version of the method implementation
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DidMethodMetadata", exact = true)
@JsExportCompat
@Serializable
data class MethodMetadata
    @JvmOverloads
    constructor(
        val description: String? = null,
        val specificationUrl: String? = null,
        val version: String? = null,
    )
