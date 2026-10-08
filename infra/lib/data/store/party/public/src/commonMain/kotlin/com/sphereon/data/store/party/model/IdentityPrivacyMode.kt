/*
 * Copyright 2026 Sphereon International B.V.
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

package com.sphereon.data.store.party.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Controls where readable identity profile material is allowed to live.
 *
 * Identity rows remain the privacy boundary and lookup anchor. When plaintext profile data is
 * permitted, it belongs on Party records and Party extensions rather than on Identity itself.
 */
@JsExportCompat
@Serializable
enum class IdentityPrivacyMode {
    /** Plaintext Party profile data may exist; identifiers are lookup projections. */
    @SerialName("partyProfiled")
    PARTY_PROFILED,

    /** Only public or business-safe Party data is stored plaintext; PII remains identifier-only. */
    @SerialName("partyPublicOnly")
    PARTY_PUBLIC_ONLY,

    /** No Party profile is required; matching/contact material lives in protected identifiers. */
    @SerialName("identifierOnly")
    IDENTIFIER_ONLY,

    /** Only non-revealing identifiers are allowed by default. */
    @SerialName("pseudonymous")
    PSEUDONYMOUS,
}
