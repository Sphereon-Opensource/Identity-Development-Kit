/*
 * © 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

package com.sphereon.openid.oid4vci.issuer.attribute

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration

/**
 * The result of a [CredentialAttributeContributor.contribute] call: the contributed attributes
 * to merge into the credential and the set of source ids the contributor is still waiting on for
 * an inbound async-callback contribution.
 *
 * [pendingAsyncCallbackSources] is the set of opaque contributor ids whose binding declares
 * `callbackStyle = ASYNC_CALLBACK` and have not yet contributed to the pipeline session bag.
 * The issuer command holds its `/credential` response open for [syncWaitWindow] waiting for those
 * sources to land via the inbound callback endpoint, then re-runs the contributor before deciding
 * whether to defer.
 *
 * [syncWaitWindow] is the maximum binding-level `syncWaitWindow` of the still-pending sources —
 * the issuer waits up to this duration for ALL of them, then falls through to the deferral
 * decision on timeout. `Duration.ZERO` means "do not wait" (the existing IDK behaviour).
 *
 * Empty defaults make the no-pipeline / no-async-callback path a degenerate no-op so pure-IDK
 * deployments behave identically to before.
 */
@JsExportCompat
@Serializable
data class CredentialAttributeContribution(
    @JsExportIgnoreCompat
    val attributes: Map<String, JsonElement>,
    @JsExportIgnoreCompat
    val pendingAsyncCallbackSources: Set<String> = emptySet(),
    val syncWaitWindow: Duration = Duration.ZERO,
    /** Lossless top-level VCDM properties contributed by the issuance pipeline. */
    @JsExportIgnoreCompat
    val vcdmProperties: JsonObject = JsonObject(emptyMap()),
    /** Optional semantic VCDM credential identifier, independent from protocol identities. */
    val credentialId: String? = null,
    /** Ordered complete semantic VCDM credential-subject objects. */
    @JsExportIgnoreCompat
    val credentialSubjects: List<JsonObject> = emptyList(),
)
