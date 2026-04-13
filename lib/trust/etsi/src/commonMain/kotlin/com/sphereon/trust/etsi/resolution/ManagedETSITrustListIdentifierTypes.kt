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
 *
 */

package com.sphereon.trust.etsi.resolution

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.crypto.core.IdentifierLookupType
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.ManagedKeyInfoType
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.resolution.AdditionalIdentifierLookup
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOpts
import com.sphereon.crypto.resolution.managed.ManagedIdentifierOptsOrResult
import com.sphereon.crypto.resolution.managed.ManagedIdentifierResult
import com.sphereon.trust.core.model.TrustAnchor
import com.sphereon.trust.etsi.model.ETSILoTE
import kotlinx.serialization.json.JsonObject

/**
 * Options for managed (locally hosted) ETSI Trust List resolution.
 *
 * This is used when the trust list is hosted locally by the application
 * rather than fetched from an external source.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ManagedETSITrustListOpts", exact = true)
data class ManagedETSITrustListOpts(
    /**
     * Local identifier for the trust list (e.g., path, key, or alias in local storage)
     */
    override val identifier: String,

    /**
     * Optional territory filter
     */
    val territory: String? = null,

    /**
     * Optional service type filter
     */
    val serviceTypeFilter: List<String>? = null,

    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: IdentifierLookupType = AdditionalIdentifierLookup(),
) : ManagedIdentifierOpts(
    identifier = identifier,
    method = ETSIIdentifierMethods.ETSI_TSL,
    context = context,
    lookup = lookup
)

/**
 * Result of managed ETSI Trust List resolution.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("ManagedETSITrustListResult", exact = true)
data class ManagedETSITrustListResult(
    override val identifier: String,
    override val context: IdentifierContext,
    override val keyInfo: ManagedKeyInfoType<JwkType>,

    /**
     * The resolved trust list
     */
    val trustList: ETSILoTE,

    /**
     * Trust anchors from the list
     */
    val trustAnchors: List<TrustAnchor>,

    /**
     * All JWK keys from the trust list
     */
    val keys: List<ManagedKeyInfoType<JwkType>>,

    val metadata: JsonObject = JsonObject(emptyMap())
) : ManagedIdentifierResult<JwkType>(
    method = ETSIIdentifierMethods.ETSI_TSL,
    keyInfo = keyInfo,
    context = context,
    identifier = identifier,
    resultMetadata = metadata
)

/**
 * Type guards for managed ETSI identifier options.
 */
object ManagedETSIIdentifierOptsGuards {
    fun isManagedETSITrustListOpts(opts: ManagedIdentifierOpts): Boolean {
        return opts is ManagedETSITrustListOpts ||
               opts.method == ETSIIdentifierMethods.ETSI_TSL
    }

    fun asManagedETSITrustListOpts(opts: ManagedIdentifierOpts): ManagedETSITrustListOpts {
        return require(isManagedETSITrustListOpts(opts)) {
            "opts is not a ManagedETSITrustListOpts"
        }.let { opts as ManagedETSITrustListOpts }
    }
}

/**
 * Type guards for managed ETSI identifier results.
 */
object ManagedETSIIdentifierResultGuards {
    fun isManagedETSITrustListResult(result: ManagedIdentifierResult<*>): Boolean {
        return result is ManagedETSITrustListResult ||
               result.method == ETSIIdentifierMethods.ETSI_TSL
    }

    fun asManagedETSITrustListResult(result: ManagedIdentifierResult<*>): ManagedETSITrustListResult {
        return require(isManagedETSITrustListResult(result)) {
            "result is not a ManagedETSITrustListResult"
        }.let { result as ManagedETSITrustListResult }
    }
}
