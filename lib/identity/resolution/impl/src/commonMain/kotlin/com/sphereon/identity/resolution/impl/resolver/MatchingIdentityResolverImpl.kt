/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.identity.resolution.impl.resolver

import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.resolution.command.ResolveMatchingIdentityCommand
import com.sphereon.identity.resolution.model.IdentityResolutionResult
import com.sphereon.identity.resolution.model.ResolveMatchingIdentityArgs
import com.sphereon.identity.resolution.model.ResolverConfig
import com.sphereon.identity.resolution.service.IdentityResolver
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

/**
 * Thin resolver adapter that delegates to [ResolveMatchingIdentityCommand].
 * Extracts config properties, builds args, delegates to command.
 *
 * Registered via multibinding so [ResolveIdentityCommandImpl] discovers it.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<IdentityResolver>())
class MatchingIdentityResolverImpl(
    private val resolveMatchingCommand: ResolveMatchingIdentityCommand
) : IdentityResolver {

    override val resolverId = "identity-matching"
    override val priority = 100

    override suspend fun supports(tenantId: String, resolverConfig: ResolverConfig?): Boolean {
        return resolverConfig?.enabled == true && resolverConfig.properties["kms-key-id"] != null
    }

    override suspend fun resolve(
        identifier: String,
        tenantId: String,
        resolverConfig: ResolverConfig?
    ): IdkResult<IdentityResolutionResult, IdkError> {
        val keyId = resolverConfig?.properties?.get("kms-key-id")
            ?: return Ok(IdentityResolutionResult.NOT_FOUND)
        val identifierType = resolverConfig.properties["identifier-type"]
            ?.let { IdentifierType(it) }
            ?: IdentifierType.SUBJECT_ID

        return resolveMatchingCommand.execute(
            ResolveMatchingIdentityArgs(
                identifier = identifier,
                tenantId = tenantId,
                kmsKeyId = keyId,
                identifierType = identifierType
            )
        )
    }
}
