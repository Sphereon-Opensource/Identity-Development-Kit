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
import com.sphereon.data.store.credential.design.model.ResolveCredentialDesignInput
import com.sphereon.data.store.credential.design.persistence.CredentialDesignRepository
import com.sphereon.data.store.credential.design.resolution.CredentialDesignLayerResult
import com.sphereon.data.store.credential.design.resolution.DesignLayerProvider

class LocalOverrideDesignProvider(
    private val credentialRepo: CredentialDesignRepository,
) : DesignLayerProvider {
    override val sourceType = DesignSourceType.LOCAL_OVERRIDE
    override val authoritative = false

    override suspend fun resolveCredentialLayer(
        tenantId: String,
        input: ResolveCredentialDesignInput,
    ): CredentialDesignLayerResult? {
        val design =
            if (input.designId != null) {
                credentialRepo.findById(tenantId, input.designId!!)
            } else {
                null
            }

        return design?.let {
            CredentialDesignLayerResult(
                displays = it.displays,
                claims = it.claims,
                providedFields =
                    buildSet {
                        it.displays.forEach { d -> add("display:${d.locale}") }
                        it.claims.forEach { c -> add("claim:${c.path}") }
                    },
            )
        }
    }
}
