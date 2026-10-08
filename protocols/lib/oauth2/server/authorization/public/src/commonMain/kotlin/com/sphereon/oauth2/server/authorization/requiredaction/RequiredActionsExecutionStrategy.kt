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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.identity.idv.model.IdvExecution
import com.sphereon.identity.idv.model.IdvSubjectRef

/**
 * Materialises a required-actions IDV execution from a list of unmet actions. The
 * AS orchestrator delegates to this SPI on every refused mint so deployments can
 * pick how the obligation gets fulfilled:
 *
 *  - **Adhoc** strategy composes a graph from
 *    [RequiredActionGraphProvider]s (zero-config, just works once method modules
 *    are on the classpath).
 *  - **Stored use case** strategy looks up a pre-authored
 *    [com.sphereon.identity.idv.model.IdvUseCaseDefinition] (lets a tenant author
 *    bespoke flows: branching, intro screens, additional consent, …).
 *  - Custom strategies are first-class via the same multibinding.
 *
 * Multibound — the orchestrator picks one based on the strategy id resolved from
 * config. Two strategies sharing the same [id] is a configuration error caught
 * eagerly by the orchestrator.
 *
 * **Scope:** SessionScope. Strategies typically inject session-scoped commands
 * ([com.sphereon.identity.idv.command.StartIdvExecutionCommand] /
 * [com.sphereon.identity.idv.command.StartAdhocIdvExecutionCommand]) and read
 * tenant config via the natural cascade.
 */
interface RequiredActionsExecutionStrategy {
    /**
     * Stable identifier for the strategy. The orchestrator matches this against
     * the value resolved from per-client `additionalMetadata["required_actions.strategy"]`
     * → tenant `oauth2.required_actions.strategy` → app default `adhoc`.
     *
     * Convention: lowercase-kebab-case (`adhoc`, `stored-use-case`, `tenantA-onboarding`).
     */
    val id: String

    /**
     * Build (and persist) an [IdvExecution] that fulfils [unmet] for [tenantId] /
     * [subject]. The orchestrator will inspect the returned execution's
     * `currentPendingActions` to render the next HTTP response.
     *
     * **Failure mode contract:** strategies that cannot materialise an execution
     * — composer found no providers; configured use-case id missing or unknown;
     * invalid configuration — MUST return [IdkResult.Err] so the caller can fall
     * back to the JSON `interaction_required` recovery path. Throwing an
     * exception is reserved for genuinely unexpected runtime failures (DB outage,
     * etc.) where the AS prefers to bubble a 5xx.
     */
    suspend fun start(
        unmet: List<RequiredAction>,
        tenantId: String,
        subject: IdvSubjectRef,
        callbackBaseUrl: String,
        correlationId: String,
    ): IdkResult<IdvExecution, IdkError>
}
