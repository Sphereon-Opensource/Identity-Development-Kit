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

package com.sphereon.mdoc.engagement

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

import com.sphereon.mdoc.transfer.device.BleOptions
import com.sphereon.mdoc.transfer.device.DeviceRetrievalMethodType

/**
 * Result of engagement configuration validation.
 *
 * @property isValid True if configuration is valid (no errors)
 * @property errors List of validation errors that must be fixed
 * @property warnings List of warnings about suboptimal configurations
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("EngagementConfigValidationResult", exact = true)
data class EngagementConfigValidationResult(
    val isValid: Boolean,
    val errors: List<String>,
    val warnings: List<String>
) {
    companion object {
        fun valid(warnings: List<String> = emptyList()): EngagementConfigValidationResult {
            return EngagementConfigValidationResult(isValid = true, errors = emptyList(), warnings = warnings)
        }

        fun invalid(errors: List<String>, warnings: List<String> = emptyList()): EngagementConfigValidationResult {
            return EngagementConfigValidationResult(isValid = false, errors = errors, warnings = warnings)
        }
    }

    /**
     * Format validation result as a human-readable message.
     */
    fun formatMessage(): String {
        val parts = mutableListOf<String>()

        if (errors.isNotEmpty()) {
            parts.add("Configuration Errors:")
            errors.forEach { parts.add("  $it") }
        }

        if (warnings.isNotEmpty()) {
            parts.add("Configuration Warnings:")
            warnings.forEach { parts.add(" ️  $it") }
        }

        return if (parts.isEmpty()) {
            "Configuration is valid"
        } else {
            parts.joinToString("\n")
        }
    }
}

/**
 * Validator for engagement configurations.
 * Validates that configurations are complete, consistent, and follow best practices.
 */
object EngagementConfigurationValidator {

    /**
     * Validate an engagement configuration.
     *
     * @param config The configuration to validate
     * @return Validation result with errors and warnings
     */
    fun validate(config: EngagementConfiguration): EngagementConfigValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Validate engagement methods
        validateEngagementMethods(config, errors, warnings)

        // Validate retrieval methods
        validateRetrievalMethods(config, errors, warnings)

        // Validate compatibility between engagement and retrieval
        validateCompatibility(config, errors, warnings)

        return if (errors.isEmpty()) {
            EngagementConfigValidationResult.valid(warnings)
        } else {
            EngagementConfigValidationResult.invalid(errors, warnings)
        }
    }

    private fun validateEngagementMethods(
        config: EngagementConfiguration,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        val methods = config.engagementMethods

        if (methods.isEmpty()) {
            errors.add("At least one engagement method (NFC, QR, or Reader) must be configured")
            return
        }

        // Check for QR scheme validity
        methods.filterIsInstance<QREngagementMethod>().forEach { qrMethod ->
            if (qrMethod.scheme.isBlank()) {
                errors.add("QR engagement scheme cannot be blank")
            }
            if (!qrMethod.scheme.endsWith(":")) {
                warnings.add("QR scheme '${qrMethod.scheme}' should end with ':' (e.g., 'mdoc:')")
            }
        }

        // Warn about NFC-only configurations
        if (methods.size == 1 && methods.first() is NfcEngagementMethod) {
            warnings.add("NFC-only engagement may limit compatibility. Consider adding QR as fallback.")
        }
    }

    private fun validateRetrievalMethods(
        config: EngagementConfiguration,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        val methods = config.retrievalMethods

        if (methods.isEmpty()) {
            errors.add("At least one retrieval method (BLE, NFC, WEBSITE, or OID4VP) must be configured.")
            return
        }

        // Validate BLE configurations
        methods.filter { it.type == DeviceRetrievalMethodType.BLE }.forEach { method ->
            val bleOptions = method.retrievalOptions as? BleOptions
            if (bleOptions != null) {
                if (!bleOptions.centralClientMode && !bleOptions.peripheralServerMode) {
                    errors.add("BLE retrieval must have at least one mode enabled (central client or peripheral server)")
                }

                if (bleOptions.centralClientMode && bleOptions.peripheralServerMode) {
                    warnings.add("BLE configured for both central and peripheral modes. iOS only supports one mode at a time.")
                }
            }
        }

        // Check for ISO 18013-5 compliance
        val hasBle = methods.any { it.type == DeviceRetrievalMethodType.BLE }
        val hasWebsite = methods.any { it.type == DeviceRetrievalMethodType.WEBSITE }
        val hasOid4vp = methods.any { it.type == DeviceRetrievalMethodType.OID4VP }
        if (!hasBle && !hasWebsite && !hasOid4vp) {
            warnings.add("No BLE retrieval method configured. BLE is recommended for ISO 18013-5 compliance.")
        }
    }

    private fun validateCompatibility(
        config: EngagementConfiguration,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ) {
        val engagementMethods = config.engagementMethods
        val retrievalMethods = config.retrievalMethods

        val hasQr = engagementMethods.any { it is QREngagementMethod }
        val hasNfcEngagement = engagementMethods.any { it is NfcEngagementMethod }
        val hasBle = retrievalMethods.any { it.type == DeviceRetrievalMethodType.BLE }
        val hasNfcRetrieval = retrievalMethods.any { it.type == DeviceRetrievalMethodType.NFC }
        val hasWebsite = retrievalMethods.any { it.type == DeviceRetrievalMethodType.WEBSITE }
        val hasOid4vp = retrievalMethods.any { it.type == DeviceRetrievalMethodType.OID4VP }

        // QR engagement typically uses BLE retrieval
        if (hasQr && !hasBle) {
            warnings.add("QR engagement without BLE retrieval is uncommon. Consider adding BLE retrieval method.")
        }

        // NFC engagement typically uses either BLE or NFC retrieval
        if (hasNfcEngagement && !hasBle && !hasNfcRetrieval) {
            warnings.add("NFC engagement without BLE or NFC retrieval method. Add at least one retrieval method.")
        }

        // Warn about NFC engagement with NFC retrieval (same channel for both)
        if (hasNfcEngagement && hasNfcRetrieval) {
            warnings.add("Using NFC for both engagement and retrieval. This is valid but less common than NFC engagement with BLE retrieval.")
        }

        if ((hasWebsite || hasOid4vp) && (hasBle || hasNfcRetrieval)) {
            warnings.add("Combining WEBSITE/OID4VP retrieval with BLE/NFC is uncommon. Ensure this matches the intended flow.")
        }
    }
}

/**
 * Extension function to validate a configuration and throw if invalid.
 *
 * @throws IllegalArgumentException if configuration is invalid
 */
fun EngagementConfiguration.validateOrThrow() {
    val result = EngagementConfigurationValidator.validate(this)
    require(result.isValid) {
        result.formatMessage()
    }
}

/**
 * Extension function to validate a configuration and return the result.
 *
 * @return Validation result
 */
fun EngagementConfiguration.validate(): EngagementConfigValidationResult {
    return EngagementConfigurationValidator.validate(this)
}
