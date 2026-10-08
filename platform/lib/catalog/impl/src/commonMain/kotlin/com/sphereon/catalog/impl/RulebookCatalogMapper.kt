/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

import com.sphereon.catalog.model.AttestationSchemaDocument
import com.sphereon.catalog.model.CatalogDocumentKind
import com.sphereon.catalog.model.SchemaMeta
import com.sphereon.catalog.model.SchemaUriRef
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

data class MappedRulebookSchema(
    val slug: String,
    val schema: SchemaMeta,
    val documents: List<AttestationSchemaDocument>,
    val complete: Boolean,
)

object RulebookCatalogMapper {
    private val json = Json { ignoreUnknownKeys = true }

    fun map(
        files: Map<String, String>,
        defaultAttestationLoS: String?,
        defaultBindingType: String?,
    ): List<MappedRulebookSchema> {
        val sdJwt =
            files.filterKeys { key ->
                val normalized = key.replace('\\', '/')
                normalized.contains("data-schemas/sd-jwt/") && normalized.endsWith(".json")
            }
        return sdJwt.map { (path, content) ->
            val normalized = path.replace('\\', '/')
            val fileName = normalized.substringAfterLast('/')
            val slug =
                fileName
                    .removeSuffix("-sd-jwt.json")
                    .removeSuffix(".json")
                    .replace(Regex("^ds\\d+-"), "")
            val parsed = runCatching { json.parseToJsonElement(content).jsonObject }.getOrNull()
            val vct = VctFormatMapper.extractVct(parsed)
            val mdocPath =
                files.keys.firstOrNull { key ->
                    val candidate = key.replace('\\', '/')
                    candidate.contains("data-schemas/mdoc/") &&
                        candidate.substringAfterLast('/').contains(slug)
                }
            val rulebookPath =
                files.keys.firstOrNull { key ->
                    val candidate = key.replace('\\', '/')
                    candidate.contains("rulebooks/") &&
                        candidate.contains("rb-$slug") &&
                        (candidate.endsWith("README.md") || candidate.endsWith("rulebook.md"))
                } ?: files.keys.firstOrNull { key ->
                    val candidate = key.replace('\\', '/')
                    candidate.contains("rulebooks/rb-$slug/")
                }
            val complete = !defaultAttestationLoS.isNullOrBlank() && !defaultBindingType.isNullOrBlank()
            val formats =
                buildList {
                    add("dc+sd-jwt")
                    if (mdocPath != null) add("mso_mdoc")
                }
            val schema =
                SchemaMeta(
                    version = "0.1.0",
                    rulebookURI = rulebookPath?.let { "file:$it" } ?: "about:blank",
                    attestationLoS = defaultAttestationLoS ?: "iso_18045_basic",
                    bindingType = defaultBindingType ?: "key",
                    supportedFormats = formats,
                    schemaURIs =
                        buildList {
                            add(SchemaUriRef("dc+sd-jwt", vct ?: "urn:attestation:$slug"))
                            if (mdocPath != null) add(SchemaUriRef("mso_mdoc", "urn:mdoc:$slug"))
                        },
                )
            val documents =
                buildList {
                    add(
                        AttestationSchemaDocument(
                            kind = CatalogDocumentKind.FORMAT,
                            formatIdentifier = "dc+sd-jwt",
                            mediaType = "application/json",
                            bytes =
                                VctFormatMapper.toSdJwtVctBytes(
                                    raw = content,
                                    fallbackVct = vct ?: "urn:attestation:$slug",
                                    name = slug,
                                ),
                        ),
                    )
                    mdocPath?.let { key ->
                        files[key]?.let { body ->
                            add(
                                AttestationSchemaDocument(
                                    kind = CatalogDocumentKind.FORMAT,
                                    formatIdentifier = "mso_mdoc",
                                    mediaType = "application/json",
                                    bytes = body.encodeToByteArray(),
                                ),
                            )
                        }
                    }
                    rulebookPath?.let { key ->
                        files[key]?.let { body ->
                            add(
                                AttestationSchemaDocument(
                                    kind = CatalogDocumentKind.RULEBOOK,
                                    mediaType = "text/markdown",
                                    bytes = body.encodeToByteArray(),
                                ),
                            )
                        }
                    }
                }
            MappedRulebookSchema(slug = slug, schema = schema, documents = documents, complete = complete)
        }
    }
}
