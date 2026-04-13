/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.verifier.store

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * A store for short-lived, security-sensitive session data.
 *
 * Adds single-use semantics ("consume") on top of [Oid4vpStore].
 */
//@JsExportCompat
interface SessionStore<K : Any, V : Any> : Oid4vpStore<K, V> {

    /**
     * Retrieve a value and optionally consume it (single-use).
     *
     * @param key The key to retrieve.
     * @param consume If true, the entry is marked as consumed before returning.
     */
    suspend fun getAndConsume(key: K, consume: Boolean = true): IdkResult<V, IdkError>

    /**
     * Whether an entry is consumed (single-use).
     */
    suspend fun isConsumed(key: K): IdkResult<Boolean, IdkError>

    /**
     * Mark an entry as consumed.
     */
    suspend fun markConsumed(key: K): IdkResult<Unit, IdkError>

    /**
     * True iff the entry exists, is not expired, and is not consumed.
     */
    suspend fun isValid(key: K): IdkResult<Boolean, IdkError>
}
