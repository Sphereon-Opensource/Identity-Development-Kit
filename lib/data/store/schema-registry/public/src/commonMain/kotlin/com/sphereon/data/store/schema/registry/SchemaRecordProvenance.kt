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

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.store.schema.registry

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Where a schema record originated. */
@JsExportCompat
@Serializable
enum class SchemaRecordOrigin {
    /** Fetched from an external URL (CACHED_EXTERNAL hosting mode). */
    EXTERNAL,

    /** Generated internally by the platform (catalog/profile derivation). */
    INTERNAL_GENERATED,

    /** Authored directly inside the platform by a tenant or operator. */
    INTERNAL_AUTHORED,
}

/**
 * Which semantic layer owns a generated schema record.
 * Only meaningful when [SchemaRecordOrigin] is [SchemaRecordOrigin.INTERNAL_GENERATED].
 */
@JsExportCompat
@Serializable
enum class SchemaRecordOwnerLayer {
    CATALOG,
    PROFILE,
    DEFINITION,
    OTHER,
}

/**
 * Tracks the provenance of a [SchemaRecord]: where it came from and what owns it.
 *
 * Stored as JSON so future fields can be added without a schema migration.
 * Existing rows that have no provenance data deserialize as null and are
 * treated conservatively as unknown until explicitly updated.
 */
@JsExportCompat
@Serializable
data class SchemaRecordProvenance
    @JvmOverloads
    constructor(
        val origin: SchemaRecordOrigin,
        val ownerLayer: SchemaRecordOwnerLayer? = null,
        /** Stable identifier of the owning entity (e.g. catalog ID, profile ID). */
        val ownerId: String? = null,
        /** Version of the owning entity at the time the schema was produced. */
        val ownerVersion: String? = null,
        /** ID of the source schema this record was derived from, if any. */
        val sourceSchemaId: Uuid? = null,
        /** Canonical URL of the external source, when origin is EXTERNAL. */
        val sourceUrl: String? = null,
    )
