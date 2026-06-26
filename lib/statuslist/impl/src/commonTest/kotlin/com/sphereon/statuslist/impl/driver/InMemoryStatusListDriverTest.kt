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

package com.sphereon.statuslist.impl.driver

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.context.ContextConfig
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.log.SessionLogService
import com.sphereon.di.context.NoOpSessionContext
import com.sphereon.di.session.SessionContext
import com.sphereon.di.session.SessionContextManager
import com.sphereon.statuslist.AllocateEntryArgs
import com.sphereon.statuslist.CreateStatusListArgs
import com.sphereon.statuslist.EntryRef
import com.sphereon.statuslist.StatusListRef
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusListToken
import com.sphereon.statuslist.StatusProofFormat
import com.sphereon.statuslist.StatusPurpose
import com.sphereon.statuslist.StatusValues
import com.sphereon.statuslist.spi.SignStatusListTokenArgs
import com.sphereon.statuslist.spi.StatusListSigner
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Test signer: echoes the encoded list so tests can assert the driver encoded the live bit state. */
private class EchoStatusListSigner : StatusListSigner {
    var signCount: Int = 0

    override suspend fun signStatusListToken(args: SignStatusListTokenArgs): IdkResult<StatusListToken, IdkError> =
        Ok(
            StatusListToken(
                token = "signed:${++signCount}:${args.encodedList}",
                contentType = args.proofFormat.contentType,
                ttlSeconds = args.ttlSeconds,
            ),
        )
}

/** Minimal session execution fixture: only the tenant id is consulted by the driver. */
private class TestSessionExecution(
    override val tenantId: String = "test-tenant",
    override val sessionContext: SessionContext = NoOpSessionContext,
) : SessionExecution {
    override val sessionContextManager: SessionContextManager
        get() = throw NotImplementedError("Not needed for test")
    override val log: SessionLogService
        get() = throw NotImplementedError("Not needed for test")
    override val conf: ContextConfig
        get() = throw NotImplementedError("Not needed for test")
}

class InMemoryStatusListDriverTest {
    private fun driver() = InMemoryStatusListDriver(InMemoryStatusListStore(), EchoStatusListSigner(), TestSessionExecution())

    private fun driverWithSigner(signer: EchoStatusListSigner) = InMemoryStatusListDriver(InMemoryStatusListStore(), signer, TestSessionExecution())

    private fun createArgs(
        correlationId: String = "sl-1",
        length: Int = 8,
        bitsPerStatus: Int = 1,
    ) = CreateStatusListArgs(
        correlationId = correlationId,
        spec = StatusListSpec.TOKEN_STATUS_LIST,
        purposes = listOf(StatusPurpose.REVOCATION),
        proofFormat = StatusProofFormat.JWT,
        issuer = "did:example:issuer",
        statusListUri = "https://issuer.example/statuslists/$correlationId",
        length = length,
        bitsPerStatus = bitsPerStatus,
        signingKeyAlias = "key-1",
    )

    @Test
    fun createRejectsDuplicateCorrelationId() =
        runTest {
            val d = driver()
            assertTrue(d.createStatusList(createArgs()).isOk)
            val dup = d.createStatusList(createArgs())
            assertTrue(dup.isErr)
            assertEquals("STATUSLIST_DUPLICATE_CORRELATION_ID", (dup as com.sphereon.core.api.Err).error.code)
        }

    @Test
    fun randomAllocationProducesDistinctIndices() =
        runTest {
            val d = driver()
            d.createStatusList(createArgs(length = 64))
            val indices =
                (1..32).map {
                    val r = d.allocateEntry(AllocateEntryArgs(statusList = StatusListRef(correlationId = "sl-1")))
                    assertTrue(r.isOk)
                    (r as Ok).value.statusListIndex
                }
            assertEquals(32, indices.toSet().size, "all allocated indices must be unique")
            assertTrue(indices.all { it in 0 until 64 })
        }

    @Test
    fun explicitIndexHonouredAndConflictsRejected() =
        runTest {
            val d = driver()
            d.createStatusList(createArgs())
            val first = d.allocateEntry(AllocateEntryArgs(StatusListRef(correlationId = "sl-1"), explicitIndex = 3))
            assertEquals(3, (first as Ok).value.statusListIndex)

            val inUse = d.allocateEntry(AllocateEntryArgs(StatusListRef(correlationId = "sl-1"), explicitIndex = 3))
            assertTrue(inUse.isErr)
            assertEquals("STATUSLIST_INDEX_IN_USE", (inUse as com.sphereon.core.api.Err).error.code)

            val oob = d.allocateEntry(AllocateEntryArgs(StatusListRef(correlationId = "sl-1"), explicitIndex = 999))
            assertEquals("STATUSLIST_INDEX_OUT_OF_RANGE", (oob as com.sphereon.core.api.Err).error.code)
        }

    @Test
    fun exhaustionReportedNotSilentlyTruncated() =
        runTest {
            val d = driver()
            d.createStatusList(createArgs(length = 4))
            repeat(4) { assertTrue(d.allocateEntry(AllocateEntryArgs(StatusListRef(correlationId = "sl-1"))).isOk) }
            val overflow = d.allocateEntry(AllocateEntryArgs(StatusListRef(correlationId = "sl-1")))
            assertTrue(overflow.isErr)
            assertEquals("STATUSLIST_EXHAUSTED", (overflow as com.sphereon.core.api.Err).error.code)
        }

    @Test
    fun revokeByBusinessKeyAndCredentialId() =
        runTest {
            val d = driver()
            d.createStatusList(createArgs())
            val entry =
                (
                    d.allocateEntry(
                        AllocateEntryArgs(
                            statusList = StatusListRef(correlationId = "sl-1"),
                            entryCorrelationId = "order-1234",
                            credentialId = "cred-abc",
                        ),
                    ) as Ok
                ).value
            assertEquals(StatusValues.VALID, entry.value)

            // Revoke by business key, without knowing the index.
            val revoked =
                d.updateEntryStatus(
                    com.sphereon.statuslist.UpdateEntryStatusArgs(
                        entry = EntryRef(correlationId = "sl-1", entryCorrelationId = "order-1234"),
                        value = StatusValues.INVALID,
                    ),
                )
            assertEquals(StatusValues.INVALID, (revoked as Ok).value.value)

            // Lookup by credentialId reflects the revocation.
            val byCred =
                d.getEntry(EntryRef(correlationId = "sl-1", credentialId = "cred-abc")) as Ok
            assertNotNull(byCred.value)
            assertEquals(StatusValues.INVALID, byCred.value!!.value)
            assertEquals(entry.statusListIndex, byCred.value!!.statusListIndex)
        }

    @Test
    fun tokenReflectsLiveBitStateViaSigner() =
        runTest {
            val d = driver()
            d.createStatusList(createArgs(length = 128))
            d.allocateEntry(AllocateEntryArgs(StatusListRef(correlationId = "sl-1"), explicitIndex = 7, credentialId = "c7"))
            d.updateEntryStatus(
                com.sphereon.statuslist.UpdateEntryStatusArgs(EntryRef(correlationId = "sl-1", credentialId = "c7"), StatusValues.INVALID),
            )
            val token = (d.getStatusListToken(StatusListRef(correlationId = "sl-1")) as Ok).value
            assertNotNull(token)
            assertTrue(token!!.token.startsWith("signed:"), "echo signer carries the encoded list")
        }

    @Test
    fun tokenReadReturnsStoredProjectionWithoutResigning() =
        runTest {
            val signer = EchoStatusListSigner()
            val d = driverWithSigner(signer)
            d.createStatusList(createArgs())
            assertEquals(1, signer.signCount)

            val first = (d.getStatusListToken(StatusListRef(correlationId = "sl-1")) as Ok).value
            val second = (d.getStatusListToken(StatusListRef(statusListUri = "https://issuer.example/statuslists/sl-1")) as Ok).value

            assertEquals(1, signer.signCount, "token reads must not invoke KMS/signing")
            assertEquals(first, second)
        }

    @Test
    fun getUnknownListReturnsNull() =
        runTest {
            val d = driver()
            assertNull((d.getStatusList(StatusListRef(correlationId = "nope")) as Ok).value)
        }
}
