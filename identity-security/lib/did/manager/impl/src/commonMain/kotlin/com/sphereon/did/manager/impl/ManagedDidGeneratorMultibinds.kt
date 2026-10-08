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

package com.sphereon.did.manager.impl

import com.sphereon.di.session.SessionScope
import com.sphereon.did.manager.ManagedDidGenerator
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds

/**
 * The DID manager consults `Set<ManagedDidGenerator>` to mint methods (e.g. did:webvh) that cannot
 * go through `DidProvider.create`. A deployment without any such method contributes zero generators,
 * so the multibinding must be allowed to be empty — otherwise the critical DID graph would fail to
 * build wherever did:webvh is absent.
 */
@ContributesTo(SessionScope::class)
interface ManagedDidGeneratorMultibinds {
    @Multibinds(allowEmpty = true)
    fun managedDidGenerators(): Set<ManagedDidGenerator>
}
