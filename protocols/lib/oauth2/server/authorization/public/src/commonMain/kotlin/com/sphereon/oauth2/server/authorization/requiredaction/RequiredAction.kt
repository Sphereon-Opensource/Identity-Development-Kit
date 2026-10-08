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
 */

package com.sphereon.oauth2.server.authorization.requiredaction

/**
 * A pending obligation the authenticated user MUST satisfy before the AS will
 * issue an authorization code. Surfaced by [RequiredActionEvaluator] instances
 * during code-issuance time and consumed by the orchestrator that kicks off the
 * matching IDV graph (TOTP enrollment, ToS acceptance, password rotation, ...).
 *
 * Standalone-mode contract (per IDV's dual-use design): RequiredActions are an
 * AS-side concept only. The IDV graph runtime never creates a RequiredAction — it
 * only EXECUTES whichever graph the AS hands it. So one-shot external flows
 * (verify-this-email-for-document-signing without an IdP account) keep working
 * unchanged.
 */
data class RequiredAction(
    /**
     * Stable identifier for the action's TYPE. Convention: lowercase-kebab-case,
     * namespaced when shipped from a deployment overlay (`platform.must-change-password`,
     * `tenantA.accept-terms-v3`, `oauth2.totp-enrollment`).
     *
     * The AS uses this id to (a) emit the matching audit event when the action is
     * satisfied and (b) look up the IDV graph fragment that fulfils it via the
     * (future) RequiredActionGraphProvider — out of scope this iteration.
     */
    val actionId: String,
    /**
     * Human-readable label suitable for surfacing in the IDV runner UI's progress
     * indicator ("Set up two-factor authentication", "Accept the new Terms of Service").
     * The AS does no i18n on this string; localisation happens at the UI layer.
     */
    val displayName: String,
    /**
     * Per-action carrier for whatever the matching IDV node needs to materialise
     * the action — e.g. `terms_version: "2026-04-01"` for an accept-terms action,
     * `policy_id: "rotate-90d"` for a password-rotation action. Opaque to the
     * evaluator framework.
     */
    val metadata: Map<String, String> = emptyMap(),
)
