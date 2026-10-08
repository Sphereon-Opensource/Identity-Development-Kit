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

package com.sphereon.mdoc.engagement.nfc

/**
 * Configuration for the NFC service behavior
 */
data class NfcServiceConfig(
    val engagementStartupTimeoutMs: Long = 10000L,
    val handoverTimeoutMs: Long = 10000L,
    val transferStartupTimeoutMs: Long = 60000L,
    val maxRetryAttempts: Int = 3,
    val retryDelayMs: Long = 1000L,
    val healthCheckIntervalMs: Long = 5000L,
    val enableVibration: Boolean = true,
    val enableMetrics: Boolean = true,
    val enableHealthMonitoring: Boolean = true,
    val enableAdvancedLogging: Boolean = true,
    /**
     * Delay in milliseconds to keep NFC active AFTER handover completes and BLE scanning starts.
     * This prevents NFC deactivation from triggering Android system BLE reconfiguration that
     * interferes with the BLE scan. Keeping NFC active for 1-2 seconds allows BLE scan to
     * establish and receive results before NFC resources are released.
     *
     * Default: 500ms (0.5 seconds) - enough time for BLE scan to establish
     * Set to 0 to disable (not recommended - will cause BLE scan interference issues)
     */
    val nfcKeepAliveAfterHandoverMs: Long = 500L,
) {
    companion object {
        val DEFAULT_CONFIG = NfcServiceConfig()
        val PRODUCTION_CONFIG =
            NfcServiceConfig(
                maxRetryAttempts = 2,
                enableMetrics = true,
                enableHealthMonitoring = true,
                enableAdvancedLogging = false,
                nfcKeepAliveAfterHandoverMs = 1500L,
            )
        val DEBUG_CONFIG =
            NfcServiceConfig(
                maxRetryAttempts = 5,
                enableAdvancedLogging = true,
                nfcKeepAliveAfterHandoverMs = 1500L,
            )

        fun validate(config: NfcServiceConfig): NfcServiceConfig {
            require(config.engagementStartupTimeoutMs > 0) { "Engagement startup timeout must be positive" }
            require(config.handoverTimeoutMs > 0) { "Handover timeout must be positive" }
            require(config.transferStartupTimeoutMs > 0) { "Transfer startup timeout must be positive" }
            require(config.maxRetryAttempts >= 0) { "Max retry attempts must be non-negative" }
            require(config.retryDelayMs >= 0) { "Retry delay must be non-negative" }
            require(config.healthCheckIntervalMs > 0) { "Health check interval must be positive" }
            require(config.nfcKeepAliveAfterHandoverMs >= 0) { "NFC keep alive after handover must be non-negative" }
            return config
        }
    }

    fun validate(): NfcServiceConfig {
        require(engagementStartupTimeoutMs > 0) { "Engagement startup timeout must be positive" }
        require(handoverTimeoutMs > 0) { "Handover timeout must be positive" }
        require(transferStartupTimeoutMs > 0) { "Transfer startup timeout must be positive" }
        require(maxRetryAttempts >= 0) { "Max retry attempts must be non-negative" }
        require(retryDelayMs >= 0) { "Retry delay must be non-negative" }
        require(healthCheckIntervalMs > 0) { "Health check interval must be positive" }
        require(nfcKeepAliveAfterHandoverMs >= 0) { "NFC keep alive after handover must be non-negative" }
        return this
    }
}
