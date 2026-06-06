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

package com.sphereon.openid.oid4vci.issuer.command

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat

/**
 * SPI guarding fresh-offer minting on a reusable offer URI.
 *
 * A [OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH] offer URI mints a new offer payload on every
 * wallet fetch. This SPI caps how many of those mint operations the issuer services for a given
 * offer within the offer's configured [OfferRateLimit] window.
 *
 * The default implementation is a fixed-window counter keyed by `offerId`. Sliding-window and
 * per-IP variants are a later follow-up.
 */
@JsExportCompat
interface OfferRateLimiter {
    /**
     * Attempts to consume one mint slot for [offerId] under [limit].
     *
     * Returns `Ok(true)` when the request is within the limit (the slot is consumed), or
     * `Ok(false)` when the limit for the current window is exhausted. `Err` is reserved for
     * infrastructure failures, not for a hit limit.
     */
    suspend fun tryAcquire(
        offerId: String,
        limit: OfferRateLimit,
    ): IdkResult<Boolean, IdkError>
}
