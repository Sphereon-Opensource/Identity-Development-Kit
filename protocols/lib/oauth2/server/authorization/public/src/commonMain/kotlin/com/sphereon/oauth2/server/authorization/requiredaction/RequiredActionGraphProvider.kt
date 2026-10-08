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

import com.sphereon.identity.idv.model.IdvNode

/**
 * Maps a [RequiredAction] (surfaced by an evaluator at code-issuance time) to the
 * IDV graph fragment that fulfils it. One provider per action type, contributed via
 * Metro multibinding. The composer ([com.sphereon.oauth2.server.authorization.requiredaction.RequiredActionGraphComposer]
 * in the impl module) walks the providers in order and short-circuits on the first
 * provider that returns a non-null node — so two providers claiming the same
 * `actionId` is a configuration error caught at composition time rather than at
 * runtime.
 *
 * Providers ship next to their owning method module (e.g. the `accept-terms`
 * provider lives next to `AcceptTermsMethodDefinition`), so adding a new
 * required-action type and adding the IDV node that fulfils it is a single
 * cohesive change.
 *
 * **Why nullable, not Either-style:** providers usually only know their own
 * actionId, so a no-op for any other action is the common case. Returning null
 * is cheaper to author than a typed mismatch.
 *
 * **Why pass tenantId:** providers can read tenant-scoped config (e.g.
 * `oauth2.required_action.<actionId>.method_id`) to swap the underlying
 * enrollment method per tenant without rewiring the multibinding.
 */
interface RequiredActionGraphProvider {
    /**
     * Build the [IdvNode] that fulfils [action] for the given [tenantId], or return
     * `null` if this provider does not handle the action's [RequiredAction.actionId].
     *
     * Implementations are free to return any node shape — most providers return a
     * single [com.sphereon.identity.idv.model.MethodNode], but a provider for a
     * compound action (e.g. "verify email then enroll TOTP") may return a
     * [com.sphereon.identity.idv.model.SequenceNode] of its own.
     */
    suspend fun graphFor(
        action: RequiredAction,
        tenantId: String
    ): IdvNode?
}
