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

package com.sphereon.wallet.impl

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.data.store.party.model.IdentityRole
import com.sphereon.di.session.SessionScope
import com.sphereon.wallet.WalletIdentityResolver
import com.sphereon.wallet.credential.IdentifierRef
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * IDK default: no identity book is wired; returns [ref] unchanged with no
 * [IdentifierRef.identityIdentifierId]. The EDK [com.sphereon.wallet.identity.EdkWalletIdentityResolver]
 * replaces this via Metro [dev.zacsweers.metro.ContributesBinding.replaces].
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<WalletIdentityResolver>())
class NoOpWalletIdentityResolver : WalletIdentityResolver {
    override suspend fun resolve(
        ref: IdentifierRef,
        role: IdentityRole
    ): IdkResult<IdentifierRef, IdkError> = Ok(ref)
}
