/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vci.issuer.pipeline

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.issuance.pipeline.PipelineConfiguration
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.OptionalBinding

/**
 * Resolves the pipeline configuration that applies to an offer's credentials.
 * The IDK default resolves nothing; the EDK ConfigService-backed impl reads
 * `oid4vci.issuer.{issuerId}.pipeline.*`.
 */
interface PipelineConfigurationResolver {
    suspend fun resolve(
        issuerId: String,
        credentialConfigurationIds: List<String>,
    ): IdkResult<PipelineConfiguration?, IdkError>
}

/**
 * Exposes [PipelineConfigurationResolver] as an optional graph accessor so that consumers
 * declaring `PipelineConfigurationResolver? = null` constructor parameters resolve cleanly under
 * the Metro `nullable type key`. Suppliers (the IDK NoOp and any EDK / test replacement) add a
 * second `@ContributesBinding(scope, binding = binding<PipelineConfigurationResolver?>())` so the
 * default `null` body here is overridden whenever a real binding is present in the graph.
 */
@ContributesTo(AppScope::class)
interface PipelineConfigurationResolverOptionalProvider {
    @OptionalBinding
    val optionalPipelineConfigurationResolver: PipelineConfigurationResolver? get() = null
}
