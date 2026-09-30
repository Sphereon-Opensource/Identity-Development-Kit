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

package com.sphereon.catalog.eu.resolution

/**
 * External identifier method names served by EU catalogue resolvers. The value formats are:
 *
 * - [ATTRIBUTE]: `{catalogueIdentifier}#{namespace}/{attributeIdentifier}[@version]`
 * - [ATTRIBUTE_REGISTRATION]: the AttributeRegistrationIdentifier
 * - [SCHEME]: the EAASchemeName, optionally `[@version]`
 * - [SCHEME_REGISTRATION]: the EAASchemeRegistrationIdentifier
 * - [EAA_TYPE]: `{schemeName}@{version}#{eaaTypeIdentifier}`
 * - [CREDENTIAL_TYPE]: a vct or docType value, optionally narrowed by a media type option
 */
object CatalogIdentifierMethods {
    const val ATTRIBUTE = "catalog_attribute"
    const val ATTRIBUTE_REGISTRATION = "catalog_attribute_registration"
    const val SCHEME = "catalog_scheme"
    const val SCHEME_REGISTRATION = "catalog_scheme_registration"
    const val EAA_TYPE = "catalog_eaa_type"
    const val CREDENTIAL_TYPE = "catalog_credential_type"

    val ALL: Set<String> = setOf(ATTRIBUTE, ATTRIBUTE_REGISTRATION, SCHEME, SCHEME_REGISTRATION, EAA_TYPE, CREDENTIAL_TYPE)
}
