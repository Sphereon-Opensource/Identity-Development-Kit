/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vc.common.vcdm

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlin.time.Instant

/** A VCDM version profile used for structural detection and J1 validation. */
interface VcdmProfile {
    val version: VcdmVersion
    val baseContext: String

    fun detect(document: JsonObject): IdkResult<VcdmDocumentKind, IdkError>

    fun validateCredential(document: JsonObject): VcdmValidationResult

    fun validatePresentation(document: JsonObject): VcdmValidationResult
}

/** The VCDM profiles supported by the J1 classifier. */
object VcdmProfiles {
    const val V1_1_CONTEXT: String = "https://www.w3.org/2018/credentials/v1"
    const val V2_0_CONTEXT: String = "https://www.w3.org/ns/credentials/v2"

    val v1_1: VcdmProfile = DefaultVcdmProfile(VcdmVersion.V1_1, V1_1_CONTEXT)
    val v2_0: VcdmProfile = DefaultVcdmProfile(VcdmVersion.V2_0, V2_0_CONTEXT)
}

private class DefaultVcdmProfile(
    override val version: VcdmVersion,
    override val baseContext: String,
) : VcdmProfile {
    override fun detect(document: JsonObject): IdkResult<VcdmDocumentKind, IdkError> {
        val prepared = VcdmShape.prepareForProfile(document, version, baseContext)
        if (prepared.isErr) return Err(prepared.error)
        return VcdmShape.detectKind(prepared.value)
    }

    override fun validateCredential(document: JsonObject): VcdmValidationResult =
        validate(document, VcdmDocumentKind.CREDENTIAL)

    override fun validatePresentation(document: JsonObject): VcdmValidationResult =
        validate(document, VcdmDocumentKind.PRESENTATION)

    private fun validate(
        document: JsonObject,
        expectedKind: VcdmDocumentKind,
    ): VcdmValidationResult {
        val prepared = VcdmShape.prepareForProfile(document, version, baseContext)
        if (prepared.isErr) return VcdmValidationResult(false, listOf(prepared.error.source as? VcdmError ?: VcdmError.ContradictoryDocumentShape("profile preparation failed")))

        val detected = VcdmShape.detectKind(prepared.value)
        if (detected.isErr) return VcdmValidationResult(false, listOf(detected.error.source as? VcdmError ?: VcdmError.UnsupportedDocumentKind(emptyList())))
        if (detected.value != expectedKind) {
            return VcdmValidationResult(
                false,
                listOf(
                    VcdmError.ContradictoryDocumentShape(
                        "document kind is ${detected.value}, expected $expectedKind",
                    ),
                ),
            )
        }
        val errors = mutableListOf<VcdmError>()
        val profileDocument = prepared.value
        validateContext(profileDocument, errors)
        validateTypes(profileDocument, expectedKind, errors)
        validateOptionalIdentifier(profileDocument, "id", errors)
        when (expectedKind) {
            VcdmDocumentKind.CREDENTIAL -> validateCredentialProperties(profileDocument, errors)
            VcdmDocumentKind.PRESENTATION -> validatePresentationProperties(profileDocument, errors)
        }
        return VcdmValidationResult(errors.isEmpty(), errors)
    }

    private fun validateContext(document: JsonObject, errors: MutableList<VcdmError>) {
        val context = document["@context"]
        val values = when (context) {
            is JsonPrimitive -> {
                if (!context.isString) {
                    errors.invalid("@context", "must be a string or a non-empty array")
                    return
                }
                listOf<JsonElement>(context)
            }
            is JsonArray -> {
                if (context.isEmpty()) {
                    errors.invalid("@context", "must not be empty")
                    return
                }
                context.toList()
            }
            else -> {
                errors.invalid("@context", "must be a string or a non-empty array")
                return
            }
        }
        val first = (values.first() as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (first != baseContext) errors.invalid("@context", "the VCDM ${version.value} base context must be first")
        values.drop(1).forEach { value ->
            when (value) {
                is JsonPrimitive -> if (!value.isString || !isAbsoluteUri(value.content)) {
                    errors.invalid("@context", "subsequent string contexts must be absolute URIs")
                }
                is JsonObject -> Unit
                else -> errors.invalid("@context", "subsequent entries must be absolute URI strings or context objects")
            }
        }
        val stringContexts = values.mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.contentOrNull }
        if (stringContexts.size != stringContexts.distinct().size) errors.invalid("@context", "must not contain duplicate URI entries")
    }

    private fun validateTypes(
        document: JsonObject,
        expectedKind: VcdmDocumentKind,
        errors: MutableList<VcdmError>,
    ) {
        val values = strictStringSet(document["type"])
        if (values == null) {
            errors.invalid("type", "must be a non-empty string or array of unique non-empty strings")
            return
        }
        val baseType = if (expectedKind == VcdmDocumentKind.CREDENTIAL) "VerifiableCredential" else "VerifiablePresentation"
        if (values.count { it == baseType } != 1) errors.invalid("type", "must contain '$baseType' exactly once")
    }

    private fun validateCredentialProperties(document: JsonObject, errors: MutableList<VcdmError>) {
        validateController(document, "issuer", required = true, errors)
        validateCredentialSubjects(document, errors)
        if (version == VcdmVersion.V1_1) {
            validateRequiredInstant(document, "issuanceDate", errors)
            val issuance = instant(document["issuanceDate"])
            val expiration = validateOptionalInstant(document, "expirationDate", errors)
            if (issuance != null && expiration != null && expiration < issuance) {
                errors.invalid("expirationDate", "must not be before issuanceDate")
            }
        } else {
            val validFrom = validateOptionalInstant(document, "validFrom", errors)
            val validUntil = validateOptionalInstant(document, "validUntil", errors)
            if (validFrom != null && validUntil != null && validUntil < validFrom) {
                errors.invalid("validUntil", "must not be before validFrom")
            }
        }
        validateTypedObjectSetIfPresent(
            document,
            "credentialStatus",
            errors,
            requireId = version == VcdmVersion.V1_1,
        )
        validateTypedObjectSetIfPresent(document, "credentialSchema", errors, requireId = true)
        validateTypedObjectSetIfPresent(
            document,
            "refreshService",
            errors,
            requireId = version == VcdmVersion.V1_1,
        )
        validateTypedObjectSetIfPresent(document, "termsOfUse", errors)
        validateTypedObjectSetIfPresent(document, "evidence", errors)
        if (version == VcdmVersion.V2_0) {
            validateLanguageValuesIfPresent(document, "name", errors)
            validateLanguageValuesIfPresent(document, "description", errors)
            validateRelatedResources(document, errors)
        }
    }

    private fun validatePresentationProperties(document: JsonObject, errors: MutableList<VcdmError>) {
        validateController(document, "holder", required = false, errors)
        validateTypedObjectSetIfPresent(document, "termsOfUse", errors)
        val credentials = document["verifiableCredential"] ?: return
        if (version == VcdmVersion.V1_1) {
            val entries = oneOrMore(credentials)
            if (entries == null || entries.any { it !is JsonObject && (it !is JsonPrimitive || !it.isString || it.content.isBlank()) }) {
                errors.invalid("verifiableCredential", "must contain one or more secured credential objects or compact strings")
            }
        } else {
            val entries = oneOrMore(credentials)
            if (entries == null || entries.any { !isValidVcdm20PresentationChild(it) }) {
                errors.invalid(
                    "verifiableCredential",
                    "VCDM 2.0 requires credential objects, enveloped credentials, or enveloped nested presentations",
                )
            }
        }
    }

    private fun isValidVcdm20PresentationChild(entry: JsonElement): Boolean {
        val child = entry as? JsonObject ?: return false
        val types = strictStringSet(child["type"]) ?: return false
        val supported = types.filter {
            it == "VerifiableCredential" ||
                it == "EnvelopedVerifiableCredential" ||
                it == "EnvelopedVerifiablePresentation"
        }
        if (supported.size != 1) return false
        return when (supported.single()) {
            "VerifiableCredential" -> {
                contextStartsWithBase(child["@context"]) &&
                    child.containsKey("issuer") &&
                    child.containsKey("credentialSubject")
            }
            "EnvelopedVerifiableCredential" -> isValidEnvelope(child, "vc")
            "EnvelopedVerifiablePresentation" -> isValidEnvelope(child, "vp")
            else -> false
        }
    }

    private fun isValidEnvelope(document: JsonObject, documentToken: String): Boolean {
        if (!contextStartsWithBase(document["@context"])) return false
        val id = (document["id"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        if (id == null || !id.startsWith("data:application/", ignoreCase = true) || !id.contains(',')) return false
        val mediaType = id.substringAfter("data:").substringBefore(',').substringBefore(';').lowercase()
        val subtype = mediaType.substringAfter("application/", missingDelimiterValue = "")
        if (subtype != documentToken && !subtype.startsWith("$documentToken+") && !subtype.startsWith("$documentToken-")) return false
        // The VCDM envelope is deliberately securing-mechanism neutral: its normative shape only
        // requires a data URL that expresses the corresponding secured document. The concrete
        // JOSE, COSE, SD-JWT, or future vc/vp media type is validated by the envelope dispatcher.
        return id.substringAfter(',').isNotBlank()
    }

    private fun contextStartsWithBase(value: JsonElement?): Boolean =
        when (value) {
            is JsonPrimitive -> value.isString && value.content == baseContext
            is JsonArray -> (value.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content == baseContext
            else -> false
        }

    private fun validateCredentialSubjects(document: JsonObject, errors: MutableList<VcdmError>) {
        val value = document["credentialSubject"]
        if (value == null) {
            errors.invalid("credentialSubject", "is required")
            return
        }
        val entries = oneOrMore(value)
        if (entries == null || entries.any { entry ->
                val subject = entry as? JsonObject ?: return@any true
                // VCDM requires one or more properties related to the subject. The optional
                // `id` is itself such a property, so an identifier-only subject is conforming;
                // only an empty object lacks a subject property.
                if (subject.isEmpty()) return@any true
                val id = subject["id"] ?: return@any false
                (id as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.let(::isAbsoluteUri) != true
            }
        ) {
            errors.invalid(
                "credentialSubject",
                "must contain one or more objects with claims and optional absolute URI ids",
            )
        }
    }

    private fun validateController(
        document: JsonObject,
        property: String,
        required: Boolean,
        errors: MutableList<VcdmError>,
    ) {
        val value = document[property]
        if (value == null) {
            if (required) errors.invalid(property, "is required")
            return
        }
        val identifier = when (value) {
            is JsonPrimitive -> value.takeIf { it.isString }?.contentOrNull
            is JsonObject -> (value["id"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            else -> null
        }
        if (identifier == null || !isAbsoluteUri(identifier)) {
            errors.invalid(property, "must be an absolute URI string or an object with an absolute URI id")
        }
    }

    private fun validateTypedObjectSetIfPresent(
        document: JsonObject,
        property: String,
        errors: MutableList<VcdmError>,
        requireId: Boolean = false,
    ) {
        val value = document[property] ?: return
        val entries = oneOrMore(value)
        if (entries == null || entries.any { entry ->
                val obj = entry as? JsonObject ?: return@any true
                if (strictStringSet(obj["type"]) == null) return@any true
                val id = obj["id"]
                if (id == null) return@any requireId
                (id as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.let(::isAbsoluteUri) != true
            }
        ) {
            errors.invalid(property, "must contain typed objects${if (requireId) " with absolute URI ids" else ""}")
        }
    }

    private fun validateLanguageValuesIfPresent(
        document: JsonObject,
        property: String,
        errors: MutableList<VcdmError>,
    ) {
        val value = document[property] ?: return
        val values = if (value is JsonArray) value.toList().takeIf { it.isNotEmpty() } else listOf(value)
        if (values == null || values.any { !isLanguageValue(it) }) {
            errors.invalid(property, "must be a string or one or more strict language value objects")
        }
    }

    private fun isLanguageValue(value: JsonElement): Boolean {
        if (value is JsonPrimitive) return value.isString
        val obj = value as? JsonObject ?: return false
        if (!obj.keys.all { it == "@value" || it == "@language" || it == "@direction" }) return false
        if ((obj["@value"] as? JsonPrimitive)?.takeIf { it.isString } == null) return false
        obj["@language"]?.let { language ->
            val tag = (language as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            if (tag.isNullOrBlank()) return false
        }
        obj["@direction"]?.let { direction ->
            val text = (direction as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            if (text != "ltr" && text != "rtl") return false
        }
        return true
    }

    private fun validateRelatedResources(document: JsonObject, errors: MutableList<VcdmError>) {
        val value = document["relatedResource"] ?: return
        val entries = oneOrMore(value)
        val ids = mutableSetOf<String>()
        val invalid = entries == null || entries.any { entry ->
            val resource = entry as? JsonObject ?: return@any true
            val id = (resource["id"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            if (id == null || !isAbsoluteUri(id) || !ids.add(id)) return@any true

            resource["mediaType"]?.let { mediaType ->
                val text = (mediaType as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
                if (text == null || !MEDIA_TYPE.matches(text)) return@any true
            }
            val digestSri = resource["digestSRI"]?.let(::strictStringSet)
            val digestMultibase = resource["digestMultibase"]?.let(::strictStringSet)
            (resource.containsKey("digestSRI") && digestSri == null) ||
                (resource.containsKey("digestMultibase") && digestMultibase == null) ||
                (digestSri == null && digestMultibase == null)
        }
        if (invalid) {
            errors.invalid(
                "relatedResource",
                "must contain objects with unique absolute URI ids and at least one valid integrity digest",
            )
        }
    }

    private fun validateOptionalIdentifier(document: JsonObject, property: String, errors: MutableList<VcdmError>) {
        val value = document[property] ?: return
        val identifier = (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        if (identifier == null || !isAbsoluteUri(identifier)) errors.invalid(property, "must be a single absolute URI")
    }

    private fun validateRequiredInstant(document: JsonObject, property: String, errors: MutableList<VcdmError>): Instant? {
        if (!document.containsKey(property)) {
            errors.invalid(property, "is required")
            return null
        }
        return validateOptionalInstant(document, property, errors)
    }

    private fun validateOptionalInstant(document: JsonObject, property: String, errors: MutableList<VcdmError>): Instant? {
        val value = document[property] ?: return null
        return instant(value) ?: run {
            errors.invalid(property, "must be an XML Schema dateTimeStamp string")
            null
        }
    }

    private fun instant(value: JsonElement?): Instant? =
        (value as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun strictStringSet(value: JsonElement?): List<String>? {
        val values = when (value) {
            is JsonPrimitive -> value.takeIf { it.isString }?.let { listOf(it.content) }
            is JsonArray -> value.map { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content ?: return null }
            else -> null
        } ?: return null
        return values.takeIf { it.isNotEmpty() && it.none(String::isBlank) && it.size == it.distinct().size }
    }

    private fun oneOrMore(value: JsonElement): List<JsonElement>? =
        when (value) {
            is JsonArray -> value.toList().takeIf { it.isNotEmpty() }
            else -> listOf(value)
        }

    private fun isAbsoluteUri(value: String): Boolean {
        if (value.isBlank() || value.any { it.isWhitespace() || it.code < 0x20 }) return false
        val colon = value.indexOf(':')
        if (colon <= 0) return false
        val scheme = value.substring(0, colon)
        return scheme.first().isLetter() && scheme.drop(1).all { it.isLetterOrDigit() || it == '+' || it == '-' || it == '.' }
    }

    private fun MutableList<VcdmError>.invalid(property: String, reason: String) {
        add(VcdmError.InvalidProperty(property, reason))
    }

    private companion object {
        val MEDIA_TYPE = Regex("^[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+(?:\\s*;.*)?$")
    }
}

internal object VcdmShape {
    fun prepareForProfile(
        document: JsonObject,
        version: VcdmVersion,
        baseContext: String,
    ): IdkResult<JsonObject, IdkError> {
        val wrappers = wrapperNames(document)
        if (wrappers.size > 1) return failure(VcdmError.ContradictoryDocumentShape("both vc and vp wrappers are present"))

        val wrapped = wrappers.singleOrNull()?.let { document[it] }
        val inner = wrapped as? JsonObject
        val contextValues = contextStrings(document["@context"]) + contextStrings(inner?.get("@context"))
        val contextError = resolveContext(contextValues)
        if (contextError != null) return failure(contextError)
        val profileDocument =
            if (version == VcdmVersion.V1_1 && inner != null) {
                inner
            } else {
                document
            }
        if (contextStrings(profileDocument["@context"]).firstOrNull() != baseContext) {
            return failure(VcdmError.ContradictoryDocumentShape("${version.value} base context must be the first @context entry"))
        }
        val selectedContext = contextValues.firstOrNull { it == VcdmProfiles.V1_1_CONTEXT || it == VcdmProfiles.V2_0_CONTEXT }
        if (selectedContext != baseContext) {
            return failure(VcdmError.ContradictoryDocumentShape("document base context does not match ${version.value}"))
        }

        if (version == VcdmVersion.V1_1) {
            if (wrappers.isEmpty()) return Ok(document)
            if (inner == null) {
                return failure(VcdmError.ContradictoryDocumentShape("VCDM 1.1 vc or vp wrapper must contain a JSON object"))
            }
            return Ok(inner)
        }

        if (wrappers.isNotEmpty()) {
            return failure(VcdmError.ContradictoryDocumentShape("VCDM 2.0 forbids vc and vp wrappers"))
        }
        return Ok(document)
    }

    fun detectKind(document: JsonObject): IdkResult<VcdmDocumentKind, IdkError> {
        val types = typeStrings(document["type"])
        val credentials = types.count { it == "VerifiableCredential" }
        val presentations = types.count { it == "VerifiablePresentation" }
        if (credentials > 0 && presentations > 0) {
            return failure(VcdmError.ContradictoryDocumentShape("both VerifiableCredential and VerifiablePresentation types are present"))
        }
        if (credentials > 1 || presentations > 1) {
            return failure(VcdmError.ContradictoryDocumentShape("a base document type occurs more than once"))
        }
        return when {
            credentials == 1 -> Ok(VcdmDocumentKind.CREDENTIAL)
            presentations == 1 -> Ok(VcdmDocumentKind.PRESENTATION)
            else -> failure(VcdmError.UnsupportedDocumentKind(types))
        }
    }

    fun contextStrings(element: JsonElement?): List<String> =
        when (element) {
            is JsonPrimitive -> if (element.isString) listOf(element.content) else emptyList()
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content }
            else -> emptyList()
        }

    fun typeStrings(element: JsonElement?): List<String> = contextStrings(element)

    private fun wrapperNames(document: JsonObject): List<String> =
        listOf("vc", "vp").filter(document::containsKey)

    private fun resolveContext(contexts: List<String>): VcdmError? {
        val recognized = contexts.filter { it == VcdmProfiles.V1_1_CONTEXT || it == VcdmProfiles.V2_0_CONTEXT }.distinct()
        return when {
            recognized.size > 1 -> VcdmError.AmbiguousVersion(recognized)
            recognized.isEmpty() && contexts.isNotEmpty() -> VcdmError.UnsupportedVersion(contexts)
            recognized.isEmpty() -> VcdmError.MissingBaseContext()
            else -> null
        }
    }

    private fun failure(error: VcdmError): IdkResult<Nothing, IdkError> = Err(IdkError.fromDTO(error))
}

/**
 * URI syntax accepted for VCDM identifiers. VCDM identifiers are absolute URI
 * references (including DID and URN identifiers), so this deliberately remains
 * platform independent and does not require a JVM URL parser.
 */
object VcdmUris {
    private val absoluteUri = Regex("^[A-Za-z][A-Za-z0-9+.-]*:[^\\s]+$")

    fun isValid(value: String): Boolean =
        value.isNotEmpty() && value == value.trim() && absoluteUri.matches(value)
}
