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

package com.sphereon.did.manager

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

/**
 * SPI for DID methods whose minting cannot go through the generic [DidProvider.create] — e.g.
 * did:webvh, which needs richer inputs (update keys, witnesses, a signed genesis log entry) and
 * computes its identifier (the SCID) from the genesis itself.
 *
 * A generator is discovered by the DID manager via DI multibinding (`Set<ManagedDidGenerator>`),
 * keyed by [method]. When a generator exists for the requested method, the manager uses it to
 * produce the [DidCreateResult] (instead of `DidProvider.create`) and then persists the resulting
 * current-state document through its normal aggregate path. The manager itself stays method-agnostic:
 * it never names did:webvh.
 */
interface ManagedDidGenerator {
    /** The DID method this generator mints (e.g. `webvh`). */
    val method: String

    /**
     * Mints a DID for [options], returning the created DID, its current-state document, and the
     * verification-method/key information the manager needs to persist the managed record. The
     * generator owns the method-specific ceremony (signing, SCID computation, …); it does not persist.
     */
    suspend fun generate(options: DidCreateOptions): IdkResult<DidCreateResult, IdkError>
}
