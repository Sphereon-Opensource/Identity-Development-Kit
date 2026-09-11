/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.oauth2.server.authorization.impl.command.federation

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FederatedClaimValueTest {
    @Test
    fun preservesArraysAndBooleanClaimsFromUpstreamJson() {
        val claims = Json.parseToJsonElement(
            """{
                "eduperson_scoped_affiliation":["student@kw1c.nl","affiliate@eduid.nl"],
                "eduperson_assurance":["https://refeds.org/assurance/IAP/medium","https://refeds.org/assurance/ID/eppn-unique-no-reassign"],
                "email_verified":true
            }""",
        ) as JsonObject

        val affiliation = claims.getValue("eduperson_scoped_affiliation").toFederatedClaimValue()
        val assurance = claims.getValue("eduperson_assurance").toFederatedClaimValue()
        val emailVerified = claims.getValue("email_verified").toFederatedClaimValue()

        assertIs<List<*>>(affiliation)
        assertEquals(listOf("student@kw1c.nl", "affiliate@eduid.nl"), affiliation)
        assertIs<List<*>>(assurance)
        assertEquals(
            listOf(
                "https://refeds.org/assurance/IAP/medium",
                "https://refeds.org/assurance/ID/eppn-unique-no-reassign",
            ),
            assurance,
        )
        assertEquals(true, emailVerified)
    }
}
