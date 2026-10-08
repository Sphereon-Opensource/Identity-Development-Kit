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

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * Controls how long an offer URI remains valid and how its offer payload is generated on each access.
 *
 * SINGLE_USE is the spec-default: the offer URI serves a pre-created offer JSON once and then expires.
 * REUSABLE_FRESH_PER_FETCH keeps the URI alive across multiple wallet fetches, minting a new offer
 * payload on each GET so every holder receives an independent issuance session.
 */
@JsExportCompat
@Serializable
enum class OfferUriLifecycle {
    /** The offer URI serves the pre-created offer JSON exactly once, then the session expires. */
    SINGLE_USE,

    /**
     * The offer URI stays active across multiple fetches. Each GET mints a fresh offer payload
     * so every wallet receives its own independent issuance session from the same URI.
     */
    REUSABLE_FRESH_PER_FETCH,
}

/**
 * Optional rate-limit applied to a reusable offer URI. Caps the number of fresh offer-mint
 * operations that the issuer will service within a rolling time window.
 *
 * Only meaningful when the parent offer is configured with [OfferUriLifecycle.REUSABLE_FRESH_PER_FETCH].
 *
 * @param maxPerWindow Maximum number of offer-mint operations allowed within [windowSeconds].
 * @param windowSeconds Length of the rolling time window in seconds.
 */
@JsExportCompat
@Serializable
data class OfferRateLimit(
    val maxPerWindow: Int,
    val windowSeconds: Long,
)
