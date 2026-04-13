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

package com.sphereon.data.store.credential.design.impl.resolution

import com.sphereon.data.store.credential.design.model.DesignSourceType
import com.sphereon.data.store.credential.design.model.ResolveEntityDesignInput
import com.sphereon.data.store.credential.design.resolution.DesignLayerProvider
import com.sphereon.data.store.credential.design.resolution.IssuerDesignLayerResult
import com.sphereon.data.store.credential.design.resolution.VerifierDesignLayerResult

class PartyStoreDesignProvider : DesignLayerProvider {
    override val sourceType = DesignSourceType.PARTY_STORE
    override val authoritative = false

    override suspend fun resolveIssuerLayer(
        tenantId: String,
        input: ResolveEntityDesignInput,
    ): IssuerDesignLayerResult? {
        // Phase 1 stub: Party store integration requires Party service dependency
        return null
    }

    override suspend fun resolveVerifierLayer(
        tenantId: String,
        input: ResolveEntityDesignInput,
    ): VerifierDesignLayerResult? = null
}
