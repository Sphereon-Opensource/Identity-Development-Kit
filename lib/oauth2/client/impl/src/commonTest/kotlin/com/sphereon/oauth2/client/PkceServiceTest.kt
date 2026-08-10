/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.oauth2.client

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.core.defaults.random.defaultSecureRandom
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.command.VerifyPkceArgs
import com.sphereon.oauth2.client.impl.pkce.CreatePkceCommandImpl
import com.sphereon.oauth2.client.impl.pkce.PkceServiceImpl
import com.sphereon.oauth2.client.impl.pkce.VerifyPkceCommandImpl
import com.sphereon.oauth2.client.service.PkceService
import com.sphereon.oauth2.client.testutil.createOAuth2ClientTestAppGraph
import com.sphereon.oauth2.common.model.PkceMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for PKCE (Proof Key for Code Exchange) RFC 7636
 *
 * Tests cover:
 * - PKCE generation with S256 and PLAIN methods
 * - Code verifier generation and validation
 * - Challenge calculation and verification
 * - Error handling for invalid inputs
 */
class PkceServiceTest {
    val app = createOAuth2ClientTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("pkce-service-test", principalType = com.sphereon.di.context.PrincipalType.USER)
    val execution = session.asCoreApiServiceGraph().serviceExecution

    private fun createPkceService(): PkceService {
        val createCommand = CreatePkceCommandImpl(execution, defaultSecureRandom())
        val verifyCommand = VerifyPkceCommandImpl(execution)
        return PkceServiceImpl(createCommand, verifyCommand)
    }

    @Test
    fun testCreatePkceWithS256Method() =
        runTest {
            val pkceService = createPkceService()

            // Generate PKCE data with S256 method
            val result =
                pkceService.createPkce(
                    CreatePkceArgs(codeVerifier = null, allowedMethods = listOf(PkceMethod.S256)),
                )

            assertTrue(result.isOk, "PKCE generation should succeed")
            val pkceData = result.value

            assertNotNull(pkceData)
            assertEquals(PkceMethod.S256, pkceData.codeChallengeMethod, "Should use S256 method")
            assertTrue(pkceData.codeVerifier.length >= 43, "Code verifier should be at least 43 characters")
            assertTrue(pkceData.codeVerifier.length <= 128, "Code verifier should be at most 128 characters")
            assertNotNull(pkceData.codeChallenge)
            assertTrue(pkceData.codeChallenge.isNotEmpty(), "Code challenge should not be empty")
        }

    @Test
    fun testCreatePkceWithPlainMethod() =
        runTest {
            val pkceService = createPkceService()

            // Generate PKCE data with PLAIN method
            val result =
                pkceService.createPkce(
                    CreatePkceArgs(codeVerifier = null, allowedMethods = listOf(PkceMethod.PLAIN)),
                )

            assertTrue(result.isOk, "PKCE generation should succeed")
            val pkceData = result.value

            assertEquals(PkceMethod.PLAIN, pkceData.codeChallengeMethod)
            // For PLAIN method, challenge should equal verifier
            assertEquals(pkceData.codeVerifier, pkceData.codeChallenge, "PLAIN method challenge should equal verifier")
        }

    @Test
    fun testCreatePkceWithCustomVerifier() =
        runTest {
            val pkceService = createPkceService()
            val customVerifier = "custom_verifier_with_at_least_43_characters_aaa"

            // Generate PKCE with custom verifier
            val result =
                pkceService.createPkce(
                    CreatePkceArgs(codeVerifier = customVerifier, allowedMethods = listOf(PkceMethod.S256)),
                )

            assertTrue(result.isOk)
            assertEquals(customVerifier, result.value.codeVerifier, "Should use provided verifier")
        }

    @Test
    fun testVerifyPkceS256Success() =
        runTest {
            val pkceService = createPkceService()

            // First create PKCE data
            val createResult =
                pkceService.createPkce(
                    CreatePkceArgs(codeVerifier = null, allowedMethods = listOf(PkceMethod.S256)),
                )
            assertTrue(createResult.isOk)
            val pkceData = createResult.value

            // Verify with the correct verifier
            val verifyResult =
                pkceService.verifyPkce(
                    VerifyPkceArgs(codeVerifier = pkceData.codeVerifier, codeChallenge = pkceData.codeChallenge, method = pkceData.codeChallengeMethod),
                )

            assertTrue(verifyResult.isOk, "Verification should succeed")
        }

    @Test
    fun testVerifyPkceS256Failure() =
        runTest {
            val pkceService = createPkceService()

            // Create PKCE data
            val createResult =
                pkceService.createPkce(
                    CreatePkceArgs(codeVerifier = null, allowedMethods = listOf(PkceMethod.S256)),
                )
            assertTrue(createResult.isOk)
            val pkceData = createResult.value

            // Verify with wrong verifier
            val verifyResult =
                pkceService.verifyPkce(
                    VerifyPkceArgs(codeVerifier = "wrong_verifier_that_should_not_match_challenge", codeChallenge = pkceData.codeChallenge, method = pkceData.codeChallengeMethod),
                )

            assertTrue(verifyResult.isErr, "PKCE verification should fail with wrong verifier")
        }

    @Test
    fun testVerifyPkcePlainSuccess() =
        runTest {
            val pkceService = createPkceService()

            val createResult =
                pkceService.createPkce(
                    CreatePkceArgs(codeVerifier = null, allowedMethods = listOf(PkceMethod.PLAIN)),
                )
            assertTrue(createResult.isOk)
            val pkceData = createResult.value

            val verifyResult =
                pkceService.verifyPkce(
                    VerifyPkceArgs(codeVerifier = pkceData.codeVerifier, codeChallenge = pkceData.codeChallenge, method = pkceData.codeChallengeMethod),
                )

            assertTrue(verifyResult.isOk, "PLAIN method verification should succeed")
        }

    @Test
    fun testCreatePkceFailsWithNoAllowedMethods() =
        runTest {
            val pkceService = createPkceService()

            val result =
                pkceService.createPkce(
                    CreatePkceArgs(codeVerifier = null, allowedMethods = emptyList()),
                )

            assertTrue(result.isErr, "Should fail with no allowed methods")
        }

    @Test
    fun testCreatePkcePreferS256OverPlain() =
        runTest {
            val pkceService = createPkceService()

            // When both methods are allowed, S256 should be preferred
            val result =
                pkceService.createPkce(
                    CreatePkceArgs(codeVerifier = null, allowedMethods = listOf(PkceMethod.PLAIN, PkceMethod.S256)),
                )

            assertTrue(result.isOk)
            assertEquals(PkceMethod.S256, result.value.codeChallengeMethod, "Should prefer S256 over PLAIN")
        }

    @Test
    fun testVerifyPkceWithShortVerifierFails() =
        runTest {
            val pkceService = createPkceService()

            // Verifier must be at least 43 characters
            val verifyResult =
                pkceService.verifyPkce(
                    VerifyPkceArgs(
                        codeVerifier = "short", // Too short
                        codeChallenge = "any_challenge",
                        method = PkceMethod.S256,
                    ),
                )

            assertTrue(verifyResult.isErr, "Should fail with verifier too short")
        }

    @Test
    fun testVerifyPkceWithLongVerifierFails() =
        runTest {
            val pkceService = createPkceService()

            // Verifier must be at most 128 characters
            val tooLongVerifier = "a".repeat(129)
            val verifyResult =
                pkceService.verifyPkce(
                    VerifyPkceArgs(
                        codeVerifier = tooLongVerifier,
                        codeChallenge = "any_challenge",
                        method = PkceMethod.S256,
                    ),
                )

            assertTrue(verifyResult.isErr, "Should fail with verifier too long")
        }

    @Test
    fun testCommandAccessViaService() =
        runTest {
            val pkceService = createPkceService()

            // Test that commands are accessible via service.commands
            assertNotNull(pkceService.commands.createPkce, "CreatePkceCommand should be accessible")
            assertNotNull(pkceService.commands.verifyPkce, "VerifyPkceCommand should be accessible")

            // Test using command directly
            val commandResult =
                pkceService.commands.createPkce.execute(
                    CreatePkceArgs(codeVerifier = null, allowedMethods = listOf(PkceMethod.S256)),
                )

            assertTrue(commandResult.isOk)
            assertNotNull(commandResult.value)
        }
}
