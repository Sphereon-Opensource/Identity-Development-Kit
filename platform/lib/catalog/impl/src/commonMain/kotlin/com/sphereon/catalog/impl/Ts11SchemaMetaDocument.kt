/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

object Ts11SchemaMetaDocument {
    const val RESOURCE = "/ts11-json-cat-attestations-data-model.json"

    val requiredFields =
        setOf(
            "version",
            "rulebookURI",
            "attestationLoS",
            "bindingType",
            "supportedFormats",
            "schemaURIs",
        )

    val allowedFields = requiredFields + setOf("id", "trustedAuthorities")

    val schemaRefAllowedFields = setOf("formatIdentifier", "uri")

    val trustAuthorityAllowedFields = setOf("frameworkType", "value", "isLOTE")

    val attestationLoS =
        setOf(
            "iso_18045_high",
            "iso_18045_moderate",
            "iso_18045_enhanced-basic",
            "iso_18045_basic",
        )

    val bindingTypes = setOf("claim", "key", "biometric", "none")

    val formats =
        setOf(
            "dc+sd-jwt",
            "mso_mdoc",
            "jwt_vc_json",
            "jwt_vc_json-ld",
            "ldp_vc",
        )

    val frameworkTypes = setOf("aki", "etsi_tl", "openid_federation")
}
