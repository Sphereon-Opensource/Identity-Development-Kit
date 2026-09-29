/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.impl

import com.sphereon.catalog.impl.command.absoluteVctSchemaUris
import com.sphereon.catalog.model.SchemaUriRef
import kotlin.test.Test
import kotlin.test.assertEquals

class CatalogVctUriTest {
    @Test
    fun relativeHostedVctIsResolvedOnTheTenantPublicBaseUrl() {
        // Regression: a design with a HOSTED VCT binding was listed under the relative path
        // `/public/schema/vct/<id>`. A vct must be an absolute URI.
        val resolved =
            absoluteVctSchemaUris(
                listOf(SchemaUriRef("dc+sd-jwt", "/public/schema/vct/EuPidE2E-1790031217463-70232")),
                "https://acme.e2e.nk.sphereon.com/",
            )
        assertEquals(
            listOf(SchemaUriRef("dc+sd-jwt", "https://acme.e2e.nk.sphereon.com/public/schema/vct/EuPidE2E-1790031217463-70232")),
            resolved,
        )
    }

    @Test
    fun absoluteVctsOtherFormatsAndAMissingBaseUrlAreLeftAlone() {
        val refs =
            listOf(
                SchemaUriRef("dc+sd-jwt", "https://issuer.example/ud"),
                SchemaUriRef("dc+sd-jwt", "urn:eudi:pid:1"),
                SchemaUriRef("mso_mdoc", "org.iso.18013.5.1.mDL"),
            )
        assertEquals(refs, absoluteVctSchemaUris(refs, "https://acme.example"))
        val relative = listOf(SchemaUriRef("dc+sd-jwt", "/public/schema/vct/EuPid"))
        assertEquals(relative, absoluteVctSchemaUris(relative, " "))
    }
}
