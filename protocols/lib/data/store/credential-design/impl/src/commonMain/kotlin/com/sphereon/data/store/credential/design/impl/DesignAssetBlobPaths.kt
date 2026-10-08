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

package com.sphereon.data.store.credential.design.impl

import com.sphereon.data.store.credential.design.model.DesignAssetType
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

object DesignAssetBlobPaths {
    fun contentAddressed(tenantId: String, hash: String): String = "vc-designs/$tenantId/assets/by-hash/$hash"

    fun slot(tenantId: String, designId: Uuid, locale: String, assetType: DesignAssetType): String =
        "vc-designs/$tenantId/assets/slots/$designId/$locale/${assetType.name}"
}
