package com.sphereon.core.api.service.contract

import kotlinx.serialization.Serializable

/**
 * Metadata overlay for input/output fields.
 *
 * Layered on top of the structural schema derived from kotlinx.serialization
 * [SerialDescriptor]. Only fields that need extra metadata (labels, sensitivity,
 * policy mapping) are listed here — unlisted fields inherit defaults from the
 * serialization descriptor.
 *
 * i18n keys are derived by convention: `cmd.{commandId}.field.{fieldName}.label`
 * and `cmd.{commandId}.field.{fieldName}.description`. EDK's TranslationService
 * resolves these at runtime.
 */
@Serializable
data class SchemaOverlay(
    val fields: Map<String, FieldOverlay> = emptyMap(),
) {
    companion object {
        val EMPTY = SchemaOverlay()
    }
}

/**
 * Metadata overlay for a single field.
 *
 * [label] and [description] are default fallback strings.
 * [labelKey] and [descriptionKey] are explicit i18n keys for EDK's TranslationService.
 * When not set, i18n keys are derived by convention: `cmd.{commandId}.field.{fieldName}.label` / `.description`.
 * Explicit keys override the convention (useful for shared keys across commands or non-standard naming).
 */
@Serializable
data class FieldOverlay(
    /** Human-readable label (workflow UI, MCP tool parameter label). Default fallback. */
    val label: String? = null,
    /** Human-readable description (workflow UI, MCP tool parameter docs). Default fallback. */
    val description: String? = null,
    /** Explicit i18n key for the label. Null = derive from convention. */
    val labelKey: String? = null,
    /** Explicit i18n key for the description. Null = derive from convention. */
    val descriptionKey: String? = null,
    /** Data sensitivity classification (audit redaction, data residency) */
    val sensitivity: SensitivityClassification? = null,
    /** Policy attribute mapping. Null = operational (not policy-relevant). */
    val policyMapping: PolicyFieldMapping? = null,
    /** Example value for documentation and MCP tool hints */
    val example: String? = null,
)

/**
 * Maps an input/output field to a policy resource attribute.
 */
@Serializable
data class PolicyFieldMapping(
    /** Policy attribute name (e.g., "provider_id", "max_lifetime_days") */
    val attributeName: String,
    /** Which resource type this attribute belongs to. Null = primary resource from command's resourceTarget. */
    val resourceType: String? = null,
    /** Classification: identifier vs constraint */
    val kind: AttributeKind = AttributeKind.IDENTIFIER,
)

/**
 * Data sensitivity classification aligned with ISO 27001 information classification
 * and GDPR data categories.
 *
 * Declared values are defaults — EDK config can override per field, per tenant.
 */
@Serializable
enum class SensitivityClassification {
    /** Non-sensitive, safe in logs and audit trails */
    PUBLIC,

    /** Standard operational data, no special handling */
    INTERNAL,

    /** Personal data (GDPR Art. 4) — name, email, phone. Redacted in audit. */
    CONFIDENTIAL,

    /** Special category data (GDPR Art. 9) — biometrics, health, racial/ethnic. Extra protection. */
    RESTRICTED,

    /** Government-issued identifiers (national ID, passport, SSN). Strongest controls. */
    REGULATED,
}

// ========== DSL ==========

fun schemaOverlay(block: SchemaOverlayBuilder.() -> Unit): SchemaOverlay = SchemaOverlayBuilder().apply(block).build()

class SchemaOverlayBuilder {
    private val fields = mutableMapOf<String, FieldOverlay>()

    operator fun String.invoke(block: FieldOverlayBuilder.() -> Unit) {
        fields[this] = FieldOverlayBuilder().apply(block).build()
    }

    fun build() = SchemaOverlay(fields)
}

class FieldOverlayBuilder {
    private var label: String? = null
    private var description: String? = null
    private var labelKey: String? = null
    private var descriptionKey: String? = null
    private var sensitivity: SensitivityClassification? = null
    private var policyMapping: PolicyFieldMapping? = null
    private var example: String? = null

    fun label(l: String) {
        label = l
    }

    fun description(d: String) {
        description = d
    }

    fun labelKey(key: String) {
        labelKey = key
    }

    fun descriptionKey(key: String) {
        descriptionKey = key
    }

    fun confidential() {
        sensitivity = SensitivityClassification.CONFIDENTIAL
    }

    fun restricted() {
        sensitivity = SensitivityClassification.RESTRICTED
    }

    fun regulated() {
        sensitivity = SensitivityClassification.REGULATED
    }

    /** Biometric data (GDPR Art. 9). Maps to RESTRICTED. */
    fun biometric() {
        sensitivity = SensitivityClassification.RESTRICTED
    }

    /** Genetic data (GDPR Art. 9). Maps to RESTRICTED. */
    fun genetic() {
        sensitivity = SensitivityClassification.RESTRICTED
    }

    /** Health data (GDPR Art. 9). Maps to RESTRICTED. */
    fun healthData() {
        sensitivity = SensitivityClassification.RESTRICTED
    }

    fun example(e: String) {
        example = e
    }

    fun identifier(
        attributeName: String,
        resource: String? = null,
    ) {
        policyMapping = PolicyFieldMapping(attributeName, resource, AttributeKind.IDENTIFIER)
    }

    fun constraint(attributeName: String) {
        policyMapping = PolicyFieldMapping(attributeName, null, AttributeKind.CONSTRAINT)
    }

    fun build() = FieldOverlay(label, description, labelKey, descriptionKey, sensitivity, policyMapping, example)
}
