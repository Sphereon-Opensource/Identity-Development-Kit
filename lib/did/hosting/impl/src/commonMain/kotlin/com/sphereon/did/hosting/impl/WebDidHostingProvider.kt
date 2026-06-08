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

package com.sphereon.did.hosting.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.did.hosting.DidHostingProvider
import com.sphereon.did.hosting.HostedDid
import com.sphereon.did.models.DidDocument
import com.sphereon.did.persistence.DidRepository
import com.sphereon.did.persistence.toDidDocument
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.serialization.json.Json

/**
 * Hosts the `did.json` for a did:web DID: the stored document served verbatim. Contributed into the
 * hosting `Set<DidHostingProvider>` via DI; claims a web location only when the stored record is
 * method `web`.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<DidHostingProvider>())
class WebDidHostingProvider(
    private val repository: DidRepository,
) : DidHostingProvider {
    private val json =
        Json {
            encodeDefaults = false
            explicitNulls = false
            prettyPrint = false
        }

    override val method: String = "web"

    override suspend fun resolveDidJson(
        tenantId: String?,
        webLocation: String,
    ): IdkResult<HostedDid?, IdkError> {
        val detail = repository.findByWebLocation(tenantId, webLocation).getOrElse { return Err(it) } ?: return Ok(null)
        if (detail.record.method != method) {
            return Ok(null)
        }
        val document = detail.toDidDocument()
        return Ok(
            HostedDid(
                json = json.encodeToString(DidDocument.serializer(), document),
                method = method,
                deactivated = detail.record.deactivated,
                // did:web resolution cache TTL (5 minutes), per WebDidCapabilities.DEFAULT_CACHE_TTL_SECONDS.
                cacheMaxAgeSeconds = DID_WEB_CACHE_TTL_SECONDS,
            ),
        )
    }

    private companion object {
        const val DID_WEB_CACHE_TTL_SECONDS: Long = 300
    }
}
