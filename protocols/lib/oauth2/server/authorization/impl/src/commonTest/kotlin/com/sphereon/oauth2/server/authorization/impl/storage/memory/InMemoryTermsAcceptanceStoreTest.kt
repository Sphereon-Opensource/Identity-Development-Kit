/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.sphereon.oauth2.server.authorization.impl.storage.memory

import com.sphereon.core.api.IdkResult
import com.sphereon.oauth2.server.authorization.storage.TermsAcceptanceStoreError
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class InMemoryTermsAcceptanceStoreTest {
    private val tenant = "t-A"
    private val otherTenant = "t-B"
    private val identity = "user-1"
    private val now: Instant = Instant.fromEpochSeconds(1_700_000_000)

    @Test
    fun findReturnsNullWhenNothingRecorded() =
        runTest {
            val store = InMemoryTermsAcceptanceStore()
            assertNull(store.findAcceptedVersion(tenant, identity).expectOk())
        }

    @Test
    fun recordAndFindRoundTrip() =
        runTest {
            val store = InMemoryTermsAcceptanceStore()
            store.recordAcceptance(tenant, identity, "2026-04-01", now).expectOk()
            assertEquals("2026-04-01", store.findAcceptedVersion(tenant, identity).expectOk())
        }

    @Test
    fun recordingNewerVersionOverwritesOlder() =
        runTest {
            // Acceptance is single-version per (tenant, identity); the latest record wins.
            // The store doesn't keep prior acceptances — that's the deployment's audit
            // trail concern via the append-only audit_event chain.
            val store = InMemoryTermsAcceptanceStore()
            store.recordAcceptance(tenant, identity, "2026-04-01", now).expectOk()
            store.recordAcceptance(tenant, identity, "2026-04-15", now + 1L.toInstantOffset()).expectOk()
            assertEquals("2026-04-15", store.findAcceptedVersion(tenant, identity).expectOk())
        }

    @Test
    fun tenantsAreIsolated() =
        runTest {
            // Same identity id, different tenants. Recording under tenant-A must not surface
            // under tenant-B.
            val store = InMemoryTermsAcceptanceStore()
            store.recordAcceptance(tenant, identity, "2026-04-01", now).expectOk()
            assertNull(
                store.findAcceptedVersion(otherTenant, identity).expectOk(),
                "tenant isolation: tenant-A acceptance must NOT count for tenant-B",
            )
        }

    @Test
    fun identitiesAreIsolatedWithinATenant() =
        runTest {
            val store = InMemoryTermsAcceptanceStore()
            store.recordAcceptance(tenant, identity, "v1", now).expectOk()
            assertNull(
                store.findAcceptedVersion(tenant, "different-user").expectOk(),
                "no cross-identity bleed within a tenant",
            )
        }

    @Test
    fun reRecordingSameVersionIsIdempotent() =
        runTest {
            val store = InMemoryTermsAcceptanceStore()
            store.recordAcceptance(tenant, identity, "v7", now).expectOk()
            store.recordAcceptance(tenant, identity, "v7", now).expectOk()
            store.recordAcceptance(tenant, identity, "v7", now).expectOk()
            assertEquals("v7", store.findAcceptedVersion(tenant, identity).expectOk())
        }

    private fun Long.toInstantOffset(): kotlin.time.Duration = kotlin.time.Duration.parse("PT${this}S")

    private fun <V> IdkResult<V, TermsAcceptanceStoreError>.expectOk(): V {
        check(isOk) { "expected Ok, got $error" }
        return value
    }
}
