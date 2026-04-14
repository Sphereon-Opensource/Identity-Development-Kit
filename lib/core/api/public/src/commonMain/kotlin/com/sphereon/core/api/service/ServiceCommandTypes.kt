/*
 * Copyright 2023-2026 Sphereon International B.V.
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

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * Wrapper for commands that return a String value.
 *
 * Used instead of raw `String` as `ServiceCommand<Args, String>` output type
 * to ensure proper serialization through binary transport (EDK remote/server modules).
 *
 * @property value The string result value
 */
@JsExportCompat
@Serializable
data class StringResult(
    val value: String,
)

/**
 * Wrapper for commands that return Unit (no meaningful output).
 *
 * Used instead of raw `Unit` as `ServiceCommand<Args, Unit>` output type
 * to ensure proper serialization through binary transport (EDK remote/server modules).
 */
@Serializable
data object EmptyResult
