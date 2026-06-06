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

package com.sphereon.openid.wallet

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.party.model.IdentityRole

/**
 * SPI that links a wallet [IdentifierRef] to the identity book.
 *
 * Implementations may record the ref as an [com.sphereon.data.store.party.model.Identity]
 * with a [com.sphereon.data.store.party.model.CorrelationIdentifier] and return a copy
 * enriched with the resulting [IdentifierRef.correlationId]. The IDK default is a
 * no-op that returns [ref] unchanged; the EDK wires the real identity repository.
 */
interface WalletIdentityResolver {
    /**
     * Returns [ref] enriched with a [IdentifierRef.correlationId] linking it to the
     * identity book, or [ref] unchanged if no identity book is available.
     *
     * @param ref the identifier reference to resolve (issuer URL, DID, verifier client_id, etc.)
     * @param role the role this identity plays in the credential ecosystem
     */
    suspend fun resolve(
        ref: IdentifierRef,
        role: IdentityRole
    ): IdkResult<IdentifierRef, IdkError>
}
