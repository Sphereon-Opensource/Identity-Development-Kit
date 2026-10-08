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

package com.sphereon.did.resolver

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.did.models.VerificationPurpose
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.jvm.JvmOverloads
import kotlin.native.ObjCName

/**
 * Arguments for the ResolveDidCommand.
 *
 * @property did The DID to resolve
 * @property options Resolution options (caching, filtering, etc.)
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolveDidArgs", exact = true)
@JsExportCompat
@Serializable
data class ResolveDidArgs
    @JvmOverloads
    constructor(
        val did: String,
        val options: DidResolutionOptions = DidResolutionOptions(),
    )

/**
 * Arguments for the DereferenceDidCommand.
 *
 * @property didUrl The DID URL to dereference
 * @property options Dereference options
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("DereferenceDidArgs", exact = true)
@JsExportCompat
@Serializable
data class DereferenceDidArgs
    @JvmOverloads
    constructor(
        val didUrl: String,
        val options: DidDereferenceOptions = DidDereferenceOptions(),
    )

/**
 * Arguments for the ResolveVerificationMethodCommand.
 *
 * @property did The DID containing the verification method
 * @property kid The key ID (fragment) of the verification method
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolveVerificationMethodArgs", exact = true)
@JsExportCompat
@Serializable
data class ResolveVerificationMethodArgs(
    val did: String,
    val kid: String,
)

/**
 * Arguments for the ResolveVerificationMethodsByPurposeCommand.
 *
 * @property did The DID to resolve
 * @property purpose The verification purpose to filter by
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolveVerificationMethodsByPurposeArgs", exact = true)
@JsExportCompat
@Serializable
data class ResolveVerificationMethodsByPurposeArgs(
    val did: String,
    val purpose: VerificationPurpose,
)
