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

package com.sphereon.openid.oid4vci.issuer.attribute

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import com.sphereon.openid.oid4vci.issuer.bridge.ValidatedTokenContext
import com.sphereon.openid.oid4vci.issuer.store.IssuanceSession

/**
 * Optional callback invoked before credential issuance to contribute additional attributes.
 *
 * IDK ships a no-op default. EDK replaces it via `@ContributesBinding`.
 *
 * The result is a [CredentialAttributeContribution] that carries the contributed attributes plus
 * the set of source ids the contributor is still waiting on for an inbound async-callback
 * contribution. The issuer command consumes that set to drive the §6.5.7 sync-wait window:
 * before falling through to the deferral decision tree it suspends up to
 * [CredentialAttributeContribution.syncWaitWindow] for those sources to land via the callback
 * endpoint, then re-runs the contributor so any freshly-arrived attributes flow into the merge.
 */
@JsExportCompat
interface CredentialAttributeContributor {
    @JsExportIgnoreCompat
    suspend fun contribute(
        session: IssuanceSession,
        tokenContext: ValidatedTokenContext,
        credentialConfigurationId: String,
    ): IdkResult<CredentialAttributeContribution, IdkError>
}
