/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.provider.aws

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AwsKmsEncryptionContextTest {
    @Test
    fun absentAdditionalAuthenticatedDataDoesNotInventContext() {
        assertNull(awsKmsEncryptionContext(null))
    }

    @Test
    fun additionalAuthenticatedDataIsHashedIntoCanonicalProviderContext() {
        val aad = "tenant-a\u0000secret-a\u0000generation-1".encodeToByteArray()
        val context = awsKmsEncryptionContext(aad)

        assertEquals(setOf("sphereon-aad-digest"), context?.keys)
        assertTrue(context?.getValue("sphereon-aad-digest")?.matches(Regex("""sha256:[A-Za-z0-9_-]{43}""")) == true)
        assertEquals(context, awsKmsEncryptionContext(aad.copyOf()))
        assertNotEquals(context, awsKmsEncryptionContext("tenant-b\u0000secret-a\u0000generation-1".encodeToByteArray()))
        assertTrue(context.values.none { value -> value.contains("tenant-a") || value.contains("secret-a") })
    }
}
