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

package com.sphereon.crypto.resolution.managed

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.resolution.BaseIdentifierService

interface ManagedIdentifierService : BaseIdentifierService<ManagedIdentifierOptsOrResult> {
    /**
     * Are these opts in a form this service can handle?
     */
    override suspend fun isSupportedOpts(opts: ManagedIdentifierOptsOrResult): Boolean

    /**
     * Normalize or enrich opts to a supported form.
     */
    override suspend fun asSupportedOpts(opts: ManagedIdentifierOptsOrResult): IdkResult<ManagedIdentifierOpts, IdkErrorType>

    /**
     * Perform the actual resolution.
     */
    override suspend fun resolve(opts: ManagedIdentifierOptsOrResult): IdkResult<ManagedIdentifierResult<KeyType>, IdkErrorType>
}
