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

package com.sphereon.crypto.resolution
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.IdentifierLookupType

@JsExportCompat
abstract class IdentifierOptsOrResult(
    open val method: IIdentifierMethod? = null,
    open val identifier: Any,
    open val context: IdentifierContext = IdentifierContext(),
    open val lookup: IdentifierLookupType = AdditionalIdentifierLookup(),
) {
    abstract val isResolved: Boolean

    fun isOpts() = !isResolved

    fun isResult() = isResolved

    abstract fun asOpts(): Any

    abstract fun asResult(): Any
}
