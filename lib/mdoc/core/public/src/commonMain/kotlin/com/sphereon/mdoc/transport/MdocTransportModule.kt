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
 *
 */

package com.sphereon.mdoc.transport

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet

/**
 * Dependency injection module for mdoc transport infrastructure.
 *
 * This module provides the [MdocTransportRegistry] which auto-discovers
 * all available transport factories via multibinding.
 *
 * ## How It Works
 *
 * 1. Each transport module (BLE, NFC, REST API, etc.) contributes its factory:
 *    ```kotlin
 *    @ContributesIntoSet(SessionScope::class)
 @OptIn(ExperimentalObjCName::class)
 @ObjCName("BleTransportFactory", exact = true)
 *    class BleTransportFactory : MdocTransportFactory { ... }
 *    ```
 *
 * 2. This module aggregates all factories and creates the registry:
 *    ```kotlin
 *    @Provides
 *    fun provideRegistry(factories: Set<MdocTransportFactory>): MdocTransportRegistry
 *    ```
 *
 * 3. Components inject the registry to discover available transports:
 *    ```kotlin
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocReaderEngagementManagerImpl", exact = true)
 *    class MdocReaderEngagementManagerImpl(
 *        private val transportRegistry: MdocTransportRegistry
 *    )
 *    ```
 *
 * ## SessionScope Rationale
 *
 * The registry is scoped to `SessionScope` (tenant-specific) rather than `AppScope` because:
 * - Some transport factories (e.g., REST API) require tenant-specific configuration (mTLS certificates)
 * - The registry consumers (`MdocTransferFactoryImpl`, `MdocEngagementFactoryImpl`) are SessionScope
 * - Different tenants may have different transport capabilities or configurations
 *
 * ## Benefits
 *
 * - **Auto-discovery**: Transport modules are automatically discovered
 * - **Modular**: Add/remove transports by adding/removing dependencies
 * - **Type-safe**: Compile-time checking via DI
 * - **No manual registration**: Everything happens via annotations
 * - **Tenant-aware**: Each session can have its own transport configuration
 */
@ContributesTo(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocTransportModule", exact = true)
interface MdocTransportModule {

    /**
     * Provides the transport registry with all discovered transport factories.
     *
     * This method is called by the DI framework to create the registry.
     * The `factories` parameter is automatically populated with all
     * classes annotated with:
     * ```
     * @ContributesIntoSet(SessionScope::class, binding = binding<MdocTransportFactory>())
     * ```
     *
     * @param factories Set of all registered transport factories (injected via multibinding)
     * @return Configured transport registry
     */
    @Provides
    @SingleIn(SessionScope::class)
    fun provideMdocTransportRegistry(
        factories: Set<MdocTransportFactory>
    ): MdocTransportRegistry {
        val registry = MdocTransportRegistry()

        // Register all discovered factories
        factories.forEach { factory ->
            registry.register(factory)
        }

        return registry
    }
}
