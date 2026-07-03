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

package com.sphereon.statuslist.impl.config

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.statuslist.CreateStatusListArgs
import com.sphereon.statuslist.DEFAULT_STATUS_LIST_LENGTH
import com.sphereon.statuslist.StatusListDefinitionsProvider
import com.sphereon.statuslist.StatusListHostingMode
import com.sphereon.statuslist.StatusListSpec
import com.sphereon.statuslist.StatusProofFormat
import com.sphereon.statuslist.StatusPurpose
import com.sphereon.statuslist.spi.StatusListPublisher
import com.sphereon.statuslist.spi.StatusListPublisherIds
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * [StatusListDefinitionsProvider] backed by IDK's ConfigService, reading the root,
 * protocol-neutral `statuslists` namespace (NOT nested under any issuance protocol):
 *
 * ```yaml
 * sphereon:
 *   statuslists:
 *     ids: revocation,suspension          # which lists this deployment hosts
 *     issuer: https://example.com         # optional default `iss` for every list token
 *     "[revocation]":
 *       uri: https://example.com/statuslists/revocation   # the hosted root path, embedded in artifacts
 *       spec: token_status_list           # or bitstring_status_list
 *       purposes: revocation              # comma-separated StatusPurpose values
 *       proofFormat: jwt                  # optional; defaults per spec
 *       hostingMode: hosted               # optional; hosted or export
 *       issuer: https://example.com       # optional per-list override
 *       length: 131072                    # optional
 *       bitsPerStatus: 1                  # optional
 *       signingKeyAlias: <kms-alias>      # optional
 *       ttlSeconds: 3600                  # optional
 * ```
 *
 * Bracket-quote the list id (`"[revocation]"`) so the property-key normaliser preserves it verbatim.
 * The lists are hosted at their own root path by the status-list hosting REST surface; the `uri`
 * here is the externally referenced address artifacts embed and verifiers dereference.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<StatusListDefinitionsProvider>())
class ConfigDrivenStatusListDefinitionsProvider(
    private val execution: SessionExecution,
    /**
     * Hosting drivers contributed on the classpath (empty-safe). When a list does not configure its
     * `uri` explicitly, the publisher selected by `publisher: <id>` (default `rest`) derives it.
     */
    private val publishers: Set<StatusListPublisher> = emptySet(),
) : StatusListDefinitionsProvider {
    private val configService: PrincipalConfigService
        get() = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService

    override val definitions: List<CreateStatusListArgs>
        get() {
            val ids = configService.getPropertyAsString("$NAMESPACE.ids")?.splitCsv() ?: return emptyList()
            val globalIssuer = configService.getPropertyAsString("$NAMESPACE.issuer")?.takeIf { it.isNotBlank() }
            return ids.mapNotNull { id -> buildDefinition(id, globalIssuer) }
        }

    override fun byId(correlationId: String): CreateStatusListArgs? = definitions.firstOrNull { it.correlationId == correlationId }

    private fun buildDefinition(
        id: String,
        globalIssuer: String?,
    ): CreateStatusListArgs? {
        val prefix = "$NAMESPACE.[$id]"
        // URI precedence: an explicit per-list `uri`, else derive it via the selected hosting driver
        // (`publisher`, default `rest`). A null from both (e.g. self-host/export with no explicit uri)
        // skips the list.
        val publisherId = configService.getPropertyAsString("$prefix.publisher")?.takeIf { it.isNotBlank() } ?: StatusListPublisherIds.REST
        val uri =
            configService.getPropertyAsString("$prefix.uri")?.takeIf { it.isNotBlank() }
                ?: publishers.firstOrNull { it.id == publisherId }?.resolveStatusListUri(id)
                ?: return null
        val spec =
            configService.getPropertyAsString("$prefix.spec")?.let { parseSpec(it) } ?: StatusListSpec.TOKEN_STATUS_LIST
        val proofFormat =
            configService.getPropertyAsString("$prefix.proofFormat")?.let { parseProofFormat(it) }
                ?: defaultProofFormat(spec)
        val hostingMode =
            configService.getPropertyAsString("$prefix.hostingMode")?.let { StatusListHostingMode.fromValue(it) }
                ?: StatusListHostingMode.HOSTED
        val purposes =
            configService
                .getPropertyAsString("$prefix.purposes")
                ?.splitCsv()
                ?.mapNotNull { StatusPurpose.fromValue(it) }
                ?.ifEmpty { null }
                ?: listOf(StatusPurpose.REVOCATION)
        val length = configService.getPropertyAsString("$prefix.length")?.toIntOrNull() ?: DEFAULT_STATUS_LIST_LENGTH
        val bitsPerStatus = configService.getPropertyAsString("$prefix.bitsPerStatus")?.toIntOrNull() ?: 1
        val signingKeyAlias = configService.getPropertyAsString("$prefix.signingKeyAlias")?.takeIf { it.isNotBlank() }
        // How the token references its signing key in the JOSE header (did:<method> / x5c / ...). Set
        // this to match the credentials that reference the list so wallets trust the same key/anchor.
        val signingKeyMode = configService.getPropertyAsString("$prefix.signingKeyMode")?.takeIf { it.isNotBlank() }
        // For DID modes, the configured verification-method URL used as the token `kid` (did:web/webvh
        // cannot derive it from the key).
        val signingVerificationMethodId = configService.getPropertyAsString("$prefix.verificationMethodId")?.takeIf { it.isNotBlank() }
        val signingCertChainPath = configService.getPropertyAsString("$prefix.signingCertChainPath")?.takeIf { it.isNotBlank() }
        val ttlSeconds = configService.getPropertyAsString("$prefix.ttlSeconds")?.toLongOrNull()
        // The list token's `iss`: per-list override, then the global default, then the origin of the
        // hosting URI (so a self-contained config need only set the root-path `uri`).
        val issuer =
            configService.getPropertyAsString("$prefix.issuer")?.takeIf { it.isNotBlank() }
                ?: globalIssuer
                ?: originOf(uri)
        return CreateStatusListArgs(
            correlationId = id,
            spec = spec,
            purposes = purposes,
            proofFormat = proofFormat,
            hostingMode = hostingMode,
            issuer = issuer,
            statusListUri = uri,
            length = length,
            bitsPerStatus = bitsPerStatus,
            signingKeyAlias = signingKeyAlias,
            signingKeyMode = signingKeyMode,
            signingVerificationMethodId = signingVerificationMethodId,
            signingCertChainPath = signingCertChainPath,
            ttlSeconds = ttlSeconds,
        )
    }

    private fun parseSpec(value: String): StatusListSpec =
        when (value.lowercase().replace("-", "_")) {
            "bitstring_status_list", "bitstring", "w3c" -> StatusListSpec.BITSTRING_STATUS_LIST
            else -> StatusListSpec.TOKEN_STATUS_LIST
        }

    private fun parseProofFormat(value: String): StatusProofFormat? =
        StatusProofFormat.entries.firstOrNull {
            it.value.equals(value, ignoreCase = true) || it.name.equals(value, ignoreCase = true)
        }

    private fun defaultProofFormat(spec: StatusListSpec): StatusProofFormat =
        when (spec) {
            StatusListSpec.TOKEN_STATUS_LIST -> StatusProofFormat.JWT
            StatusListSpec.BITSTRING_STATUS_LIST -> StatusProofFormat.VC_JWT
        }

    /** `https://host:port/path` -> `https://host:port`; falls back to the whole value when no path. */
    private fun originOf(uri: String): String {
        val schemeEnd = uri.indexOf("://")
        if (schemeEnd < 0) return uri
        val authorityStart = schemeEnd + 3
        val pathStart = uri.indexOf('/', authorityStart)
        return if (pathStart < 0) uri else uri.substring(0, pathStart)
    }

    private fun String.splitCsv(): List<String> = split(",").map { it.trim() }.filter { it.isNotEmpty() }

    private companion object {
        /** Root, protocol-neutral config namespace (`sphereon.statuslists.*`). */
        const val NAMESPACE = "statuslists"
    }
}
