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
 */

package com.sphereon.did.resolver.impl.testutil

import com.sphereon.crypto.resolution.extern.CnfExternalIdentifierResolutionService
import com.sphereon.crypto.resolution.extern.CnfExternalIdentifierResolutionServiceImpl
import com.sphereon.did.methods.jwk.JwkDidResolver
import com.sphereon.did.methods.key.KeyDidResolver
import com.sphereon.did.resolver.DidResolverRegistry
import com.sphereon.did.resolver.impl.DidExternalIdentifierResolutionService
import com.sphereon.did.resolver.impl.DidExternalIdentifierResolutionServiceImpl
import com.sphereon.di.app.AppComponent
import com.sphereon.di.session.SessionInstance

expect fun createDidResolverTestAppComponent(testInstance: Any): AppComponent

class DidResolverTestContext(sessionId: String, testInstance: Any) {
    val app: AppComponent = createDidResolverTestAppComponent(testInstance)
    val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId(sessionId)
    val resolverRegistry: DidResolverRegistry = (session.component as DidResolverRegistry.Component).didResolverRegistry
    val keyDidResolver: KeyDidResolver = (session.component as KeyDidResolver.Component).keyDidResolver
    val jwkDidResolver: JwkDidResolver = (session.component as JwkDidResolver.Component).jwkDidResolver
    val cnfResolver: CnfExternalIdentifierResolutionService = (session.component as CnfExternalIdentifierResolutionServiceImpl.Component).cnfExternalIdentifierResolutionService
    val didResolver: DidExternalIdentifierResolutionService = (session.component as DidExternalIdentifierResolutionServiceImpl.Component).didExternalIdentifierResolutionService
}
