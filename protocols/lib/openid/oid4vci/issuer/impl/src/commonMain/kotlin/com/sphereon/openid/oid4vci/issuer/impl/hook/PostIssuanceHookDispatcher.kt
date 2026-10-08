/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.openid.oid4vci.issuer.impl.hook

import com.sphereon.core.api.conf.PropertyResolver
import com.sphereon.core.api.log.Log
import com.sphereon.core.api.service.ServiceCommand
import com.sphereon.core.api.service.ServiceCommandRegistry
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.openid.oid4vci.issuer.hook.PostIssuanceHookArgs
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Fan-out dispatcher for post-issuance hook [ServiceCommand]s. Extracted
 * from the issuer command so its resolve + fire behaviour can be tested
 * without wiring the full issuer graph.
 *
 * Contract:
 *  * `resolver`: the app-scoped discovery registry (used to list registered
 *    command IDs).
 *  * `sessionCommands`: the session-scoped registry that resolves an ID to
 *    an executable [ServiceCommand] instance.
 *  * `propertyResolver`: optional — when present, `hooks.<hookPointId>.commands`
 *    and `hooks.<hookPointId>.patterns` config keys feed the resolve step.
 *    When null, the default pattern `hook.post-issuance.**` applies.
 *
 * Per-hook failures are isolated via `runCatching` so one failing hook
 * doesn't cascade to siblings or roll back the credential that was just
 * issued. Retry semantics are the hook's own concern.
 */
class PostIssuanceHookDispatcher(
    private val resolver: ServiceCommandRegistry,
    private val sessionCommands: SessionScopedCommandRegistry,
    private val propertyResolver: PropertyResolver? = null,
    private val hookPointId: String = DEFAULT_HOOK_POINT,
) {
    suspend fun dispatch(
        args: PostIssuanceHookArgs,
        sessionAllowList: List<String>? = null,
    ) {
        val ids =
            PostIssuanceHookResolver.resolveHookIds(
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
                            (hook as ServiceCommand<PostIssuanceHookArgs, *, *>).execute(args)
                        }.onFailure {
                            log.warn("post-issuance hook ${hook.commandId} failed: ${it.message}")
                        }
                    }
                }.awaitAll()
        }
    }

    companion object {
        const val DEFAULT_HOOK_POINT: String = "oid4vci.after-credential-issued"
        private val log = Log.app().withTag("Oid4vciPostIssuanceHooks")
    }
}
