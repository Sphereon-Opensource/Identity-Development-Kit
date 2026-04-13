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

package com.sphereon.oauth2.server.authorization.impl

import dev.zacsweers.metro.DependencyGraph

/**
 * Authorization Server Graph Marker
 *
 * Marker interface for OAuth2 Authorization Server DI contributions.
 *
 * This library provides implementations via @ContributesBinding annotations:
 * - Command implementations (token, authorization, PAR, introspection)
 * - Service implementations
 *
 * **IMPORTANT**: This is a library module and does NOT define a @DependencyGraph.
 * Applications using this library must create their own graph and merge
 * the contributions.
 *
 * Usage in application:
 * ```kotlin
 * @DependencyGraph(AppScope::class)
 * @SingleIn(AppScope::class)
 * abstract class ApplicationGraph {
 *     // OAuth2 Authorization Server commands and services will be available
 *     // through @ContributesBinding annotations
 *
 *     // Provide required dependencies:
 *     abstract val tokenStorage: TokenStorage
 *     abstract val clientRegistry: ClientRegistry
 *     abstract val authorizationCodeStorage: AuthorizationCodeStorage
 *     abstract val sessionStorage: SessionStorage
 *     abstract val nonceStorage: NonceStorage
 * }
 * ```
 *
 * Custom storage implementations:
 * ```kotlin
 * @Inject
 * @SingleIn(AppScope::class)
 * @ContributesBinding(AppScope::class)
 * class PostgresTokenStorage : TokenStorage {
 *     // Production implementation
 * }
 * ```
 *
 * Note: Storage interfaces must be provided by the application.
 * The library does NOT include default storage implementations.
 */
interface AuthorizationServerGraph
