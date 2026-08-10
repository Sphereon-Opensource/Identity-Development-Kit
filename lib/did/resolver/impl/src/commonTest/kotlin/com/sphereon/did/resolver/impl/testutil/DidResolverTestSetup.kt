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

package com.sphereon.did.resolver.impl.testutil

import com.sphereon.crypto.resolution.extern.CnfExternalIdentifierResolutionService
import com.sphereon.di.app.AppGraph
import com.sphereon.di.session.SessionInstance
import com.sphereon.did.methods.jwk.JwkDidResolver
import com.sphereon.did.methods.key.KeyDidResolver
import com.sphereon.did.resolver.DidResolverRegistry
import com.sphereon.did.resolver.impl.DidExternalIdentifierResolutionService
import com.sphereon.did.resolver.impl.DidExternalIdentifierResolutionServiceImpl
import com.sphereon.did.resolver.impl.DidResolverRegistryImpl

expect fun createDidResolverTestAppGraph(testInstance: Any): AppGraph

class DidResolverTestContext(
    sessionId: String,
    testInstance: Any,
) {
    val app: AppGraph = createDidResolverTestAppGraph(testInstance)
    val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId(sessionId, principalType = com.sphereon.di.context.PrincipalType.USER)
    val resolverRegistry: DidResolverRegistry = (session.graph as DidResolverRegistryImpl.Graph).didResolverRegistry
    val keyDidResolver: KeyDidResolver = (session.graph as KeyDidResolver.Graph).keyDidResolver
    val jwkDidResolver: JwkDidResolver = (session.graph as JwkDidResolver.Graph).jwkDidResolver
    val cnfResolver: CnfExternalIdentifierResolutionService = (session.graph as CnfExternalIdentifierResolutionService.Graph).cnfExternalIdentifierResolutionService
    val didResolver: DidExternalIdentifierResolutionService = (session.graph as DidExternalIdentifierResolutionServiceImpl.Graph).didExternalIdentifierResolutionService
}
