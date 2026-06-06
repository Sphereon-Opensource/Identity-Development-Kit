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

package com.sphereon.openid.oid4vp.dcql.store.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.dcql.store.DcqlQueryConfigurationStore
import com.sphereon.openid.oid4vp.dcql.store.DcqlQueryResolver
import com.sphereon.openid.oid4vp.dcql.store.ResolvedDcqlQuery
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Default [DcqlQueryResolver]: resolves a `query_id` to the current configuration in the
 * [DcqlQueryConfigurationStore]. This store has no version history, so [resolveForCreate]
 * reports `version = null` and [resolvePinned] ignores the pinned version.
 *
 * Assemblies that supply a versioned store drop this module in favour of its resolver, so
 * there is no binding conflict.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DcqlQueryResolver>())
class DefaultDcqlQueryResolver(
    private val store: DcqlQueryConfigurationStore,
) : DcqlQueryResolver {
    override suspend fun resolveForCreate(
        queryId: String,
        verifierId: String?
    ): IdkResult<ResolvedDcqlQuery, IdkError> {
        val config =
            store.getByQueryId(queryId).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Query configuration not found: $queryId"))
        if (!config.enabled) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Query configuration is disabled: $queryId"))
        }
        return Ok(
            ResolvedDcqlQuery(
                dcqlQuery = config.dcqlQuery,
                dcqlQueryId = queryId,
                version = config.currentVersion,
            ),
        )
    }

    override suspend fun resolvePinned(
        queryId: String,
        version: Int?
    ): IdkResult<DcqlQuery, IdkError> {
        val config =
            store.getByQueryId(queryId).getOrElse { return Err(it) }
                ?: return Err(IdkError.NOT_FOUND_ERROR(message = "Query configuration not found: $queryId"))
        return Ok(config.dcqlQuery)
    }
}
