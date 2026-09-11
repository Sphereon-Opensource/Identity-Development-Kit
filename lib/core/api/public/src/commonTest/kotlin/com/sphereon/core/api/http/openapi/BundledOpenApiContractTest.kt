package com.sphereon.core.api.http.openapi

import kotlin.test.Test
import kotlin.test.assertEquals

class BundledOpenApiContractTest {
    @Test
    fun selectionUsesTheBundledIndexSemanticsAndStableSpecIds() {
        val base = BundledOpenApiSpecEntry(
            division = "idk",
            domain = "widgets",
            file = "widgets-openapi.yml",
            title = "Widgets",
            paths = listOf("/widgets", "/widgets/{}"),
        )
        val richer = BundledOpenApiSpecEntry(
            division = "vdx",
            domain = "widgets",
            file = "widgets-product-openapi.yml",
            title = "Widgets Product",
            paths = listOf("/api/widgets", "/api/widgets/{}"),
        )

        assertEquals("widgets-openapi", base.specId)
        assertEquals("/api/widgets/{}", normalizeBundledOpenApiPath("api//widgets/{widgetId}"))
        assertEquals(true, bundledOpenApiPathsOverlap("/widgets/{}", "/api/widgets/{}"))
        assertEquals(listOf(richer), selectBundledOpenApiSpecs(listOf(base, richer), listOf("/api/widgets", "/api/widgets/{}")))
    }
}
