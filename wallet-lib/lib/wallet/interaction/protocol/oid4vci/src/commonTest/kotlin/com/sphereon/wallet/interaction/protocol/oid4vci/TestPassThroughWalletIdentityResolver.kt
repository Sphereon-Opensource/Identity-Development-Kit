/*
 * Copyright 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.party.model.IdentityRole
import com.sphereon.wallet.WalletIdentityResolver
import com.sphereon.wallet.credential.IdentifierRef

/** Test-only identity authority for protocol tests that intentionally do not persist Party data. */
internal object TestPassThroughWalletIdentityResolver : WalletIdentityResolver {
    override suspend fun resolve(
        ref: IdentifierRef,
        role: IdentityRole,
    ): IdkResult<IdentifierRef, IdkError> = Ok(ref)
}
