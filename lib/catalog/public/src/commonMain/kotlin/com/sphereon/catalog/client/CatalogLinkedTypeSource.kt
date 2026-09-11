/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.catalog.client

import com.sphereon.catalog.model.AttestationSchemaDocument
import com.sphereon.catalog.model.SchemaUriRef
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError

data class LinkedTypeSnapshot(
    val vct: String? = null,
    val doctype: String? = null,
    val schemaURIs: List<SchemaUriRef> = emptyList(),
    val documents: List<AttestationSchemaDocument> = emptyList(),
)

interface CatalogLinkedTypeSource {
    suspend fun resolve(
        designId: String?,
        vctId: String?
    ): IdkResult<LinkedTypeSnapshot?, IdkError>
}
