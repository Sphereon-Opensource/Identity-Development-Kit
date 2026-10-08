/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.jsonld.loader

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.jsonld.JsonLdError
import com.sphereon.jsonld.LinkedDataDocument

/**
 * Authorization decorator for JSON-LD document loading.
 *
 * The requested IRI is checked before delegation, and both a returned final
 * document URL and an HTTP Link context URL are checked afterwards. This
 * keeps redirects and externally linked contexts inside the same explicit
 * trust boundary, including when a cached loader is underneath this
 * decorator.
 */
class AllowlistedLinkedDataDocumentLoader(
    private val next: LinkedDataDocumentLoader,
    private val policy: JsonLdDocumentLoadingPolicy,
) : LinkedDataDocumentLoader {
    override suspend fun loadDocument(iri: String): IdkResult<LinkedDataDocument, JsonLdError> {
        if (!policy.isAllowed(iri)) {
            return Err(JsonLdError.DocumentNotAllowed(iri = iri))
        }

        val result = next.loadDocument(iri)
        if (result.isErr) return result

        val document = result.value
        if (!policy.isAllowed(document.documentUrl)) {
            return Err(
                JsonLdError.DocumentNotAllowed(
                    iri = document.documentUrl,
                    reason = "resolved document URL is outside the JSON-LD document allowlist",
                ),
            )
        }
        val contextUrl = document.contextUrl
        if (contextUrl != null && !policy.isAllowed(contextUrl)) {
            return Err(
                JsonLdError.DocumentNotAllowed(
                    iri = contextUrl,
                    reason = "HTTP context link is outside the JSON-LD document allowlist",
                ),
            )
        }
        return result
    }
}
