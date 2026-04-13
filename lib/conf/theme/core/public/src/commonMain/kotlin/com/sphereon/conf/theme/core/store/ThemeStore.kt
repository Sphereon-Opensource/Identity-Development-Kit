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

package com.sphereon.conf.theme.core.store

import com.sphereon.conf.theme.core.model.ThemeDefinition
import com.sphereon.conf.theme.core.model.ThemeScope
import com.sphereon.conf.theme.core.model.ThemeVariant

/**
 * Storage abstraction for theme definitions.
 * Implementations may be in-memory, settings-backed, or database-backed.
 */
interface ThemeStore {
    suspend fun getDefinition(
        tenant: String,
        themeId: String,
    ): ThemeDefinition?

    suspend fun listDefinitions(
        tenant: String,
        scope: ThemeScope? = null,
    ): List<ThemeDefinition>

    suspend fun saveDefinition(
        tenant: String,
        definition: ThemeDefinition,
    ): ThemeDefinition

    suspend fun deleteDefinition(
        tenant: String,
        themeId: String,
    ): Boolean

    /**
     * Returns definitions matching a specific scope and optional variant.
     * Used during resolution to collect layers.
     */
    suspend fun getDefinitionsByScope(
        tenant: String,
        scope: ThemeScope,
        variant: ThemeVariant? = null,
        appId: String? = null,
    ): List<ThemeDefinition>

    // ========== Count Methods (for quota enforcement) ==========

    /**
     * Count theme definitions for a tenant.
     */
    suspend fun countDefinitions(tenant: String): Int = listDefinitions(tenant).size

    /**
     * Count media assets for a tenant.
     */
    suspend fun countMediaAssets(tenant: String): Int = 0

    // ========== History Snapshot Methods ==========

    /**
     * Save a snapshot of a theme definition at a specific version.
     * Used for undo/history before overwriting or deleting.
     */
    suspend fun saveDefinitionSnapshot(
        tenant: String,
        themeId: String,
        version: Long,
        definition: ThemeDefinition,
    ) {}

    /**
     * Get a historical snapshot at a specific version.
     */
    suspend fun getDefinitionHistory(
        tenant: String,
        themeId: String,
        version: Long,
    ): ThemeDefinition? = null

    /**
     * List all historical snapshots for a theme definition.
     */
    suspend fun listDefinitionHistory(
        tenant: String,
        themeId: String,
    ): List<ThemeDefinition> = emptyList()

    /**
     * Remove history snapshots beyond the retention limit.
     */
    suspend fun pruneHistory(
        tenant: String,
        themeId: String,
        keepVersions: Int = 5,
    ) {}

    /**
     * Restore a soft-deleted theme definition (un-delete).
     */
    suspend fun restoreDefinition(
        tenant: String,
        themeId: String,
    ): ThemeDefinition? = null

    // ========== Media Asset Methods ==========

    /**
     * Save a media asset (Base64-encoded).
     */
    suspend fun saveMediaAsset(
        tenant: String,
        assetId: String,
        contentType: String,
        base64Data: String,
        sizeBytes: Long,
    ) {}

    /**
     * Get a media asset by ID. Returns (contentType, base64Data, sizeBytes) or null if not found.
     */
    suspend fun getMediaAsset(
        tenant: String,
        assetId: String,
    ): MediaAssetData? = null

    /**
     * Delete a media asset. Returns true if deleted.
     */
    suspend fun deleteMediaAsset(
        tenant: String,
        assetId: String,
    ): Boolean = false
    // ========== Custom CSS Methods ==========

    /**
     * Save custom CSS for a tenant/app.
     */
    suspend fun saveCustomCss(
        tenant: String,
        appId: String,
        css: String,
        contentHash: String,
    ) {}

    /**
     * Get custom CSS for a tenant/app. Returns (css, contentHash, version) or null if not found.
     */
    suspend fun getCustomCss(
        tenant: String,
        appId: String,
    ): CustomCssData? = null

    /**
     * Delete custom CSS for a tenant/app. Returns true if deleted.
     */
    suspend fun deleteCustomCss(
        tenant: String,
        appId: String,
    ): Boolean = false
}

/**
 * Simple data holder for custom CSS content retrieved from the store.
 */
data class CustomCssData(
    val css: String,
    val contentHash: String,
    val version: Long = 1,
)

/**
 * Simple data holder for media asset content retrieved from the store.
 */
data class MediaAssetData(
    val assetId: String,
    val contentType: String,
    val base64Data: String,
    val sizeBytes: Long,
)
