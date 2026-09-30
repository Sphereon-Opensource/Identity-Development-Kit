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

package com.sphereon.catalog.eu.impl.resolution

import com.sphereon.catalog.eu.spi.CatalogEaaTypeMatch
import com.sphereon.catalog.eu.spi.CatalogIndexReader
import com.sphereon.catalog.eu.spi.CatalogScope
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.asErrorResult
import com.sphereon.core.api.asOkResult
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.error.IdkErrorType
import com.sphereon.crypto.resolution.IIdentifierMethod
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOptsOrResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalIdentifierService
import com.sphereon.crypto.resolution.extern.ExternalIdentifierServiceAdapter
import com.sphereon.crypto.resolution.extern.ExternalJwkInfo
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.interop.getPublicKeyJwk
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.core.x509.certificateFromDer
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Provider
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Shared behaviour of the catalogue resolvers. The [CatalogIndexReader] is an optional binding: when nothing provides it, the service reports
 * every identifier as unsupported instead of failing graph construction, and no default reader exists.
 */
abstract class CatalogIdentifierResolutionServiceBase(
    supportedIdentifierMethods: List<IIdentifierMethod>,
    execution: SessionExecution,
    private val readerProvider: Provider<CatalogIndexReader>?,
    commandId: String,
) : ExternalIdentifierServiceAdapter<ExternalIdentifierResult>(
        supportedIdentifierMethods = supportedIdentifierMethods,
        execution = execution,
        commandId = commandId,
    ) {
    override suspend fun supports(args: Any): Boolean =
        readerProvider != null &&
            args is ExternalIdentifierCatalogOpts &&
            supportedIdentifierMethods.contains(args.method)

    override suspend fun isSupportedIdentifier(identifier: Any): Boolean = identifier is String && identifier.isNotBlank()

    override suspend fun asSupportedOpts(opts: ExternalIdentifierOptsOrResult): IdkResult<ExternalIdentifierOpts, IdkErrorType> =
        if (supports(opts)) {
            (opts as ExternalIdentifierOpts).asOkResult()
        } else {
            IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(message = "Catalogue identifier resolution is not available for this request").asErrorResult()
        }

    override suspend fun resolve(opts: ExternalIdentifierOptsOrResult): IdkResult<out ExternalIdentifierResult, IdkErrorType> = execute(opts)

    override suspend fun doExecute(
        args: ExternalIdentifierOptsOrResult,
        applyDuring: (ExternalIdentifierOptsOrResult) -> ExternalIdentifierOptsOrResult,
    ): IdkResult<ExternalIdentifierResult, IdkErrorType> {
        val opts = applyDuring(args)
        val reader = readerProvider?.invoke()
        if (reader == null || !supports(opts)) {
            return IdkError.COMMAND_ARG_NOT_SUPPORTED_ERROR(arg = opts).asErrorResult()
        }
        opts as ExternalIdentifierCatalogOpts
        val identifier = opts.identifier.trim()
        if (identifier.isEmpty() || opts.domainId.isBlank()) {
            return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "identifier and domainId are required").asErrorResult()
        }
        // The tenant always comes from the executing session, never from the request options.
        val scope = CatalogScope(tenantId = execution.tenantId, domainId = opts.domainId, sourceId = opts.sourceId)
        return resolveIn(reader, scope, opts, identifier)
    }

    protected abstract suspend fun resolveIn(
        reader: CatalogIndexReader,
        scope: CatalogScope,
        opts: ExternalIdentifierCatalogOpts,
        identifier: String,
    ): IdkResult<ExternalIdentifierResult, IdkErrorType>

    /**
     * Builds the result with the signer evidence (x5c) of the catalogue revision the entry came from. Without any signer certificate there is no
     * key to report, and the resolution fails with a clear error instead of substituting one.
     */
    @OptIn(ExperimentalEncodingApi::class)
    protected fun signed(
        opts: ExternalIdentifierCatalogOpts,
        certificates: List<ByteArray>,
        build: (Array<ExternalJwkInfo>, ResolvedKeyInfoType<JwkType>) -> ExternalIdentifierResult,
    ): IdkResult<ExternalIdentifierResult, IdkErrorType> {
        if (certificates.isEmpty()) {
            return IdkError
                .UNKNOWN_ERROR(message = "No signer certificate is recorded for the catalogue holding ${opts.method.methodName}:${opts.identifier}")
                .asErrorResult()
        }
        val infos: List<ResolvedKeyInfoType<JwkType>> =
            certificates.map { der ->
                val x5c = arrayOf(Base64.Default.encode(der))
                ResolvedKeyInfo(key = certificateFromDer(der).getPublicKeyJwk(x5c = x5c), x5c = x5c)
            }
        return build(infos.toTypedArray(), infos.first()).asOkResult()
    }

    protected fun notFound(opts: ExternalIdentifierCatalogOpts): IdkResult<ExternalIdentifierResult, IdkErrorType> =
        IdkError
            .NOT_FOUND_ERROR(resource = "${opts.method.methodName}:${opts.identifier}")
            .asErrorResult()
}

/** Resolves `catalog_attribute` and `catalog_attribute_registration` identifiers. */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<ExternalIdentifierService>())
class CatalogAttributeIdentifierResolutionService(
    execution: SessionExecution,
    readerProvider: Provider<CatalogIndexReader>? = null,
) : CatalogIdentifierResolutionServiceBase(
        supportedIdentifierMethods = listOf(CatalogIdentifierMethod.ATTRIBUTE, CatalogIdentifierMethod.ATTRIBUTE_REGISTRATION),
        execution = execution,
        readerProvider = readerProvider,
        commandId = "catalog.attribute.resolve",
    ) {
    override suspend fun resolveIn(
        reader: CatalogIndexReader,
        scope: CatalogScope,
        opts: ExternalIdentifierCatalogOpts,
        identifier: String,
    ): IdkResult<ExternalIdentifierResult, IdkErrorType> {
        if (opts.method == CatalogIdentifierMethod.ATTRIBUTE_REGISTRATION) {
            val found = reader.findAttributeByRegistrationIdentifier(scope, identifier)
            if (found.isErr) return found.error.asErrorResult()
            val match = found.value ?: return notFound(opts)
            return signed(opts, match.signerCertificates) { j, k -> ExternalIdentifierCatalogResult.Attribute(opts, match, null, j, k) }
        }
        val ref =
            CatalogIdentifierParser.parseAttribute(identifier)
                ?: return IdkError
                    .ILLEGAL_ARGUMENT_ERROR(message = "Expected {catalogueIdentifier}#{namespace}/{attributeIdentifier}[@version]")
                    .asErrorResult()
        val found = reader.findAttribute(scope, ref.namespace, ref.attributeIdentifier, ref.catalogueIdentifier)
        if (found.isErr) return found.error.asErrorResult()
        val match = found.value?.takeIf { it.catalogueIdentifier == ref.catalogueIdentifier } ?: return notFound(opts)
        if (ref.version != null && match.entry.versions.none { it.version == ref.version }) return notFound(opts)
        return signed(opts, match.signerCertificates) { j, k -> ExternalIdentifierCatalogResult.Attribute(opts, match, ref.version, j, k) }
    }
}

/** Resolves `catalog_scheme`, `catalog_scheme_registration` and `catalog_eaa_type` identifiers. */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<ExternalIdentifierService>())
class CatalogSchemeIdentifierResolutionService(
    execution: SessionExecution,
    readerProvider: Provider<CatalogIndexReader>? = null,
) : CatalogIdentifierResolutionServiceBase(
        supportedIdentifierMethods =
            listOf(
                CatalogIdentifierMethod.SCHEME,
                CatalogIdentifierMethod.SCHEME_REGISTRATION,
                CatalogIdentifierMethod.EAA_TYPE,
            ),
        execution = execution,
        readerProvider = readerProvider,
        commandId = "catalog.scheme.resolve",
    ) {
    override suspend fun resolveIn(
        reader: CatalogIndexReader,
        scope: CatalogScope,
        opts: ExternalIdentifierCatalogOpts,
        identifier: String,
    ): IdkResult<ExternalIdentifierResult, IdkErrorType> =
        when (opts.method) {
            CatalogIdentifierMethod.SCHEME_REGISTRATION -> {
                val found = reader.findSchemeByRegistrationIdentifier(scope, identifier)
                if (found.isErr) {
                    found.error.asErrorResult()
                } else {
                    found.value?.let { m -> signed(opts, m.signerCertificates) { j, k -> ExternalIdentifierCatalogResult.Scheme(opts, m, null, j, k) } } ?: notFound(opts)
                }
            }

            CatalogIdentifierMethod.EAA_TYPE -> resolveEaaType(reader, scope, opts, identifier)
            else -> resolveScheme(reader, scope, opts, identifier)
        }

    private suspend fun resolveScheme(
        reader: CatalogIndexReader,
        scope: CatalogScope,
        opts: ExternalIdentifierCatalogOpts,
        identifier: String,
    ): IdkResult<ExternalIdentifierResult, IdkErrorType> {
        // A scheme name may itself contain '@', so the whole value is tried before splitting off a version.
        for (candidate in CatalogIdentifierParser.schemeCandidates(identifier)) {
            val found = reader.findScheme(scope, candidate.schemeName)
            if (found.isErr) return found.error.asErrorResult()
            val match = found.value ?: continue
            if (candidate.version != null && match.entry.versions.none { it.version == candidate.version }) continue
            return signed(opts, match.signerCertificates) { j, k -> ExternalIdentifierCatalogResult.Scheme(opts, match, candidate.version, j, k) }
        }
        return notFound(opts)
    }

    private suspend fun resolveEaaType(
        reader: CatalogIndexReader,
        scope: CatalogScope,
        opts: ExternalIdentifierCatalogOpts,
        identifier: String,
    ): IdkResult<ExternalIdentifierResult, IdkErrorType> {
        val ref =
            CatalogIdentifierParser.parseEaaType(identifier)
                ?: return IdkError.ILLEGAL_ARGUMENT_ERROR(message = "Expected {schemeName}@{version}#{eaaTypeIdentifier}").asErrorResult()
        val found = reader.findScheme(scope, ref.schemeName)
        if (found.isErr) return found.error.asErrorResult()
        val schemeMatch = found.value ?: return notFound(opts)
        val type =
            schemeMatch.entry.versions
                .firstOrNull { it.version == ref.version }
                ?.eaaTypes
                ?.firstOrNull { it.identifier == ref.eaaTypeIdentifier } ?: return notFound(opts)
        val match =
            CatalogEaaTypeMatch(
                origin = schemeMatch.origin,
                catalogueIdentifier = schemeMatch.catalogueIdentifier,
                schemeName = schemeMatch.entry.name,
                schemeVersion = ref.version,
                eaaType = type,
                signerCertificates = schemeMatch.signerCertificates,
            )
        return signed(opts, match.signerCertificates) { j, k -> ExternalIdentifierCatalogResult.EaaType(opts, match, j, k) }
    }
}

/** Resolves `catalog_credential_type`: a vct or docType, optionally narrowed by [ExternalIdentifierCatalogOpts.mediaType]. */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<ExternalIdentifierService>())
class CatalogCredentialTypeIdentifierResolutionService(
    execution: SessionExecution,
    readerProvider: Provider<CatalogIndexReader>? = null,
) : CatalogIdentifierResolutionServiceBase(
        supportedIdentifierMethods = listOf(CatalogIdentifierMethod.CREDENTIAL_TYPE),
        execution = execution,
        readerProvider = readerProvider,
        commandId = "catalog.credentialtype.resolve",
    ) {
    override suspend fun resolveIn(
        reader: CatalogIndexReader,
        scope: CatalogScope,
        opts: ExternalIdentifierCatalogOpts,
        identifier: String,
    ): IdkResult<ExternalIdentifierResult, IdkErrorType> {
        val found = reader.findEaaTypesByFormatIdentifier(scope, identifier, opts.mediaType)
        if (found.isErr) return found.error.asErrorResult()
        if (found.value.isEmpty()) return notFound(opts)
        val certificates = found.value.flatMap { it.signerCertificates }.distinctBy { it.contentHashCode() }
        return signed(opts, certificates) { j, k -> ExternalIdentifierCatalogResult.CredentialType(opts, found.value, j, k) }
    }
}

internal data class AttributeRef(
    val catalogueIdentifier: String,
    val namespace: String,
    val attributeIdentifier: String,
    val version: String?,
)

internal data class SchemeRef(
    val schemeName: String,
    val version: String?,
)

internal data class EaaTypeRef(
    val schemeName: String,
    val version: String,
    val eaaTypeIdentifier: String,
)

internal object CatalogIdentifierParser {
    fun parseAttribute(value: String): AttributeRef? {
        val hash = value.indexOf('#')
        if (hash <= 0) return null
        val catalogue = value.substring(0, hash)
        var rest = value.substring(hash + 1)
        val slash = rest.lastIndexOf('/')
        if (slash <= 0) return null
        var version: String? = null
        val at = rest.indexOf('@', startIndex = slash)
        if (at >= 0) {
            version = rest.substring(at + 1).takeIf { it.isNotEmpty() } ?: return null
            rest = rest.substring(0, at)
        }
        val namespace = rest.substring(0, slash)
        val attribute = rest.substring(slash + 1)
        if (attribute.isEmpty()) return null
        return AttributeRef(catalogue, namespace, attribute, version)
    }

    fun schemeCandidates(value: String): List<SchemeRef> {
        val at = value.lastIndexOf('@')
        val whole = SchemeRef(value, null)
        if (at <= 0 || at == value.length - 1) return listOf(whole)
        return listOf(whole, SchemeRef(value.substring(0, at), value.substring(at + 1)))
    }

    fun parseEaaType(value: String): EaaTypeRef? {
        val hash = value.lastIndexOf('#')
        if (hash <= 0 || hash == value.length - 1) return null
        val scheme = value.substring(0, hash)
        val at = scheme.lastIndexOf('@')
        if (at <= 0 || at == scheme.length - 1) return null
        return EaaTypeRef(scheme.substring(0, at), scheme.substring(at + 1), value.substring(hash + 1))
    }
}
