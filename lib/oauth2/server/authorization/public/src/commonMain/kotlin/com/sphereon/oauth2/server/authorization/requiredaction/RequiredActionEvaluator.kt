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

import com.sphereon.oauth2.server.authorization.model.AuthorizationSession
import com.sphereon.oauth2.server.authorization.model.ClientRegistration

/**
 * Hook the AS calls right before minting an authorization code. Each registered
 * evaluator returns the list of [RequiredAction] obligations the user must
 * satisfy; if any evaluator returns a non-empty list, the AS refuses the mint
 * with [com.sphereon.oauth2.server.authorization.error.AuthorizationServerError.RequiredActionsPending].
 *
 * Multibound — every evaluator runs and the union of returned actions is the
 * full work-list. Empty set (the IDK default) means no actions are ever required;
 * EDK overlays add evaluators for password rotation, ToS acceptance, mandatory
 * MFA enrollment, etc.
 *
 * **Contract:**
 *  - Evaluators MUST be deterministic for a given (client, userId) pair within a
 *    single request — if action A is returned for the IDV-runner kickoff but
 *    NOT for the post-IDV resume, the AS will mint a code for an unsatisfied
 *    obligation.
 *  - Evaluators MUST be idempotent / side-effect-free — they run on every
 *    code-issuance attempt; persisting state inside an evaluator is wrong.
 *  - Evaluators MUST be cheap — they run synchronously on the auth code path.
 *
 * **Scope:** SessionScope (matches the gate). Implementations inject
 * [com.sphereon.core.api.conf.TenantConfigService] for per-tenant config with the
 * natural parent-cascade to app default, and [com.sphereon.core.api.context.SessionExecution]
 * for the in-flight tenant binding when needed.
 */
interface RequiredActionEvaluator {
    /**
     * Decide what (if anything) the user must satisfy before this code can be
     * minted. Empty list = no obligation; non-empty = the AS will refuse the mint
     * and surface the actions to the orchestrator.
     *
     * @param client the OAuth2 client requesting the code; lets per-client policies
     *   (e.g. "this client requires AAL2 enrollment for any user") apply via
     *   [com.sphereon.oauth2.server.authorization.model.ClientRegistration.additionalMetadata]
     *   overrides.
     * @param userId the authenticated user's stable identifier (typically the
     *   identity-id Uuid as a string).
     * @param session the in-flight AS session — gives the evaluator visibility into
     *   `acrValues`, `authTime`, and any session-scoped flags an upstream
     *   authentication step set. Tenant resolution happens through the evaluator's
     *   own session-scoped dependencies.
     */
    suspend fun evaluate(
        client: ClientRegistration,
        userId: String,
        session: AuthorizationSession,
    ): List<RequiredAction>
}
