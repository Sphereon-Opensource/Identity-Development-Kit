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

package com.sphereon.catalog.eu

object EuCatalogueConstants {
    const val COA_NAMESPACE = "http://data.europa.eu/c9v/catalogueOfAttributes/v1#"
    const val COS_NAMESPACE = "http://data.europa.eu/c9v/catalogueOfSchemes/v1#"
    const val LOTE_NAMESPACE = "http://uri.etsi.org/019602/v1#"
    const val XMLDSIG_NAMESPACE = "http://www.w3.org/2000/09/xmldsig#"

    const val COA_IDENTIFIER = "http://data.europa.eu/c9v/EUCatalogueOfAttributes"
    const val COS_IDENTIFIER = "http://data.europa.eu/c9v/EUCatalogueOfSchemes"
    const val LOC_TYPE = "http://data.europa.eu/c9v/EUListOfCatalogues"
    const val LOC_TAG = "http://data.europa.eu/c9v/LoCTag"
    const val LOC_STATUS_DETERMINATION_APPROACH = "http://data.europa.eu/c9v/EUListOfCatalogues/statusDeterminationApproach"
    const val LOC_SCHEME_TYPE_COMMUNITY_RULES = "http://data.europa.eu/c9v/EUListOfCatalogues/schemeTypeCommunityRules"
    const val COA_LOTE_TYPE = COA_IDENTIFIER
    const val COS_LOTE_TYPE = COS_IDENTIFIER

    /** File names of a published catalogue next to its entry directories under `/public/catalogs/{slug}/`. */
    const val COA_PUBLIC_FILE = "coa.xml"
    const val COS_PUBLIC_FILE = "cos.xml"

    const val SUPPORTED_CATALOGUE_VERSION = 1
    const val HISTORY_FOREVER_MONTHS = 65535
    const val LOC_MAX_UPDATE_INTERVAL_MONTHS = 6
    const val TERRITORY_EU = "EU"

    const val EXC_C14N_TRANSFORM = "http://www.w3.org/2001/10/xml-exc-c14n#"
    const val DIGEST_SHA512_SPEC = "https://www.w3.org/TR/xmlenc-core1/#sec-SHA512"
    const val DIGEST_SHA512_XMLENC = "http://www.w3.org/2001/04/xmlenc#sha512"
    const val SHA512_DIGEST_LENGTH = 64

    const val ATTRIBUTE_STATUS_INFORCE = "http://data.europa.eu/c9v/EUCatalogueOfAttributes/VersionedAttributeStatus/inforce"
    const val ATTRIBUTE_STATUS_DEPRECATED = "http://data.europa.eu/c9v/EUCatalogueOfAttributes/VersionedAttributeStatus/deprecated"
    const val ATTRIBUTE_STATUS_OBSOLETE = "http://data.europa.eu/c9v/EUCatalogueOfAttributes/VersionedAttributeStatus/obsolete"

    const val SCHEME_STATUS_INFORCE = "http://data.europa.eu/c9v/EUCatalogueOfSchemes/VersionedEAASchemeStatus/inforce"
    const val SCHEME_STATUS_DEPRECATED = "http://data.europa.eu/c9v/EUCatalogueOfSchemes/VersionedEAASchemeStatus/deprecated"
    const val SCHEME_STATUS_OBSOLETE = "http://data.europa.eu/c9v/EUCatalogueOfSchemes/VersionedEAASchemeStatus/obsolete"

    const val TRUST_MODEL_QEAA = "http://data.europa.eu/c9v/EUCatalogueOfSchemes/EAAType/TrustModel/QEAA"
    const val TRUST_MODEL_PUB_EAA = "http://data.europa.eu/c9v/EUCatalogueOfSchemes/EAAType/TrustModel/PubEAA"
    const val TRUST_MODEL_NON_QEAA_NON_PUB_EAA = "http://data.europa.eu/c9v/EUCatalogueOfSchemes/EAAType/TrustModel/NonQEAANonPubEAA"

    const val EXTENSION_EIDAS_ANNEX_VI_ATTRIBUTE = "eIDASAnnexVIAttribute"
    const val EXTENSION_EAA_TYPE_TRUST_MODEL_TYPES = "EAATypeTrustModelTypes"

    const val EIDAS_ANNEX_VI_ATTRIBUTE_TYPE_MIN = 1
    const val EIDAS_ANNEX_VI_ATTRIBUTE_TYPE_MAX = 11

    val ACCEPTED_DIGEST_METHODS: Set<String> = setOf(DIGEST_SHA512_SPEC, DIGEST_SHA512_XMLENC)

    val ATTRIBUTE_STATUSES: Set<String> = setOf(ATTRIBUTE_STATUS_INFORCE, ATTRIBUTE_STATUS_DEPRECATED, ATTRIBUTE_STATUS_OBSOLETE)

    val SCHEME_STATUSES: Set<String> = setOf(SCHEME_STATUS_INFORCE, SCHEME_STATUS_DEPRECATED, SCHEME_STATUS_OBSOLETE)

    val TRUST_MODEL_TYPES: Set<String> = setOf(TRUST_MODEL_QEAA, TRUST_MODEL_PUB_EAA, TRUST_MODEL_NON_QEAA_NON_PUB_EAA)

    /**
     * Qualified names (`{namespace}localName`) of the critical extensions this library understands.
     */
    val UNDERSTOOD_EXTENSIONS: Set<String> =
        setOf(
            "{$COA_NAMESPACE}$EXTENSION_EIDAS_ANNEX_VI_ATTRIBUTE",
            "{$COS_NAMESPACE}$EXTENSION_EAA_TYPE_TRUST_MODEL_TYPES",
        )
}
