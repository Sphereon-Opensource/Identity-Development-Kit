/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.client

import com.sphereon.catalog.store.AttestationCatalogStore

/**
 * Optional session hook so wallets can populate a local index of trusted
 * catalog APIs. [hydrate] is cheap when the index is fresh. [reindex]
 * always pulls from those APIs.
 */
interface CatalogSessionHydrator {
    suspend fun hydrate(
        store: AttestationCatalogStore,
        tenantId: String
    )

    suspend fun reindex(
        store: AttestationCatalogStore,
        tenantId: String
    ) = hydrate(store, tenantId)
}
