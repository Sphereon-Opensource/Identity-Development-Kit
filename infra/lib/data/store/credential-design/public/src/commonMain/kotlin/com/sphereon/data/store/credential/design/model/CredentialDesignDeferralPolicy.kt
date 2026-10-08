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

package com.sphereon.data.store.credential.design.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * Credential-config (credential-design) default deferred-issuance policy. Mirrors the wire-level
 * `IssuanceDeferral` OpenAPI schema (`enabled`, `pollIntervalSeconds`, `maxDeferralSeconds`,
 * `approvalRequired`).
 *
 * This is a neutral, IDK-owned value type — it is NOT the EDK pipeline's
 * `com.sphereon.credential.issuance.pipeline.DeferralPolicy` (this module cannot depend on the
 * EDK issuance-pipeline module; that module already depends the other way, on this one, via
 * [SemanticAttributeSetRef]/[CredentialDesignRecord]). Callers that resolve the effective deferral
 * policy (config default < template override < offer override) map this into the pipeline's
 * `DeferralPolicyOverride` at the point where both types are visible.
 *
 * All fields are nullable so the lowest-precedence "config default" level participates correctly
 * in a field-level fall-through merge — a design need only set the fields it wants to fix; unset
 * fields fall through to the template/offer level, or ultimately the pipeline's built-in default.
 */
@JsExportCompat
@Serializable
data class CredentialDesignDeferralPolicy(
    val enabled: Boolean? = null,
    val pollIntervalSeconds: Int? = null,
    val maxDeferralSeconds: Long? = null,
    val approvalRequired: Boolean? = null,
)
