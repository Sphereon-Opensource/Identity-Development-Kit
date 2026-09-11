/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.issuer.impl.http

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.openid.oid4vci.rest.CredentialOfferSession
import com.sphereon.openid.oid4vci.rest.CredentialOfferSessionStore

internal class FakeCredentialOfferSessionStore : CredentialOfferSessionStore {
    private val byCorrelationId = mutableMapOf<String, CredentialOfferSession>()

    fun putSession(session: CredentialOfferSession) {
        byCorrelationId[session.correlationId] = session
    }

    override suspend fun create(session: CredentialOfferSession): IdkResult<CredentialOfferSession, IdkError> {
        byCorrelationId[session.correlationId] = session
        return Ok(session)
    }

    override suspend fun get(correlationId: String): IdkResult<CredentialOfferSession?, IdkError> = Ok(byCorrelationId[correlationId])

    override suspend fun getByOfferId(offerId: String): IdkResult<CredentialOfferSession?, IdkError> = Ok(byCorrelationId.values.firstOrNull { it.offerId == offerId })

    override suspend fun update(session: CredentialOfferSession): IdkResult<CredentialOfferSession, IdkError> {
        byCorrelationId[session.correlationId] = session
        return Ok(session)
    }

    override suspend fun delete(correlationId: String): IdkResult<Boolean, IdkError> = Ok(byCorrelationId.remove(correlationId) != null)
}
