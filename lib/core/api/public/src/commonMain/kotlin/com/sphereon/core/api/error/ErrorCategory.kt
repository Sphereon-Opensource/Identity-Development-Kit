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

package com.sphereon.core.api.error

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Transport-neutral semantic error categories.
 *
 * Categories describe WHAT KIND of failure occurred without coupling to any specific
 * transport protocol. Transport renderers (HTTP, binary, gRPC) map categories to
 * protocol-specific status codes.
 *
 * This is the single source of truth for error semantics across all transports.
 */
@OptIn(ExperimentalObjCName::class)
@Serializable
@JsExportCompat
@ObjCName("ErrorCategory", exact = true)
enum class ErrorCategory {
    /** Bad input, missing parameters, malformed data, validation failure */
    VALIDATION,

    /** Authentication required or failed */
    UNAUTHORIZED,

    /** Authenticated but not permitted for this operation */
    FORBIDDEN,

    /** Requested resource does not exist */
    NOT_FOUND,

    /** Already exists, version mismatch, or state conflict */
    CONFLICT,

    /** Client-side precondition not met (e.g., ETag mismatch) */
    PRECONDITION_FAILED,

    /** Quota or rate limit exceeded */
    RATE_LIMITED,

    /** Service or command disabled or temporarily unavailable */
    UNAVAILABLE,

    /** Unexpected server-side failure */
    INTERNAL,

    /** Protocol-level issue such as unsupported operation */
    PROTOCOL,
}
