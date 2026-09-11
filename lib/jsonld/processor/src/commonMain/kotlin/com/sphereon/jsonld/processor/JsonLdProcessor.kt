/*
 * Copyright (c) 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.jsonld.processor

import com.sphereon.core.api.json.jcs.Jcs
import com.sphereon.jsonld.Iri
import com.sphereon.jsonld.LinkedDataDocument
import com.sphereon.jsonld.loader.LinkedDataDocumentLoader
import com.sphereon.jsonld.rdfcanon.RdfDataset
import com.sphereon.jsonld.rdfcanon.RdfIri
import com.sphereon.jsonld.rdfcanon.RdfLiteral
import com.sphereon.jsonld.rdfcanon.RdfBlankNode
import com.sphereon.jsonld.rdfcanon.RdfObject
import com.sphereon.jsonld.rdfcanon.RdfQuad
import com.sphereon.jsonld.rdfcanon.RdfSubject
import com.sphereon.jsonld.rdfcanon.RdfGraphName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/** Deterministic limits applied to every JSON-LD operation. */
data class JsonLdProcessingLimits(
    val maxDepth: Int = 64,
    val maxContextDocuments: Int = 64,
    val maxNodes: Int = 100_000,
    val maxQuads: Int = 200_000,
    val maxListItems: Int = 100_000,
)

data class JsonLdProcessingOptions(
    val baseIri: String? = null,
    val allowGeneralizedRdf: Boolean = false,
)

/** Expanded node map grouped by graph name (`@default` for the default graph). */
data class JsonLdNodeMap(val graphs: Map<String, Map<String, JsonObject>>)

/** A processing error is deliberately not recoverable as an empty dataset. */
class JsonLdProcessingException(
    message: String,
    val code: String? = null,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

/**
 * JSON-LD 1.1 expansion and Deserialize JSON-LD to RDF implementation.
 *
 * The processor is intentionally independent of JVM APIs. Context documents
 * are resolved only through [LinkedDataDocumentLoader], which lets callers
 * compose built-in, cached, pinned, and HTTP loaders. The implementation
 * follows the JSON-LD 1.1 processing model: local/remote context processing,
 * expansion, value expansion, and RDF dataset generation with deterministic
 * blank-node allocation. Unsupported generalized-RDF predicates fail closed.
 */
class JsonLdProcessor(
    private val loader: LinkedDataDocumentLoader,
    private val limits: JsonLdProcessingLimits = JsonLdProcessingLimits(),
) {
    suspend fun expand(
        input: JsonElement,
        options: JsonLdProcessingOptions = JsonLdProcessingOptions(),
    ): JsonArray {
        val state = State()
        val base = options.baseIri?.let { absoluteIri(it, "base IRI") }
        val expanded = expandElement(input, Context(base = base), null, state, 0, applyContextToNode = true)
        return when (expanded) {
            null -> JsonArray(emptyList())
            is JsonArray -> expanded
            else -> JsonArray(listOf(expanded))
        }
    }

    suspend fun nodeMap(
        input: JsonElement,
        options: JsonLdProcessingOptions = JsonLdProcessingOptions(),
    ): JsonLdNodeMap = NodeMapBuilder(limits).build(expand(input, options))

    suspend fun expand(
        document: LinkedDataDocument,
        options: JsonLdProcessingOptions = JsonLdProcessingOptions(),
    ): JsonArray = expand(document.content, options.copy(baseIri = options.baseIri ?: document.documentUrl))

    suspend fun toRdf(
        input: JsonElement,
        options: JsonLdProcessingOptions = JsonLdProcessingOptions(),
    ): RdfDataset {
        val expanded = expand(input, options)
        val state = EmitState(options.allowGeneralizedRdf, limits)
        expanded.forEach { state.emitNode(it as? JsonObject ?: fail("expanded document contains a non-object"), null, this) }
        return RdfDataset(state.quads.toList())
    }

    suspend fun toRdf(
        document: LinkedDataDocument,
        options: JsonLdProcessingOptions = JsonLdProcessingOptions(),
    ): RdfDataset = toRdf(document.content, options.copy(baseIri = options.baseIri ?: document.documentUrl))

    private suspend fun expandElement(
        element: JsonElement,
        context: Context,
        activeProperty: String?,
        state: State,
        depth: Int,
        applyContextToNode: Boolean = false,
    ): JsonElement? {
        checkDepth(depth)
        return when (element) {
            JsonNull -> null
            is JsonArray -> {
                val values = mutableListOf<JsonElement>()
                element.forEach { expanded ->
                    val value = expandElement(expanded, context, activeProperty, state, depth + 1, applyContextToNode)
                    when (value) {
                        null -> Unit
                        is JsonArray -> values.addAll(value)
                        else -> values.add(value)
                    }
                }
                JsonArray(values)
            }
            is JsonPrimitive -> expandScalar(element, context, activeProperty)
            is JsonObject -> expandObject(element, context, activeProperty, state, depth, applyContextToNode)
        }
    }

    private suspend fun expandObject(
        objectValue: JsonObject,
        inherited: Context,
        activeProperty: String?,
        state: State,
        depth: Int,
        applyContextToNode: Boolean,
    ): JsonElement? {
        if (++state.nodes > limits.maxNodes) fail("JSON-LD node limit exceeded")
        var context = if (applyContextToNode) inherited else inherited.forChild()
        objectValue["@context"]?.let { context = processContext(context, it, state, depth + 1) }

        // Value objects, node references, and lists are expanded by keyword.
        // Keyword aliases are valid in node/value objects too. Resolve the
        // aliases after applying this object's context so a nested @nest map
        // cannot hide an @value object behind an alias.
        fun keywordEntry(keyword: String): Pair<String, JsonElement>? = objectValue.entries
            .filter { (key, _) -> key == keyword || expandIri(key, context, vocab = true, documentRelative = false) == keyword }
            .singleOrNull()
            ?.let { it.key to it.value }

        val valueEntry = keywordEntry("@value")
        if (valueEntry != null) {
            val result = linkedMapOf<String, JsonElement>()
            val value = valueEntry.second
            val typeValue = keywordEntry("@type")?.second
            val jsonValue = typeValue?.let { expandType(it, context) }
            if (value is JsonNull || ((value is JsonArray || value is JsonObject) && jsonValue != JsonArray(listOf(JsonPrimitive("@json"))))) {
                fail("@value must be a scalar")
            }
            result["@value"] = value
            jsonValue?.let { result["@type"] = it }
            keywordEntry("@language")?.second?.let { language -> result["@language"] = JsonPrimitive(requireString(language, "@language")) }
            keywordEntry("@direction")?.second?.let { direction ->
                if (direction !is JsonNull) {
                    val valueDirection = requireString(direction, "@direction")
                    if (valueDirection != "ltr" && valueDirection != "rtl") fail("@direction must be ltr or rtl")
                    result["@direction"] = JsonPrimitive(valueDirection)
                }
            }
            keywordEntry("@index")?.second?.let { index -> result["@index"] = JsonPrimitive(requireString(index, "@index")) }
            val allowedValueKeys = setOf("@context", "@value", "@type", "@language", "@direction", "@index")
            objectValue.keys.firstOrNull { key ->
                key !in allowedValueKeys && expandIri(key, context, vocab = true, documentRelative = false) !in allowedValueKeys
            }?.let { fail("value object contains unsupported key $it") }
            if ((result.containsKey("@language") || result.containsKey("@direction")) && result.containsKey("@type")) {
                fail("a value object cannot contain @language or @direction with @type")
            }
            return JsonObject(result)
        }

        if (objectValue.containsKey("@list")) {
            val list = expandElement(objectValue["@list"] ?: JsonArray(emptyList()), context.forChild(), activeProperty, state, depth + 1)
            val values = when (list) {
                null -> emptyList()
                is JsonArray -> list
                else -> listOf(list)
            }
            if (values.size > limits.maxListItems) fail("JSON-LD list item limit exceeded")
            val result = linkedMapOf<String, JsonElement>("@list" to JsonArray(values))
            objectValue["@index"]?.let { result["@index"] = JsonPrimitive(requireString(it, "@index")) }
            return JsonObject(result)
        }

        // Type-scoped contexts apply to the whole node, regardless of where
        // the @type member appears in the JSON object.
        context = activateTypeScopedContexts(objectValue, context, state, depth + 1)

        val result = linkedMapOf<String, JsonElement>()
        var setExpanded: JsonElement? = null
        var hasSet = false
        for ((key, value) in objectValue) {
            if (key == "@context") continue
            val definition = context.definition(key)
            val expandedKey = expandIri(key, context, vocab = true, documentRelative = false)

            when {
                key == "@id" || expandedKey == "@id" -> {
                    if (value !is JsonPrimitive || !value.isString) fail("@id must be a string")
                    result["@id"] = JsonPrimitive(expandIri(value.content, context, vocab = false, documentRelative = true) ?: fail("invalid @id"))
                }
                key == "@type" || expandedKey == "@type" -> result["@type"] = expandType(value, context)
                key == "@language" || expandedKey == "@language" -> result["@language"] = JsonPrimitive(requireString(value, "@language").lowercase())
                key == "@direction" || expandedKey == "@direction" -> {
                    val direction = requireString(value, "@direction")
                    if (direction != "ltr" && direction != "rtl") fail("@direction must be ltr or rtl")
                    result["@direction"] = JsonPrimitive(direction)
                }
                key == "@index" || expandedKey == "@index" -> result["@index"] = JsonPrimitive(requireString(value, "@index"))
                key == "@graph" || expandedKey == "@graph" || key == "@included" || expandedKey == "@included" -> {
                    val nested = expandElement(value, context.forChild(), "@graph", state, depth + 1)
                    if (nested != null) result[if (key == "@included" || expandedKey == "@included") "@included" else "@graph"] = asArray(nested)
                }
                key == "@reverse" || expandedKey == "@reverse" -> {
                    val nested = expandElement(value, context.forChild(), "@reverse", state, depth + 1)
                    if (nested != null) result["@reverse"] = nested
                }
                key == "@set" || expandedKey == "@set" -> {
                    if (hasSet) fail("colliding @set keywords")
                    hasSet = true
                    // @set is a transparent wrapper. Expansion is deliberately
                    // delegated for every JSON value (including scalar, null,
                    // value-object, node-object, and arrays), then unwrapped
                    // below so the keyword never leaks into the result.
                    setExpanded = expandElement(value, context.forChild(), activeProperty, state, depth + 1)
                }
                key == "@nest" || expandedKey == "@nest" -> {
                    val nestedValues = when (value) {
                        is JsonObject -> listOf(value)
                        is JsonArray -> value.map { it as? JsonObject ?: fail("@nest array members must be objects") }
                        else -> fail("@nest value must be an object or array", "invalid @nest value")
                    }
                    nestedValues.forEach { nestedValue ->
                        // The value of a nested property is a property map,
                        // not a value object. Check the unexpanded keys as
                        // well as the expanded result: this catches both the
                        // native @value keyword and an alias for it, as
                        // required by Expansion Algorithm step 14.
                        if (nestedValue.keys.any { nestedKey ->
                                nestedKey == "@value" || expandIri(nestedKey, context, vocab = true, documentRelative = false) == "@value"
                            }) {
                            fail("@nest value must not contain @value")
                        }
                        val nested = expandElement(nestedValue, context.forChild(), activeProperty, state, depth + 1)
                        when (nested) {
                            null -> Unit
                            is JsonObject -> {
                                if (nested.containsKey("@value")) fail("@nest value must not contain @value")
                                mergeExpandedObject(result, nested)
                            }
                            else -> fail("@nest value must expand to an object")
                        }
                    }
                }
                key.startsWith("@") -> fail("unsupported JSON-LD keyword $key")
                expandedKey == null -> Unit
                definition?.reverse == true -> {
                    val nested = expandPropertyValue(value, context, expandedKey, definition, state, depth + 1)
                    if (nested != null) mergeObjectArray(result, "@reverse", expandedKey, nested)
                }
                else -> {
                    val nested = expandPropertyValue(value, context, expandedKey, definition, state, depth + 1)
                    if (nested != null) mergeArray(result, expandedKey, nested)
                }
            }
        }
        if (hasSet) {
            if (result.keys.any { it != "@index" }) fail("@set cannot be combined with other properties")
            return setExpanded
        }
        return JsonObject(result).takeIf { it.isNotEmpty() }
    }

    private suspend fun expandPropertyValue(
        value: JsonElement,
        context: Context,
        property: String,
        definition: TermDefinition?,
        state: State,
        depth: Int,
    ): JsonElement? {
        val valueContext = definition?.scopedContext?.let {
            // A scoped context is applied to the value node itself and may
            // explicitly opt into protection overrides. The value node must
            // retain the resulting context even when it is non-propagating;
            // its own nested node values will revert on entry.
            processContext(context, it, state, depth + 1, overrideProtected = true)
        } ?: context
        val container = definition?.container.orEmpty()
        if ("@language" in container || "@id" in container || "@type" in container || "@index" in container) {
            if (value !is JsonObject) fail("map container for $property requires an object")
            val values = mutableListOf<JsonElement>()
            for ((mapKey, mapValue) in value) {
                val nested = expandElement(mapValue, valueContext, property, state, depth + 1, applyContextToNode = definition?.scopedContext != null)
                val items = when (nested) { null -> emptyList(); is JsonArray -> nested; else -> listOf(nested) }
                items.forEach { item ->
                    val expandedObject = item as? JsonObject ?: fail("expanded map value is not an object")
                    val withMap = LinkedHashMap(expandedObject)
                    when {
                        "@language" in container && mapKey != "@none" -> withMap["@language"] = JsonPrimitive(mapKey.lowercase())
                        "@id" in container && mapKey != "@none" -> withMap["@id"] = JsonPrimitive(expandIri(mapKey, valueContext, false, true) ?: fail("invalid @id map key"))
                        "@type" in container && mapKey != "@none" -> withMap["@type"] = JsonPrimitive(expandIri(mapKey, valueContext, true, false) ?: fail("invalid @type map key"))
                        "@index" in container && mapKey != "@none" -> withMap["@index"] = JsonPrimitive(mapKey)
                    }
                    values += JsonObject(withMap)
                }
            }
            return JsonArray(values)
        }
        val expanded = expandElement(value, valueContext, property, state, depth, applyContextToNode = definition?.scopedContext != null)
        if (expanded == null) return null
        val values = if (expanded is JsonArray) expanded else JsonArray(listOf(expanded))
        return if ("@list" in container) JsonObject(mapOf("@list" to values)) else values
    }

    private fun expandScalar(value: JsonPrimitive, context: Context, activeProperty: String?): JsonObject? {
        if (activeProperty == null || activeProperty.startsWith("@")) return null
        val definition = context.definitionByIri(activeProperty)
        val result = linkedMapOf<String, JsonElement>("@value" to value)
        val type = definition?.type
        when {
            type == "@id" -> return buildJsonObject { put("@id", JsonPrimitive(expandIri(value.content, context, false, true) ?: fail("invalid @id value"))) }
            type == "@vocab" -> return buildJsonObject { put("@id", JsonPrimitive(expandIri(value.content, context, true, false) ?: fail("invalid @vocab value"))) }
            type == "@json" -> result["@type"] = JsonPrimitive("@json")
            type != null -> result["@type"] = JsonPrimitive(expandIri(type, context, true, false) ?: fail("invalid datatype"))
            (definition?.language ?: context.language) != null -> result["@language"] = JsonPrimitive(definition?.language ?: context.language!!)
            (definition?.direction ?: context.direction) != null -> result["@direction"] = JsonPrimitive(definition?.direction ?: context.direction!!)
        }
        return JsonObject(result)
    }

    private fun expandType(value: JsonElement, context: Context): JsonElement {
        val items = if (value is JsonArray) value else JsonArray(listOf(value))
        return JsonArray(items.map {
            val raw = requireString(it, "@type")
            JsonPrimitive(expandIri(raw, context, true, false) ?: fail("invalid @type $raw"))
        })
    }

    private suspend fun activateTypeScopedContexts(
        objectValue: JsonObject,
        context: Context,
        state: State,
        depth: Int,
    ): Context {
        val typeValue = objectValue["@type"] ?: objectValue.entries.firstOrNull { (key, _) ->
            key != "@context" && expandIri(key, context, vocab = true, documentRelative = false) == "@type"
        }?.value ?: return context
        val types = if (typeValue is JsonArray) typeValue else JsonArray(listOf(typeValue))
        // Resolve all type definitions against the pre-scope context. A
        // preceding type scope must not change which scope a later @type
        // value refers to.
        val scopedContexts = types.mapNotNull { type ->
            val raw = requireString(type, "@type")
            val expanded = expandIri(raw, context, vocab = true, documentRelative = false)
            (context.definition(raw) ?: expanded?.let { context.definitionByIri(it) })?.scopedContext
        }
        var active = context
        scopedContexts.forEach { scoped ->
            // Type-scoped contexts are non-propagating by default. A scoped
            // context can opt into propagation explicitly with @propagate.
            active = processContext(active, scoped, state, depth, propagateDefault = false)
        }
        return active
    }

    private suspend fun processContext(
        parent: Context,
        value: JsonElement,
        state: State,
        depth: Int,
        overrideProtected: Boolean = false,
        propagateDefault: Boolean = true,
    ): Context {
        checkDepth(depth)
        if (value is JsonNull) {
            if (!overrideProtected && parent.terms.values.any { it.protected }) {
                fail("cannot nullify a context containing protected terms")
            }
            return Context(base = parent.base)
        }
        val values = if (value is JsonArray) value else JsonArray(listOf(value))
        var context = parent
        values.forEach { entry ->
            context = when {
                entry is JsonPrimitive && entry.isString -> loadRemoteContext(context, entry.content, state, depth, overrideProtected, propagateDefault)
                entry is JsonObject -> processContextObject(context, entry, state, depth, overrideProtected, propagateDefault)
                else -> fail("@context entry must be an IRI, object, array, or null")
            }
        }
        return context
    }

    private suspend fun loadRemoteContext(
        parent: Context,
        raw: String,
        state: State,
        depth: Int,
        overrideProtected: Boolean,
        propagateDefault: Boolean,
    ): Context {
        // Remote context URLs are resolved against the active base IRI. They
        // are URLs, not terms, and therefore must not use term or vocabulary
        // mappings from the active context.
        val iri = absoluteIri(raw, "remote context IRI", parent.base)
        if (!state.remoteContexts.add(iri)) fail("cyclic remote context $iri")
        if (++state.contextDocuments > limits.maxContextDocuments) fail("JSON-LD remote context limit exceeded")
        return try {
            val document = loader.loadDocument(iri)
            if (document.isErr) fail("failed to load JSON-LD context <$iri>: ${document.error}")
            val body = document.value.content as? JsonObject ?: fail("remote context <$iri> is not a JSON object")
            val nested = body["@context"] ?: fail("remote document <$iri> has no @context")
            processContext(parent.copy(base = document.value.documentUrl), nested, state, depth + 1, overrideProtected, propagateDefault)
        } finally {
            state.remoteContexts.remove(iri)
        }
    }

    private suspend fun processContextObject(
        parent: Context,
        objectValue: JsonObject,
        state: State,
        depth: Int,
        overrideProtected: Boolean,
        propagateDefault: Boolean,
        defaultProtectedOverride: Boolean? = null,
    ): Context {
        val propagate = objectValue["@propagate"]?.let {
            (it as? JsonPrimitive)?.booleanOrNull ?: fail("@propagate must be a boolean")
        } ?: propagateDefault
        val previous = if (!propagate && parent.previousContext == null) parent else parent.previousContext
        var context = parent.copy(previousContext = previous, propagate = propagate)
        objectValue["@version"]?.let {
            val version = (it as? JsonPrimitive)?.content ?: fail("@version must be a string or number")
            if (version != "1.1") fail("only JSON-LD 1.1 processing mode is supported")
        }
        objectValue["@import"]?.let { importValue ->
            val raw = requireString(importValue, "@import")
            // @import is likewise a URL reference. Resolve it against the
            // active base and bypass the active term/vocabulary mappings.
            val iri = absoluteIri(raw, "@import IRI", parent.base)
            if (!state.remoteContexts.add(iri)) fail("cyclic remote context $iri")
            if (++state.contextDocuments > limits.maxContextDocuments) fail("JSON-LD remote context limit exceeded")
            try {
                val document = loader.loadDocument(iri)
                if (document.isErr) fail("failed to load JSON-LD import <$iri>: ${document.error}")
                val body = document.value.content as? JsonObject ?: fail("imported context <$iri> is not a JSON object")
                val imported = body["@context"] as? JsonObject ?: fail("imported context <$iri> must contain an object @context")
                if (imported.containsKey("@import")) fail("an imported context may not contain @import")
                // JSON-LD 1.1 performs a reverse merge: imported entries are
                // copied first and every containing entry overrides the same
                // key. The resulting context is then processed exactly once.
                // This is observable for relative term IDs (which must see a
                // containing @vocab) and for @protected (the containing
                // wrapper can protect its local override together with the
                // imported definition).
                val merged = LinkedHashMap<String, JsonElement>(imported)
                for ((key, localValue) in objectValue) {
                    if (key != "@import") merged[key] = localValue
                }
                context = processContextObject(
                    parent,
                    JsonObject(merged),
                    state,
                    depth + 1,
                    overrideProtected,
                    propagateDefault,
                )
            } finally {
                state.remoteContexts.remove(iri)
            }
            return context
        }
        objectValue["@base"]?.let { base ->
            context = context.copy(base = if (base is JsonNull) null else absoluteIri(requireString(base, "@base"), "@base", context.base))
        }
        objectValue["@vocab"]?.let { vocab ->
            context = context.copy(vocab = if (vocab is JsonNull) null else absoluteIri(requireString(vocab, "@vocab"), "@vocab", context.base))
        }
        objectValue["@language"]?.let { language -> context = context.copy(language = if (language is JsonNull) null else requireString(language, "@language").lowercase()) }
        objectValue["@direction"]?.let { direction ->
            val value = if (direction is JsonNull) null else requireString(direction, "@direction")
            if (value != null && value != "ltr" && value != "rtl") fail("@direction must be ltr or rtl")
            context = context.copy(direction = value)
        }
        val protectedDefault = objectValue["@protected"]?.let {
            (it as? JsonPrimitive)?.booleanOrNull ?: fail("@protected must be a boolean")
        } ?: defaultProtectedOverride ?: false
        for ((term, definitionValue) in objectValue) {
            if (term.startsWith("@")) continue
            val previousDefinition = context.terms[term]
            val definition = parseTermDefinition(
                context,
                term,
                definitionValue,
                state,
                depth,
                protectedDefault,
            )
            if (!overrideProtected && previousDefinition?.protected == true) {
                if (!definition.equivalentTo(previousDefinition)) {
                    fail("protected term redefinition: $term")
                }
                context = context.copy(terms = context.terms + (term to previousDefinition))
            } else {
                context = context.copy(terms = context.terms + (term to definition))
            }
        }
        validateNestDefinitions(context)
        return context
    }

    private fun validateNestDefinitions(context: Context) {
        context.terms.forEach { (term, definition) ->
            val nest = definition.nest ?: return@forEach
            if (nest != "@nest" && context.terms[nest]?.id != "@nest") {
                fail("@nest value for $term must be @nest or an alias mapped to @nest")
            }
        }
    }

    private fun parseTermDefinition(
        context: Context,
        term: String,
        value: JsonElement,
        state: State,
        depth: Int,
        defaultProtected: Boolean,
    ): TermDefinition {
        if (term.isEmpty()) fail("term definition must not have an empty term")
        if (value is JsonNull) {
            return TermDefinition(id = null, protected = defaultProtected)
        }
        if (value is JsonPrimitive && value.isString) {
            val id = expandIri(value.content, context, true, false)
            requireIriMapping(id, term, allowKeywords = true)
            return TermDefinition(id = id, prefix = id?.endsWith('/') == true || id?.endsWith('#') == true, protected = defaultProtected)
        }
        val objectValue = value as? JsonObject ?: fail("term definition $term must be a string, object, or null")
        val scopedContext = objectValue["@context"]
        val nest = objectValue["@nest"]?.let {
            val raw = requireString(it, "@nest")
            if (raw.startsWith("@") && raw != "@nest") fail("@nest value for $term must be @nest or a term alias")
            raw
        }
        val reverse = objectValue["@reverse"]?.let { requireString(it, "@reverse") }
        if (reverse != null && (objectValue.containsKey("@id") || objectValue.containsKey("@nest"))) {
            fail("a reverse term definition cannot contain @id or @nest")
        }
        val id = when {
            reverse != null -> expandIri(reverse, context, true, false)
            objectValue["@id"] == null -> expandIri(term, context, true, false)
            objectValue["@id"] is JsonNull -> null
            else -> expandIri(requireString(objectValue["@id"]!!, "@id"), context, true, false)
        }
        if (reverse != null) {
            requireIriMapping(id, term, allowKeywords = false)
        } else if (objectValue.containsKey("@id") && objectValue["@id"] !is JsonNull) {
            requireIriMapping(id, term, allowKeywords = true)
        }
        val container = when (val c = objectValue["@container"]) {
            null -> emptySet()
            JsonNull -> emptySet()
            is JsonPrimitive -> parseContainer(listOf(requireString(c, "@container")), term)
            is JsonArray -> parseContainer(c.map { requireString(it, "@container") }, term)
            else -> fail("@container must be a string or array")
        }
        if (reverse != null && objectValue.containsKey("@container")) {
            val rawContainer = objectValue["@container"]
            val supported = rawContainer is JsonNull ||
                (rawContainer is JsonPrimitive && rawContainer.isString && rawContainer.content in setOf("@set", "@index"))
            if (!supported) fail("reverse terms only support @set and @index containers")
        }
        val type = objectValue["@type"]?.let { rawType ->
            val raw = requireString(rawType, "@type")
            val expanded = expandIri(raw, context, true, false)
            requireTypeMapping(expanded, term)
        }
        if (type != null && (objectValue.containsKey("@language") || objectValue.containsKey("@direction"))) {
            fail("a term definition cannot contain @type with @language or @direction")
        }
        if ("@type" in container && type != null && type != "@id" && type != "@vocab") {
            fail("@type container requires an @id or @vocab type mapping")
        }
        val language = objectValue["@language"]?.let { if (it is JsonNull) null else requireString(it, "@language").lowercase() }
        val direction = objectValue["@direction"]?.let {
            if (it is JsonNull) null else requireString(it, "@direction").also { value ->
                if (value != "ltr" && value != "rtl") fail("@direction must be ltr or rtl")
            }
        }
        val prefix = objectValue["@prefix"]?.let {
            if (term.contains(':') || term.contains('/')) fail("@prefix is not allowed on IRI terms")
            val primitive = it as? JsonPrimitive
            if (primitive == null || primitive.isString || primitive.booleanOrNull == null) fail("@prefix must be a boolean")
            primitive.booleanOrNull!!
        } ?: false
        if (prefix && id?.startsWith("@") == true) fail("@prefix cannot be used with a keyword mapping")
        val protectedExplicit = objectValue.containsKey("@protected")
        val protected = objectValue["@protected"]?.let { (it as? JsonPrimitive)?.booleanOrNull ?: fail("@protected must be a boolean") } ?: defaultProtected
        val allowedKeys = setOf("@id", "@reverse", "@container", "@context", "@direction", "@index", "@language", "@nest", "@prefix", "@protected", "@type")
        objectValue.keys.firstOrNull { it !in allowedKeys }?.let {
            fail("unsupported term-definition entry $it", "invalid term definition")
        }
        return TermDefinition(
            id = id,
            reverse = reverse != null,
            type = type ?: if ("@type" in container) "@id" else null,
            language = language,
            direction = direction,
            container = container,
            prefix = prefix,
            scopedContext = scopedContext,
            nest = nest,
            protected = protected,
            protectedExplicit = protectedExplicit,
        )
    }

    private fun parseContainer(values: List<String>, term: String): Set<String> {
        if (values.isEmpty() || values.any { it !in CONTAINER_KEYWORDS }) {
            fail("invalid @container mapping for $term")
        }
        if (values.size != values.toSet().size) fail("invalid duplicate @container value for $term")
        val container = values.toSet()
        val valid = when {
            container.size == 1 -> true
            container.size == 2 && "@set" in container &&
                (container - "@set").single() in setOf("@index", "@id", "@graph", "@type", "@language") -> true
            "@graph" in container && (("@id" in container) xor ("@index" in container)) &&
                container.all { it in setOf("@graph", "@id", "@index", "@set") } -> true
            else -> false
        }
        if (!valid) fail("invalid @container combination for $term")
        return container
    }

    private fun requireTypeMapping(expanded: String?, term: String): String {
        if (expanded == null || (expanded.startsWith("@") && expanded !in TYPE_KEYWORDS) ||
            (!expanded.startsWith("@") && !expanded.startsWith("_:") && Iri.tryParse(expanded)?.isAbsolute != true)) {
            fail("invalid @type mapping for $term")
        }
        return expanded
    }

    private fun requireIriMapping(expanded: String?, term: String, allowKeywords: Boolean) {
        if (expanded == null || expanded == "@context" ||
            (!allowKeywords && expanded.startsWith("@")) ||
            (!expanded.startsWith("@") && !expanded.startsWith("_:") && Iri.tryParse(expanded)?.isAbsolute != true)) {
            fail("invalid IRI mapping for term $term")
        }
    }

    private fun expandIri(raw: String, context: Context, vocab: Boolean, documentRelative: Boolean): String? {
        if (raw.startsWith("@")) return raw
        if (raw == "a" && vocab) return RDF_TYPE
        context.terms[raw]?.let { return it.id }
        val colon = raw.indexOf(':')
        if (colon > 0) {
            val prefix = raw.substring(0, colon)
            val suffix = raw.substring(colon + 1)
            context.terms[prefix]?.let { definition ->
                if (definition.prefix && definition.id != null) return definition.id + suffix
            }
            if (Iri.tryParse(raw)?.isAbsolute == true) return raw
            return null
        }
        if (vocab && context.vocab != null) return context.vocab + raw
        if (documentRelative && context.base != null) return Iri.unsafeOf(context.base).resolve(raw)?.value
        return raw.takeIf { Iri.tryParse(it) != null }
    }

    private fun Context.definitionByIri(iri: String): TermDefinition? = terms.values.firstOrNull { it.id == iri }
    private fun Context.definition(term: String): TermDefinition? = terms[term]

    private class State {
        val remoteContexts = linkedSetOf<String>()
        var contextDocuments: Int = 0
        var nodes: Int = 0
    }

    private inner class NodeMapBuilder(private val limits: JsonLdProcessingLimits) {
        private val graphs = linkedMapOf<String, LinkedHashMap<String, JsonObject>>()
        private var blankCounter = 0
        private var nodes = 0

        fun build(expanded: JsonArray): JsonLdNodeMap {
            expanded.forEach { add(it as? JsonObject ?: fail("expanded document contains a non-object"), "@default") }
            return JsonLdNodeMap(graphs.mapValues { it.value.toMap() })
        }

        private fun add(node: JsonObject, graph: String) {
            if (++nodes > limits.maxNodes) fail("JSON-LD node limit exceeded")
            val id = node["@id"]?.let { requireString(it, "@id") } ?: "_:b${blankCounter++}"
            val target = graphs.getOrPut(graph) { linkedMapOf() }
            val existing = target[id]
            target[id] = if (existing == null) JsonObject(LinkedHashMap(node).apply { put("@id", JsonPrimitive(id)) }) else mergeNodes(existing, node)
            node["@graph"]?.let { nested ->
                val nestedGraph = id
                val values = if (nested is JsonArray) nested else JsonArray(listOf(nested))
                values.forEach { add(it as? JsonObject ?: fail("@graph item is not an object"), nestedGraph) }
            }
            node["@included"]?.let { included ->
                val values = if (included is JsonArray) included else JsonArray(listOf(included))
                values.forEach { add(it as? JsonObject ?: fail("@included item is not an object"), graph) }
            }
        }

        private fun mergeNodes(left: JsonObject, right: JsonObject): JsonObject {
            val result = LinkedHashMap(left)
            for ((key, value) in right) {
                if (key == "@id") continue
                val previous = result[key]
                val values = (if (previous is JsonArray) previous else previous?.let { JsonArray(listOf(it)) }.orEmpty()) +
                    (if (value is JsonArray) value else JsonArray(listOf(value)))
                result[key] = JsonArray(values.distinct())
            }
            return JsonObject(result)
        }
    }

    private inner class EmitState(
        val allowGeneralizedRdf: Boolean,
        private val limits: JsonLdProcessingLimits,
    ) {
        val quads = mutableListOf<RdfQuad>()
        var blankCounter = 0
        var nodeCount = 0

        suspend fun emitNode(node: JsonObject, graph: RdfGraphName?, processor: JsonLdProcessor, forcedSubject: RdfSubject? = null) {
            processor.checkEmitNode(++nodeCount)
            val subject: RdfSubject = forcedSubject ?: node["@id"]?.let { term(requireString(it, "@id")) as RdfSubject } ?: newBlank()
            node["@type"]?.let { types ->
                val values = if (types is JsonArray) types else JsonArray(listOf(types))
                values.forEach { type -> add(RdfQuad(subject, RdfIri(RDF_TYPE), term(requireString(type, "@type")) as RdfObject, graph)) }
            }
            node["@graph"]?.let { graphValue ->
                val nested = graphValue as? JsonArray ?: JsonArray(listOf(graphValue))
                nested.forEach { child -> emitNode(child as? JsonObject ?: fail("@graph item is not an object"), subject as RdfGraphName, processor) }
            }
            node["@included"]?.let { included ->
                val nested = included as? JsonArray ?: JsonArray(listOf(included))
                nested.forEach { child -> emitNode(child as? JsonObject ?: fail("@included item is not an object"), graph, processor) }
            }
            node["@reverse"]?.let { reverse ->
                val reverseObject = reverse as? JsonObject ?: fail("@reverse must be an object")
                for ((predicate, values) in reverseObject) emitValues(values, predicate, subject, graph, processor, reversed = true)
            }
            for ((predicate, values) in node) {
                if (predicate.startsWith("@")) continue
                emitValues(values, predicate, subject, graph, processor, reversed = false)
            }
        }

        private suspend fun emitValues(values: JsonElement, predicate: String, subject: RdfSubject, graph: RdfGraphName?, processor: JsonLdProcessor, reversed: Boolean) {
            val objects = if (values is JsonArray) values else JsonArray(listOf(values))
            objects.forEach { value ->
                val rdfObject = emitObject(value, graph, processor)
                val p = RdfIri(predicate)
                if (reversed) add(RdfQuad(rdfObject as RdfSubject, p, subject as RdfObject, graph))
                else add(RdfQuad(subject, p, rdfObject, graph))
            }
        }

        private suspend fun emitObject(value: JsonElement, graph: RdfGraphName?, processor: JsonLdProcessor): RdfObject {
            val objectValue = value as? JsonObject ?: fail("expanded property value is not an object")
            if (objectValue.containsKey("@value")) return literal(objectValue)
            if (objectValue.containsKey("@list")) {
                val list = objectValue["@list"] as? JsonArray ?: JsonArray(emptyList())
                if (list.size > processor.limits.maxListItems) fail("JSON-LD list item limit exceeded")
                if (list.isEmpty()) return RdfIri(RDF_NIL)
                var head: RdfBlankNode? = null
                var previous: RdfBlankNode? = null
                list.forEachIndexed { index, item ->
                    val current = newBlank()
                    if (head == null) head = current
                    previous?.let { add(RdfQuad(it, RdfIri(RDF_REST), current, graph)) }
                    val itemObject = emitObject(item, graph, processor)
                    add(RdfQuad(current, RdfIri(RDF_FIRST), itemObject, graph))
                    previous = current
                    if (index == list.lastIndex) add(RdfQuad(current, RdfIri(RDF_REST), RdfIri(RDF_NIL), graph))
                }
                return head!!
            }
            val subject = objectValue["@id"]?.let { term(requireString(it, "@id")) } ?: newBlank()
            emitNode(objectValue, graph, processor, subject as RdfSubject)
            return subject
        }

        private fun literal(value: JsonObject): RdfLiteral {
            val rawElement = value["@value"] ?: fail("@value must be scalar")
            val type = value["@type"]?.let { typeValue(it) }
            if (type == "@json" || type == RDF_JSON) return RdfLiteral(Jcs.canonicalize(rawElement).decodeToString(), datatype = RDF_JSON)
            val raw = rawElement as? JsonPrimitive ?: fail("@value must be scalar")
            val language = value["@language"]?.let { requireString(it, "@language").lowercase() }
            val direction = value["@direction"]?.let { requireString(it, "@direction") }
            if (direction != null) return RdfLiteral(raw.content, datatype = "https://www.w3.org/ns/i18n#${language ?: ""}_$direction")
            if (language != null) return RdfLiteral(raw.content, language = language)
            val datatype = type ?: when {
                !raw.isString && raw.booleanOrNull != null -> XSD_BOOLEAN
                !raw.isString && INTEGER.matches(raw.content) -> XSD_INTEGER
                !raw.isString && (raw.content.contains('.') || raw.content.contains('e', true)) -> XSD_DOUBLE
                else -> null
            }
            return RdfLiteral(raw.content, datatype = datatype)
        }

        private fun typeValue(value: JsonElement): String = when (value) {
            is JsonPrimitive -> requireString(value, "@type")
            is JsonArray -> value.singleOrNull()?.let { requireString(it, "@type") } ?: fail("value object must have one @type")
            else -> fail("@type must be a string or array")
        }

        private fun term(value: String): RdfTermAny {
            if (value.startsWith("_:")) return RdfBlankNode(value.removePrefix("_:"))
            return RdfIri(value)
        }

        private fun add(quad: RdfQuad) {
            if (quad.predicate.value.startsWith("_:") && !allowGeneralizedRdf) fail("generalized RDF predicate rejected")
            quads += quad
            if (quads.size > limits.maxQuads) fail("JSON-LD RDF quad limit exceeded")
        }

        private fun newBlank(): RdfBlankNode = RdfBlankNode("b${blankCounter++}")
    }

    private fun checkDepth(depth: Int) { if (depth > limits.maxDepth) fail("JSON-LD recursion depth limit exceeded") }
    private fun checkEmitNode(count: Int) { if (count > limits.maxNodes) fail("JSON-LD node limit exceeded") }

    private data class Context(
        val base: String? = null,
        val vocab: String? = null,
        val language: String? = null,
        val direction: String? = null,
        val terms: Map<String, TermDefinition> = emptyMap(),
        val previousContext: Context? = null,
        val propagate: Boolean = true,
    ) {
        fun forChild(): Context = if (!propagate) previousContext ?: this else this
    }
    private data class TermDefinition(
        val id: String?,
        val reverse: Boolean = false,
        val type: String? = null,
        val language: String? = null,
        val direction: String? = null,
        val container: Set<String> = emptySet(),
        val prefix: Boolean = false,
        val scopedContext: JsonElement? = null,
        val nest: String? = null,
        val protected: Boolean = false,
        val protectedExplicit: Boolean = false,
    ) {
        fun equivalentTo(other: TermDefinition): Boolean =
            id == other.id &&
                reverse == other.reverse &&
                type == other.type &&
                language == other.language &&
                direction == other.direction &&
                container == other.container &&
                prefix == other.prefix &&
                scopedContext == other.scopedContext &&
                nest == other.nest
    }

    private fun absoluteIri(raw: String, what: String, base: String? = null): String {
        val iri = Iri.tryParse(raw) ?: fail("invalid $what: $raw")
        return if (iri.isAbsolute) iri.value else base?.let { Iri.unsafeOf(it).resolve(iri).value } ?: fail("relative $what without a base: $raw")
    }
    private fun requireString(value: JsonElement, what: String): String = (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: fail("$what must be a string")
    private fun asArray(value: JsonElement): JsonArray = if (value is JsonArray) value else JsonArray(listOf(value))
    private fun mergeArray(target: MutableMap<String, JsonElement>, key: String, value: JsonElement) {
        val existing = target[key]
        val values = if (existing is JsonArray) existing.toMutableList() else mutableListOf<JsonElement>()
        if (value is JsonArray) values.addAll(value) else values.add(value)
        target[key] = JsonArray(values)
    }
    private fun mergeExpandedObject(target: MutableMap<String, JsonElement>, nested: JsonObject) {
        for ((key, value) in nested) {
            when (key) {
                "@id", "@index", "@language", "@direction" -> {
                    val previous = target[key]
                    if (previous != null && previous != value) fail("conflicting $key values while expanding @nest")
                    target[key] = value
                }
                "@reverse" -> {
                    val reverseObject = value as? JsonObject ?: fail("@reverse must expand to an object")
                    val mergedReverse = (target["@reverse"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
                    for ((predicate, values) in reverseObject) mergeArray(mergedReverse, predicate, values)
                    target["@reverse"] = JsonObject(mergedReverse)
                }
                else -> mergeArray(target, key, value)
            }
        }
    }
    private fun mergeObjectArray(target: MutableMap<String, JsonElement>, container: String, key: String, value: JsonElement) {
        val reverse = (target[container] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
        mergeArray(reverse, key, value)
        target[container] = JsonObject(reverse)
    }
    private fun fail(message: String, code: String? = null): Nothing = throw JsonLdProcessingException(message, code)

    private companion object {
        const val RDF_TYPE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"
        const val RDF_FIRST = "http://www.w3.org/1999/02/22-rdf-syntax-ns#first"
        const val RDF_REST = "http://www.w3.org/1999/02/22-rdf-syntax-ns#rest"
        const val RDF_NIL = "http://www.w3.org/1999/02/22-rdf-syntax-ns#nil"
        const val RDF_JSON = "http://www.w3.org/1999/02/22-rdf-syntax-ns#JSON"
        const val XSD_STRING = "http://www.w3.org/2001/XMLSchema#string"
        const val XSD_INTEGER = "http://www.w3.org/2001/XMLSchema#integer"
        const val XSD_DOUBLE = "http://www.w3.org/2001/XMLSchema#double"
        const val XSD_BOOLEAN = "http://www.w3.org/2001/XMLSchema#boolean"
        val CONTAINER_KEYWORDS = setOf("@graph", "@id", "@index", "@language", "@list", "@set", "@type")
        val TYPE_KEYWORDS = setOf("@id", "@json", "@none", "@vocab")
        val INTEGER = Regex("[-+]?[0-9]+")
    }
}

private typealias RdfTermAny = RdfObject
