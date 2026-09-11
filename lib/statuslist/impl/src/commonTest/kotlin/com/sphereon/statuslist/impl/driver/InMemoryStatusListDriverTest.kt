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

import com.sphereon.core.api.Err
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
import com.sphereon.statuslist.MdocStatusListPayload
import com.sphereon.statuslist.MdocStatusListProfile
import com.sphereon.statuslist.impl.enrich.CredentialStatusEnricherImpl
import com.sphereon.statuslist.spi.SignStatusListTokenArgs
import com.sphereon.statuslist.spi.StatusListSigner
import com.sphereon.statuslist.spi.StatusListSigningKeyNameResolver
import com.sphereon.statuslist.spi.StatusEnrichmentContext
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

/** Test signer: echoes the encoded list so tests can assert the driver encoded the live bit state. */
private class EchoStatusListSigner : StatusListSigner {
    var signCount: Int = 0
    var lastArgs: SignStatusListTokenArgs? = null

    override suspend fun signStatusListToken(args: SignStatusListTokenArgs): IdkResult<StatusListToken, IdkError> {
        lastArgs = args
        return Ok(
            StatusListToken(
                token = "signed:${++signCount}:${args.encodedList}",
                contentType = args.proofFormat.contentType,
                ttlSeconds = args.ttlSeconds,
            ),
        )
    }
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

    private fun driverFor(
        tenantId: String,
        store: InMemoryStatusListStore,
    ) = InMemoryStatusListDriver(store, EchoStatusListSigner(), TestSessionExecution(tenantId = tenantId))

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
    fun createRejectsAHostingUriAlreadyOwnedByAnotherList() =
        runTest {
            val d = driver()
            assertTrue(d.createStatusList(createArgs(correlationId = "sl-1")).isOk)
            val duplicateUri =
                d.createStatusList(
                    createArgs(correlationId = "sl-2").copy(
                        statusListUri = "https://issuer.example/statuslists/sl-1",
                    ),
                )
            assertTrue(duplicateUri.isErr)
            assertEquals("STATUSLIST_DUPLICATE_URI", (duplicateUri as com.sphereon.core.api.Err).error.code)
        }

    @Test
    fun failedInitialSigningDoesNotLeaveAnUnpublishedList() =
        runTest {
            val signer = ToggleStatusListSigner()
            signer.fail = true
            val d = InMemoryStatusListDriver(InMemoryStatusListStore(), signer, TestSessionExecution())

            assertTrue(d.createStatusList(createArgs()).isErr)
            assertNull((d.getStatusList(StatusListRef(correlationId = "sl-1")) as Ok).value)
            assertNull((d.getStatusList(StatusListRef(statusListUri = "https://issuer.example/statuslists/sl-1")) as Ok).value)
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
    fun releasingAnUnboundReservationClearsItsBitAndMakesTheIndexReusable() =
        runTest {
            val d = driver()
            d.createStatusList(createArgs(length = 8))
            val allocated =
                d.allocateEntry(
                    AllocateEntryArgs(
                        statusList = StatusListRef(correlationId = "sl-1"),
                        explicitIndex = 3,
                    ),
                ) as Ok

            assertTrue(
                d.releaseEntry(
                    EntryRef(statusListId = allocated.value.statusListId, statusListIndex = allocated.value.statusListIndex),
                ).isOk,
            )
            assertNull(
                (d.getEntry(EntryRef(statusListId = allocated.value.statusListId, statusListIndex = 3)) as Ok).value,
            )

            val reused =
                d.allocateEntry(
                    AllocateEntryArgs(
                        statusList = StatusListRef(correlationId = "sl-1"),
                        explicitIndex = 3,
                    ),
                ) as Ok
            assertEquals(3, reused.value.statusListIndex)
        }

    @Test
    fun releasingAnUnknownReservationIsIdempotentAndDoesNotFail() =
        runTest {
            val d = driver()
            d.createStatusList(createArgs())
            val result = d.releaseEntry(EntryRef(correlationId = "sl-1", statusListIndex = 2))
            assertTrue(result.isOk)
            assertEquals(false, (result as Ok).value)
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
    fun failedAllocationRollsBackTheReservationAndKeepsTheLastSignedToken() =
        runTest {
            val signer = ToggleStatusListSigner()
            val d = InMemoryStatusListDriver(InMemoryStatusListStore(), signer, TestSessionExecution())
            d.createStatusList(createArgs())
            val before = (d.getStatusListToken(StatusListRef(correlationId = "sl-1")) as Ok).value
            signer.fail = true

            val failed = d.allocateEntry(AllocateEntryArgs(StatusListRef(correlationId = "sl-1"), explicitIndex = 3))
            assertTrue(failed.isErr)
            assertNull((d.getEntry(EntryRef(correlationId = "sl-1", statusListIndex = 3)) as Ok).value)
            assertEquals(before, (d.getStatusListToken(StatusListRef(correlationId = "sl-1")) as Ok).value)

            signer.fail = false
            assertTrue(d.allocateEntry(AllocateEntryArgs(StatusListRef(correlationId = "sl-1"), explicitIndex = 3)).isOk)
        }

    @Test
    fun failedStatusUpdateRestoresThePreviousEntryAndSignedToken() =
        runTest {
            val signer = ToggleStatusListSigner()
            val d = InMemoryStatusListDriver(InMemoryStatusListStore(), signer, TestSessionExecution())
            d.createStatusList(createArgs())
            val allocated =
                d.allocateEntry(
                    AllocateEntryArgs(StatusListRef(correlationId = "sl-1"), explicitIndex = 3, credentialId = "cred-3"),
                ) as Ok
            val before = (d.getStatusListToken(StatusListRef(correlationId = "sl-1")) as Ok).value
            signer.fail = true

            val failed =
                d.updateEntryStatus(
                    com.sphereon.statuslist.UpdateEntryStatusArgs(
                        entry = EntryRef(correlationId = "sl-1", credentialId = "cred-3"),
                        value = StatusValues.INVALID,
                    ),
                )
            assertTrue(failed.isErr)
            val restored = (d.getEntry(EntryRef(statusListId = allocated.value.statusListId, statusListIndex = 3)) as Ok).value
            assertNotNull(restored)
            assertEquals(StatusValues.VALID, restored!!.value)
            assertEquals(before, (d.getStatusListToken(StatusListRef(correlationId = "sl-1")) as Ok).value)
        }

    @Test
    fun failedDefinitionRefreshRestoresThePreviousDefinitionAndSignedToken() =
        runTest {
            val signer = ToggleStatusListSigner()
            val d = InMemoryStatusListDriver(InMemoryStatusListStore(), signer, TestSessionExecution())
            d.createStatusList(createArgs())
            val before = (d.getStatusList(StatusListRef(correlationId = "sl-1")) as Ok).value
            val beforeToken = (d.getStatusListToken(StatusListRef(correlationId = "sl-1")) as Ok).value
            signer.fail = true

            val failed = d.refreshStatusListDefinition(createArgs().copy(issuer = "did:example:changed"))
            assertTrue(failed.isErr)
            val restored = (d.getStatusList(StatusListRef(correlationId = "sl-1")) as Ok).value
            assertNotNull(restored)
            assertEquals(before!!.issuer, restored!!.issuer)
            assertEquals(beforeToken?.token, restored.signedToken)
        }

    @Test
    fun entryLookupAndMutationCannotCrossTenantBoundariesByListId() =
        runTest {
            val store = InMemoryStatusListStore()
            val tenantA = driverFor("tenant-a", store)
            val tenantB = driverFor("tenant-b", store)
            assertTrue(tenantA.createStatusList(createArgs()).isOk)
            val allocated =
                tenantA.allocateEntry(
                    AllocateEntryArgs(
                        statusList = StatusListRef(correlationId = "sl-1"),
                        explicitIndex = 2,
                    ),
                ) as Ok

            val foreignRef = EntryRef(statusListId = allocated.value.statusListId, statusListIndex = 2)
            assertNull((tenantB.getEntry(foreignRef) as Ok).value)
            assertTrue(
                tenantB.updateEntryStatus(
                    com.sphereon.statuslist.UpdateEntryStatusArgs(foreignRef, StatusValues.INVALID),
                ).isErr,
            )
            assertEquals(
                StatusValues.VALID,
                ((tenantA.getEntry(foreignRef) as Ok).value ?: error("entry disappeared")).value,
            )
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
    fun hostedUriIsPublicReadOnlyAndCannotSelectAnotherTenantsManagementList() =
        runTest {
            val store = InMemoryStatusListStore()
            val tenantA = driverFor("tenant-a", store)
            val tenantB = driverFor("tenant-b", store)
            val created = (tenantA.createStatusList(createArgs("tenant-a-list")) as Ok).value
            val uri = created.statusListUri

            assertNull((tenantB.getStatusList(StatusListRef(statusListUri = uri)) as Ok).value)
            assertTrue((tenantB.deleteStatusList(StatusListRef(statusListUri = uri)) as Ok).value == false)
            assertTrue(
                tenantB.allocateEntry(
                    AllocateEntryArgs(
                        statusList = StatusListRef(statusListUri = uri),
                        explicitIndex = 0,
                    ),
                ).isErr,
            )
            // The same URI remains usable by the public token projection, but does not grant
            // access to tenant-scoped management state.
            assertNotNull((tenantB.getStatusListToken(StatusListRef(statusListUri = uri)) as Ok).value)
            assertNotNull((tenantA.getStatusList(StatusListRef(id = created.id)) as Ok).value)
        }

    @Test
    fun getUnknownListReturnsNull() =
        runTest {
            val d = driver()
            assertNull((d.getStatusList(StatusListRef(correlationId = "nope")) as Ok).value)
        }

    @Test
    fun signingKeyComesFromTheBoundResolverAndNeverFromTheCorrelationId() =
        runTest {
            val signer = RecordingStatusListSigner()
            val resolver = FixedSigningKeyNameResolver("status-list-signing-instance-7")
            val d = InMemoryStatusListDriver(InMemoryStatusListStore(), signer, TestSessionExecution(), signingKeyNameResolver = { resolver })

            assertTrue(d.createStatusList(createArgs()).isOk)

            assertEquals("status-list-signing-instance-7", signer.lastKeyName)
            assertEquals(listOf("test-tenant" to "sl-1"), resolver.requests)
        }

    @Test
    fun aDefinitionKeyAliasIsIgnoredWhileAResolverIsBound() =
        runTest {
            val signer = RecordingStatusListSigner()
            val resolver = FixedSigningKeyNameResolver("status-list-signing-instance-7")
            val d = InMemoryStatusListDriver(InMemoryStatusListStore(), signer, TestSessionExecution(), signingKeyNameResolver = { resolver })

            assertTrue(d.createStatusList(createArgs().copy(signingKeyAlias = "attacker-chosen-alias")).isOk)

            assertEquals("status-list-signing-instance-7", signer.lastKeyName)
        }

    @Test
    fun everyUnusableBindingReachesTheSignerAsTheSameAbsentKeyName() =
        runTest {
            // Absent, detached, cross-tenant, inactive, and unmapped bindings are all a null answer
            // from the resolver. The driver passes each through untouched, so they are
            // indistinguishable and none of them yields a substituted name.
            val handed =
                listOf("absent", "detached", "cross-tenant", "inactive", "unmapped").map { _ ->
                    val signer = RecordingStatusListSigner()
                    val d =
                        InMemoryStatusListDriver(
                            InMemoryStatusListStore(),
                            signer,
                            TestSessionExecution(),
                            signingKeyNameResolver = { FixedSigningKeyNameResolver(null) },
                        )
                    assertTrue(d.createStatusList(createArgs().copy(signingKeyAlias = "attacker-chosen-alias")).isOk)
                    signer.lastKeyName
                }

            assertEquals(listOf(null, null, null, null, null), handed, "no unusable binding may yield a key name")
        }

    @Test
    fun aBlankResolvedKeyNameIsPassedThroughAsAbsent() =
        runTest {
            val signer = RecordingStatusListSigner()
            val d =
                InMemoryStatusListDriver(
                    InMemoryStatusListStore(),
                    signer,
                    TestSessionExecution(),
                    signingKeyNameResolver = { FixedSigningKeyNameResolver("  ") },
                )

            assertTrue(d.createStatusList(createArgs()).isOk)

            assertNull(signer.lastKeyName)
        }

    @Test
    fun withoutAResolverAListCarryingNoKeyHandsTheSignerNoName() =
        runTest {
            val signer = RecordingStatusListSigner()
            val d = InMemoryStatusListDriver(InMemoryStatusListStore(), signer, TestSessionExecution())

            assertTrue(d.createStatusList(createArgs().copy(signingKeyAlias = null)).isOk)

            assertNull(signer.lastKeyName, "the correlation id must never stand in for a signing key")
        }

    @Test
    fun aSignerWithItsOwnDurableKeyStillProducesATokenWhenNoBindingResolves() =
        runTest {
            // The license-portal shape: the signer mints from its own certificate chain and ignores
            // the key name, so it must keep working on a deployment that binds no KMS handle.
            val signer = RecordingStatusListSigner()
            val d =
                InMemoryStatusListDriver(
                    InMemoryStatusListStore(),
                    signer,
                    TestSessionExecution(),
                    signingKeyNameResolver = { FixedSigningKeyNameResolver(null) },
                )

            val created = d.createStatusList(createArgs().copy(signingKeyAlias = null))

            assertTrue(created.isOk)
            assertTrue((created as Ok).value.signedToken.isNotBlank())
            assertNull(signer.lastKeyName)
        }

    @Test
    fun mdocStatusListProfilePassesOneBitBinaryPayloadToSigner() =
        runTest {
            val signer = EchoStatusListSigner()
            val d = driverWithSigner(signer)
            val args =
                createArgs(length = 16).copy(
                    proofFormat = StatusProofFormat.CWT,
                    mdocProfile = MdocStatusListProfile.STATUS_LIST,
                    validUntil = Instant.parse("2030-01-01T00:00:00Z"),
                    aggregationUri = "https://issuer.example/statuslists/aggregate",
                )

            val created = d.createStatusList(args)
            assertTrue(created.isOk)
            assertEquals(MdocStatusListProfile.STATUS_LIST, (created as Ok).value.mdocProfile)
            val initial = signer.lastArgs?.mdocPayload as MdocStatusListPayload.Token
            assertEquals(1, initial.bits)
            assertEquals(2, initial.list.size)
            assertEquals(args.aggregationUri, initial.aggregationUri)

            d.allocateEntry(AllocateEntryArgs(StatusListRef(correlationId = args.correlationId), explicitIndex = 3))
            d.updateEntryStatus(
                com.sphereon.statuslist.UpdateEntryStatusArgs(
                    entry = EntryRef(correlationId = args.correlationId, statusListIndex = 3),
                    value = StatusValues.INVALID,
                ),
            )
            val updated = signer.lastArgs?.mdocPayload as MdocStatusListPayload.Token
            assertEquals(1, (updated.list[0].toInt() ushr 3) and 1)
        }

    @Test
    fun mdocIdentifierListPublishesOnlyRevokedAllocatedIdentifiers() =
        runTest {
            val signer = EchoStatusListSigner()
            val d = driverWithSigner(signer)
            val args =
                createArgs(correlationId = "mdoc-identifiers", length = 4).copy(
                    proofFormat = StatusProofFormat.CWT,
                    mdocProfile = MdocStatusListProfile.IDENTIFIER_LIST,
                    validUntil = Instant.parse("2030-01-01T00:00:00Z"),
                )
            assertTrue(d.createStatusList(args).isOk)
            val identifier = byteArrayOf(0x10, 0x20)
            val allocated =
                d.allocateEntry(
                    AllocateEntryArgs(
                        statusList = StatusListRef(correlationId = args.correlationId),
                        entryCorrelationId = "mso-1",
                        identifier = identifier,
                    ),
                ) as Ok
            assertTrue((signer.lastArgs?.mdocPayload as MdocStatusListPayload.IdentifierList).identifiers.isEmpty())

            d.updateEntryStatus(
                com.sphereon.statuslist.UpdateEntryStatusArgs(
                    entry = EntryRef(correlationId = args.correlationId, entryCorrelationId = "mso-1"),
                    value = StatusValues.INVALID,
                ),
            )
            val revoked = signer.lastArgs?.mdocPayload as MdocStatusListPayload.IdentifierList
            assertEquals(listOf(identifier.toList()), revoked.identifiers.map { it.toList() })
            assertEquals(identifier.toList(), allocated.value.identifier?.toList())

            val duplicate =
                d.allocateEntry(
                    AllocateEntryArgs(
                        statusList = StatusListRef(correlationId = args.correlationId),
                        identifier = identifier,
                    ),
                )
            assertTrue(duplicate.isErr)
        }

    @Test
    fun mdocStatusBindingRejectsAConfiguredProfileMismatchBeforeAllocation() =
        runTest {
            val d = driver()
            val args =
                createArgs(correlationId = "mdoc-status-mismatch").copy(
                    proofFormat = StatusProofFormat.CWT,
                    mdocProfile = MdocStatusListProfile.STATUS_LIST,
                    validUntil = Instant.parse("2030-01-01T00:00:00Z"),
                )
            assertTrue(d.createStatusList(args).isOk)

            val result =
                CredentialStatusEnricherImpl(d).reserve(
                    StatusEnrichmentContext(
                        credentialConfigurationId = "org.iso.18013.mdl",
                        format = "mso_mdoc",
                        spec = StatusListSpec.TOKEN_STATUS_LIST,
                        purposes = listOf(StatusPurpose.REVOCATION),
                        statusListCorrelationId = args.correlationId,
                        mdocProfile = MdocStatusListProfile.IDENTIFIER_LIST,
                        proofFormat = StatusProofFormat.CWT,
                    ),
                )

            assertTrue(result.isErr)
            assertEquals(
                "STATUSLIST_BINDING_DEFINITION_MISMATCH",
                (result as Err).error.code,
            )
            assertNull(
                (d.getEntry(EntryRef(correlationId = args.correlationId, statusListIndex = 0)) as Ok).value,
                "profile mismatch must be rejected before an entry is allocated",
            )
        }
}

/**
 * Records the key name the driver resolved, so a test can assert where it came from. It signs from
 * its own material and never consults a KMS, mirroring a deployment whose signer owns its key.
 */
private class RecordingStatusListSigner : StatusListSigner {
    var lastKeyName: String? = null

    override suspend fun signStatusListToken(args: SignStatusListTokenArgs): IdkResult<StatusListToken, IdkError> {
        lastKeyName = args.signingKeyName
        return Ok(
            StatusListToken(
                token = "signed:${args.encodedList}",
                contentType = args.proofFormat.contentType,
                ttlSeconds = args.ttlSeconds,
            ),
        )
    }
}

private class ToggleStatusListSigner : StatusListSigner {
    var fail: Boolean = false
    var signCount: Int = 0

    override suspend fun signStatusListToken(args: SignStatusListTokenArgs): IdkResult<StatusListToken, IdkError> {
        if (fail) return Err(IdkError.UNKNOWN_ERROR(message = "signer unavailable"))
        return Ok(
            StatusListToken(
                token = "signed:${++signCount}:${args.encodedList}",
                contentType = args.proofFormat.contentType,
                ttlSeconds = args.ttlSeconds,
            ),
        )
    }
}

/** Stands in for a deployment that manages signing material centrally; null means "refuse". */
private class FixedSigningKeyNameResolver(
    private val keyName: String?,
) : StatusListSigningKeyNameResolver {
    val requests = mutableListOf<Pair<String, String>>()

    override suspend fun resolveSigningKeyName(
        tenantId: String,
        statusListId: String,
    ): String? {
        requests += tenantId to statusListId
        return keyName
    }
}
