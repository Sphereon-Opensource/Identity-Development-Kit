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

import com.sphereon.data.store.credential.design.impl.mapper.JsonSchemaDesignMapper
import com.sphereon.data.store.credential.design.model.DesignSourceType
import com.sphereon.data.store.credential.design.model.ResolveCredentialDesignInput
import com.sphereon.data.store.credential.design.resolution.CredentialDesignLayerResult
import com.sphereon.data.store.credential.design.resolution.DesignLayerProvider
import kotlinx.serialization.json.JsonObject

class SchemaInferenceDesignProvider(
    private val mapper: JsonSchemaDesignMapper = JsonSchemaDesignMapper(),
) : DesignLayerProvider {
    override val sourceType = DesignSourceType.SCHEMA_INFERENCE
    override val authoritative = false

    override suspend fun resolveCredentialLayer(
        tenantId: String,
        input: ResolveCredentialDesignInput,
    ): CredentialDesignLayerResult? {
        val schema = input.externalMetadata?.jsonSchema ?: return null

        val hints = mapper.deriveHints(schema)
        val claims = mapper.deriveClaims(schema)

        return CredentialDesignLayerResult(
            claims = claims,
            derivedRenderHints = hints,
            providedFields =
                buildSet {
                    claims.forEach { add("claim:${it.path}") }
                },
        )
    }
}
