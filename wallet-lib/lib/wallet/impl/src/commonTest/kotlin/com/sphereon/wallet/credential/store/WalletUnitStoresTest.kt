/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.wallet.credential.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.wallet.credential.CredentialMetadata
import com.sphereon.wallet.credential.CredentialMetadataFilter
import com.sphereon.wallet.credential.CredentialRecord
import com.sphereon.wallet.credential.CredentialTypeRef
import com.sphereon.wallet.credential.IssuanceSession
import com.sphereon.wallet.credential.IssuanceSessionStatus
import com.sphereon.wallet.credential.SecretRef
import com.sphereon.wallet.credential.WalletCredentialStore
import com.sphereon.wallet.credential.WalletIssuanceSessionStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * [DefaultWalletUnitStores] is pure composition over its two constructor-injected stores: no
 * routing, caching, or re-resolution of its own. These tests pin that contract (the exact
 * injected instances come back out, on every access) since Metro's `@SingleIn(SessionScope::class)`
 * annotation on the class itself is a compile-time contract this test source set cannot exercise
 * without building a full session graph (no other lib-wallet-impl test does that either).
 */
class WalletUnitStoresTest {
    @Test
    fun exposesTheInjectedCredentialsStoreInstance() {
        val credentials = UnitStoresRecordingCredentialStore()
        val issuanceSessions = UnitStoresRecordingIssuanceSessionStore()

        val stores = DefaultWalletUnitStores(credentials = credentials, issuanceSessions = issuanceSessions)

        assertSame(credentials, stores.credentials)
    }

    @Test
    fun exposesTheInjectedIssuanceSessionStoreInstance() {
        val credentials = UnitStoresRecordingCredentialStore()
        val issuanceSessions = UnitStoresRecordingIssuanceSessionStore()

        val stores = DefaultWalletUnitStores(credentials = credentials, issuanceSessions = issuanceSessions)

        assertSame(issuanceSessions, stores.issuanceSessions)
    }

    @Test
    fun repeatedAccessReturnsTheSameStoreInstancesEveryTime() {
        val stores =
            DefaultWalletUnitStores(
                credentials = UnitStoresRecordingCredentialStore(),
                issuanceSessions = UnitStoresRecordingIssuanceSessionStore(),
            )

        assertSame(stores.credentials, stores.credentials)
        assertSame(stores.issuanceSessions, stores.issuanceSessions)
    }

    @Test
    fun operationsOnTheExposedCredentialsStoreReachTheOriginalInjectedInstance() =
        runTest {
            val credentials = UnitStoresRecordingCredentialStore()
            val stores = DefaultWalletUnitStores(credentials = credentials, issuanceSessions = UnitStoresRecordingIssuanceSessionStore())

            stores.credentials.getCredential("wallet-unit-1", "record-1")

            assertTrue(credentials.getCredentialCalls.contains("wallet-unit-1" to "record-1"))
        }

    @Test
    fun operationsOnTheExposedIssuanceSessionStoreReachTheOriginalInjectedInstance() =
        runTest {
            val issuanceSessions = UnitStoresRecordingIssuanceSessionStore()
            val stores = DefaultWalletUnitStores(credentials = UnitStoresRecordingCredentialStore(), issuanceSessions = issuanceSessions)

            stores.issuanceSessions.getSession("wallet-unit-1", "session-1")

            assertTrue(issuanceSessions.getSessionCalls.contains("wallet-unit-1" to "session-1"))
        }
}

private class UnitStoresRecordingCredentialStore : WalletCredentialStore {
    val getCredentialCalls: MutableList<Pair<String, String>> = mutableListOf()

    override suspend fun putCredential(
        walletUnitId: String,
        record: CredentialRecord,
    ): IdkResult<CredentialRecord, IdkError> = Ok(record)

    override suspend fun getCredential(
        walletUnitId: String,
        credentialRecordId: String,
    ): IdkResult<CredentialRecord?, IdkError> {
        getCredentialCalls += walletUnitId to credentialRecordId
        return Ok(null)
    }

    override suspend fun getMetadata(
        walletUnitId: String,
        credentialRecordId: String,
    ): IdkResult<CredentialMetadata?, IdkError> = Ok(null)

    override suspend fun listMetadata(
        walletUnitId: String,
        filter: CredentialMetadataFilter,
    ): IdkResult<List<CredentialMetadata>, IdkError> = Ok(emptyList())

    override suspend fun findByCredentialTypeRef(
        walletUnitId: String,
        ref: CredentialTypeRef,
    ): IdkResult<List<CredentialMetadata>, IdkError> = Ok(emptyList())

    override suspend fun deleteCredential(
        walletUnitId: String,
        credentialRecordId: String,
    ): IdkResult<Boolean, IdkError> = Ok(true)
}

private class UnitStoresRecordingIssuanceSessionStore : WalletIssuanceSessionStore {
    val getSessionCalls: MutableList<Pair<String, String>> = mutableListOf()

    override suspend fun putSession(
        walletUnitId: String,
        session: IssuanceSession,
    ): IdkResult<IssuanceSession, IdkError> = Ok(session)

    override suspend fun getSession(
        walletUnitId: String,
        issuanceSessionId: String,
    ): IdkResult<IssuanceSession?, IdkError> {
        getSessionCalls += walletUnitId to issuanceSessionId
        return Ok(null)
    }

    override suspend fun listSessions(
        walletUnitId: String,
        statuses: Set<IssuanceSessionStatus>,
    ): IdkResult<List<IssuanceSession>, IdkError> = Ok(emptyList())

    override suspend fun storeDeferredAccessToken(
        walletUnitId: String,
        issuanceSessionId: String,
        accessToken: String,
    ): IdkResult<SecretRef, IdkError> = Ok(SecretRef(id = "secret"))

    override suspend fun getDeferredAccessToken(
        walletUnitId: String,
        issuanceSessionId: String,
    ): IdkResult<String?, IdkError> = Ok(null)

    override suspend fun deleteSession(
        walletUnitId: String,
        issuanceSessionId: String,
    ): IdkResult<Boolean, IdkError> = Ok(true)
}
