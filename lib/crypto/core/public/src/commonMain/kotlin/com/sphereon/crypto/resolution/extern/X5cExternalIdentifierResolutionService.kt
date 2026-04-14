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

package com.sphereon.crypto.resolution.extern
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo

interface X5cExternalIdentifierResolutionService : ExternalIdentifierService {
    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierResult.X5c, IdkErrorType>

    override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierX5cOpts, IdkErrorType>

    @ContributesTo(SessionScope::class)
    interface Graph {
        val x5cExternalIdentifierResolutionService: X5cExternalIdentifierResolutionService
    }
}
