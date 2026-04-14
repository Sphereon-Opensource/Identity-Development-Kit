/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.data.store.schema.registry

import com.sphereon.core.compat.JsExportCompat

/**
 * Resolved schema content: the raw bytes, content type, and the associated schema record.
 */
@JsExportCompat
data class ResolvedSchemaContent(
    val data: ByteArray,
    val contentType: String,
    val schemaRecord: SchemaRecord,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is ResolvedSchemaContent) {
            return false
        }
        return contentType == other.contentType &&
            schemaRecord == other.schemaRecord &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + contentType.hashCode()
        result = 31 * result + schemaRecord.hashCode()
        return result
    }
}
