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

package com.sphereon.oauth2.server.authorization.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual

/**
 * User consent decision for an authorization request
 *
 * RFC 6749 Section 3.3: The authorization server SHOULD display the scope
 * information to the resource owner when requested.
 */
@Serializable
data class ConsentDecision(
    /**
     * User ID who made the decision
     */
    val userId: String,

    /**
     * Client ID the decision applies to
     */
    val clientId: String,

    /**
     * Whether consent was granted
     */
    val granted: Boolean,

    /**
     * Scopes that were granted
     * May be a subset of requested scopes if user partially consented
     */
    val grantedScopes: List<String>? = null,

    /**
     * When the consent was given
     */
    val grantedAt: Instant,

    /**
     * Whether this consent should be remembered
     * If true, future authorization requests for the same client/scopes
     * can skip the consent prompt
     */
    val rememberConsent: Boolean = false,

    /**
     * When the consent expires (if remembered)
     * null = never expires
     */
    val expiresAt: Instant? = null,

    /**
     * Additional consent metadata
     */
    val additionalData: Map<String, @Contextual Any> = emptyMap()
)
