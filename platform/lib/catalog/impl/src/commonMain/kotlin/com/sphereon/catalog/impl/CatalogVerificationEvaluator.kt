/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

import com.sphereon.catalog.model.AttestationTypeKey
import com.sphereon.catalog.model.AttestationTypeKeyKind
import com.sphereon.catalog.model.CatalogVerificationDecision
import com.sphereon.catalog.model.CatalogVerificationMode
import com.sphereon.catalog.model.CatalogVerificationOutcome
import com.sphereon.catalog.model.SchemaMeta

object CatalogVerificationEvaluator {
    fun matches(
        schema: SchemaMeta,
        type: AttestationTypeKey
    ): Boolean {
        val needle = type.value.trim()
        if (needle.isEmpty()) return false
        return when (type.kind) {
            AttestationTypeKeyKind.SCHEMA_URI -> {
                schema.schemaURIs.any { uriMatches(it.uri, needle) }
            }

            AttestationTypeKeyKind.VCT -> {
                schema.schemaURIs.any {
                    it.formatIdentifier == "dc+sd-jwt" && uriMatches(it.uri, needle)
                } ||
                    uriMatches(
                        schema.schemaURIs
                            .firstOrNull()
                            ?.uri
                            .orEmpty(),
                        needle
                    )
            }

            AttestationTypeKeyKind.DOCTYPE -> {
                schema.schemaURIs.any {
                    it.formatIdentifier == "mso_mdoc" && uriMatches(it.uri, needle)
                }
            }
        }
    }

    fun evaluate(
        mode: CatalogVerificationMode,
        type: AttestationTypeKey,
        matches: List<SchemaMeta>,
    ): CatalogVerificationDecision {
        if (mode == CatalogVerificationMode.DISCOVERY) {
            return CatalogVerificationDecision(
                outcome = CatalogVerificationOutcome.ALLOW,
                mode = mode,
                reasons = listOf("discovery-only"),
                schema = matches.firstOrNull(),
                selectedAuthorities = matches.firstOrNull()?.trustedAuthorities.orEmpty(),
            )
        }
        val schema = matches.firstOrNull()
        if (schema == null) {
            return CatalogVerificationDecision(
                outcome = CatalogVerificationOutcome.DENY,
                mode = mode,
                reasons = listOf("type-not-in-enabled-catalog"),
            )
        }
        if (mode == CatalogVerificationMode.TYPE_MUST_EXIST) {
            return CatalogVerificationDecision(
                outcome = CatalogVerificationOutcome.ALLOW,
                mode = mode,
                reasons = listOf("type-exists"),
                schema = schema,
            )
        }
        return CatalogVerificationDecision(
            outcome = CatalogVerificationOutcome.ALLOW,
            mode = mode,
            reasons = listOf("type-exists", "trusted-authorities-hint"),
            schema = schema,
            selectedAuthorities = schema.trustedAuthorities,
        )
    }

    private fun uriMatches(
        uri: String,
        needle: String
    ): Boolean {
        if (uri == needle) return true
        val stripped = uri.substringBefore('#')
        return stripped == needle || stripped.endsWith("/$needle") || stripped.substringAfterLast('/') == needle
    }
}
