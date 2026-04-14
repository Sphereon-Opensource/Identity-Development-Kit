/*
 * (c) 2026 Sphereon International B.V.
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

package com.sphereon.core.api.auth

import com.sphereon.core.compat.JsExportCompat

/**
 * Provides the identity of the current service for outbound propagation.
 *
 * Each service assembly provides its [serviceId] via environment variable
 * or configuration (e.g., `SPHEREON_SERVICE_ID=service-crypto`).
 * The service ID is attached to outbound gRPC calls as an `X-Service-Id` header
 * so receiving services can identify the caller.
 */
@JsExportCompat
interface ServiceIdentity {
    val serviceId: String
}
