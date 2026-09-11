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
import com.sphereon.crypto.jose.jws.StrictCompactJws
import com.sphereon.openid.oid4vc.common.CredentialFormat
import com.sphereon.openid.oid4vc.common.PresentationFormat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.time.Instant

/** A parsed VCDM document and the protocol envelope used to secure it. */
data class VcdmClassification(
    val document: VcdmDocument,
    /** Untouched decoded JWS payload. Signature verification always targets the compact input. */
    val rawPayload: JsonObject,
    val securingMechanism: String,
    /** The credential representation format, only when [document] is a credential. */
    val credentialFormat: CredentialFormat? = null,
    /** The presentation representation format, only when [document] is a presentation. */
    val presentationFormat: PresentationFormat? = null,
    val protectedHeader: JsonObject,
) {
    init {
        when (document.kind) {
            VcdmDocumentKind.CREDENTIAL -> require(credentialFormat != null && presentationFormat == null) {
                "A VCDM credential classification must expose only credentialFormat"
            }
            VcdmDocumentKind.PRESENTATION -> require(credentialFormat == null && presentationFormat != null) {
                "A VCDM presentation classification must expose only presentationFormat"
            }
        }
    }
}

object VcdmClassifier {
    /**
     * Classifies an unsecured or Data Integrity secured VCDM JSON-LD document.
     *
     * Unlike the JWT classifier, this accepts VCDM 1.1 directly at the document root. JWT-only
     * `vc` and `vp` registered-claim envelopes are rejected so protocol-envelope rules cannot leak
     * into the JSON-LD representation.
     */
    fun classifyDocument(document: JsonObject): IdkResult<VcdmDocument, IdkError> {
        val wrappers = listOf("vc", "vp").filter(document::containsKey)
        if (wrappers.isNotEmpty()) {
            return failure(VcdmError.ContradictoryDocumentShape("bare VCDM documents forbid vc and vp JWT envelope claims"))
        }

        val contexts = VcdmShape.contextStrings(document["@context"])
        val recognized = contexts.filter { it == VcdmProfiles.V1_1_CONTEXT || it == VcdmProfiles.V2_0_CONTEXT }.distinct()
        if (recognized.size > 1) return failure(VcdmError.AmbiguousVersion(recognized))
        val version = when (recognized.singleOrNull()) {
            VcdmProfiles.V1_1_CONTEXT -> VcdmVersion.V1_1
            VcdmProfiles.V2_0_CONTEXT -> VcdmVersion.V2_0
            null -> return failure(if (contexts.isEmpty()) VcdmError.MissingBaseContext() else VcdmError.UnsupportedVersion(contexts))
            else -> return failure(VcdmError.UnsupportedVersion(contexts))
        }
        val profile = if (version == VcdmVersion.V1_1) VcdmProfiles.v1_1 else VcdmProfiles.v2_0
        val prepared = VcdmShape.prepareForProfile(document, version, profile.baseContext)
        if (prepared.isErr) return Err(prepared.error)
        val kind = VcdmShape.detectKind(prepared.value)
        if (kind.isErr) return Err(kind.error)
        return Ok(VcdmDocument(version, kind.value, prepared.value))
    }

    fun classifyCompactJws(value: String): IdkResult<VcdmClassification, IdkError> {
        val parsed = StrictCompactJws.parse(value)
        if (parsed.isErr) return Err(parsed.error)

        val algorithmElement = parsed.value.protectedHeader["alg"]
        val algorithm = (algorithmElement as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (algorithm.isNullOrBlank() || algorithm.equals("none", ignoreCase = true)) {
            return failure(VcdmError.UnsupportedSecuringAlgorithm(algorithm))
        }

        val payload = parsed.value.payload
        val wrappers = listOf("vc", "vp").filter(payload::containsKey)
        if (wrappers.size > 1) return failure(VcdmError.ContradictoryDocumentShape("both vc and vp wrappers are present"))
        val wrapped = wrappers.singleOrNull()?.let { payload[it] as? JsonObject }
        val contexts = VcdmShape.contextStrings(payload["@context"]) + VcdmShape.contextStrings(wrapped?.get("@context"))
        val recognized = contexts.filter { it == VcdmProfiles.V1_1_CONTEXT || it == VcdmProfiles.V2_0_CONTEXT }.distinct()
        if (recognized.size > 1) return failure(VcdmError.AmbiguousVersion(recognized))
        val version = when (recognized.singleOrNull()) {
            VcdmProfiles.V1_1_CONTEXT -> VcdmVersion.V1_1
            VcdmProfiles.V2_0_CONTEXT -> VcdmVersion.V2_0
            null -> return failure(if (contexts.isEmpty()) VcdmError.MissingBaseContext() else VcdmError.UnsupportedVersion(contexts))
            else -> return failure(VcdmError.UnsupportedVersion(contexts))
        }
        val profile = if (version == VcdmVersion.V1_1) VcdmProfiles.v1_1 else VcdmProfiles.v2_0
        if (version == VcdmVersion.V1_1 && wrappers.isEmpty()) {
            return failure(VcdmError.ContradictoryDocumentShape("VCDM 1.1 requires exactly one vc or vp wrapper"))
        }
        if (version == VcdmVersion.V1_1) {
            validateVcdm11JoseHeader(parsed.value.protectedHeader).getOrElse { return Err(it) }
        }
        val prepared = VcdmShape.prepareForProfile(payload, version, profile.baseContext)
        if (prepared.isErr) return Err(prepared.error)
        val kind = VcdmShape.detectKind(prepared.value)
        if (kind.isErr) return Err(kind.error)
        if (version == VcdmVersion.V2_0) {
            validateVcdm20JoseHeader(parsed.value.protectedHeader, kind.value).getOrElse { return Err(it) }
        }
        val wrapperKind = wrappers.singleOrNull()?.let { if (it == "vc") VcdmDocumentKind.CREDENTIAL else VcdmDocumentKind.PRESENTATION }
        if (wrapperKind != null && wrapperKind != kind.value) {
            return failure(VcdmError.ContradictoryDocumentShape("$wrappers wrapper does not match document type"))
        }
        val semanticDocument =
            if (version == VcdmVersion.V1_1) {
                normalizeVcdm11Jwt(payload, prepared.value, kind.value).getOrElse { return Err(it) }
            } else {
                validateVcdm20JwtTemporalClaims(payload).getOrElse { return Err(it) }
                validateVcdm20RegisteredClaims(payload, kind.value).getOrElse { return Err(it) }
                prepared.value
            }
        val credentialFormat = when (version to kind.value) {
            VcdmVersion.V1_1 to VcdmDocumentKind.CREDENTIAL -> CredentialFormat.JWT_VC_JSON
            VcdmVersion.V2_0 to VcdmDocumentKind.CREDENTIAL -> CredentialFormat.JWT_VC_JSON_LD
            else -> null
        }
        val presentationFormat = when (kind.value) {
            VcdmDocumentKind.PRESENTATION -> PresentationFormat.JWT_VP_JSON
            VcdmDocumentKind.CREDENTIAL -> null
        }
        return Ok(
            VcdmClassification(
                document = VcdmDocument(version, kind.value, semanticDocument),
                rawPayload = payload,
                securingMechanism = "compact-jws",
                credentialFormat = credentialFormat,
                presentationFormat = presentationFormat,
                protectedHeader = parsed.value.protectedHeader,
            ),
        )
    }

    private fun failure(error: VcdmError): IdkResult<Nothing, IdkError> = Err(IdkError.fromDTO(error))

    private fun validateVcdm11JoseHeader(header: JsonObject): IdkResult<Unit, IdkError> {
        val typ = header["typ"] ?: return Ok(Unit)
        val value = (typ as? JsonPrimitive)?.takeIf { it.isString }?.content
        return if (value != null && value.equals("JWT", ignoreCase = true)) {
            Ok(Unit)
        } else {
            failure(VcdmError.InvalidJoseHeader("typ", "VCDM 1.1 requires 'JWT' when typ is present"))
        }
    }

    /**
     * `typ` and `cty` are optional in VC-JOSE-COSE, but a present value identifies the secured
     * document and therefore cannot contradict its VCDM shape. Media-type tokens are compared
     * case-insensitively as required for media types; arbitrary or cross-kind values fail closed.
     */
    private fun validateVcdm20JoseHeader(
        header: JsonObject,
        kind: VcdmDocumentKind,
    ): IdkResult<Unit, IdkError> {
        val token = if (kind == VcdmDocumentKind.CREDENTIAL) "vc" else "vp"
        val expectedTyp = "$token+jwt"

        header["typ"]?.let { element ->
            val value = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (value == null || !value.equals(expectedTyp, ignoreCase = true)) {
                return failure(VcdmError.InvalidJoseHeader("typ", "VCDM 2.0 $kind requires '$expectedTyp' when typ is present"))
            }
        }
        header["cty"]?.let { element ->
            val value = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (value == null || !value.equals(token, ignoreCase = true)) {
                return failure(VcdmError.InvalidJoseHeader("cty", "VCDM 2.0 $kind requires '$token' when cty is present"))
            }
        }
        return Ok(Unit)
    }

    /**
     * VCDM 2.0 uses the credential or presentation directly as the JOSE payload. Registered JWT
     * claims therefore remain ordinary payload members, but whenever both forms are present they
     * must identify the same semantic entity. No transformation is applied to the VCDM 2.0 JSON.
     */
    private fun validateVcdm20RegisteredClaims(
        payload: JsonObject,
        kind: VcdmDocumentKind,
    ): IdkResult<Unit, IdkError> {
        payload["iss"]?.let { element ->
            val issuer = stringClaim(element, "iss").getOrElse { return Err(it) }
            val property = if (kind == VcdmDocumentKind.CREDENTIAL) "issuer" else "holder"
            val controller = payload[property]
            if (controller != null && controllerIdentifier(controller) != issuer) {
                return failure(VcdmError.InconsistentJwtClaim("iss", property))
            }
        }
        payload["jti"]?.let { element ->
            val jti = stringClaim(element, "jti").getOrElse { return Err(it) }
            payload["id"]?.let { id ->
                val semanticId = (id as? JsonPrimitive)?.takeIf { it.isString }?.content
                if (semanticId != jti) return failure(VcdmError.InconsistentJwtClaim("jti", "id"))
            }
        }
        payload["sub"]?.let { element ->
            if (kind != VcdmDocumentKind.CREDENTIAL) {
                return failure(VcdmError.InvalidJwtClaim("sub", "is not defined for verifiable presentations"))
            }
            val subject = stringClaim(element, "sub").getOrElse { return Err(it) }
            val subjectEntries = when (val credentialSubject = payload["credentialSubject"]) {
                is JsonObject -> listOf(credentialSubject)
                is JsonArray -> credentialSubject.map { it as? JsonObject ?: return failure(VcdmError.InvalidJwtClaim("sub", "requires object credentialSubject entries")) }
                else -> return failure(VcdmError.InvalidJwtClaim("sub", "requires credentialSubject"))
            }
            if (subjectEntries.size != 1) {
                return failure(VcdmError.InvalidJwtClaim("sub", "cannot represent multiple credential subjects"))
            }
            val semanticSubject = (subjectEntries.single()["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            if (semanticSubject != subject) {
                return failure(VcdmError.InconsistentJwtClaim("sub", "credentialSubject.id"))
            }
        }
        if (kind == VcdmDocumentKind.PRESENTATION && payload.containsKey("aud")) {
            validateAudience(payload["aud"]).getOrElse { return Err(it) }
        }
        payload["nonce"]?.let { stringClaim(it, "nonce").getOrElse { return Err(it) } }
        return Ok(Unit)
    }

    /**
     * Applies the mandatory VCDM 1.1 JWT decoding transform while retaining [payload] separately.
     * Duplicate inner properties are accepted only when they are semantically equal to the
     * corresponding registered JWT claim.
     */
    private fun normalizeVcdm11Jwt(
        payload: JsonObject,
        wrappedDocument: JsonObject,
        kind: VcdmDocumentKind,
    ): IdkResult<JsonObject, IdkError> {
        val normalized = wrappedDocument.toMutableMap()

        val issuer = requiredStringClaim(payload, "iss").getOrElse { return Err(it) }
        val controllerProperty = if (kind == VcdmDocumentKind.CREDENTIAL) "issuer" else "holder"
        mergeController(normalized, controllerProperty, issuer).getOrElse { return Err(it) }

        payload["jti"]?.let { element ->
            val jti = stringClaim(element, "jti").getOrElse { return Err(it) }
            mergeString(normalized, "id", jti, "jti").getOrElse { return Err(it) }
        }

        payload["nbf"]?.let { element ->
            val nbf = numericDate(element, "nbf").getOrElse { return Err(it) }
            mergeInstant(normalized, "issuanceDate", nbf, "nbf").getOrElse { return Err(it) }
        }
        payload["exp"]?.let { element ->
            val expiration = numericDate(element, "exp").getOrElse { return Err(it) }
            mergeInstant(normalized, "expirationDate", expiration, "exp").getOrElse { return Err(it) }
        }
        payload["iat"]?.let { numericDate(it, "iat").getOrElse { return Err(it) } }

        if (normalized.containsKey("id") && !payload.containsKey("jti")) {
            return failure(VcdmError.InvalidJwtClaim("jti", "is required to represent id"))
        }
        if (kind == VcdmDocumentKind.CREDENTIAL) {
            if (!payload.containsKey("nbf")) {
                return failure(VcdmError.InvalidJwtClaim("nbf", "is required to represent issuanceDate"))
            }
            if (normalized.containsKey("expirationDate") && !payload.containsKey("exp")) {
                return failure(VcdmError.InvalidJwtClaim("exp", "is required to represent expirationDate"))
            }
            // VCDM 1.1's registered `sub` claim is single-valued. A credential with multiple
            // subjects cannot be represented faithfully, so reject it rather than dropping ids.
            val subjectEntries = when (val credentialSubject = normalized["credentialSubject"]) {
                is JsonObject -> listOf(credentialSubject)
                is JsonArray -> credentialSubject.map {
                    it as? JsonObject
                        ?: return failure(VcdmError.InvalidJwtClaim("sub", "requires object credentialSubject entries"))
                }
                else -> emptyList()
            }
            if (subjectEntries.size > 1) {
                return failure(VcdmError.InvalidJwtClaim("sub", "cannot represent multiple credential subjects"))
            }
            val hasSemanticSubjectId = subjectEntries.singleOrNull()?.containsKey("id") == true
            if (hasSemanticSubjectId && !payload.containsKey("sub")) {
                return failure(VcdmError.InvalidJwtClaim("sub", "is required to represent credentialSubject.id"))
            }
            payload["sub"]?.let { element ->
                val subject = stringClaim(element, "sub").getOrElse { return Err(it) }
                mergeCredentialSubjectId(normalized, subject).getOrElse { return Err(it) }
            }
        } else {
            if (payload.containsKey("sub")) {
                return failure(VcdmError.InvalidJwtClaim("sub", "is not defined for verifiable presentations"))
            }
            validateAudience(payload["aud"]).getOrElse { return Err(it) }
        }

        return Ok(JsonObject(normalized))
    }

    private fun requiredStringClaim(payload: JsonObject, claim: String): IdkResult<String, IdkError> {
        val value = payload[claim]
            ?: return failure(VcdmError.InvalidJwtClaim(claim, "is required"))
        return stringClaim(value, claim)
    }

    private fun stringClaim(element: JsonElement, claim: String): IdkResult<String, IdkError> {
        val value = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
        return if (value.isNullOrBlank()) {
            failure(VcdmError.InvalidJwtClaim(claim, "must be a non-empty string"))
        } else {
            Ok(value)
        }
    }

    private fun numericDate(element: JsonElement, claim: String): IdkResult<Instant, IdkError> {
        val primitive = element as? JsonPrimitive
        val seconds = primitive?.takeUnless { it.isString }?.doubleOrNull
        if (seconds == null || !seconds.isFinite()) {
            return failure(VcdmError.InvalidJwtClaim(claim, "must be a finite JSON NumericDate"))
        }
        val wholeSeconds = floor(seconds)
        if (wholeSeconds < Long.MIN_VALUE.toDouble() || wholeSeconds > Long.MAX_VALUE.toDouble()) {
            return failure(VcdmError.InvalidJwtClaim(claim, "is outside the supported instant range"))
        }
        var epochSeconds = wholeSeconds.toLong()
        var nanoseconds = ((seconds - wholeSeconds) * NANOS_PER_SECOND).roundToLong()
        if (nanoseconds == NANOS_PER_SECOND) {
            if (epochSeconds == Long.MAX_VALUE) {
                return failure(VcdmError.InvalidJwtClaim(claim, "is outside the supported instant range"))
            }
            epochSeconds += 1
            nanoseconds = 0
        }
        return runCatching { Instant.fromEpochSeconds(epochSeconds, nanoseconds) }
            .fold(
                onSuccess = { Ok(it) },
                onFailure = { failure(VcdmError.InvalidJwtClaim(claim, "is outside the supported instant range")) },
            )
    }

    private fun mergeController(
        normalized: MutableMap<String, JsonElement>,
        property: String,
        claimValue: String,
    ): IdkResult<Unit, IdkError> {
        val current = normalized[property]
        if (current == null) {
            normalized[property] = JsonPrimitive(claimValue)
            return Ok(Unit)
        }
        val currentId = controllerIdentifier(current)
        return if (currentId == claimValue) Ok(Unit)
        else failure(VcdmError.InconsistentJwtClaim("iss", property))
    }

    private fun controllerIdentifier(element: JsonElement): String? =
        when (element) {
            is JsonPrimitive -> element.takeIf { it.isString }?.content
            is JsonObject -> (element["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
            else -> null
        }

    private fun mergeString(
        normalized: MutableMap<String, JsonElement>,
        property: String,
        claimValue: String,
        claim: String,
    ): IdkResult<Unit, IdkError> {
        val current = normalized[property]
        if (current == null) {
            normalized[property] = JsonPrimitive(claimValue)
            return Ok(Unit)
        }
        val currentValue = (current as? JsonPrimitive)?.takeIf { it.isString }?.content
        return if (currentValue == claimValue) Ok(Unit)
        else failure(VcdmError.InconsistentJwtClaim(claim, property))
    }

    private fun mergeInstant(
        normalized: MutableMap<String, JsonElement>,
        property: String,
        claimValue: Instant,
        claim: String,
    ): IdkResult<Unit, IdkError> {
        val current = normalized[property]
        if (current == null) {
            normalized[property] = JsonPrimitive(claimValue.toString())
            return Ok(Unit)
        }
        val currentInstant =
            (current as? JsonPrimitive)
                ?.takeIf { it.isString }
                ?.content
                ?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return if (currentInstant == claimValue) Ok(Unit)
        else failure(VcdmError.InconsistentJwtClaim(claim, property))
    }

    private fun mergeCredentialSubjectId(
        normalized: MutableMap<String, JsonElement>,
        subjectId: String,
    ): IdkResult<Unit, IdkError> {
        val subject = normalized["credentialSubject"]
            ?: return failure(VcdmError.InvalidJwtClaim("sub", "requires credentialSubject"))
        val entries = when (subject) {
            is JsonObject -> listOf(subject)
            is JsonArray -> subject.map {
                it as? JsonObject
                    ?: return failure(VcdmError.InvalidJwtClaim("sub", "requires object credentialSubject entries"))
            }
            else -> return failure(VcdmError.InvalidJwtClaim("sub", "requires an object credentialSubject"))
        }
        if (entries.size != 1) {
            return failure(VcdmError.InvalidJwtClaim("sub", "cannot represent multiple credential subjects"))
        }
        val existingId = (entries.single()["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (entries.single().containsKey("id") && existingId != subjectId) {
            return failure(VcdmError.InconsistentJwtClaim("sub", "credentialSubject.id"))
        }
        val merged = JsonObject(entries.single() + ("id" to JsonPrimitive(subjectId)))
        normalized["credentialSubject"] = if (subject is JsonArray) JsonArray(listOf(merged)) else merged
        return Ok(Unit)
    }

    private fun validateAudience(element: JsonElement?): IdkResult<Unit, IdkError> {
        if (element == null) return failure(VcdmError.InvalidJwtClaim("aud", "is required for a verifiable presentation"))
        val audiences = when (element) {
            is JsonPrimitive -> element.takeIf { it.isString }?.content?.let(::listOf)
            is JsonArray -> element.map { (it as? JsonPrimitive)?.takeIf { value -> value.isString }?.content ?: return failure(VcdmError.InvalidJwtClaim("aud", "must contain only strings")) }
            else -> null
        }
        return if (audiences.isNullOrEmpty() || audiences.any(String::isBlank) || audiences.size != audiences.distinct().size) {
            failure(VcdmError.InvalidJwtClaim("aud", "must be a non-empty string or array of unique non-empty strings"))
        } else {
            Ok(Unit)
        }
    }

    private const val NANOS_PER_SECOND: Long = 1_000_000_000L
}

/**
 * Validates VCDM 2.0 JWT temporal claims without conflating the two timelines.
 *
 * VCDM `validFrom`/`validUntil` describe the secured data, while JWT `iat`/`nbf`/`exp` describe
 * token timing. VC-JOSE-COSE does not define these as aliases. NumericDate claims are therefore
 * shape-checked and their expiration ordering is checked independently of VCDM validity.
 */
fun validateVcdm20JwtTemporalClaims(payload: JsonObject): IdkResult<Unit, IdkError> {
    val validFrom = payload["validFrom"]?.let {
        parseVcdm20DateTime(it, "validFrom").getOrElse { return Err(it) }
    }
    val validUntil = payload["validUntil"]?.let {
        parseVcdm20DateTime(it, "validUntil").getOrElse { return Err(it) }
    }
    if (validFrom != null && validUntil != null && validUntil < validFrom) {
        return Err(IdkError.fromDTO(VcdmError.InvalidProperty("validUntil", "must not be before validFrom")))
    }

    val iat = payload["iat"]?.let {
        parseVcdm20NumericDate(it, "iat").getOrElse { return Err(it) }
    }
    val nbf = payload["nbf"]?.let {
        parseVcdm20NumericDate(it, "nbf").getOrElse { return Err(it) }
    }
    val exp = payload["exp"]?.let {
        parseVcdm20NumericDate(it, "exp").getOrElse { return Err(it) }
    }
    if (iat != null && exp != null && exp < iat) {
        return Err(IdkError.fromDTO(VcdmError.InvalidJwtClaim("exp", "must not be before iat")))
    }
    if (nbf != null && exp != null && exp < nbf) {
        return Err(IdkError.fromDTO(VcdmError.InvalidJwtClaim("exp", "must not be before nbf")))
    }
    return Ok(Unit)
}

private fun parseVcdm20DateTime(element: JsonElement, property: String): IdkResult<Instant, IdkError> {
    val value = (element as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: return Err(IdkError.fromDTO(VcdmError.InvalidProperty(property, "must be an ISO-8601 date-time string")))
    return runCatching { Instant.parse(value) }
        .fold(
            onSuccess = { Ok(it) },
            onFailure = {
                Err(IdkError.fromDTO(VcdmError.InvalidProperty(property, "must be a valid ISO-8601 date-time")))
            },
        )
}

private fun parseVcdm20NumericDate(element: JsonElement, claim: String): IdkResult<Instant, IdkError> {
    val primitive = element as? JsonPrimitive
    val seconds = primitive?.takeUnless { it.isString }?.doubleOrNull
    if (seconds == null || !seconds.isFinite()) {
        return Err(IdkError.fromDTO(VcdmError.InvalidJwtClaim(claim, "must be a finite JSON NumericDate")))
    }
    val wholeSeconds = floor(seconds)
    if (wholeSeconds < Long.MIN_VALUE.toDouble() || wholeSeconds > Long.MAX_VALUE.toDouble()) {
        return Err(IdkError.fromDTO(VcdmError.InvalidJwtClaim(claim, "is outside the supported instant range")))
    }
    var epochSeconds = wholeSeconds.toLong()
    var nanoseconds = ((seconds - wholeSeconds) * NANOS_PER_SECOND).roundToLong()
    if (nanoseconds == NANOS_PER_SECOND) {
        if (epochSeconds == Long.MAX_VALUE) {
            return Err(IdkError.fromDTO(VcdmError.InvalidJwtClaim(claim, "is outside the supported instant range")))
        }
        epochSeconds += 1
        nanoseconds = 0
    }
    return runCatching { Instant.fromEpochSeconds(epochSeconds, nanoseconds) }
        .fold(
            onSuccess = { Ok(it) },
            onFailure = {
                Err(IdkError.fromDTO(VcdmError.InvalidJwtClaim(claim, "is outside the supported instant range")))
            },
        )
}

private const val NANOS_PER_SECOND: Long = 1_000_000_000L
