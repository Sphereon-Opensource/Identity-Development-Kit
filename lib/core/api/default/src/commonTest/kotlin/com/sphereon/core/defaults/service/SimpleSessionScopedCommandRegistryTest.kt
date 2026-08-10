/*
 * Â© 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.core.defaults.service

import com.sphereon.core.api.service.ServiceCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SimpleSessionScopedCommandRegistryTest {
    @Test
    fun registryAcceptsAndSnapshotsCanonicalMapKeys() {
        val commands = mutableMapOf<String, Lazy<ServiceCommand<*, *, *>>>()
        commands["did.hosting.document-get"] = lazy { error("Command must remain lazy") }

        val registry = SimpleSessionScopedCommandRegistry(commands)
        commands.clear()

        assertEquals(listOf("did.hosting.document-get"), registry.listCommandIds())
    }

    @Test
    fun registryRejectsNonCanonicalMapKeysBeforeIndexing() {
        val commands =
            mapOf<String, Lazy<ServiceCommand<*, *, *>>>(
                "did.hosting.document--get" to lazy { error("Command must remain lazy") },
            )

        assertFailsWith<IllegalArgumentException> {
            SimpleSessionScopedCommandRegistry(commands)
        }
    }
}
