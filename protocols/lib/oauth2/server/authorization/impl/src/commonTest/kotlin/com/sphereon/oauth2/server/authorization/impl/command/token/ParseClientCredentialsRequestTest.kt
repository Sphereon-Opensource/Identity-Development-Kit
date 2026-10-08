/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.command.token

import com.sphereon.oauth2.common.model.GrantType
import com.sphereon.oauth2.server.authorization.command.GrantParameters
import com.sphereon.oauth2.server.authorization.command.ParseTokenRequestArgs
import com.sphereon.oauth2.server.authorization.impl.testutil.OAuth2ServerTestContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ParseClientCredentialsRequestTest {
    private val ctx = OAuth2ServerTestContext("parse-client-credentials-test", this)
    private val command = ParseTokenRequestCommandImpl(ctx.execution)

    @Test
    fun parseClientCredentialsCarriesAudienceParameters() =
        runTest {
            val requestBody =
                mapOf(
                    "grant_type" to listOf("client_credentials"),
                    "client_id" to listOf("tenant-as-service"),
                    "scope" to listOf("internal"),
                    "audience" to listOf("enterprise-tenant-kms"),
                )

            val result = command.execute(ParseTokenRequestArgs(requestBody, emptyMap()))

            assertTrue(result.isOk)
            val request = result.value
            assertEquals(GrantType.CLIENT_CREDENTIALS, request.grantType)
            assertTrue(request.grantParameters is GrantParameters.ClientCredentials)
            val grantParams = request.grantParameters as GrantParameters.ClientCredentials
            assertEquals("internal", grantParams.scope)
            assertEquals(listOf("enterprise-tenant-kms"), grantParams.audiences)
        }
}
