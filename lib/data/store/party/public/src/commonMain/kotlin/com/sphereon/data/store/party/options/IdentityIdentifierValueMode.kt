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

package com.sphereon.data.store.party.options

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JsExportCompat
@Serializable
enum class IdentityIdentifierValueMode {
    /** Return the stored lookup representation, e.g. plaintext lookup key or blind index. */
    @SerialName("lookup")
    LOOKUP,

    /** Return a non-reversible display placeholder or mask. */
    @SerialName("masked")
    MASKED,

    /** Return plaintext when the row carries or can reveal it; otherwise fallback to lookup value. */
    @SerialName("revealed")
    REVEALED,
}
