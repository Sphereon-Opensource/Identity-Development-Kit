/* Copyright 2026 Sphereon International B.V. Licensed under Apache-2.0. */
package com.sphereon.authenticsource

import com.sphereon.core.api.IdkErrorResult
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import kotlinx.serialization.json.*
import kotlinx.coroutines.CancellationException

/** Annex structural codec. It does not authenticate any response. */
class DefaultAuthenticSourceWireCodec : AuthenticSourceWireCodec {
    override fun encodeRequest(request: WireVerifyRequest): IdkResult<String, IdkError> = guarded {
        val o = linkedMapOf<String, JsonElement>()
        request.attributes?.let { list -> o["attributes"] = JsonArray(list.map {
            validateUri(it.attributeIdentifier)
            JsonObject(mapOf("attributeIdentifier" to JsonPrimitive(it.attributeIdentifier), "attributeValue" to objectElement(it.attributeValueJson)))
        }) }
        request.attributeFragments?.let { list -> o["attributeFragments"] = JsonArray(list.map {
            validateUri(it.attributeIdentifier)
            JsonObject(mapOf("attributeIdentifier" to JsonPrimitive(it.attributeIdentifier), "location" to JsonPrimitive(it.location), "value" to objectElement(it.valueJson)))
        }) }
        request.mandateJson?.let { o["mandate"] = objectElement(it) }
        JsonObject(o).toString()
    }
    override fun decodeResponse(json: String): IdkResult<WireVerifyResponse, IdkError> = guarded {
        val root = LexicalJson(json).parse().obj()
        WireVerifyResponse(json, provider(root.required("provider")), root.optional("authenticSource")?.let(::provider),
            root.optional("attributeVerificationResults")?.array()?.map { item ->
                val o = item.obj(); WireAttributeResult(o.uri("attributeIdentifier"), o.uri("attributeVerificationResult"), o.optional("attributeValue")?.objectRaw())
            },
            root.optional("fragmentVerificationResults")?.array()?.map { item ->
                val o = item.obj(); WireFragmentResult(o.uri("attributeIdentifier"), o.uri("fragmentVerificationResult"), o.optional("fragmentValue")?.objectRaw())
            }, root.optional("mandateResult")?.objectRaw())
    }
    private fun objectElement(s: String): JsonObject {
        val n = LexicalJson(s).parse(); n.obj()
        return Json.parseToJsonElement(s) as JsonObject
    }
    private inline fun <T> guarded(block: () -> T): IdkResult<T, IdkError> = try {
        IdkResult.ok(block())
    } catch (_: IllegalArgumentException) {
        IdkErrorResult(IdkError.fromString("Invalid authentic source wire document", "AUTHENTIC_SOURCE_WIRE_INVALID"))
    }
}

private fun provider(n: Node): WireProvider {
        val o = n.obj(); o.only("legalName", "identifiers", "establishedByLaw", "currentAddress")
        return WireProvider(o.string("legalName"), o.optional("identifiers")?.array()?.map {
            val i = it.obj(); i.only("type", "identifier"); WireProviderIdentifier(i.uri("type"), i.string("identifier"))
        }, o.optional("establishedByLaw")?.let {
            val l = it.obj(); l.only("legislationIdentifier", "legalBasis"); WireLaw(l.uri("legislationIdentifier"), l.optional("legalBasis")?.string())
        }, o.optional("currentAddress")?.string())
    }

/* URI reference syntax policy: reject empty, whitespace/control characters and bad percent escapes.
   No normalization, scheme allowlist or claim of network destination validation. */
private fun validateUri(s: String): String {
    require(s.isNotEmpty() && s.none { it <= ' ' || it == '\u007f' })
    require(Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(s))
    var i = 0
    while (i < s.length) { if (s[i] == '%') { require(i + 2 < s.length && s[i+1].isHex() && s[i+2].isHex()); i += 2 }; i++ }
    require(s.none { it in "<>\"{}|\\^`" })
    return s
}
private fun Char.isHex() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
private data class Node(val raw: String, val value: JsonElement, val members: Map<String, Node>? = null, val items: List<Node>? = null) {
    fun obj(): Map<String, Node> = members ?: throw IllegalArgumentException()
    fun array(): List<Node> = items ?: throw IllegalArgumentException()
    fun string(): String = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw IllegalArgumentException()
    fun objectRaw(): String { obj(); return raw }
}
private fun Map<String, Node>.required(key: String) = this[key] ?: throw IllegalArgumentException()
private fun Map<String, Node>.optional(key: String) = this[key]
private fun Map<String, Node>.string(key: String) = required(key).string()
private fun Map<String, Node>.uri(key: String): String = validateUri(string(key))
private fun Map<String, Node>.only(vararg allowed: String) { require(keys.all { it in allowed }) }

/** Bounded recursive lexical walk rejects duplicate decoded keys before kotlinx JSON loses them. */
private class LexicalJson(private val text: String) {
    private var pos = 0
    fun parse(): Node { require(text.length <= 1_048_576); val node = value(0); ws(); require(pos == text.length); return node }
    private fun ws() { while (pos < text.length && text[pos] in " \t\r\n") pos++ }
    private fun value(depth: Int): Node {
        require(depth <= 64); ws(); require(pos < text.length); val start = pos
        if (text[pos] == '{') {
            pos++; ws(); val members = linkedMapOf<String, Node>()
            if (pos < text.length && text[pos] != '}') while (true) {
                ws(); val key = stringToken(); require(!members.containsKey(key)); ws(); take(':')
                members[key] = value(depth + 1); ws(); if (pos < text.length && text[pos] == ',') { pos++; continue }; break
            }
            take('}'); val raw = text.substring(start, pos); return Node(raw, Json.parseToJsonElement(raw), members = members)
        }
        if (text[pos] == '[') {
            pos++; ws(); val items = mutableListOf<Node>()
            if (pos < text.length && text[pos] != ']') while (true) {
                items.add(value(depth + 1)); ws(); if (pos < text.length && text[pos] == ',') { pos++; continue }; break
            }
            take(']'); val raw = text.substring(start, pos); return Node(raw, Json.parseToJsonElement(raw), items = items)
        }
        if (text[pos] == '"') stringToken() else while (pos < text.length && text[pos] !in ",]} \t\r\n") pos++
        require(pos > start); val raw = text.substring(start, pos)
        return Node(raw, Json.parseToJsonElement(raw))
    }
    private fun take(c: Char) { ws(); require(pos < text.length && text[pos] == c); pos++ }
    private fun stringToken(): String {
        require(pos < text.length && text[pos] == '"'); val start = pos++
        var escaped = false
        while (pos < text.length) {
            val c = text[pos++]
            if (escaped) { escaped = false; continue }
            if (c == '\\') { escaped = true; continue }
            if (c == '"') return (Json.parseToJsonElement(text.substring(start, pos)) as JsonPrimitive).content
        }
        throw IllegalArgumentException()
    }
}

/** The response remains unverified; authorization owner must reject stale, switched or unbound contexts. */
class AuthenticSourceHttpClient(private val codec: AuthenticSourceWireCodec, private val authorization: SourceAuthorizationOwner) {
    suspend fun verify(binding: SourceEndpointBinding, context: SourceTransactionRef, request: WireVerifyRequest): IdkResult<UnverifiedSourceResponse<WireVerifyResponse>, IdkError> {
        if (binding.revision != context.bindingRevision) return failure("AUTHENTIC_SOURCE_BINDING_CHANGED")
        val encoded = codec.encodeRequest(request)
        if (encoded.isErr) return failure("AUTHENTIC_SOURCE_WIRE_INVALID")
        val response = call(binding, context, "/verify", encoded.value)
        if (response.isErr) return IdkErrorResult(requireNotNull(response.errorOrNull()))
        val decoded = codec.decodeResponse(response.value)
        if (decoded.isErr) return failure("AUTHENTIC_SOURCE_WIRE_INVALID")
        return IdkResult.ok(UnverifiedSourceResponse(context, binding.providerRef, binding.sourceRef, decoded.value))
    }
    suspend fun retrieve(binding: SourceEndpointBinding, context: SourceTransactionRef, request: WireRetrieveRequest): IdkResult<UnverifiedSourceResponse<WireRetrieveResponse>, IdkError> {
        if (!binding.retrievalAdvertised) return failure("AUTHENTIC_SOURCE_RETRIEVAL_UNSUPPORTED")
        if (binding.revision != context.bindingRevision) return failure("AUTHENTIC_SOURCE_BINDING_CHANGED")
        val encoded = encodeRetrieve(request)
        if (encoded.isErr) return failure("AUTHENTIC_SOURCE_WIRE_INVALID")
        val response = call(binding, context, "/retrieve", encoded.value)
        if (response.isErr) return IdkErrorResult(requireNotNull(response.errorOrNull()))
        val decoded = decodeRetrieve(response.value)
        if (decoded.isErr) return failure("AUTHENTIC_SOURCE_WIRE_INVALID")
        return IdkResult.ok(UnverifiedSourceResponse(context, binding.providerRef, binding.sourceRef, decoded.value))
    }
    private suspend fun call(binding: SourceEndpointBinding, context: SourceTransactionRef, path: String, body: String): IdkResult<String, IdkError> {
        val granted = try { authorization.authorize(binding, context) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { return failure("AUTHENTIC_SOURCE_AUTHORIZATION_FAILED") }
        if (granted.isErr) return failure("AUTHENTIC_SOURCE_AUTHORIZATION_REQUIRED")
        val result = try { granted.value.postJson(binding.endpoint.trimEnd('/') + path, body) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { return failure("AUTHENTIC_SOURCE_HTTP_FAILURE") }
        if (result.isErr) return failure("AUTHENTIC_SOURCE_HTTP_FAILURE")
        val response = result.value
        if (response.status == 401 || response.status == 403) return failure("AUTHENTIC_SOURCE_AUTHORIZATION_REQUIRED")
        if (response.status == 404) return failure("AUTHENTIC_SOURCE_NOT_FOUND")
        if (response.status == 501) return failure("AUTHENTIC_SOURCE_OPERATION_UNSUPPORTED")
        if (response.status != 200) return failure("AUTHENTIC_SOURCE_HTTP_FAILURE")
        if (response.contentType?.substringBefore(';')?.trim()?.lowercase() != "application/json") return failure("AUTHENTIC_SOURCE_CONTENT_TYPE")
        return IdkResult.ok(response.body)
    }
}
private fun <T> failure(code: String): IdkResult<T, IdkError> = IdkErrorResult(IdkError.fromString("Authentic source operation failed", code))
private fun encodeRetrieve(request: WireRetrieveRequest): IdkResult<String, IdkError> = try {
    val values = request.attributeIdentifiers.map { validateUri(it); JsonObject(mapOf("attributeIdentifier" to JsonPrimitive(it))) }
    val obj = linkedMapOf<String, JsonElement>("attributeIdentifiers" to JsonArray(values))
    request.mandateJson?.let { val n = LexicalJson(it).parse(); n.obj(); obj["mandate"] = n.value }
    IdkResult.ok(JsonObject(obj).toString())
} catch (_: IllegalArgumentException) { failure("AUTHENTIC_SOURCE_WIRE_INVALID") }
private fun decodeRetrieve(json: String): IdkResult<WireRetrieveResponse, IdkError> {
    return try {
    val root = LexicalJson(json).parse().obj()
    val sourceProvider = provider(root.required("provider"))
    val authenticSource = root.optional("authenticSource")?.let(::provider)
    val mandateResultJson = root.optional("mandateResult")?.objectRaw()
    val attributes = root.optional("attributes")?.array()?.map {
        val o = it.obj(); WireRetrievedAttribute(o.uri("attributeIdentifier"), o.required("attributeValue").objectRaw())
    }
    IdkResult.ok(WireRetrieveResponse(json, sourceProvider, authenticSource, attributes, mandateResultJson))
} catch (_: IllegalArgumentException) { failure("AUTHENTIC_SOURCE_WIRE_INVALID") }

}
