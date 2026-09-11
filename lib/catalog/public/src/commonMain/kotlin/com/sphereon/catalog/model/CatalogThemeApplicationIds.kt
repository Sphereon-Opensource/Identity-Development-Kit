/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.model

/**
 * Theme application ids for hosted public catalogs.
 *
 * One published catalog is one brandable application layer (`catalog:{slug}`),
 * so a tenant can give each catalog its own hero, logo, and explanation
 * without a parallel branding store.
 */
object CatalogThemeApplicationIds {
    const val PREFIX: String = "catalog:"

    fun of(slug: String): String = PREFIX + slug.trim()
}
