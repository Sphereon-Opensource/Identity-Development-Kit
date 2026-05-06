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
import com.sphereon.identity.idv.model.IdvMethodId
import com.sphereon.identity.idv.model.IdvNode
import com.sphereon.identity.idv.model.IdvNodeId
import com.sphereon.identity.idv.model.MethodNode
import com.sphereon.identity.idv.model.SequenceNode
import com.sphereon.oauth2.server.authorization.requiredaction.RequiredAction
import com.sphereon.oauth2.server.authorization.requiredaction.RequiredActionGraphProvider
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RequiredActionGraphComposerTest {
    private val tenant = "t-1"

    @Test
    fun returnsNullWhenUnmetIsEmpty() =
        runTest {
            val composer = newComposer(providers = emptySet())
            assertNull(composer.compose(unmet = emptyList(), tenantId = tenant))
        }

    @Test
    fun returnsNullWhenNoProviderHandlesAnyAction() =
        runTest {
            // The pure-IDK case: zero method modules on the classpath, evaluators may still fire
            // (e.g. a misconfigured deployment), but nothing can fulfil them. Composer must report
            // "no graph" so the strategy layer falls back to JSON `interaction_required`.
            val composer = newComposer(providers = setOf(NoMatchProvider))
            val result =
                composer.compose(
                    unmet = listOf(action("must-change-password")),
                    tenantId = tenant,
                )
            assertNull(result)
        }

    @Test
    fun singleResolvedActionUnwrapsTheSequenceWrapper() =
        runTest {
            // One-node graph collapses to the bare MethodNode — no point in wrapping a single
            // child in a SequenceNode (one fewer state-machine hop, simpler audit trail).
            val composer = newComposer(providers = setOf(forActionId("accept-terms", "accept-terms-default")))
            val result =
                composer.compose(
                    unmet = listOf(action("accept-terms")),
                    tenantId = tenant,
                )
            assertTrue(result is MethodNode, "single resolved node must NOT be wrapped in SequenceNode")
            assertEquals("accept-terms-default", (result as MethodNode).methodId.value)
        }

    @Test
    fun multipleResolvedActionsAreSequencedInOrder() =
        runTest {
            // Order matters: an evaluator might surface password-rotation BEFORE accept-terms; the
            // graph must drive them in that order so the user sees the same flow ordering as the
            // evaluator's emission order.
            val composer =
                newComposer(
                    providers =
                        setOf(
                            forActionId("must-change-password", "force-rotation-default"),
                            forActionId("accept-terms", "accept-terms-default"),
                        ),
                )
            val result =
                composer.compose(
                    unmet = listOf(action("must-change-password"), action("accept-terms")),
                    tenantId = tenant,
                )
            assertTrue(result is SequenceNode)
            val children = (result as SequenceNode).children
            assertEquals(2, children.size)
            assertEquals("force-rotation-default", (children[0] as MethodNode).methodId.value)
            assertEquals("accept-terms-default", (children[1] as MethodNode).methodId.value)
        }

    @Test
    fun firstNonNullProviderWinsForAnAction() =
        runTest {
            // If two providers handle the same actionId, iteration order picks the winner — the
            // deployment owner controls which one wins by ordering the multibinding via standard
            // Metro mechanisms; the composer's job is just to honour first-non-null-wins.
            val composer =
                newComposer(
                    providers =
                        linkedSetOf(
                            forActionId("accept-terms", "tenant-custom-terms"),
                            forActionId("accept-terms", "default-terms"),
                        ),
                )
            val result =
                composer.compose(
                    unmet = listOf(action("accept-terms")),
                    tenantId = tenant,
                )
            assertEquals("tenant-custom-terms", (result as MethodNode).methodId.value)
        }

    @Test
    fun unmappableActionIsSkippedAndOthersStillResolve() =
        runTest {
            // Configuration drift case: the `enroll-mfa` evaluator is on the classpath but the MFA
            // method module isn't. The unmappable action must be skipped (logged as a warning), but
            // the resolvable actions must still produce a graph so logins for users without MFA
            // requirements aren't bricked.
            val composer =
                newComposer(
                    providers = setOf(forActionId("accept-terms", "accept-terms-default")),
                )
            val result =
                composer.compose(
                    unmet = listOf(action("enroll-mfa"), action("accept-terms")),
                    tenantId = tenant,
                )
            assertTrue(result is MethodNode, "single resolved action collapses to bare MethodNode even with siblings dropped")
            assertEquals("accept-terms-default", (result as MethodNode).methodId.value)
        }

    // ── Helpers ─────────────────────────────────────────────────────

    private fun newComposer(providers: Set<RequiredActionGraphProvider>): DefaultRequiredActionGraphComposer =
        DefaultRequiredActionGraphComposer(
            providers = providers,
            execution = NoLogExecution,
        )

    private fun action(id: String): RequiredAction = RequiredAction(actionId = id, displayName = id, metadata = emptyMap())

    private fun forActionId(
        actionId: String,
        methodId: String
    ): RequiredActionGraphProvider =
        object : RequiredActionGraphProvider {
            override suspend fun graphFor(
                action: RequiredAction,
                tenantId: String
            ): IdvNode? =
                if (action.actionId == actionId) {
                    MethodNode(nodeId = IdvNodeId("$actionId-node"), methodId = IdvMethodId(methodId))
                } else {
                    null
                }
        }
}

/** Provider that NEVER claims any action — proves the all-null path returns null. */
private object NoMatchProvider : RequiredActionGraphProvider {
    override suspend fun graphFor(
        action: RequiredAction,
        tenantId: String
    ): IdvNode? = null
}

/**
 * Stub execution whose `log` access throws — wrapped in `runCatching` inside the composer,
 * so the composer's "log warning, continue" branch must NOT fail even when logging blows up.
 * Verifies the composer is robust to a missing log subsystem.
 */
private object NoLogExecution : SessionExecution {
    override val tenantId: String get() = "t-1"
    override val principalId: String get() = "principal-stub"
    override val sessionContextManager get() = error("not used in composer tests")
    override val sessionContext get() = error("not used in composer tests")
    override val log get() = error("composer must runCatching around log calls")
    override val conf get() = error("not used in composer tests")
}
