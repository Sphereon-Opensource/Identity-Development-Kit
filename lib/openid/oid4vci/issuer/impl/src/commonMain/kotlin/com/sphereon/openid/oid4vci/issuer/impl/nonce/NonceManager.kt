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

package com.sphereon.openid.oid4vci.issuer.impl.nonce

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vci.common.model.NonceResponse
import com.sphereon.openid.oid4vci.issuer.store.CredentialNonceStore
import com.sphereon.openid.oid4vci.issuer.store.NonceEntry
import dev.whyoleg.cryptography.random.CryptographyRandom
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.time.Clock

/**
 * Internal helper for nonce issuance and consumption.
 */
@Inject
@SingleIn(SessionScope::class)
class NonceManager(
    private val nonceStore: CredentialNonceStore,
) {
    /**
     * Issue a fresh nonce with the given TTL.
     */
    suspend fun issue(ttlSeconds: Long = 300): IdkResult<NonceResponse, IdkError> {
        val nonce = generateNonce()
        val entry = nonceStore.create(nonce, ttlSeconds).getOrElse { return Err(it) }
        return Ok(
            NonceResponse(
                cNonce = entry.nonce,
                cNonceExpiresIn = ttlSeconds.toInt(),
            ),
        )
    }

    /**
     * Consume a nonce atomically. Returns the entry if valid, null if not found/expired.
     */
    suspend fun consume(nonce: String): IdkResult<NonceEntry?, IdkError> {
        val entry = nonceStore.consume(nonce).getOrElse { return Err(it) }
        if (entry == null) {
            return Ok(null)
        }
        val now = Clock.System.now().epochSeconds
        if (entry.expiresAt < now) {
            return Ok(null)
        }
        return Ok(entry)
    }

    private fun generateNonce(): String = CryptographyRandom.nextBytes(32).encodeToBase64Url()
}
