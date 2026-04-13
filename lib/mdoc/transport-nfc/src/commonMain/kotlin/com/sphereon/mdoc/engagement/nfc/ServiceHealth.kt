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

package com.sphereon.mdoc.engagement.nfc

/**
 * Health status of the service
 */
enum class ServiceHealth(val priority: Int) {
    /** No issues detected */
    HEALTHY(0),

    /** Some issues detected, but service still operational */
    DEGRADED(1),

    /** Service is not operational due to issues */
    UNHEALTHY(2),

    /** Critical failure, service cannot operate */
    CRITICAL(3);

    fun isWorseThan(other: ServiceHealth): Boolean = priority > other.priority
}