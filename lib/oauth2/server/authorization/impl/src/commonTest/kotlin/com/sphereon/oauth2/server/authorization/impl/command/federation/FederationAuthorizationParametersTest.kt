/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.oauth2.server.authorization.impl.command.federation

import com.sphereon.oauth2.server.authorization.command.federation.InitiateProviderAuthenticationArgs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class FederationAuthorizationParametersTest {
    @Test
    fun forcedReauthenticationRequestsFreshUpstreamLogin() {
        val parameters = federationAuthorizationParameters(args(forceReauth = true), nonce = "nonce")
        assertEquals("login", parameters["prompt"])
    }

    @Test
    fun ordinaryFederationDoesNotForceUpstreamLogin() {
        val parameters = federationAuthorizationParameters(args(forceReauth = false), nonce = "nonce")
        assertFalse("prompt" in parameters)
    }

    private fun args(forceReauth: Boolean) =
        InitiateProviderAuthenticationArgs(
            sessionId = "session",
            returnUrl = "https://as.example/authorize/callback?session_id=session",
            providerId = "provider",
            forceReauth = forceReauth,
        )
}
