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

package com.sphereon.statuslist.spi

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.statuslist.StatusListRef
import com.sphereon.statuslist.StatusListToken

/**
 * A hosting "driver": decides WHERE/HOW a signed status-list token is made retrievable, and what
 * public URI an issued credential embeds for it. This is distinct from [StatusListDriver] (which
 * persists the BITS) and [StatusListSigner] (which signs the TOKEN) — the publisher owns the
 * *hosting location*.
 *
 * Implementations are contributed as a multibinding `Set` (see `StatusListPublisherMultibinds`); a
 * list selects one by [id] via config (`sphereon.statuslists.[<id>].publisher`, default `rest`).
 *
 * Two retrieval models:
 * - **pull** (REST, self-host/export): the token is fetched on demand from the persisted bits;
 *   [publish] is a no-op. The REST host derives the URI from the configured base path; export means
 *   you host it yourself, so the URI is explicit.
 * - **push** (GitHub Pages, CDN — future): [publish] writes the freshly-signed token to the external
 *   host on create/update. Those publishers are seams for now.
 */
interface StatusListPublisher {
    /** Stable selector id, e.g. one of [StatusListPublisherIds]. */
    val id: String

    /**
     * The public URI a credential embeds for the list with this [correlationId], or null when this
     * publisher cannot derive it (e.g. self-host/export, where the URI must be configured explicitly).
     * Used to populate `statusListUri` when a list does not configure one explicitly.
     */
    fun resolveStatusListUri(correlationId: String): String?

    /**
     * Push the freshly-signed [token] to the external host. A no-op for pull-based publishers (REST,
     * export); push publishers (GitHub Pages, CDN) override it. No lifecycle trigger calls this yet —
     * it is the seam push hosts will hook once implemented.
     */
    suspend fun publish(
        ref: StatusListRef,
        token: StatusListToken,
    ): IdkResult<Unit, IdkError>
}

/** Canonical publisher selector ids. */
object StatusListPublisherIds {
    const val REST: String = "rest"
    const val EXPORT: String = "export"
    const val GITHUB_PAGES: String = "github-pages"
    const val CDN: String = "cdn"
}
