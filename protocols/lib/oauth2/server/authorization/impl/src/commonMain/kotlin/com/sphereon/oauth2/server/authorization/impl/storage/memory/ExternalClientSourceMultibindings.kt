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

package com.sphereon.oauth2.server.authorization.impl.storage.memory

import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.server.authorization.provider.ExternalClientSource
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Multibinds

/** Deployments without external client sources resolve only configured and stored clients. */
@ContributesTo(SessionScope::class)
interface ExternalClientSourceMultibindings {
    @Multibinds(allowEmpty = true)
    fun externalClientSources(): Set<ExternalClientSource>
}
