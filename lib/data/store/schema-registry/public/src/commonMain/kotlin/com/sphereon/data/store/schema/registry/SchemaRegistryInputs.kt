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

import kotlinx.serialization.Serializable

@Serializable
data class CreateSchemaInput(
    val namespace: String = "",
    val name: String,
    val schemaType: SchemaType,
    val hostingMode: SchemaHostingMode = SchemaHostingMode.PUBLIC_HOSTED,
    val description: String? = null,
    val contentType: String? = null,
    val contentBase64: String? = null,
    val contentText: String? = null,
)

@Serializable
data class UpdateSchemaInput(
    val description: String? = null,
    val contentBase64: String? = null,
    val contentText: String? = null,
    val hostingMode: SchemaHostingMode? = null,
)

@Serializable
data class ImportExternalInput(
    val namespace: String = "",
    val name: String,
    val schemaType: SchemaType,
    val sourceUrl: String,
    val description: String? = null,
)
