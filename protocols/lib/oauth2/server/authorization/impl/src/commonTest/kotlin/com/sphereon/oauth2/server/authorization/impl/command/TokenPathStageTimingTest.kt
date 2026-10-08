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

package com.sphereon.oauth2.server.authorization.impl.command

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A token request that takes seconds has to say which of its stages took them. These pin the
 * shape of that report: one line, every stage that ran, in the order it ran, including the stage
 * that failed.
 */
class TokenPathStageTimingTest {
    @Test
    fun everyStageThatRanIsReportedOnOneLineInExecutionOrder() = runTest {
        val timings = TokenPathStageTimings(operation = "token-request")

        timings.record(TokenPathStage.PARSE_REQUEST) { "parsed" }
        timings.record(TokenPathStage.CLIENT_AUTHENTICATION) { "verified" }
        timings.record(TokenPathStage.GRANT_DISPATCH) { "dispatched" }

        val line = requireNotNull(timings.format(outcome = "success"))
        assertTrue(line.startsWith("VDX_OAUTH2_TOKEN_PATH_TIMING operation=token-request outcome=success"))
        assertEquals(
            listOf(
                TokenPathStage.PARSE_REQUEST,
                TokenPathStage.CLIENT_AUTHENTICATION,
                TokenPathStage.GRANT_DISPATCH,
            ),
            line.reportedStages(),
        )
    }

    @Test
    fun aStageThatThrowsIsStillAttributed() = runTest {
        val timings = TokenPathStageTimings(operation = "access-token-mint")

        timings.record(TokenPathStage.SIGNING_IDENTIFIER_RESOLUTION) { "resolved" }
        assertFailsWith<IllegalStateException> {
            timings.record(TokenPathStage.JWS_SIGNING) { error("signing key unavailable") }
        }

        val line = requireNotNull(timings.format(outcome = "failed"))
        assertEquals(
            listOf(TokenPathStage.SIGNING_IDENTIFIER_RESOLUTION, TokenPathStage.JWS_SIGNING),
            line.reportedStages(),
        )
        assertTrue(line.contains("outcome=failed"))
    }

    @Test
    fun aRequestThatMeasuredNothingEmitsNothing() {
        assertNull(TokenPathStageTimings(operation = "token-request").format(outcome = "rejected"))
    }

    @Test
    fun stageNamesAreDistinctSoOneLineCannotHideAStage() {
        val names =
            listOf(
                TokenPathStage.PARSE_REQUEST,
                TokenPathStage.DPOP_PROOF_VERIFICATION,
                TokenPathStage.CLIENT_AUTHENTICATION_COMMAND_RESOLUTION,
                TokenPathStage.CLIENT_AUTHENTICATION,
                TokenPathStage.GRANT_DISPATCH,
                TokenPathStage.CLIENT_REGISTRY_VIEW,
                TokenPathStage.CLIENT_LOOKUP,
                TokenPathStage.CREDENTIAL_VERIFICATION,
                TokenPathStage.SIGNING_IDENTIFIER_RESOLUTION,
                TokenPathStage.AS_CONFIG_RESOLUTION,
                TokenPathStage.SIGNING_TENANT_DERIVATION,
                TokenPathStage.ACTIVE_SIGNING_KEY_RESOLUTION,
                TokenPathStage.JWS_SIGNING,
                TokenPathStage.TOKEN_STORAGE,
            )

        assertEquals(names.size, names.toSet().size)
    }

    private fun String.reportedStages(): List<String> =
        split(" ")
            .filter { it.contains('=') }
            .map { it.substringBefore('=') }
            .filterNot { it in RESERVED_FIELDS }

    private companion object {
        val RESERVED_FIELDS = setOf("VDX_OAUTH2_TOKEN_PATH_TIMING", "operation", "outcome", "measuredMs")
    }
}
