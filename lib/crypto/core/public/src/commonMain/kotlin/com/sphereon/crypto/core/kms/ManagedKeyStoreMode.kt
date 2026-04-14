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

package com.sphereon.crypto.core.kms
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Strategy mode for the managed key store.
 *
 * - [ITERATING]: Iterate all KMS providers to list/resolve keys (existing behavior).
 * - [PERSISTENT]: Use the persistent key reference store (database) for listing;
 *   resolution still goes through providers.
 * - [AUTO]: Automatically select [PERSISTENT] if a key reference store is available,
 *   otherwise fall back to [ITERATING].
 *
 * Configured via `sphereon.crypto.kms.managed-key-store.mode`.
 */
@Serializable
@JsExportCompat
enum class ManagedKeyStoreMode {
    @SerialName("iterating")
    ITERATING,

    @SerialName("persistent")
    PERSISTENT,

    @SerialName("auto")
    AUTO,
}

/**
 * Resolves the effective [ManagedKeyStoreMode] for the current session.
 */
fun interface ManagedKeyStoreModeResolver {
    fun resolve(): ManagedKeyStoreMode
}
