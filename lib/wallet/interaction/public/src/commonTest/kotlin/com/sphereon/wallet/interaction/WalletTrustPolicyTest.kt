/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * H3 conformance trust profile: [WalletTrustPolicy.allow] always ALLOWs, regardless of
 * [WalletCounterpartyTrustSummary.status] (including BLOCKED), contrasted with the production
 * default [WalletTrustPolicy.warn] which still BLOCKs a BLOCKED counterparty and WARNs on UNKNOWN.
 */
class WalletTrustPolicyTest {
    @Test
    fun allowPolicyAlwaysAllowsRegardlessOfStatus() =
        runTest {
            assertEquals(WalletTrustPolicyAction.ALLOW, WalletTrustPolicy.allow.evaluate(summary(WalletTrustStatus.BLOCKED)).action)
            assertEquals(WalletTrustPolicyAction.ALLOW, WalletTrustPolicy.allow.evaluate(summary(WalletTrustStatus.UNKNOWN)).action)
            assertEquals(WalletTrustPolicyAction.ALLOW, WalletTrustPolicy.allow.evaluate(summary(WalletTrustStatus.TRUSTED)).action)
        }

    @Test
    fun warnPolicyStillBlocksAndWarnsByStatus() =
        runTest {
            assertEquals(WalletTrustPolicyAction.BLOCK, WalletTrustPolicy.warn.evaluate(summary(WalletTrustStatus.BLOCKED)).action)
            assertEquals(WalletTrustPolicyAction.WARN, WalletTrustPolicy.warn.evaluate(summary(WalletTrustStatus.UNKNOWN)).action)
            assertEquals(WalletTrustPolicyAction.ALLOW, WalletTrustPolicy.warn.evaluate(summary(WalletTrustStatus.TRUSTED)).action)
        }

    private fun summary(status: WalletTrustStatus): WalletCounterpartyTrustSummary =
        WalletCounterpartyTrustSummary(
            counterparty =
                WalletCounterpartySummary(
                    role = WalletCounterpartyRole.ISSUER,
                    identifier = "issuer.example",
                ),
            status = status,
        )
}
