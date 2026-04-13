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
import com.sphereon.oauth2.client.command.CreatePkceArgs
import com.sphereon.oauth2.client.command.VerifyPkceArgs
import com.sphereon.oauth2.client.impl.pkce.CreatePkceCommandImpl
import com.sphereon.oauth2.client.impl.pkce.VerifyPkceCommandImpl
import com.sphereon.oauth2.client.testutil.createOAuth2ClientTestAppGraph
import com.sphereon.oauth2.common.model.PkceMethod
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for PKCE command implementations
 */
class PkceCommandTest {
    val app = createOAuth2ClientTestAppGraph(this)
    val context = app.userContextManager.getAnonymous()
    val session = context.sessionContextManager.createOrGetFromId("pkce-command-test")
    val execution = session.asCoreApiServiceGraph().serviceExecution

    @Test
    fun testCreatePkceCommandDirectly() =
        runTest {
            val command = CreatePkceCommandImpl(execution)

            val result =
                command.execute(
                    CreatePkceArgs(codeVerifier = null, allowedMethods = listOf(PkceMethod.S256)),
                )

            println("Result isOk: ${result.isOk}")
            if (result.isErr) {
                println("Error: ${result.error}")
            } else {
                val pkce = result.value
                println("Generated PKCE:")
                println("  Verifier length: ${pkce.codeVerifier.length}")
                println("  Verifier: ${pkce.codeVerifier}")
                println("  Challenge length: ${pkce.codeChallenge.length}")
                println("  Challenge: ${pkce.codeChallenge}")
                println("  Method: ${pkce.codeChallengeMethod}")
            }

            assertTrue(result.isOk, "Should succeed")

            val pkce = result.value
            assertEquals(PkceMethod.S256, pkce.codeChallengeMethod)
            assertTrue(pkce.codeVerifier.length >= 43)
            assertTrue(pkce.codeVerifier.length <= 128)
            assertEquals(43, pkce.codeChallenge.length, "S256 challenge should be exactly 43 characters")
        }

    @Test
    fun testCreatePkceWithKnownVerifier() =
        runTest {
            val command = CreatePkceCommandImpl(execution)

            // Use a known verifier to test challenge calculation
            val knownVerifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

            val result =
                command.execute(
                    CreatePkceArgs(codeVerifier = knownVerifier, allowedMethods = listOf(PkceMethod.S256)),
                )

            println("Result with known verifier:")
            if (result.isErr) {
                println("Error: ${result.error}")
            } else {
                val pkce = result.value
                println("  Verifier: ${pkce.codeVerifier}")
                println("  Challenge: ${pkce.codeChallenge}")
                println("  Challenge length: ${pkce.codeChallenge.length}")
            }

            assertTrue(result.isOk)
            val pkce = result.value
            assertEquals(knownVerifier, pkce.codeVerifier)
            assertEquals(43, pkce.codeChallenge.length)
        }

    @Test
    fun testVerifyPkceCommand() =
        runTest {
            val createCommand = CreatePkceCommandImpl(execution)
            val verifyCommand = VerifyPkceCommandImpl(execution)

            // First create
            val createResult =
                createCommand.execute(
                    CreatePkceArgs(codeVerifier = null, allowedMethods = listOf(PkceMethod.S256)),
                )

            assertTrue(createResult.isOk)
            val pkce = createResult.value

            println("Testing verification:")
            println("  Verifier: ${pkce.codeVerifier}")
            println("  Challenge: ${pkce.codeChallenge}")

            // Then verify
            val verifyResult =
                verifyCommand.execute(
                    VerifyPkceArgs(codeVerifier = pkce.codeVerifier, codeChallenge = pkce.codeChallenge, method = pkce.codeChallengeMethod),
                )

            if (verifyResult.isErr) {
                println("Verification error: ${verifyResult.error}")
            }

            assertTrue(verifyResult.isOk, "Verification should succeed")
        }
}
