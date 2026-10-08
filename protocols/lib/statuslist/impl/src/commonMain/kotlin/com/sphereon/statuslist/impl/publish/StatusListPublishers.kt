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

package com.sphereon.statuslist.impl.publish

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.statuslist.StatusListErrors
import com.sphereon.statuslist.StatusListRef
import com.sphereon.statuslist.StatusListToken
import com.sphereon.statuslist.spi.StatusListPublisher
import com.sphereon.statuslist.spi.StatusListPublisherIds
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Self-host / export publisher: the operator retrieves the signed token (via the `statuslist.token.get`
 * command or the public hosting GET) and hosts it wherever they like. There is no platform-managed
 * location, so [resolveStatusListUri] returns null — the list must configure its `uri` explicitly —
 * and [publish] is a no-op (nothing to push; the host is external).
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<StatusListPublisher>())
class ExportStatusListPublisher : StatusListPublisher {
    override val id: String = StatusListPublisherIds.EXPORT

    override fun resolveStatusListUri(correlationId: String): String? = null

    override suspend fun publish(
        ref: StatusListRef,
        token: StatusListToken,
    ): IdkResult<Unit, IdkError> = Ok(Unit)
}

/**
 * A push-based publisher whose transport is not implemented yet (SEAM ONLY): [resolveStatusListUri]
 * returns null (configure the raw URL explicitly until the push is built) and [publish] returns a
 * not-implemented error. Concrete seams (GitHub Pages, CDN) just supply their [id].
 */
abstract class NotImplementedStatusListPublisher : StatusListPublisher {
    override fun resolveStatusListUri(correlationId: String): String? = null

    override suspend fun publish(
        ref: StatusListRef,
        token: StatusListToken,
    ): IdkResult<Unit, IdkError> = Err(StatusListErrors.publisherNotImplemented(id))
}

/** GitHub Pages publisher — SEAM ONLY (push to a gh-pages branch not implemented yet). */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<StatusListPublisher>())
class GitHubPagesStatusListPublisher : NotImplementedStatusListPublisher() {
    override val id: String = StatusListPublisherIds.GITHUB_PAGES
}

/** CDN publisher — SEAM ONLY (push to a CDN origin not implemented yet). */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<StatusListPublisher>())
class CdnStatusListPublisher : NotImplementedStatusListPublisher() {
    override val id: String = StatusListPublisherIds.CDN
}
