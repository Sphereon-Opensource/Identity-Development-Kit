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

package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vci.issuer.command.OfferRateLimit
import com.sphereon.openid.oid4vci.issuer.command.OfferUriLifecycle
import com.sphereon.openid.oid4vci.issuer.impl.lifecycle.OfferLifecycleInitializer
import com.sphereon.openid.oid4vci.issuer.impl.testAuthorizationSnapshot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class CreateCredentialOfferRateLimitInvariantTest {
    private val instanceId = "00000000-0000-4000-8000-000000000012"
    private val issuerId = "https://issuer.example.com/oid4vci"

    private fun makeCommand() =
        CreateCredentialOfferCommandImpl(
            execution = TestSessionExecution(),
            asBridge = NoOpAsBridge(),
            offerStore = NoOpOfferStore(),
            sessionStore = RecordingSessionStore(),
            lifecycleInitializer = OfferLifecycleInitializer(),
        )

    @Test
    fun reusableOfferWithoutRateLimitReturnsError() =
        runTest {
            val cmd = makeCommand()
            val args =
                CreateCredentialOfferArgs(
                    instanceId = instanceId,
                    issuerId = issuerId,
                    credentialConfigurationIds = listOf("PID"),
                    uriLifecycle = OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH,
                    rateLimit = null,
                    authorizationPolicySnapshot = testAuthorizationSnapshot(instanceId),
                )
            val result = cmd.execute(args)
            assertTrue(result.isErr, "expected Err for reusable offer without rate_limit")
        }

    @Test
    fun singleUseOfferWithoutRateLimitSucceeds() =
        runTest {
            val cmd = makeCommand()
            val args =
                CreateCredentialOfferArgs(
                    instanceId = instanceId,
                    issuerId = issuerId,
                    credentialConfigurationIds = listOf("PID"),
                    uriLifecycle = OfferUriLifecycle.SINGLE_USE,
                    rateLimit = null,
                    authorizationPolicySnapshot = testAuthorizationSnapshot(instanceId),
                )
            val result = cmd.execute(args)
            assertTrue(result.isOk, "expected Ok for single-use offer without rate_limit; error=${if (result.isErr) result.error else null}")
        }

    @Test
    fun reusableOfferWithRateLimitSucceeds() =
        runTest {
            val cmd = makeCommand()
            val args =
                CreateCredentialOfferArgs(
                    instanceId = instanceId,
                    issuerId = issuerId,
                    credentialConfigurationIds = listOf("PID"),
                    uriLifecycle = OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH,
                    rateLimit = OfferRateLimit(maxPerWindow = 10, windowSeconds = 60),
                    authorizationPolicySnapshot = testAuthorizationSnapshot(instanceId),
                )
            val result = cmd.execute(args)
            assertTrue(result.isOk, "expected Ok for reusable offer with rate_limit; error=${if (result.isErr) result.error else null}")
        }
}
