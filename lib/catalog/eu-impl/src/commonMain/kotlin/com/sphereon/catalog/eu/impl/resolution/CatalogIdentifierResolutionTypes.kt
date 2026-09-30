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

import com.sphereon.catalog.eu.resolution.CatalogIdentifierMethods
import com.sphereon.catalog.eu.spi.CatalogAttributeMatch
import com.sphereon.catalog.eu.spi.CatalogEaaTypeMatch
import com.sphereon.catalog.eu.spi.CatalogOrigin
import com.sphereon.catalog.eu.spi.CatalogSchemeMatch
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.jose.JwkType
import com.sphereon.crypto.resolution.AdditionalIdentifierLookup
import com.sphereon.crypto.resolution.IIdentifierMethod
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOpts
import com.sphereon.crypto.resolution.extern.ExternalIdentifierResult
import com.sphereon.crypto.resolution.extern.ExternalJwkInfo

/**
 * Identifier method carried by catalogue resolution opts. Equality is by [methodName] so the values of [CatalogIdentifierMethods] can be
 * compared without sharing instances.
 */
data class CatalogIdentifierMethod(
    override val methodName: String,
) : IIdentifierMethod {
    companion object {
        val ATTRIBUTE = CatalogIdentifierMethod(CatalogIdentifierMethods.ATTRIBUTE)
        val ATTRIBUTE_REGISTRATION = CatalogIdentifierMethod(CatalogIdentifierMethods.ATTRIBUTE_REGISTRATION)
        val SCHEME = CatalogIdentifierMethod(CatalogIdentifierMethods.SCHEME)
        val SCHEME_REGISTRATION = CatalogIdentifierMethod(CatalogIdentifierMethods.SCHEME_REGISTRATION)
        val EAA_TYPE = CatalogIdentifierMethod(CatalogIdentifierMethods.EAA_TYPE)
        val CREDENTIAL_TYPE = CatalogIdentifierMethod(CatalogIdentifierMethods.CREDENTIAL_TYPE)
    }
}

/**
 * Options for resolving an identifier against the catalogue index of a trust domain.
 *
 * [domainId] is mandatory. The tenant is always the tenant of the executing session, never a request option.
 * [mediaType] narrows [CatalogIdentifierMethods.CREDENTIAL_TYPE] lookups.
 */
data class ExternalIdentifierCatalogOpts(
    override val method: IIdentifierMethod,
    override val identifier: String,
    val domainId: String,
    val sourceId: String? = null,
    val mediaType: String? = null,
    override val context: IdentifierContext = IdentifierContext(),
    override val lookup: AdditionalIdentifierLookup = AdditionalIdentifierLookup(),
) : ExternalIdentifierOpts(method = method, identifier = identifier, context = context, lookup = lookup)

/**
 * Base for catalogue resolution results. keyInfo and jwks are the signing certificates (x5c) of the catalogue revision the entry came from, so
 * the caller can see which signer vouches for it. Every result states its [origin] and [catalogueIdentifier].
 */
sealed class ExternalIdentifierCatalogResult(
    override val identifierOpts: ExternalIdentifierCatalogOpts,
    override val jwks: Array<ExternalJwkInfo>,
    override val keyInfo: ResolvedKeyInfoType<JwkType>,
) : ExternalIdentifierResult(
        identifierOpts = identifierOpts,
        method = identifierOpts.method,
        jwks = jwks,
        keyInfo = keyInfo,
    ) {
    abstract val origin: CatalogOrigin
    abstract val catalogueIdentifier: String

    data class Attribute(
        override val identifierOpts: ExternalIdentifierCatalogOpts,
        val match: CatalogAttributeMatch,
        val version: String? = null,
        override val jwks: Array<ExternalJwkInfo>,
        override val keyInfo: ResolvedKeyInfoType<JwkType>,
    ) : ExternalIdentifierCatalogResult(identifierOpts, jwks, keyInfo) {
        override val origin get() = match.origin
        override val catalogueIdentifier get() = match.catalogueIdentifier
    }

    data class Scheme(
        override val identifierOpts: ExternalIdentifierCatalogOpts,
        val match: CatalogSchemeMatch,
        val version: String? = null,
        override val jwks: Array<ExternalJwkInfo>,
        override val keyInfo: ResolvedKeyInfoType<JwkType>,
    ) : ExternalIdentifierCatalogResult(identifierOpts, jwks, keyInfo) {
        override val origin get() = match.origin
        override val catalogueIdentifier get() = match.catalogueIdentifier
    }

    data class EaaType(
        override val identifierOpts: ExternalIdentifierCatalogOpts,
        val match: CatalogEaaTypeMatch,
        override val jwks: Array<ExternalJwkInfo>,
        override val keyInfo: ResolvedKeyInfoType<JwkType>,
    ) : ExternalIdentifierCatalogResult(identifierOpts, jwks, keyInfo) {
        override val origin get() = match.origin
        override val catalogueIdentifier get() = match.catalogueIdentifier
    }

    /** A vct or docType can be bound by several catalogues; every match is returned with its own origin and catalogue identifier. */
    data class CredentialType(
        override val identifierOpts: ExternalIdentifierCatalogOpts,
        val matches: List<CatalogEaaTypeMatch>,
        override val jwks: Array<ExternalJwkInfo>,
        override val keyInfo: ResolvedKeyInfoType<JwkType>,
    ) : ExternalIdentifierCatalogResult(identifierOpts, jwks, keyInfo) {
        override val origin get() = matches.first().origin
        override val catalogueIdentifier get() = matches.first().catalogueIdentifier
    }
}
