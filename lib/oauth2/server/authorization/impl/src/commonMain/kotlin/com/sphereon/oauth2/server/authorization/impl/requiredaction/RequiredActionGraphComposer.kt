/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.requiredaction

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.idv.model.IdvNode
import com.sphereon.identity.idv.model.IdvNodeId
import com.sphereon.identity.idv.model.SequenceNode
import com.sphereon.oauth2.server.authorization.requiredaction.RequiredAction
import com.sphereon.oauth2.server.authorization.requiredaction.RequiredActionGraphProvider
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

/**
 * Walks the [providers] set for each unmet [RequiredAction] and assembles a
 * [SequenceNode] of the IDV nodes that fulfil them. The composer is the bridge
 * between "what the AS still wants" (a list of action ids + metadata) and "what the
 * IDV runner can execute" (a graph of method nodes).
 *
 * **First-non-null-wins per action.** Providers are walked in iteration order; the
 * first one that returns a non-null node claims the action and subsequent providers
 * are skipped for that action. Two providers claiming the same `actionId` is
 * effectively a configuration error; the deployment owner controls which one wins
 * by ordering the multibinding via the standard Metro mechanisms.
 *
 * **Unmappable actions log + skip, not fail.** A required-action whose actionId no
 * provider handles is almost always a configuration drift problem (an evaluator
 * shipped before its matching method module landed, or the customer disabled the
 * method module). The composer logs a warning and continues — the resulting graph
 * is incomplete, the orchestrator will surface the rest of the graph, and on the
 * next gate evaluation the unmappable evaluator's action will fire again. Failing
 * the request would brick logins for every user as soon as a misconfiguration
 * landed; a partial graph degrades gracefully.
 *
 * **Returns null when nothing resolved.** Lets the caller (the strategy layer in
 * Item 11) decide between falling back to the JSON `interaction_required` response
 * and surfacing some other recovery UI.
 */
interface RequiredActionGraphComposer {
    /**
     * Build the IDV graph that fulfils all [unmet] required actions for [tenantId].
     * Returns `null` when no provider resolved any of the actions.
     */
    suspend fun compose(
        unmet: List<RequiredAction>,
        tenantId: String
    ): IdvNode?
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
class DefaultRequiredActionGraphComposer(
    private val providers: Set<RequiredActionGraphProvider>,
    private val execution: SessionExecution,
) : RequiredActionGraphComposer {
    override suspend fun compose(
        unmet: List<RequiredAction>,
        tenantId: String
    ): IdvNode? {
        if (unmet.isEmpty()) return null

        val resolved = mutableListOf<IdvNode>()
        for (action in unmet) {
            val node = resolveOne(action, tenantId)
            if (node != null) {
                resolved += node
            } else {
                // runCatching: the log service is session-scoped and may not be available in
                // every test harness (the composer's contract is just to skip unmappable actions);
                // a logging failure must NOT brick the orchestration.
                runCatching {
                    execution.log.warn(
                        message = "no RequiredActionGraphProvider mapped action '${action.actionId}'; skipping",
                        metadata =
                            mapOf(
                                "action_id" to action.actionId,
                                "tenant_id" to tenantId,
                                "tag" to TAG,
                            ),
                    )
                }
            }
        }

        return when (resolved.size) {
            0 -> {
                null
            }

            // Single-node graph: collapse the wrapping SequenceNode so the engine sees the
            // node directly (one fewer state-machine hop and one less audit row to read).
            1 -> {
                resolved.single()
            }

            else -> {
                SequenceNode(
                    nodeId = IdvNodeId("required-actions-seq"),
                    children = resolved,
                )
            }
        }
    }

    private suspend fun resolveOne(
        action: RequiredAction,
        tenantId: String
    ): IdvNode? {
        for (provider in providers) {
            val node = provider.graphFor(action, tenantId)
            if (node != null) return node
        }
        return null
    }

    companion object {
        private const val TAG: String = "RequiredActionGraphComposer"
    }
}
