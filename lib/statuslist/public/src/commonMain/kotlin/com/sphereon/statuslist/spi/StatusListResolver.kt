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

package com.sphereon.statuslist.spi

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.statuslist.ResolveStatusArgs
import com.sphereon.statuslist.ResolvedStatus

/**
 * Holder/wallet/verifier-side SPI: fetch a hosted status-list token, verify its signature, decode
 * the bit(s) at a given index, and report the status. Implementations cache resolved lists honouring
 * the token's `ttl`/`exp`. This is a standalone verifier capability — independent of any issuer.
 */
interface StatusListResolver {
    suspend fun resolveStatus(args: ResolveStatusArgs): IdkResult<ResolvedStatus, IdkError>
}
