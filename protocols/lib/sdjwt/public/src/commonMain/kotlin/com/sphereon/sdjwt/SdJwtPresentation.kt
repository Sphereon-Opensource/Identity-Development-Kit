/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.sdjwt

import com.sphereon.core.api.encodeToBase64Url
import com.sphereon.crypto.core.generic.DigestAlg
import com.sphereon.crypto.core.generic.hash
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Standards-owned SD-JWT presentation preparation.
 *
 * Key custody adapters use this object to select disclosures and construct the RFC 9901
 * KB-JWT signing input. They remain responsible only for signing that input with the selected
 * key. A null [disclosurePaths] preserves the general SD-JWT API's "disclose all" behavior;
 * an empty list deliberately discloses no selectively disclosable claims.
 */
object SdJwtPresentation {
    data class Selection(
        val presentationWithoutKeyBinding: String,
        val disclosedClaims: List<String>,
        val digestAlgorithm: DigestAlg,
    )

    data class KeyBindingInput(
        val presentationWithoutKeyBinding: String,
        val protectedHeader: JsonObject,
        val payload: JsonObject,
        val signingInput: ByteArray,
    ) {
        fun complete(signature: ByteArray): String =
            "$presentationWithoutKeyBinding${signingInput.decodeToString()}.${signature.encodeToBase64Url()}"
    }

    fun select(
        compact: String,
        disclosurePaths: List<List<JsonElement>>? = null,
        disclosureSelection: SdMap? = null,
    ): Selection {
        val sdJwt = SdJwtCodec.parse(compact).getOrElse { throw IllegalArgumentException(it.message.toString()) }
        val selected =
            when {
                disclosurePaths != null -> selectDisclosures(sdJwt, disclosurePaths)
                disclosureSelection != null ->
                    sdJwt.disclosures.filter { disclosure ->
                        disclosure.key?.let { disclosureSelection[it]?.sd } == true
                    }
                else -> sdJwt.disclosures
            }
        val presentation =
            buildString {
                append(sdJwt.jwt.value)
                selected.forEach {
                    append(SdJwt.SEPARATOR)
                    append(it.encoded)
                }
                append(SdJwt.SEPARATOR)
            }
        return Selection(
            presentationWithoutKeyBinding = presentation,
            disclosedClaims = selected.mapNotNull(Disclosure::key).distinct(),
            digestAlgorithm = digestAlgorithm(sdJwt.payload.undisclosedPayload),
        )
    }

    /**
     * Returns the first disclosure-path option that the SD-JWT can satisfy. This primitive keeps
     * credential-structure evaluation in the SD-JWT module; protocol layers own option ordering.
     */
    fun firstSatisfiableDisclosurePaths(
        compact: String,
        options: List<List<List<JsonElement>>>,
    ): List<List<JsonElement>> {
        require(options.isNotEmpty()) { "At least one disclosure-path option is required" }
        var lastFailure: Throwable? = null
        options.forEach { paths ->
            runCatching { select(compact = compact, disclosurePaths = paths) }
                .onSuccess { return paths }
                .onFailure { lastFailure = it }
        }
        throw IllegalArgumentException(
            "SD-JWT cannot satisfy any requested claim-set option",
            lastFailure,
        )
    }

    suspend fun keyBindingInput(
        selection: Selection,
        audience: String,
        nonce: String,
        algorithm: String,
        issuedAtEpochSeconds: Long,
    ): KeyBindingInput {
        val sdHash =
            hash(
                dataInput = selection.presentationWithoutKeyBinding.encodeToByteArray(),
                digestAlgorithm = selection.digestAlgorithm,
            ).encodeToBase64Url()
        val header =
            buildJsonObject {
                put("alg", algorithm)
                put("typ", "kb+jwt")
            }
        val payload =
            buildJsonObject {
                put("aud", audience)
                put("nonce", nonce)
                put("iat", issuedAtEpochSeconds)
                put("sd_hash", sdHash)
            }
        val encodedHeader = Json.encodeToString(JsonObject.serializer(), header).encodeToByteArray().encodeToBase64Url()
        val encodedPayload = Json.encodeToString(JsonObject.serializer(), payload).encodeToByteArray().encodeToBase64Url()
        val compactSigningInput = "$encodedHeader.$encodedPayload"
        return KeyBindingInput(
            presentationWithoutKeyBinding = selection.presentationWithoutKeyBinding,
            protectedHeader = header,
            payload = payload,
            signingInput = compactSigningInput.encodeToByteArray(),
        )
    }

    private fun selectDisclosures(
        sdJwt: SdJwtCompact,
        paths: List<List<JsonElement>>,
    ): List<Disclosure> {
        val selectedDigests = linkedSetOf<String>()
        paths.forEach { path ->
            require(path.isNotEmpty()) { "SD-JWT disclosure path must not be empty" }
            selectPath(
                current = sdJwt.payload.undisclosedPayload,
                path = path,
                offset = 0,
                disclosures = sdJwt.payload.digestedDisclosures,
                selectedDigests = selectedDigests,
            )
        }
        return sdJwt.disclosures.filter { it.digest in selectedDigests }
    }

    private fun selectPath(
        current: JsonElement,
        path: List<JsonElement>,
        offset: Int,
        disclosures: Map<String, Disclosure>,
        selectedDigests: MutableSet<String>,
    ) {
        if (offset == path.size) {
            collectNestedDisclosures(current, disclosures, selectedDigests)
            return
        }
        when (current) {
            is JsonObject -> {
                val name = (path[offset] as? JsonPrimitive)?.contentOrNull
                    ?: throw IllegalArgumentException("Object claim path component at index $offset must be a string")
                current[name]?.let { value ->
                    selectPath(value, path, offset + 1, disclosures, selectedDigests)
                    return
                }
                val disclosureEntry =
                    (current[SdJwt.SD_CLAIM] as? JsonArray)
                        .orEmpty()
                        .mapNotNull { it as? JsonPrimitive }
                        .mapNotNull { digest -> disclosures[digest.content]?.let { digest.content to it } }
                        .firstOrNull { (_, disclosure) -> disclosure.key == name }
                    ?: throw IllegalArgumentException("SD-JWT does not contain requested claim path component '$name'")
                selectedDigests += disclosureEntry.first
                selectPath(disclosureEntry.second.value, path, offset + 1, disclosures, selectedDigests)
            }

            is JsonArray -> {
                val component = path[offset]
                val indexes =
                    when {
                        component is JsonNull -> current.indices.toList()
                        component is JsonPrimitive && component.longOrNull != null -> {
                            val index = component.longOrNull!!
                            require(index in 0..Int.MAX_VALUE.toLong()) { "Array claim path index must be non-negative" }
                            listOf(index.toInt())
                        }
                        else -> throw IllegalArgumentException("Array claim path component at index $offset must be null or a non-negative integer")
                    }
                indexes.forEach { index ->
                    val element = current.getOrNull(index)
                        ?: throw IllegalArgumentException("Array claim path index $index is outside the credential")
                    val digest =
                        (element as? JsonObject)
                            ?.takeIf { it.size == 1 }
                            ?.get(SdJwt.SD_ARRAY_ELEMENT_CLAIM)
                            ?.let { it as? JsonPrimitive }
                            ?.contentOrNull
                    if (digest == null) {
                        selectPath(element, path, offset + 1, disclosures, selectedDigests)
                    } else {
                        val disclosure = disclosures[digest]
                            ?: throw IllegalArgumentException("SD-JWT array disclosure '$digest' is missing")
                        selectedDigests += digest
                        selectPath(disclosure.value, path, offset + 1, disclosures, selectedDigests)
                    }
                }
            }

            else -> throw IllegalArgumentException("Claim path continues beyond a scalar credential value")
        }
    }

    private fun collectNestedDisclosures(
        element: JsonElement,
        disclosures: Map<String, Disclosure>,
        selectedDigests: MutableSet<String>,
    ) {
        when (element) {
            is JsonObject -> {
                (element[SdJwt.SD_CLAIM] as? JsonArray).orEmpty().forEach { digestElement ->
                    val digest = digestElement.jsonPrimitive.content
                    disclosures[digest]?.let { disclosure ->
                        selectedDigests += digest
                        collectNestedDisclosures(disclosure.value, disclosures, selectedDigests)
                    }
                }
                element.filterKeys { it != SdJwt.SD_CLAIM }.values.forEach {
                    collectNestedDisclosures(it, disclosures, selectedDigests)
                }
            }

            is JsonArray -> element.forEach { arrayElement ->
                val digest =
                    (arrayElement as? JsonObject)
                        ?.takeIf { it.size == 1 }
                        ?.get(SdJwt.SD_ARRAY_ELEMENT_CLAIM)
                        ?.let { it as? JsonPrimitive }
                        ?.contentOrNull
                if (digest == null) {
                    collectNestedDisclosures(arrayElement, disclosures, selectedDigests)
                } else {
                    disclosures[digest]?.let { disclosure ->
                        selectedDigests += digest
                        collectNestedDisclosures(disclosure.value, disclosures, selectedDigests)
                    }
                }
            }

            else -> Unit
        }
    }

    private fun digestAlgorithm(payload: JsonObject): DigestAlg {
        val id = payload[SdJwt.SD_ALG_CLAIM]?.jsonPrimitive?.contentOrNull
        return DigestAlg.entries.firstOrNull { it.httpHeaderId?.equals(id, ignoreCase = true) == true }
            ?: SdJwt.DEFAULT_HASH_ALG
    }
}
