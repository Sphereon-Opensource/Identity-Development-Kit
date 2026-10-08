/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.openid.oid4vci.issuer.impl.http.command.GetCredentialInvitePageEndpointCommandImpl
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetCredentialInvitePageEndpointCommandTest {
    @Test
    fun invitePageIsPublicHtmlForProtocolAndInstanceLandings() =
        runTest {
            val command = GetCredentialInvitePageEndpointCommandImpl(TestSessionExecution())

            val result =
                command.execute(
                    GenericHttpRequest(
                        method = "GET",
                        path = "/invite",
                        queryParameters = mapOf("t" to "invite-token-1"),
                    ),
                )

            assertTrue(result.isOk)
            assertEquals(200, result.value.statusCode)
            assertEquals("text/html; charset=utf-8", result.value.headers["Content-Type"])
            assertTrue(result.value.body.orEmpty().contains("Claim your credential"))
            assertTrue(result.value.body.orEmpty().contains("/api/invitation/v1/redeem"))
        }
}
