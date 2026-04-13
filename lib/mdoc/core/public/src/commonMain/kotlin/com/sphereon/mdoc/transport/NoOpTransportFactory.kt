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

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.engagement.EngagementData
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesIntoSet

/**
 * No-op transport factory implementation.
 *
 * This is a placeholder implementation required by kotlin-inject's multibinding.
 * When using `Set<MdocTransportFactory>` multibinding, kotlin-inject expects at
 * least one concrete implementation to exist.
 *
 * This factory is automatically filtered out by [MdocTransportRegistry] and never
 * actually used. Real transport factories (BLE, NFC, REST API, etc.) are provided
 * by their respective transport modules.
 *
 * ## Why This Exists
 *
 * Without this, KSP processing fails because:
 * 1. `MdocTransportModule` requests `Set<MdocTransportFactory>`
 * 2. No implementations exist in transport-core module
 * 3. KSP can't generate the provider code
 *
 * ## SessionScope Rationale
 *
 * Moved to `SessionScope` to match the transport registry's scope. This ensures
 * consistency across all transport factory implementations.
 *
 * ## How It's Filtered
 *
 * The registry automatically skips this factory:
 * ```kotlin
 * fun register(factory: MdocTransportFactory) {
 *     if (factory is NoOpTransportFactory) return  // Skip
 *     // ... register real factories
 * }
 * ```
 *
 * @see MdocTransportFactory
 * @see MdocTransportRegistry
 */
@Inject
@ContributesIntoSet(SessionScope::class, binding = binding<MdocTransportFactory>())
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("NoOpTransportFactory", exact = true)
class NoOpTransportFactory : MdocTransportFactory {

    override val transportType: TransportType
        get() = TransportType.BLE  // Dummy value, never used

    override fun supports(connectionMethod: ConnectionMethod): Boolean {
        return false  // Never supports anything
    }

    override fun createTransfer(
        connectionMethod: ConnectionMethod,
        execution: SessionExecution,
        role: MdocRole,
        engagementData: EngagementData?
    ): MdocTransport<*> {
        error("NoOpTransportFactory should never be called - it's filtered by the registry")
    }

    override fun getConnectionMethodFactory(): ConnectionMethodBase.Factory? {
        return null  // No connection method factory
    }
}
