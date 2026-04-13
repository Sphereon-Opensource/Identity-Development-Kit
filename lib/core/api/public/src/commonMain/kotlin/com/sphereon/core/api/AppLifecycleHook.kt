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

package com.sphereon.core.api

/**
 * Lifecycle hook for application-scoped components that need to start/stop
 * alongside the server.
 *
 * Implementations are discovered via DI multibinding (`@ContributesMultibinding`)
 * and invoked by the transport server during startup and shutdown.
 *
 * Use this for components like event listeners, cache warmers, or background
 * tasks that need the server's lifecycle scope.
 */
interface AppLifecycleHook {
    /** Called before transport servers start. */
    suspend fun onBeforeStart() {}

    /** Called after transport servers have started successfully. */
    suspend fun onAfterStart() {}

    /** Called before transport servers stop. */
    suspend fun onBeforeStop() {}

    /** Called after transport servers have stopped. */
    suspend fun onAfterStop() {}
}
