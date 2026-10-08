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
 *
 */

package com.sphereon.crypto.resolution
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.Order
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo

interface IdentifierService : BaseIdentifierService<IdentifierOptsOrResult> {
    @ContributesTo(SessionScope::class)
    interface Graph {
        val identifierService: IdentifierService
    }
}

interface BaseIdentifierService<Type : IdentifierOptsOrResult> {
    /**
     * Which identifier‐methods this service supports.
     */
    val supportedIdentifierMethods: List<IIdentifierMethod>

    /**
     * Resolution order (lower = higher priority).
     */
    val order: Int
        get() = Order.MEDIUM.orderValue

    /**
     * Can this service handle the raw identifier?
     */
    suspend fun isSupportedIdentifier(identifier: Any): Boolean

    /**
     * Can this service handle the given identifier method?
     */
    suspend fun isSupportedIdentifierMethod(identifierMethod: IIdentifierMethod): Boolean

    /**
     * Are these opts in a form this service can handle?
     */
    suspend fun isSupportedOpts(opts: Type): Boolean

    /**
     * Normalize or enrich opts to a supported form.
     */
    suspend fun asSupportedOpts(opts: Type): IdkResult<Type, IdkErrorType>

    /**
     * Perform the actual resolution.
     */
    suspend fun resolve(opts: Type): IdkResult<out Type, IdkErrorType>
}
