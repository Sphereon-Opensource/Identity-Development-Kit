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

package com.sphereon.di.session

import com.sphereon.core.api.context.SessionExecution
import software.amazon.app.platform.scope.Scope
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Thread-safe session instance.
 * Provides stable access to session operations without race conditions.
 * This instance holds a stable reference to the session data at creation time.
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("SessionInstance", exact = true)
interface SessionInstance {
    // Stable session data (immutable references)
    val sessionId: String
    val sessionContext: SessionContext
    val sessionExecution: SessionExecution
    val graph: SessionGraph
    val scope: Scope

    val sessionContextManager: SessionContextManager

    // Session operations (thread-safe using stable references)
    fun <T : Any> getService(id: String): T

    fun addService(
        id: String,
        service: Any,
    ): Scope

    // Session lifecycle
    fun isCurrentlyActive(): Boolean // Check if this session is currently the active one

    fun makeActive(): Boolean // Make this session the active one

    fun destroy() // Destroy this session

    /**
     * Warms up the configuration cache for this session.
     *
     * This method should be called before first KMS access when using external caches (Redis, DB).
     * It asynchronously loads configuration data into the sync cache, enabling PropertyResolver
     * to access config synchronously without blocking.
     *
     * The warmup targets KMS-related config prefixes:
     * - kms.providers: KMS provider configurations
     * - kms.keystores: Keystore configurations
     * - sphereon.default.kms: Default KMS settings
     *
     * Usage pattern:
     * ```kotlin
     * val session = sessionManager.createOrGetFromId("my-session")
     * session.warmupCacheAsync()  // Must call before first KMS access
     * val kmsRegistry = session.graph.kmsProviderRegistry  // Now works with warm cache
     * ```
     *
     * This method is safe to call multiple times and is idempotent.
     * If no external cache is configured, this is a no-op.
     */
    suspend fun warmupCacheAsync()
}

// Convenience extension function for type-safe service access
inline fun <reified T : Any> SessionInstance.getService(): T = getService(T::class.simpleName ?: error("Cannot get service name for ${T::class}"))
