/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.crypto.kms.provider.aws

import aws.sdk.kotlin.services.kms.model.KeyState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AwsSymmetricKeyLifecycleTest {
    @Test
    fun creationWithoutAliasOrTaggedKeyCreatesOneKey() {
        assertEquals(
            AwsSymmetricKeyCreationReconciliationPlan.Create,
            awsSymmetricKeyCreationReconciliationPlan(null, emptyList()),
        )
    }

    @Test
    fun completedCreationReusesTheExactTaggedAliasTarget() {
        val immutableIdentity = "arn:aws:kms:eu-west-1:123456789012:key/00000000-0000-0000-0000-000000000001"

        assertEquals(
            AwsSymmetricKeyCreationReconciliationPlan.Reuse(immutableIdentity),
            awsSymmetricKeyCreationReconciliationPlan(immutableIdentity, listOf(immutableIdentity)),
        )
    }

    @Test
    fun hardCrashAfterCreateBeforeAliasReattachesTheAtomicallyTaggedKey() {
        val immutableIdentity = "arn:aws:kms:eu-west-1:123456789012:key/00000000-0000-0000-0000-000000000002"

        assertEquals(
            AwsSymmetricKeyCreationReconciliationPlan.Reattach(immutableIdentity),
            awsSymmetricKeyCreationReconciliationPlan(null, listOf(immutableIdentity)),
        )
    }

    @Test
    fun multipleTaggedKeysFailClosedInsteadOfSelectingAnOrphan() {
        assertFailsWith<IllegalStateException> {
            awsSymmetricKeyCreationReconciliationPlan(
                aliasIdentity = null,
                taggedKeyIdentities =
                    listOf(
                        "arn:aws:kms:eu-west-1:123456789012:key/00000000-0000-0000-0000-000000000003",
                        "arn:aws:kms:eu-west-1:123456789012:key/00000000-0000-0000-0000-000000000004",
                    ),
            )
        }
    }

    @Test
    fun firstRevocationSchedulesExactKeyBeforeDeletingAlias() {
        val plan = awsSymmetricKeyRevocationPlan(KeyState.Enabled)

        assertTrue(plan.scheduleDeletion)
        assertTrue(plan.deleteAlias)
    }

    @Test
    fun retryAfterScheduleCrashKeepsAliasCleanupReachable() {
        val plan = awsSymmetricKeyRevocationPlan(KeyState.PendingDeletion)

        assertFalse(plan.scheduleDeletion)
        assertTrue(plan.deleteAlias)
    }

    @Test
    fun unrelatedLifecycleStatesFailClosed() {
        assertFailsWith<IllegalStateException> {
            awsSymmetricKeyRevocationPlan(KeyState.PendingImport)
        }
    }
}
