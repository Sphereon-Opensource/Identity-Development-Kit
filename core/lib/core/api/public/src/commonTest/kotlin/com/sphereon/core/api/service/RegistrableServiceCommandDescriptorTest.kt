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

class RegistrableServiceCommandDescriptorTest {
    @Test
    fun descriptorAcceptsCanonicalCommandId() {
        val descriptor =
            RegistrableServiceCommandDescriptor.of("did.hosting.document-get") {
                error("Factory must remain lazy")
            }

        assertEquals("did.hosting.document-get", descriptor.commandId)
    }

    @Test
    fun descriptorRejectsNonCanonicalCommandIdBeforeStoringIt() {
        assertFailsWith<IllegalArgumentException> {
            RegistrableServiceCommandDescriptor.of("did.hosting.document--get") {
                error("Factory must remain lazy")
            }
        }
    }
}
