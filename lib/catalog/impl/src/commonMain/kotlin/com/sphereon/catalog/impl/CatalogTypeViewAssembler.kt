/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

import com.sphereon.catalog.client.IssuerBindingLookup
import com.sphereon.catalog.client.IssuerBindingQuery
import com.sphereon.catalog.client.IssuerBindingSnapshot
import com.sphereon.catalog.model.AttestationCatalog
import com.sphereon.catalog.model.AttestationSchemaRecord
import com.sphereon.catalog.model.AttestationTypeKey
import com.sphereon.catalog.model.AttestationTypeKeyKind
import com.sphereon.catalog.model.CatalogCardFace
import com.sphereon.catalog.model.CatalogCardSource
import com.sphereon.catalog.model.CatalogClaimView
import com.sphereon.catalog.model.CatalogDocumentKind
import com.sphereon.catalog.model.CatalogFormatSummary
import com.sphereon.catalog.model.CatalogSchemaProvenance
import com.sphereon.catalog.model.CatalogTypeKeys
import com.sphereon.catalog.model.CatalogTypeView
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class CatalogTypeViewAssembler(
    private val issuerBindingLookup: IssuerBindingLookup? = null,
) {
    suspend fun assemble(
        catalog: AttestationCatalog,
        record: AttestationSchemaRecord,
        locale: String = "en",
    ): CatalogTypeView {
        val typeKey = typeKeyOf(record)
        val snapshot =
            issuerBindingLookup?.lookup(
                IssuerBindingQuery(
                    catalogId = catalog.id,
                    schemaId = record.schema.id,
                    typeKey = typeKey,
                    linkedDesignId = record.linkedDesignId,
                ),
            ) ?: IssuerBindingSnapshot()
        return assemble(catalog, record, snapshot, locale)
    }

    fun assemble(
        catalog: AttestationCatalog,
        record: AttestationSchemaRecord,
        issuerBindings: IssuerBindingSnapshot,
        locale: String = "en",
    ): CatalogTypeView {
        val sdJwt = formatObject(record, "dc+sd-jwt")
        val mdoc = formatObject(record, "mso_mdoc")
        val vct = sdJwt?.let { FormatDocumentValidator.vctValue(it.bytes) }
        val typeKey = typeKeyOf(record, vct, mdoc?.bytes)
        val display = sdJwt?.obj?.let { preferredDisplay(it.displayEntries(), locale) }
        val title =
            display.string("name")
                ?: sdJwt?.obj.string("name")
                ?: issuerBindings.card?.displayName
                ?: record.schema.id
                ?: typeKey.value
        val description =
            display.string("description")
                ?: sdJwt?.obj.string("description")
                ?: issuerBindings.card?.description
        val claims =
            sdJwt?.obj?.let { vctClaims(it, locale) }.orEmpty().ifEmpty {
                mdoc?.obj?.let { mdocClaims(it) }.orEmpty()
            }
        return CatalogTypeView(
            schema = record.schema,
            catalogId = catalog.id,
            catalogSlug = catalog.slug,
            typeKey = typeKey,
            title = title,
            description = description,
            claims = claims,
            card = resolveCard(record, vct, title, description, display, issuerBindings),
            issuerBindings = issuerBindings.bindings,
            formatSummaries = formatSummaries(record),
            rulebookMediaType =
                record.documents.firstOrNull { it.kind == CatalogDocumentKind.RULEBOOK }?.mediaType,
            listing = record.listing,
        )
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun typeKeyOf(record: AttestationSchemaRecord): AttestationTypeKey = CatalogTypeKeys.of(record)

        private fun typeKeyOf(
            record: AttestationSchemaRecord,
            vct: String?,
            mdocBytes: ByteArray?,
        ): AttestationTypeKey {
            if (!vct.isNullOrBlank()) return AttestationTypeKey(AttestationTypeKeyKind.VCT, vct)
            val docType = mdocBytes?.let { FormatDocumentValidator.docTypeValue(it) }
            if (!docType.isNullOrBlank()) return AttestationTypeKey(AttestationTypeKeyKind.DOCTYPE, docType)
            return CatalogTypeKeys.of(record)
        }

        private fun formatBytes(
            record: AttestationSchemaRecord,
            formatIdentifier: String,
        ): ByteArray? =
            record.documents
                .firstOrNull { it.kind == CatalogDocumentKind.FORMAT && it.formatIdentifier == formatIdentifier }
                ?.bytes

        private fun formatObject(
            record: AttestationSchemaRecord,
            formatIdentifier: String,
        ): ParsedFormat? {
            val document =
                record.documents.firstOrNull {
                    it.kind == CatalogDocumentKind.FORMAT && it.formatIdentifier == formatIdentifier
                } ?: return null
            val obj = parseObject(document.bytes) ?: return null
            return ParsedFormat(document.bytes, obj)
        }

        private fun formatSummaries(record: AttestationSchemaRecord): List<CatalogFormatSummary> =
            record.documents
                .filter { it.kind == CatalogDocumentKind.FORMAT && !it.formatIdentifier.isNullOrBlank() }
                .map { CatalogFormatSummary(it.formatIdentifier!!, it.mediaType) }

        private fun resolveCard(
            record: AttestationSchemaRecord,
            vct: String?,
            title: String,
            description: String?,
            display: JsonObject?,
            issuerBindings: IssuerBindingSnapshot,
        ): CatalogCardFace? {
            val linked =
                record.provenance == CatalogSchemaProvenance.LINKED_DESIGN ||
                    !record.linkedDesignId.isNullOrBlank()
            val issuerConfigured = issuerBindings.bindings.isNotEmpty() || issuerBindings.card != null
            val seed = vct ?: record.schema.id ?: record.linkedDesignId.orEmpty()
            return when {
                issuerConfigured && issuerBindings.bindings.any { !it.credentialConfigurationId.isNullOrBlank() } -> {
                    (issuerBindings.card ?: seedCard(title, description, seed, CatalogCardSource.ISSUER_CONFIG))
                        .copy(source = CatalogCardSource.ISSUER_CONFIG)
                }

                issuerConfigured && issuerBindings.card != null -> {
                    issuerBindings.card!!.copy(
                        source = if (linked) CatalogCardSource.LINKED_DESIGN else CatalogCardSource.ISSUER_CONFIG,
                    )
                }

                linked -> {
                    (issuerBindings.card ?: seedCard(title, description, seed, CatalogCardSource.LINKED_DESIGN))
                        .copy(source = CatalogCardSource.LINKED_DESIGN)
                }

                !vct.isNullOrBlank() -> {
                    vctCard(display, title, description, vct)
                }

                else -> {
                    null
                }
            }
        }

        private fun vctCard(
            display: JsonObject?,
            title: String,
            description: String?,
            vct: String,
        ): CatalogCardFace {
            val simple = display.renderingSimple()
            val logo = simple.child("logo")
            val background = simple.child("background_image") ?: simple.child("backgroundImage")
            return CatalogCardFace(
                displayName = display.string("name") ?: title,
                description = display.string("description") ?: description,
                backgroundColor = simple.string("background_color") ?: simple.string("backgroundColor"),
                textColor = simple.string("text_color") ?: simple.string("textColor"),
                logoUrl = logo.string("uri"),
                logoAlt = logo.string("alt_text") ?: logo.string("altText"),
                backgroundUrl = background.string("uri"),
                seed = vct,
                source = CatalogCardSource.VCT_METADATA,
            )
        }

        private fun seedCard(
            title: String,
            description: String?,
            seed: String,
            source: CatalogCardSource,
        ) = CatalogCardFace(
            displayName = title,
            description = description,
            seed = seed,
            source = source,
        )

        private fun vctClaims(
            obj: JsonObject,
            locale: String,
        ): List<CatalogClaimView> {
            val claims = obj["claims"]?.jsonArray ?: return emptyList()
            return claims.mapNotNull { element ->
                val claim = runCatching { element.jsonObject }.getOrNull() ?: return@mapNotNull null
                val pathParts =
                    claim["path"]
                        ?.jsonArray
                        ?.mapNotNull { part ->
                            when (part) {
                                is JsonNull -> null
                                is JsonPrimitive -> part.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                                else -> null
                            }
                        }.orEmpty()
                if (pathParts.isEmpty()) return@mapNotNull null
                val display = claim.displayEntries()
                CatalogClaimView(
                    path = pathParts.joinToString("."),
                    label = preferredDisplay(display, locale).string("label") ?: pathParts.last(),
                    mandatory = (claim["mandatory"] as? JsonPrimitive)?.booleanOrNull ?: false,
                    description = preferredDisplay(display, locale).string("description") ?: claim.string("description"),
                    example = claim.exampleValue(),
                )
            }
        }

        private fun mdocClaims(obj: JsonObject): List<CatalogClaimView> {
            val properties = obj["properties"]?.jsonObject ?: return emptyList()
            return properties.keys
                .filter { it != "docType" && it != "namespace" }
                .map { key ->
                    val prop = runCatching { properties.getValue(key).jsonObject }.getOrNull()
                    CatalogClaimView(
                        path = key,
                        label = prop.string("title") ?: prop.string("description") ?: key,
                        mandatory = false,
                        description = prop.string("description"),
                        example = prop.exampleValue(),
                    )
                }
        }

        private fun preferredDisplay(
            entries: List<JsonObject>,
            locale: String,
        ): JsonObject? {
            if (entries.isEmpty()) return null
            entries.firstOrNull { it.string("locale").equals(locale, ignoreCase = true) }?.let { return it }
            val prefix = locale.substringBefore('-')
            return entries.firstOrNull { entry ->
                entry.string("locale")?.startsWith(prefix, ignoreCase = true) == true
            } ?: entries.first()
        }

        private fun JsonObject.displayEntries(): List<JsonObject> = this["display"]?.jsonArray?.mapNotNull { runCatching { it.jsonObject }.getOrNull() }.orEmpty()

        private fun JsonObject?.renderingSimple(): JsonObject? {
            val rendering = this?.get("rendering")?.let { runCatching { it.jsonObject }.getOrNull() } ?: return null
            return runCatching { rendering["simple"]?.jsonObject }.getOrNull()
        }

        private fun JsonObject?.child(name: String): JsonObject? = this?.get(name)?.let { runCatching { it.jsonObject }.getOrNull() }

        private fun JsonObject?.string(name: String): String? = (this?.get(name) as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

        private fun JsonObject?.exampleValue(): String? {
            val examples = this?.get("examples") ?: this?.get("example") ?: return null
            val first =
                runCatching { examples.jsonArray }.getOrNull()?.firstOrNull()
                    ?: examples
            return when (first) {
                is JsonPrimitive -> first.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
                is JsonNull -> null
                else -> first.toString().trim().takeIf { it.isNotEmpty() && it != "null" }
            }
        }

        private fun parseObject(bytes: ByteArray): JsonObject? = runCatching { json.parseToJsonElement(bytes.decodeToString()).jsonObject }.getOrNull()
    }

    private data class ParsedFormat(
        val bytes: ByteArray,
        val obj: JsonObject,
    )
}
