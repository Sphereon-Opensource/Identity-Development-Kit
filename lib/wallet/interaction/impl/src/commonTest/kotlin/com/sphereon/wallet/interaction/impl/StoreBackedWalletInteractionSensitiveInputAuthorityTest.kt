/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.wallet.interaction.impl

import com.sphereon.wallet.interaction.WalletInteractionSensitiveInputPurpose
import com.sphereon.wallet.interaction.WalletInteractionSessionId
import com.sphereon.wallet.interaction.WalletSecurityAssurance
import com.sphereon.wallet.interaction.WalletSecurityGrant
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class StoreBackedWalletInteractionSensitiveInputAuthorityTest {
    @Test
    fun valuesAreSessionAndPurposeBoundAndConsumedOnce() =
        runTest {
            val authority = StoreBackedWalletInteractionSensitiveInputAuthority(InMemoryWalletInteractionPrivateSessionStore())
            val session = WalletInteractionSessionId("session-a")
            val otherSession = WalletInteractionSessionId("session-b")
            val ref =
                authority.register(
                    session,
                    WalletInteractionSensitiveInputPurpose.OID4VCI_TRANSACTION_CODE,
                    "417901",
                )

            assertNull(authority.consume(otherSession, WalletInteractionSensitiveInputPurpose.OID4VCI_TRANSACTION_CODE, ref))
            assertNull(authority.consume(session, WalletInteractionSensitiveInputPurpose.OID4VCI_AUTHORIZATION_CALLBACK, ref))
            assertEquals(
                "417901",
                authority.consume(session, WalletInteractionSensitiveInputPurpose.OID4VCI_TRANSACTION_CODE, ref),
            )
            assertNull(authority.consume(session, WalletInteractionSensitiveInputPurpose.OID4VCI_TRANSACTION_CODE, ref))
        }

    @Test
    fun securityGrantIsTypedSessionBoundAndConsumedOnce() =
        runTest {
            val authority = StoreBackedWalletInteractionSensitiveInputAuthority(InMemoryWalletInteractionPrivateSessionStore())
            val session = WalletInteractionSessionId("session-a")
            val grant =
                WalletSecurityGrant(
                    grantId = "grant-a",
                    assurance = WalletSecurityAssurance.USER_PRESENT,
                )
            val ref = authority.registerSecurityGrant(session, grant)

            assertNull(authority.consumeSecurityGrant(WalletInteractionSessionId("session-b"), ref))
            assertEquals(grant, authority.consumeSecurityGrant(session, ref))
            assertNull(authority.consumeSecurityGrant(session, ref))
        }

    @Test
    fun blankInputsAreRejectedAndClearInvalidatesOutstandingRefs() =
        runTest {
            val authority = StoreBackedWalletInteractionSensitiveInputAuthority(InMemoryWalletInteractionPrivateSessionStore())
            val session = WalletInteractionSessionId("session-a")
            assertFailsWith<IllegalArgumentException> {
                authority.register(session, WalletInteractionSensitiveInputPurpose.OID4VCI_TRANSACTION_CODE, " ")
            }
            val ref = authority.register(session, WalletInteractionSensitiveInputPurpose.OID4VCI_TRANSACTION_CODE, "417901")

            authority.clear(session)

            assertNull(authority.consume(session, WalletInteractionSensitiveInputPurpose.OID4VCI_TRANSACTION_CODE, ref))
        }
}
