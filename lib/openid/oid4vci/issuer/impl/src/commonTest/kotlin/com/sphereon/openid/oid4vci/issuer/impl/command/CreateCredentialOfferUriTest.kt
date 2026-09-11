/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vci.issuer.config.Oid4vciIssuerProtocolConfig
import com.sphereon.openid.oid4vci.issuer.impl.lifecycle.OfferLifecycleInitializer
import com.sphereon.openid.oid4vci.issuer.impl.testAuthorizationSnapshot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class CreateCredentialOfferUriTest {
    @Test
    fun offerUriUsesConfiguredProtocolBasePath() =
        runTest {
            val command =
                CreateCredentialOfferCommandImpl(
                    execution =
                        TestSessionExecution(
                            appConfig =
                                TestAppConfigService(
                                    mapOf(Oid4vciIssuerProtocolConfig.BASE_PATH_KEY to "/oid4vci"),
                                ),
                        ),
                    asBridge = NoOpAsBridge(),
                    offerStore = NoOpOfferStore(),
                    sessionStore = RecordingSessionStore(),
                    lifecycleInitializer = OfferLifecycleInitializer(),
                )

            val result =
                command.execute(
                    CreateCredentialOfferArgs(
                        instanceId = "00000000-0000-4000-8000-000000000013",
                        issuerId = "https://issuer.example.com",
                        credentialConfigurationIds = listOf("UniversityDegree"),
                        preAuthorizedCodeGrant = true,
                        authorizationPolicySnapshot = testAuthorizationSnapshot("00000000-0000-4000-8000-000000000013"),
                    ),
                )

            assertTrue(result.isOk, "Offer creation should succeed")
            assertTrue(
                result.value.offerUri.contains("https%3A%2F%2Fissuer.example.com%2Foid4vci%2Fcredentials%2Foffers%2F"),
                "Offer URI should dereference through the configured protocol base path: ${result.value.offerUri}",
            )
        }

    @Test
    fun offerUriDoesNotDuplicateConfiguredProtocolBasePath() =
        runTest {
            val command =
                CreateCredentialOfferCommandImpl(
                    execution =
                        TestSessionExecution(
                            appConfig =
                                TestAppConfigService(
                                    mapOf(Oid4vciIssuerProtocolConfig.BASE_PATH_KEY to "/oid4vci"),
                                ),
                        ),
                    asBridge = NoOpAsBridge(),
                    offerStore = NoOpOfferStore(),
                    sessionStore = RecordingSessionStore(),
                    lifecycleInitializer = OfferLifecycleInitializer(),
                )

            val result =
                command.execute(
                    CreateCredentialOfferArgs(
                        instanceId = "00000000-0000-4000-8000-000000000014",
                        issuerId = "https://issuer.example.com/oid4vci",
                        credentialConfigurationIds = listOf("UniversityDegree"),
                        preAuthorizedCodeGrant = true,
                        authorizationPolicySnapshot = testAuthorizationSnapshot("00000000-0000-4000-8000-000000000014"),
                    ),
                )

            assertTrue(result.isOk, "Offer creation should succeed")
            assertTrue(
                result.value.offerUri.contains("https%3A%2F%2Fissuer.example.com%2Foid4vci%2Fcredentials%2Foffers%2F"),
                "Offer URI should include one protocol base path: ${result.value.offerUri}",
            )
            assertTrue(
                !result.value.offerUri.contains("%2Foid4vci%2Foid4vci%2F"),
                "Offer URI should not duplicate the configured protocol base path: ${result.value.offerUri}",
            )
        }
}
