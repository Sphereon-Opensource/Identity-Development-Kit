package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.common.model.CredentialOffer
import com.sphereon.openid.oid4vci.issuer.store.CredentialOfferStore

internal class FakeCredentialOfferStore : CredentialOfferStore {
    private val offers = mutableMapOf<String, CredentialOffer>()
    private val sessionIds = mutableMapOf<String, String>()

    var getResult: IdkResult<CredentialOffer?, IdkError>? = null

    fun putOffer(
        offerId: String,
        offer: CredentialOffer,
    ) {
        offers[offerId] = offer
    }

    override suspend fun store(
        offerId: String,
        offer: CredentialOffer,
        ttlSeconds: Long,
        sessionId: String?,
    ): IdkResult<Unit, IdkError> {
        offers[offerId] = offer
        if (sessionId != null) sessionIds[offerId] = sessionId
        return Ok(Unit)
    }

    override suspend fun get(offerId: String): IdkResult<CredentialOffer?, IdkError> {
        getResult?.let { return it }
        return Ok(offers[offerId])
    }

    override suspend fun getSessionId(offerId: String): IdkResult<String?, IdkError> = Ok(sessionIds[offerId])

    override suspend fun delete(offerId: String): IdkResult<Boolean, IdkError> {
        sessionIds.remove(offerId)
        return Ok(offers.remove(offerId) != null)
    }
}
