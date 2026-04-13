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

package com.sphereon.oauth2.server.resource.impl.service

import com.sphereon.oauth2.server.resource.command.IntrospectTokenCommand
import com.sphereon.oauth2.server.resource.command.ValidateAccessTokenCommand
import com.sphereon.oauth2.server.resource.command.VerifyDpopProofCommand
import com.sphereon.oauth2.server.resource.command.VerifyJwtCommand
import com.sphereon.oauth2.server.resource.service.ResourceServerService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import com.sphereon.di.session.SessionScope
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Implementation of ResourceServerService that delegates to command implementations
 *
 * Follows the Command/Service pattern used throughout IDK:
 * - Commands are injected and stored
 * - Service convenience methods delegate to commands for execution
 * - Commands are exposed via properties for direct access
 *
 * **Pattern Benefits**:
 * - Testability: Commands can be mocked individually
 * - Flexibility: Can use commands directly or via service methods
 * - Consistency: Same pattern across all IDK services
 * - Composability: Commands can be reused in other contexts
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ResourceServerService>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("ResourceServerServiceImpl", exact = true)
class ResourceServerServiceImpl(
    override val validateAccessTokenCommand: ValidateAccessTokenCommand,
    override val verifyJwtCommand: VerifyJwtCommand,
    override val introspectTokenCommand: IntrospectTokenCommand,
    override val verifyDpopProofCommand: VerifyDpopProofCommand
) : ResourceServerService {

    /**
     * DI Component interface for SessionScope
     */
    @ContributesTo(scope = SessionScope::class)
    interface Component {
        val resourceServerService: ResourceServerService
    }
}
