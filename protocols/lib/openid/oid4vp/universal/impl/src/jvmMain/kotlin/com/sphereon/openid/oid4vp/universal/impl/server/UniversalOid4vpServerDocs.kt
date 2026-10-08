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

package com.sphereon.openid.oid4vp.universal.impl.server

import dev.zacsweers.metro.DependencyGraph

/**
 * Universal OID4VP Server Bootstrap Documentation.
 *
 * This module provides typed service commands using the Binary API v5 pattern.
 * Commands are automatically exposed via HTTP through transport metadata.
 *
 * ## Available Typed Commands
 *
 * - [CreateAuthRequestServiceCommand]: POST /oid4vp/backend/auth/requests
 * - [GetAuthRequestStatusServiceCommand]: GET /oid4vp/backend/auth/requests/{correlation_id}
 * - [DeleteAuthRequestServiceCommand]: DELETE /oid4vp/backend/auth/requests/{correlation_id}
 *
 * ## Server Bootstrap (VDX Layer)
 *
 * To run a server exposing these commands, create a VDX server application:
 *
 * ```kotlin
 * // In VDX layer (has dependency on vdx-lib-transport-server)
 * @DependencyGraph(AppScope::class)
 * abstract class UniversalOid4vpServerGraph {
 *     abstract val dualTransportServer: DualTransportServer
 *
 *     @Provides
 *     fun provideDualTransportConfig() = dualTransportConfigFromEnvironment("oid4vp-server")
 *
 *     @Provides
 *     fun provideCommands(
 *         createCommand: CreateAuthRequestServiceCommand,
 *         getStatusCommand: GetAuthRequestStatusServiceCommand,
 *         deleteCommand: DeleteAuthRequestServiceCommand
 *     ): Set<ServiceCommand<*, *, *>> = setOf(
 *         createCommand, getStatusCommand, deleteCommand
 *     )
 * }
 *
 * fun main() = runBlocking {
 *     val graph = createGraph<UniversalOid4vpServerGraph>()
 *     runServer(graph.dualTransportServer)
 * }
 * ```
 *
 * ## Request Flow
 *
 * 1. HTTP Request arrives at Ktor server
 * 2. CommandIdResolver maps path to command ID via PublicApiCommand
 * 3. BinaryCommandAdapter decodes input using command's inputTypeToken
 * 4. Command.execute(typedInput) runs business logic
 * 5. BinaryCommandAdapter encodes output using command's outputTypeToken
 * 6. HTTP Response returned to client
 */
object UniversalOid4vpServerDocs
