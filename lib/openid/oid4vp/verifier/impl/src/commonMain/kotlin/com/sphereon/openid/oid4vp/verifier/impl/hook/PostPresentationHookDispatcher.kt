/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vp.verifier.impl.hook

import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.core.api.log.Log
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.ServiceCommandRegistry
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.openid.oid4vp.verifier.hook.PostPresentationHookArgs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Fan-out dispatcher for post-presentation hook [ServiceCommand]s. Mirrors
 * `PostIssuanceHookDispatcher` from the OID4VCI side. Extracted so the
 * resolve + fire behaviour can be tested without wiring the full verifier
 * graph.
 *
 * Contract:
 *  * `resolver`: app-scoped discovery registry (lists registered command IDs).
 *  * `sessionCommands`: session-scoped registry that resolves an ID to an
 *    executable [ServiceCommand] instance.
 *  * `propertyResolver`: optional — when present, `hooks.<hookPointId>.commands`
 *    and `hooks.<hookPointId>.patterns` config keys feed the resolve step.
 *    When null, the default pattern `hook.post-presentation.**` applies.
 *
 * Per-hook failures are isolated via `runCatching` so one failing hook
 * doesn't cascade to siblings or affect the redirect URI returned to the
 * wallet. Retry semantics are the hook's own concern.
 */
class PostPresentationHookDispatcher(
    private val resolver: ServiceCommandRegistry,
    private val sessionCommands: SessionScopedCommandRegistry,
    private val propertyResolver: PropertyResolver? = null,
    private val hookPointId: String = DEFAULT_HOOK_POINT,
) {
    suspend fun dispatch(
        args: PostPresentationHookArgs,
        sessionAllowList: List<String>? = null,
    ) {
        val ids =
            PostPresentationHookResolver.resolveHookIds(
                hookPointId = hookPointId,
                discovery = resolver,
                propertyResolver = propertyResolver,
                sessionAllowList = sessionAllowList,
            )
        if (ids.isEmpty()) return

        coroutineScope {
            ids
                .mapNotNull { sessionCommands.get(it) }
                .filter { runCatching { it.supports(args) }.getOrElse { false } }
                .map { hook ->
                    async {
                        runCatching {
                            @Suppress("UNCHECKED_CAST")
                            (hook as ServiceCommand<PostPresentationHookArgs, *, *>).execute(args)
                        }.onFailure {
                            log.warn("post-presentation hook ${hook.commandId} failed: ${it.message}")
                        }
                    }
                }.awaitAll()
        }
    }

    companion object {
        const val DEFAULT_HOOK_POINT: String = "oid4vp.after-presentation-validated"
        private val log = Log.app().withTag("Oid4vpPostPresentationHooks")
    }
}
