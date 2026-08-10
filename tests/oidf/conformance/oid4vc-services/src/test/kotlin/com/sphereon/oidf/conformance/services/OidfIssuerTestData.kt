/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance.services

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Deterministic credential datasets shared by issuer- and wallet-initiated OIDF modules. */
internal fun oidfIssuerCredentialSubjectData(credentialConfigurationId: String): JsonObject =
    when (credentialConfigurationId) {
        "Mdl" ->
            buildJsonObject {
                put("org.iso.18013.5.1.family_name", "Mustermann")
                put("org.iso.18013.5.1.given_name", "Erika")
                put("org.iso.18013.5.1.birth_date", "1990-01-15")
                put("org.iso.18013.5.1.issue_date", "2026-01-01")
                put("org.iso.18013.5.1.expiry_date", "2030-01-01")
                put("org.iso.18013.5.1.issuing_country", "NL")
                put("org.iso.18013.5.1.issuing_authority", "RDW")
                put("org.iso.18013.5.1.document_number", "OIDF-123456789")
                put("org.iso.18013.5.1.portrait", OIDF_MINIMAL_PNG_BASE64)
                put(
                    "org.iso.18013.5.1.driving_privileges",
                    buildJsonArray {
                        add(
                            buildJsonObject {
                                put("vehicle_category_code", "B")
                                put("issue_date", "2026-01-01")
                                put("expiry_date", "2030-01-01")
                            },
                        )
                    },
                )
                put("org.iso.18013.5.1.un_distinguishing_sign", "NL")
            }
        "EuPid" ->
            buildJsonObject {
                put("given_name", "Erika")
                put("family_name", "Mustermann")
                put("birth_date", "1990-01-15")
                put("age_over_18", true)
            }
        else -> JsonObject(emptyMap())
    }

private const val OIDF_MINIMAL_PNG_BASE64 =
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
