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

package com.sphereon.openid.wallet

import com.sphereon.data.store.party.model.IdentifierType
import kotlinx.serialization.Serializable

/**
 * A typed reference to an identifier (issuer, subject, relying party, etc.).
 *
 * The [correlationId] is an opaque string token (e.g. a UUID) that
 * allows callers to correlate this reference with a Party record without
 * a database round-trip. It is optional and not used for auth decisions.
 */
@Serializable
data class IdentifierRef(
    val type: IdentifierType,
    val value: String,
    val correlationId: String? = null,
)
