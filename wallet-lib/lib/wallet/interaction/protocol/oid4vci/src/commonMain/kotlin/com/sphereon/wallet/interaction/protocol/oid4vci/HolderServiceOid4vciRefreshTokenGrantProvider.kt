/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.wallet.interaction.protocol.oid4vci

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.holder.Oid4vciHolderService
import com.sphereon.openid.oid4vci.holder.TokenResponseWithContext

/** Adapts the core OID4VCI holder refresh operation to the interaction executor seam. */
class HolderServiceOid4vciRefreshTokenGrantProvider(
    private val holder: Oid4vciHolderService,
) : Oid4vciRefreshTokenGrantProvider {
    override suspend fun exchangeRefreshToken(
        request: Oid4vciRefreshTokenGrantRequest,
    ): IdkResult<TokenResponseWithContext, IdkError> =
        holder.exchangeRefreshToken(
            tokenEndpoint = request.tokenEndpoint,
            refreshToken = request.refreshToken,
            clientId = request.clientId,
            dpopProofJwt = request.dpopProofJwt,
            clientAttestationJwt = request.clientAttestationJwt,
            clientAttestationPopJwt = request.clientAttestationPopJwt,
            clientAuthentication = request.clientAuthentication,
        )
}
