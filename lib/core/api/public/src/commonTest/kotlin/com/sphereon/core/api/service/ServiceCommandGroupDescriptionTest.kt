/*
 * Â© 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.core.api.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServiceCommandGroupDescriptionTest {
    @Test
    fun descriptionAcceptsCanonicalCommandIds() {
        val description =
            ServiceCommandGroupDescription(
                groupId = "did.hosting",
                module = "did",
                service = "hosting",
                displayName = "DID Hosting",
                commandIds = listOf("did.hosting.document-get"),
            )

        assertEquals(listOf("did.hosting.document-get"), description.commandIds)
    }

    @Test
    fun descriptionRejectsNonCanonicalCommandIds() {
        assertFailsWith<IllegalArgumentException> {
            ServiceCommandGroupDescription(
                groupId = "did.hosting",
                module = "did",
                service = "hosting",
                displayName = "DID Hosting",
                commandIds = listOf("did.hosting.document-"),
            )
        }
    }
}
