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

package com.sphereon.openid.oid4vci.issuer.impl.pipeline

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.credential.issuance.pipeline.PipelineConfiguration
import com.sphereon.openid.oid4vci.issuer.pipeline.PipelineConfigurationResolver
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * No-op pipeline resolver for the IDK baseline.
 *
 * EDK replaces this via `@ContributesBinding` with a real implementation
 * that reads `oid4vci.issuer.{issuerId}.pipeline.*` from ConfigService.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, binding = binding<PipelineConfigurationResolver>())
@ContributesBinding(AppScope::class, binding = binding<PipelineConfigurationResolver?>())
class NoOpPipelineConfigurationResolver : PipelineConfigurationResolver {
    override suspend fun resolve(
        issuerId: String,
        credentialConfigurationIds: List<String>,
    ): IdkResult<PipelineConfiguration?, IdkError> = Ok(null)
}
