package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.issuer.store.CredentialIssuanceSessionStore
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession

internal class FakeCredentialIssuanceSessionStore : CredentialIssuanceSessionStore {
    private val sessions = mutableMapOf<String, IssuanceSession>()

    override suspend fun create(session: IssuanceSession): IdkResult<IssuanceSession, IdkError> {
        sessions[session.sessionId] = session
        return Ok(session)
    }

    override suspend fun get(sessionId: String): IdkResult<IssuanceSession?, IdkError> = Ok(sessions[sessionId])

    override suspend fun getByIssuerState(state: String): IdkResult<IssuanceSession?, IdkError> = Ok(sessions.values.firstOrNull { it.issuerState == state })

    override suspend fun findByCredentialConfigurationId(configId: String): IdkResult<IssuanceSession?, IdkError> = Ok(null)

    override suspend fun update(session: IssuanceSession): IdkResult<IssuanceSession, IdkError> {
        sessions[session.sessionId] = session
        return Ok(session)
    }
}
