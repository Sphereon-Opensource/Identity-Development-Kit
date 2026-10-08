/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.mdoc.engagement

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.mdoc.transfer.TransferInstance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalCoroutinesApi::class)
class MdocEngagementManagerDefaultMethodTest {
    private val manager = UnusedMdocEngagementManager()

    @Test
    fun toApp_with_malformed_reader_engagement_uri_returns_error_result() =
        runTest {
            val result = manager.toApp("mdoc://%%%")

            assertTrue(result.isErr)
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("ReaderEngagement"),
            )
        }

    @Test
    fun toApp_with_unsupported_scheme_returns_error_result() =
        runTest {
            val result = manager.toApp("https://example.com/not-mdoc")

            assertTrue(result.isErr)
            assertEquals("ILLEGAL_ARGUMENT_ERROR", result.error.code)
            assertTrue(
                result.error.message.defaultMessage
                    .contains("Unsupported URI scheme"),
            )
        }
}

private class UnusedMdocEngagementManager : MdocEngagementManager {
    override val eventHub: MdocEventHub
        get() = error("Unused in this test")
    override val sharedParameters: SharedParameters
        get() = error("Unused in this test")
    override val engagementsByType: StateFlow<Map<EngagementType, EngagementInstance>>
        get() = error("Unused in this test")
    override val nfcEngagement: StateFlow<EngagementInstance?>
        get() = error("Unused in this test")
    override val qrEngagement: StateFlow<EngagementInstance?>
        get() = error("Unused in this test")
    override val toAppEngagement: StateFlow<EngagementInstance?>
        get() = error("Unused in this test")
    override val activeEngagement: StateFlow<EngagementInstance?>
        get() = error("Unused in this test")

    @OptIn(ExperimentalUuidApi::class)
    override val transferInstances: StateFlow<Map<Uuid, TransferInstance>>
        get() = error("Unused in this test")

    override suspend fun enableAutoRestart(backgroundNfcConfig: (EngagementConfiguration.() -> Unit)?): IdkResult<Unit, IdkError> = error("Unused in this test")

    override suspend fun closeNfcEngagement(): IdkResult<Unit, IdkError> = error("Unused in this test")

    override suspend fun closeQrEngagement(): IdkResult<Unit, IdkError> = error("Unused in this test")

    override suspend fun closeToAppEngagement(): IdkResult<Unit, IdkError> = error("Unused in this test")

    override suspend fun closeEngagementByInstance(engagement: EngagementInstance): IdkResult<Unit, IdkError> = error("Unused in this test")

    @OptIn(ExperimentalUuidApi::class)
    override suspend fun closeEngagementById(id: Uuid): IdkResult<Unit, IdkError> = error("Unused in this test")

    override suspend fun closeAll(): IdkResult<Unit, IdkError> = error("Unused in this test")

    override fun close() = Unit

    override suspend fun createFromBuilder(builder: EngagementData.HolderBuilder): IdkResult<EngagementInstance, IdkError> = error("createFromBuilder should not be called for invalid toApp input")

    override suspend fun createFromEphemeralKey(
        ephemeralKey: ResolvedKeyInfoType<*>,
        configBuilder: EngagementConfiguration.() -> Unit,
    ): IdkResult<EngagementInstance, IdkError> = error("createFromEphemeralKey should not be called for invalid toApp input")

    override suspend fun createEngagement(configBuilder: EngagementConfiguration.() -> Unit): IdkResult<EngagementInstance, IdkError> =
        error("createEngagement should not be called for invalid toApp input")
}
