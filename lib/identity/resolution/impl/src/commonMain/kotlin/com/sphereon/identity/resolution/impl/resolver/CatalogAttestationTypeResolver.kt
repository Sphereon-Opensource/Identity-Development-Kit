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

package com.sphereon.identity.resolution.impl.resolver

import com.sphereon.catalog.command.EvaluateCatalogVerificationArgs
import com.sphereon.catalog.command.EvaluateCatalogVerificationCommand
import com.sphereon.catalog.command.ResolveAttestationTypeArgs
import com.sphereon.catalog.command.ResolveAttestationTypeCommand
import com.sphereon.catalog.model.AttestationTypeKey
import com.sphereon.catalog.model.AttestationTypeKeyKind
import com.sphereon.catalog.model.CatalogVerificationMode
import com.sphereon.catalog.model.CatalogVerificationOutcome
import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.service.SessionScopedCommandRegistry
import com.sphereon.identity.matching.model.IdentifierType
import com.sphereon.identity.resolution.model.IdentityResolutionResult
import com.sphereon.identity.resolution.model.ResolverConfig
import com.sphereon.identity.resolution.service.IdentityResolver

/**
 * Type-existence check used by unit tests and explicit callers. It is **not** contributed
 * into the [IdentityResolver] set: stuffing a SchemaMeta id into `internalIdentityId`
 * would abort [com.sphereon.identity.resolution.command.ResolveIdentityCommand] for DIDs
 * and emails. Production type gating is [catalog.verification.evaluate] from the
 * OID4VP trust-domain validator.
 */
class CatalogAttestationTypeResolver(
    private val commands: SessionScopedCommandRegistry? = null,
) : IdentityResolver {
    override val resolverId = RESOLVER_ID
    override val priority = 50

    override suspend fun supports(
        tenantId: String,
        resolverConfig: ResolverConfig?,
    ): Boolean {
        if (commands == null || resolverConfig?.enabled != true) return false
        val mode = parseMode(resolverConfig.properties[MODE_PROPERTY])
        return mode != null && mode != CatalogVerificationMode.DISCOVERY
    }

    override suspend fun resolve(
        identifier: String,
        tenantId: String,
        resolverConfig: ResolverConfig?,
    ): IdkResult<IdentityResolutionResult, IdkError> {
        val mode = parseMode(resolverConfig?.properties?.get(MODE_PROPERTY))
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unknown catalogVerificationMode"))
        if (mode == CatalogVerificationMode.DISCOVERY) {
            return Ok(IdentityResolutionResult.NOT_FOUND)
        }
        val kind = parseKind(resolverConfig?.properties?.get(KIND_PROPERTY))
            ?: return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Unknown attestation type kind"))
        val type = AttestationTypeKey(kind, identifier)
        val registry = commands
            ?: return Err(
                IdkError.UNKNOWN_ERROR(
                    message = "Command registry is not available; failing closed",
                ),
            )
        val resolve =
            registry.get(ResolveAttestationTypeCommand.COMMAND_ID) as? ResolveAttestationTypeCommand
                ?: return Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Command '${ResolveAttestationTypeCommand.COMMAND_ID}' is not registered; failing closed",
                    ),
                )
        val evaluate =
            registry.get(EvaluateCatalogVerificationCommand.COMMAND_ID) as? EvaluateCatalogVerificationCommand
                ?: return Err(
                    IdkError.UNKNOWN_ERROR(
                        message = "Command '${EvaluateCatalogVerificationCommand.COMMAND_ID}' is not registered; failing closed",
                    ),
                )
        val resolved = resolve.execute(ResolveAttestationTypeArgs(type = type)).getOrElse { return Err(it) }
        if (resolved.data.isEmpty()) {
            return Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "type-not-in-enabled-catalog"))
        }
        val decision =
            evaluate.execute(EvaluateCatalogVerificationArgs(type = type, mode = mode)).getOrElse { return Err(it) }
        if (decision.outcome == CatalogVerificationOutcome.DENY) {
            return Err(
                IdkError.ILLEGAL_ARGUMENT_ERROR(
                    message = decision.reasons.joinToString(", ").ifBlank { "type-not-in-enabled-catalog" },
                ),
            )
        }
        val schemaId = decision.schema?.id ?: resolved.data.firstOrNull()?.id
        return Ok(
            IdentityResolutionResult.found(
                internalIdentityId = schemaId ?: identifier,
                resolverId = resolverId,
                identifierType = IdentifierType("ATTESTATION_TYPE"),
                metadata = mapOf(
                    "catalogVerificationMode" to mode.name,
                    "attestationTypeKind" to kind.name,
                ),
            ),
        )
    }

    private fun parseMode(raw: String?): CatalogVerificationMode? =
        raw?.trim()?.takeIf { it.isNotBlank() }?.let { runCatching { CatalogVerificationMode.valueOf(it) }.getOrNull() }

    private fun parseKind(raw: String?): AttestationTypeKeyKind? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: AttestationTypeKeyKind.SCHEMA_URI.name
        return runCatching { AttestationTypeKeyKind.valueOf(value) }.getOrNull()
    }

    companion object {
        const val RESOLVER_ID = "catalog-attestation-type"
        const val MODE_PROPERTY = "catalog-verification-mode"
        const val KIND_PROPERTY = "attestation-type-kind"
    }
}
