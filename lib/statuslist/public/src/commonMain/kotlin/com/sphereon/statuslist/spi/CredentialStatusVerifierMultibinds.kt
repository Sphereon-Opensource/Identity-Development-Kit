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

import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds

/**
 * Declares the `Set<CredentialStatusVerifier>` multibinding as allow-empty so any SessionScope graph
 * resolves it even when no status-list implementation is on the classpath. Lives in the SPI module so
 * every consumer (the OID4VP verifier, the SD-JWT VC verifier, ...) can inject the set without
 * re-declaring it. A deployment that wants credential-status checking adds `lib-statuslist-impl` (or
 * its own [CredentialStatusVerifier]s), which contribute into this set; otherwise it stays empty and
 * status checking is skipped.
 */
@ContributesTo(SessionScope::class)
interface CredentialStatusVerifierMultibinds {
    @Multibinds(allowEmpty = true)
    fun credentialStatusVerifiers(): Set<CredentialStatusVerifier>
}
