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

package com.sphereon.core.api.service

import com.sphereon.core.api.error.IdkError
import com.sphereon.core.compat.JsExportCompat

/**
 * Marker interface for command input types.
 *
 * All ServiceCommand input types should implement this interface.
 * This enables future builder/DSL patterns and provides a common
 * type constraint for middleware, validation, and API evolution.
 *
 * **Usage:**
 * ```kotlin
 * @Serializable
 * data class GetKeyInput(
 *     val aliasOrKid: String,
 *     val providerId: String? = null
 * ) : CommandRequest
 * ```
 */
@JsExportCompat
interface CommandRequest

/**
 * Singleton representing an empty request for commands that don't need input.
 *
 * Use this instead of `Unit` for ServiceCommand implementations that
 * require no input but need to conform to the [CommandRequest] marker.
 *
 * **Usage:**
 * ```kotlin
 * class HealthCheckCommand(execution: SessionExecution) :
 *     TypedServiceCommandAdapter<EmptyRequest, HealthStatus, IdkError>(
 *         commandId = "system.health.check",
 *         execution = execution,
 *         inputTypeToken = typeToken<EmptyRequest>(),
 *         outputTypeToken = typeToken<HealthStatus>()
 *     ) {
 *     override suspend fun doExecute(
 *         args: EmptyRequest,
 *         applyDuring: (EmptyRequest) -> EmptyRequest
 *     ): IdkResult<HealthStatus, IdkError> = Ok(HealthStatus(healthy = true))
 * }
 * ```
 */
object EmptyRequest : CommandRequest
