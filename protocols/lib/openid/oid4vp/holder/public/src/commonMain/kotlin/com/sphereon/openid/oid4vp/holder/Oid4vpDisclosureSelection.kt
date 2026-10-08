/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

package com.sphereon.openid.oid4vp.holder

import kotlinx.serialization.json.JsonElement

/**
 * Resolves the OID4VP 1.0 Final Section 6.4.1 claim selection for a credential query.
 *
 * Each returned element is one acceptable set of claims, ordered by Verifier preference. An
 * absent `claims` member produces one empty option (no selectively disclosable claims); absent
 * `claim_sets` produces one option containing every Claim Query path.
 */
fun ResolvedOid4vpRequest.credentialDisclosurePathOptions(credentialQueryId: String): List<List<List<JsonElement>>> {
    val query =
        dcqlQuery
            ?.credentials
            .orEmpty()
            .singleOrNull { it.id == credentialQueryId }
            ?: throw IllegalArgumentException("No DCQL credential query '$credentialQueryId' exists in the resolved request")
    val claims = query.claims ?: return listOf(emptyList())
    val byId = claims.mapNotNull { claim -> claim.id?.let { it to claim } }.toMap()
    return query.claim_sets
        ?.map { option ->
            option.map { id ->
                byId[id]?.path?.components
                    ?: throw IllegalArgumentException("DCQL claim_sets references unknown Claim Query id '$id'")
            }
        }
        ?: listOf(claims.map { it.path.components })
}
