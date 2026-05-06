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
 * `did:webvh` v1.0 §3.2 log entry parameters.
 *
 * Sticky/inheriting object: when a field is `null` on a non-genesis entry it
 * inherits the value from the prior entry; the explicit empty list (e.g.
 * `updateKeys = emptyList()`) clears the prior value. Genesis entries (entry 1)
 * MUST set [method], [scid], and [updateKeys].
 *
 * Replay logic for inheritance lives in the resolver module
 * (`:lib:did:methods:webvh:resolver`); this class is a pure data carrier.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("WebvhParameters", exact = true)
@JsExportCompat
@Serializable
data class WebvhParameters(
    /** Method identifier, e.g. `did:webvh:1.0`. Required in entry 1. */
    val method: String? = null,
    /** Self-Certifying IDentifier (46 base58btc chars). Required in entry 1, MUST NOT appear later. */
    val scid: String? = null,
    /** Multikey-encoded public keys authorized to sign the next log entry. */
    val updateKeys: List<String>? = null,
    /** Pre-rotation: hashes of the keys allowed to appear in the *next* entry's [updateKeys]. */
    val nextKeyHashes: List<String>? = null,
    /** Witness configuration (threshold + witness DIDs). Empty `{}` deactivates witnessing. */
    val witness: WebvhWitnessConfig? = null,
    /** Watcher URLs that mirror the log/witness file. */
    val watchers: List<String>? = null,
    /** Once true, DID is deactivated and no further updates are permitted. */
    val deactivated: Boolean? = null,
    /** Once set true in entry 1, the DID may move to a different web location. May only be set in entry 1. */
    val portable: Boolean? = null,
    /** Cache TTL hint, seconds. `0` disables caching. Defaults to 3600 when null. */
    val ttl: Long? = null,
)
