/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.rest.server.adapter

import com.sphereon.core.api.error.IdkError
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals

/**
 * A failed key registration keeps its status code and says which of the distinct causes applies,
 * without echoing provider or exception text.
 */
class KeyRegistrationErrorResponseTest {
    @Test
    fun aMissingProviderKeyAndAnAlreadyRegisteredKeyAreToldApart() {
        val missing =
            IdkError.fromString(
                code = "KMS_EXTERNAL_KEY_NOT_FOUND",
                message = "The requested provider key is not available",
            )
        val registered =
            IdkError.fromString(
                code = "KMS_EXTERNAL_KEY_REGISTRATION_CONFLICT",
                message = "The canonical provider key identifier is already registered under another alias",
            )

        assertEquals(404, registrationHttpStatus(missing))
        assertEquals(409, registrationHttpStatus(registered))
        assertEquals(
            "No key with this alias or kid exists in the KMS provider, or it is not assigned to this tenant",
            registrationErrorMessage(missing),
        )
        assertEquals(
            "The canonical provider key identifier is already registered under another alias",
            registrationErrorMessage(registered),
        )
        assertNotEquals(registrationErrorMessage(missing), registrationErrorMessage(registered))
    }

    @Test
    fun anUnsupportedProviderKeyIsABadRequestThatSaysWhy() {
        val unsupported =
            IdkError.fromString(
                code = "KMS_EXTERNAL_KEY_UNSUPPORTED",
                message = "The provider key has no stable key identifier, so it cannot be registered as a reference",
            )

        assertEquals(400, registrationHttpStatus(unsupported))
        assertEquals(
            "The provider key has no stable key identifier, so it cannot be registered as a reference",
            registrationErrorMessage(unsupported),
        )
    }

    @Test
    fun unclassifiedFailuresKeepTheGenericMessageAndNeverEchoTheirText() {
        val unknown =
            IdkError.UNKNOWN_ERROR(
                message = "Failed to upsert key reference: jdbc:postgresql://db.internal secret=fixture",
            )

        assertEquals(500, registrationHttpStatus(unknown))
        val message = registrationErrorMessage(unknown)
        assertEquals("Key reference registration failed", message)
        assertFalse(message.contains("secret"))
    }
}
