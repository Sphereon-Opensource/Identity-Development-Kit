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

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.binary.TypeToken
import com.sphereon.core.api.binary.typeToken
import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.issuance.pipeline.PipelineConfiguration
import com.sphereon.credential.issuance.pipeline.command.InitPipelineSessionArgs
import com.sphereon.credential.issuance.pipeline.command.InitPipelineSessionCommand
import com.sphereon.credential.issuance.pipeline.command.InitPipelineSessionResult
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vci.issuer.impl.pipeline.OfferPipelineInitializer
import com.sphereon.openid.oid4vci.issuer.pipeline.PipelineConfigurationResolver
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CreateCredentialOfferCommandImplPipelineTest {
    private val issuerId = "https://issuer.example.com/oid4vci"

    // ─────────────────────────────────────────────────────────────────────────
    // Fakes
    // ─────────────────────────────────────────────────────────────────────────

    private class FixedPipelineConfigurationResolver(
        private val config: PipelineConfiguration?,
    ) : PipelineConfigurationResolver {
        override suspend fun resolve(
            issuerId: String,
            credentialConfigurationIds: List<String>,
        ): IdkResult<PipelineConfiguration?, IdkError> = Ok(config)
    }

    private class RecordingInitPipelineSessionCommand(
        private val resultSessionId: String = "ps-1",
        private val resultCorrelationId: String = "corr-1",
    ) : InitPipelineSessionCommand {
        override val commandId: String get() = InitPipelineSessionCommand.COMMAND_ID
        override val isEnabled: Boolean = true
        override val inputTypeToken: TypeToken<InitPipelineSessionArgs> = typeToken()
        override val outputTypeToken: TypeToken<InitPipelineSessionResult> = typeToken()

        var invocations: Int = 0
        var lastArgs: InitPipelineSessionArgs? = null

        override suspend fun supports(args: Any): Boolean = args is InitPipelineSessionArgs

        override suspend fun execute(args: InitPipelineSessionArgs): IdkResult<InitPipelineSessionResult, IdkError> {
            invocations += 1
            lastArgs = args
            return Ok(InitPipelineSessionResult(sessionId = resultSessionId, correlationId = resultCorrelationId))
        }
    }

    private fun sampleArgs() =
        CreateCredentialOfferArgs(
            issuerId = issuerId,
            credentialConfigurationIds = listOf("PID"),
            preAuthorizedCodeGrant = false,
            authorizationCodeGrant = false,
        )

    private fun minimalPipelineConfig() = PipelineConfiguration(pipelineId = "test-pipeline")

    // ─────────────────────────────────────────────────────────────────────────
    // Tests
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun offerCreationWithPipelineResolvedInitsSessionAndLinksIt() =
        runTest {
            val sessionStore = RecordingSessionStore()
            val initCmd = RecordingInitPipelineSessionCommand(resultSessionId = "ps-1", resultCorrelationId = "corr-1")

            val cmd =
                CreateCredentialOfferCommandImpl(
                    execution = TestSessionExecution(),
                    asBridge = NoOpAsBridge(),
                    offerStore = NoOpOfferStore(),
                    sessionStore = sessionStore,
                    pipelineInitializer =
                        OfferPipelineInitializer(
                            pipelineConfigurationResolver = FixedPipelineConfigurationResolver(minimalPipelineConfig()),
                            initPipelineSessionCommand = initCmd,
                        ),
                )

            val out = cmd.execute(sampleArgs())

            assertTrue(out.isOk)
            assertEquals(1, initCmd.invocations, "initPipelineSessionCommand must be called exactly once")
            val stored = sessionStore.created.first()
            assertEquals("corr-1", stored.pipelineCorrelationId, "stored session must carry pipelineCorrelationId from pipeline init")
        }

    @Test
    fun preSeededAttributesArePassedToPipelineInitialAttributes() =
        runTest {
            val initCmd = RecordingInitPipelineSessionCommand()
            val initializer =
                OfferPipelineInitializer(
                    pipelineConfigurationResolver = FixedPipelineConfigurationResolver(minimalPipelineConfig()),
                    initPipelineSessionCommand = initCmd,
                )

            initializer.initializePipeline(
                sampleArgs().copy(
                    preSeededAttributes =
                        mapOf(
                            "given_name" to JsonPrimitive("Ada"),
                            "family_name" to JsonPrimitive("Lovelace"),
                        ),
                ),
            )

            val initialAttributes = requireNotNull(initCmd.lastArgs).initialAttributes
            assertEquals(listOf("given_name", "family_name"), initialAttributes.map { it.path.value })
            assertEquals(JsonPrimitive("Ada"), initialAttributes[0].jsonValue)
            assertEquals(JsonPrimitive("Lovelace"), initialAttributes[1].jsonValue)
        }

    @Test
    fun offerCreationWithNoPipelineLeavesLinkNull() =
        runTest {
            val sessionStore = RecordingSessionStore()
            val initCmd = RecordingInitPipelineSessionCommand()

            val cmd =
                CreateCredentialOfferCommandImpl(
                    execution = TestSessionExecution(),
                    asBridge = NoOpAsBridge(),
                    offerStore = NoOpOfferStore(),
                    sessionStore = sessionStore,
                    pipelineInitializer =
                        OfferPipelineInitializer(
                            pipelineConfigurationResolver = FixedPipelineConfigurationResolver(null),
                            initPipelineSessionCommand = initCmd,
                        ),
                )

            val out = cmd.execute(sampleArgs())

            assertTrue(out.isOk)
            assertEquals(0, initCmd.invocations, "initPipelineSessionCommand must not be called when resolver returns null")
            val stored = sessionStore.created.first()
            assertNull(stored.pipelineCorrelationId, "stored session must have null pipelineCorrelationId when no pipeline configured")
        }

    @Test
    fun offerCreationWithNoResolverWiredLeavesLinkNull() =
        runTest {
            val sessionStore = RecordingSessionStore()

            val cmd =
                CreateCredentialOfferCommandImpl(
                    execution = TestSessionExecution(),
                    asBridge = NoOpAsBridge(),
                    offerStore = NoOpOfferStore(),
                    sessionStore = sessionStore,
                    pipelineInitializer = OfferPipelineInitializer(),
                )

            val out = cmd.execute(sampleArgs())

            assertTrue(out.isOk)
            val stored = sessionStore.created.first()
            assertNull(stored.pipelineCorrelationId, "pipelineCorrelationId must be null when no resolver is wired (pure-IDK default deployment)")
        }
}
