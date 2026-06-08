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

package com.sphereon.did.hosting

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * Resolves a hosted DID document across every [DidHostingProvider] contributed to the DI graph.
 *
 * The set of hostable methods is discovered dynamically — the registry never names did:web or
 * did:webvh, it just asks each contributed provider in turn. A deployment that includes neither
 * method module simply exposes no hostable methods.
 */
interface DidHostingRegistry {
    /** The DID methods that have a contributed hosting provider in this deployment. */
    fun hostableMethods(): Set<String>

    /**
     * Resolves the `did.json` for [webLocation] by consulting each provider until one claims it.
     * Returns `Ok(null)` when no provider manages the location. A provider error short-circuits
     * and is returned as `Err`.
     */
    suspend fun resolveDidJson(
        tenantId: String?,
        webLocation: String,
    ): IdkResult<HostedDid?, IdkError>
}
