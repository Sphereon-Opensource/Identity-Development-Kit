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

package com.sphereon.did.methods.webvh.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * `did:webvh` v1.0 §3.2 witness configuration.
 *
 * Threshold M-of-N witness DIDs (which MUST be `did:key`) co-sign the
 * DID controller's log entry versionId.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("WebvhWitnessConfig", exact = true)
@JsExportCompat
@Serializable
data class WebvhWitnessConfig(
    val threshold: Int,
    val witnesses: List<WebvhWitnessRef>,
) {
    init {
        require(threshold in 1..witnesses.size) {
            "witness threshold $threshold must be in [1, ${witnesses.size}]"
        }
        require(witnesses.distinctBy { it.id }.size == witnesses.size) {
            "witness ids must be unique"
        }
    }
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("WebvhWitnessRef", exact = true)
@JsExportCompat
@Serializable
data class WebvhWitnessRef(
    /** A `did:key` DID identifying the witness. */
    val id: String,
)
