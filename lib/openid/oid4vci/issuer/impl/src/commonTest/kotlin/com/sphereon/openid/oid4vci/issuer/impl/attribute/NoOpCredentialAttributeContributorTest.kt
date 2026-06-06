/*
 * © 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

package com.sphereon.openid.oid4vci.issuer.impl.attribute

import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSessionStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Locks down the IDK baseline contract: the NoOp contributor reports an empty attribute map AND
 * an empty pending-async-callback set, so the issuer command's §6.5.7 sync-wait block is a
 * degenerate no-op for pure-IDK deployments.
 */
class NoOpCredentialAttributeContributorTest {
    private val session =
        IssuanceSession(
            sessionId = "session-1",
            issuerId = "https://test.example/oid4vci",
            credentialConfigurationIds = listOf("PID"),
            status = IssuanceSessionStatus.CREDENTIAL_REQUESTED,
            createdAt = 0,
            expiresAt = Long.MAX_VALUE,
        )

    private val tokenContext =
        ValidatedTokenContext(
            subject = "did:example:holder",
            clientId = "test-client",
            scope = null,
            credentialConfigurationIds = listOf("PID"),
        )

    @Test
    fun returnsEmptyContributionWithNoPendingAsyncSources() =
        runTest {
            val result =
                NoOpCredentialAttributeContributor()
                    .contribute(session = session, tokenContext = tokenContext, credentialConfigurationId = "PID")

            assertTrue(result.isOk, "expected Ok but got Err: ${result.errorOrNull()}")
            assertTrue(result.value.attributes.isEmpty(), "NoOp contributes no attributes")
            assertTrue(
                result.value.pendingAsyncCallbackSources.isEmpty(),
                "NoOp never reports pending async-callback sources — IDK has no pipeline",
            )
            assertEquals(
                Duration.ZERO,
                result.value.syncWaitWindow,
                "NoOp default syncWaitWindow must be zero so the issuer never waits",
            )
        }
}
