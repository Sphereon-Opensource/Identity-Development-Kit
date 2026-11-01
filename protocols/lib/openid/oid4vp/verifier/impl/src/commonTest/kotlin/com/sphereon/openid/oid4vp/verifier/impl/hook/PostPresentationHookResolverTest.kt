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

import com.sphereon.core.api.service.ServiceCommandRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class PostPresentationHookResolverTest {
    private val appCommands = listOf("hook.post-presentation.app-hook", "oid4vp.directpost.response")

    @Test
    fun hooksRegisteredOnlyPerSessionAreResolvedToo() {
        // A service that registers its commands per session lists them only in the session registry.
        val ids =
            PostPresentationHookResolver.resolveHookIds(
                hookPointId = "oid4vp.after-presentation-validated",
                discovery = FakeRegistry(appCommands),
                propertyResolver = null,
                sessionAllowList = null,
                sessionCommandIds = listOf("hook.post-presentation.connector-dissemination", "connector.protocol-export.run"),
            )

        assertEquals(setOf("hook.post-presentation.app-hook", "hook.post-presentation.connector-dissemination"), ids)
    }

    @Test
    fun theSessionAllowListStillNarrowsSessionHooks() {
        val ids =
            PostPresentationHookResolver.resolveHookIds(
                hookPointId = "oid4vp.after-presentation-validated",
                discovery = FakeRegistry(appCommands),
                propertyResolver = null,
                sessionAllowList = listOf("hook.post-presentation.app-hook"),
                sessionCommandIds = listOf("hook.post-presentation.connector-dissemination"),
            )

        assertEquals(setOf("hook.post-presentation.app-hook"), ids)
    }

    private class FakeRegistry(
        private val ids: List<String>,
    ) : ServiceCommandRegistry {
        override fun has(commandId: String): Boolean = commandId in ids

        override fun listCommandIds(): List<String> = ids
    }
}
