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

import kotlin.time.Duration
import kotlinx.datetime.Instant

/**
 * Metrics and diagnostics data
 */
data class NfcServiceMetrics(
    val engagementStartTime: Instant? = null,
    val nfcStartTime: Instant? = null,
    val handoverTime: Instant? = null,
    val bleStartTime: Instant? = null,
    val completionTime: Instant? = null,
    val totalApduCount: Long = 0,
    val successfulApduCount: Long = 0,
    val errorCount: Long = 0,
    val retryCount: Long = 0,
    val timeoutCount: Long = 0,
    val lastError: String? = null,
    val lastErrorTime: Instant? = null,
    val averageApduProcessingTimeMs: Double = 0.0
) {
    fun nfcDuration(): Duration? =
        if (nfcStartTime != null && handoverTime != null) handoverTime - nfcStartTime else null

    fun bleDuration(): Duration? =
        if (bleStartTime != null && completionTime != null) completionTime - bleStartTime else null

    fun totalDuration(): Duration? =
        if (engagementStartTime != null && completionTime != null) completionTime - engagementStartTime else null

    fun successRate(): Double =
        if (totalApduCount > 0) successfulApduCount.toDouble() / totalApduCount.toDouble() else 1.0

    fun errorRate(): Double =
        if (totalApduCount > 0) errorCount.toDouble() / totalApduCount.toDouble() else 0.0

    fun isHealthy(): Boolean = errorRate() < 0.1 && successRate() > 0.9
}