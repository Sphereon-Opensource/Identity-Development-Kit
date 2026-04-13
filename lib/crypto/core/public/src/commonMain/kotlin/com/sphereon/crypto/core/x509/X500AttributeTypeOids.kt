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
 *
 */

package com.sphereon.crypto.core.x509

/**
 * Defines constants for common X.500 Attribute Type Object Identifiers (OIDs).
 * Based primarily on RFC 4519 and PKCS#9.
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc4519">RFC 4519</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc2985">RFC 2985 (PKCS#9)</a>
 */
object X500AttributeTypeOids {
    // --- From RFC 4519 (id-at arc: 2.5.4) ---
    const val CN = "2.5.4.3" // commonName
    const val SN = "2.5.4.4" // surname
    const val SERIALNUMBER = "2.5.4.5" // serialNumber
    const val C = "2.5.4.6" // countryName
    const val L = "2.5.4.7" // localityName
    const val ST = "2.5.4.8" // stateOrProvinceName (also 'S')
    const val STREET = "2.5.4.9" // streetAddress
    const val O = "2.5.4.10" // organizationName
    const val OU = "2.5.4.11" // organizationalUnitName
    const val TITLE = "2.5.4.12" // title
    const val DESCRIPTION = "2.5.4.13" // description
    const val SEARCH_GUIDE = "2.5.4.14" // searchGuide
    const val BUSINESS_CATEGORY = "2.5.4.15" // businessCategory
    const val POSTAL_ADDRESS = "2.5.4.16" // postalAddress
    const val POSTAL_CODE = "2.5.4.17" // postalCode
    const val POST_OFFICE_BOX = "2.5.4.18" // postOfficeBox
    const val PHYSICAL_DELIVERY_OFFICE_NAME = "2.5.4.19" // physicalDeliveryOfficeName
    const val TELEPHONE_NUMBER = "2.5.4.20" // telephoneNumber
    const val TELEX_NUMBER = "2.5.4.21" // telexNumber
    const val TELETEX_TERMINAL_IDENTIFIER = "2.5.4.22" // teletexTerminalIdentifier
    const val FACSIMILE_TELEPHONE_NUMBER = "2.5.4.23" // facsimileTelephoneNumber (also 'FAX')
    const val X121_ADDRESS = "2.5.4.24" // x121Address
    const val INTERNATIONAL_ISDN_NUMBER = "2.5.4.25" // internationalISDNNumber
    const val REGISTERED_ADDRESS = "2.5.4.26" // registeredAddress
    const val DESTINATION_INDICATOR = "2.5.4.27" // destinationIndicator
    const val PREFERRED_DELIVERY_METHOD = "2.5.4.28" // preferredDeliveryMethod
    const val PRESENTATION_ADDRESS = "2.5.4.29" // presentationAddress
    const val SUPPORTED_APPLICATION_CONTEXT = "2.5.4.30" // supportedApplicationContext
    const val MEMBER = "2.5.4.31" // member (of a group)
    const val OWNER = "2.5.4.32" // owner (of a resource)
    const val ROLE_OCCUPANT = "2.5.4.33" // roleOccupant
    const val SEE_ALSO = "2.5.4.34" // seeAlso
    const val NAME = "2.5.4.41" // name (often used generically)
    const val GIVEN_NAME = "2.5.4.42" // givenName (also 'GN')
    const val INITIALS = "2.5.4.43" // initials
    const val GENERATION_QUALIFIER = "2.5.4.44" // generationQualifier
    const val UNIQUE_IDENTIFIER = "2.5.4.45" // x500UniqueIdentifier (distinct from UID)
    const val DN_QUALIFIER = "2.5.4.46" // dnQualifier
    const val ENHANCED_SEARCH_GUIDE = "2.5.4.47" // enhancedSearchGuide
    const val PROTOCOL_INFORMATION = "2.5.4.48" // protocolInformation
    const val DISTINGUISHED_NAME = "2.5.4.49" // distinguishedName (used as an attribute type)
    const val UNIQUE_MEMBER = "2.5.4.50" // uniqueMember
    const val HOUSE_IDENTIFIER = "2.5.4.51" // houseIdentifier
    const val SUPPORTED_ALGORITHMS = "2.5.4.52" // supportedAlgorithms
    const val DELTA_REVOCATION_LIST = "2.5.4.53" // deltaRevocationList

    const val PSEUDONYM = "2.5.4.65" // pseudonym

    // --- From RFC 4519 (pilot attribute types arc: 0.9.2342.19200300.100.1) ---
    const val UID = "0.9.2342.19200300.100.1.1" // userId (often 'userid' or 'uid')
    const val DC = "0.9.2342.19200300.100.1.25" // domainComponent

    // --- From PKCS#9 (RFC 2985) (arc: 1.2.840.113549.1.9) ---
    const val EMAIL_ADDRESS = "1.2.840.113549.1.9.1" // emailAddress (also 'E')
    const val UNSTRUCTURED_NAME = "1.2.840.113549.1.9.2" // unstructuredName
    const val CHALLENGE_PASSWORD = "1.2.840.113549.1.9.7" // challengePassword (used in CSRs)
    const val UNSTRUCTURED_ADDRESS = "1.2.840.113549.1.9.8" // unstructuredAddress
}

/**
 * Maps common X.500 Attribute Type OIDs to their standard or conventional short names.
 * Referencing RFC 4514 Appendix B where applicable for recommended strings.
 */
val OID_TO_SHORT_NAME_MAP: Map<String, String> =
    mapOf(
        // RFC 4519 / X.500 Core Types
        X500AttributeTypeOids.CN to "CN",
        X500AttributeTypeOids.SN to "SN",
        X500AttributeTypeOids.SERIALNUMBER to "SERIALNUMBER", // RFC 4514 recommends 'serialNumber'
        X500AttributeTypeOids.C to "C",
        X500AttributeTypeOids.L to "L",
        X500AttributeTypeOids.ST to "ST", // RFC 4514 recommends 'st'
        X500AttributeTypeOids.STREET to "STREET", // RFC 4514 recommends 'street'
        X500AttributeTypeOids.O to "O",
        X500AttributeTypeOids.OU to "OU",
        X500AttributeTypeOids.TITLE to "TITLE", // RFC 4514 recommends 'title'
        X500AttributeTypeOids.DESCRIPTION to "DESCRIPTION", // RFC 4514 recommends 'description'
        X500AttributeTypeOids.BUSINESS_CATEGORY to "BUSINESSCATEGORY", // RFC 4514 recommends 'businessCategory'
        X500AttributeTypeOids.POSTAL_ADDRESS to "POSTALADDRESS", // RFC 4514 recommends 'postalAddress'
        X500AttributeTypeOids.POSTAL_CODE to "POSTALCODE", // RFC 4514 recommends 'postalCode'
        X500AttributeTypeOids.POST_OFFICE_BOX to "POBOX", // RFC 4514 recommends 'postOfficeBox'
        X500AttributeTypeOids.PHYSICAL_DELIVERY_OFFICE_NAME to "PHYSICALDELIVERYOFFICENAME", // RFC 4514 recommends 'physicalDeliveryOfficeName'
        X500AttributeTypeOids.TELEPHONE_NUMBER to "TELEPHONE", // RFC 4514 recommends 'telephoneNumber'
        X500AttributeTypeOids.TELEX_NUMBER to "TELEXNUMBER", // RFC 4514 recommends 'telexNumber'
        X500AttributeTypeOids.FACSIMILE_TELEPHONE_NUMBER to "FAX", // RFC 4514 recommends 'facsimileTelephoneNumber'
        X500AttributeTypeOids.X121_ADDRESS to "X121", // RFC 4514 recommends 'x121Address'
        X500AttributeTypeOids.INTERNATIONAL_ISDN_NUMBER to "ISDN", // RFC 4514 recommends 'internationalISDNNumber'
        X500AttributeTypeOids.REGISTERED_ADDRESS to "REGISTEREDADDRESS", // RFC 4514 recommends 'registeredAddress'
        X500AttributeTypeOids.DESTINATION_INDICATOR to "DESTINATIONINDICATOR", // RFC 4514 recommends 'destinationIndicator'
        X500AttributeTypeOids.PREFERRED_DELIVERY_METHOD to "PREFERREDDELIVERYMETHOD", // RFC 4514 recommends 'preferredDeliveryMethod'
        X500AttributeTypeOids.PRESENTATION_ADDRESS to "PRESENTATIONADDRESS", // RFC 4514 recommends 'presentationAddress'
        X500AttributeTypeOids.MEMBER to "MEMBER", // RFC 4514 recommends 'member'
        X500AttributeTypeOids.OWNER to "OWNER", // RFC 4514 recommends 'owner'
        X500AttributeTypeOids.ROLE_OCCUPANT to "ROLE", // RFC 4514 recommends 'roleOccupant'
        X500AttributeTypeOids.SEE_ALSO to "SEEALSO", // RFC 4514 recommends 'seeAlso'
        X500AttributeTypeOids.NAME to "NAME", // RFC 4514 recommends 'name'
        X500AttributeTypeOids.GIVEN_NAME to "GIVENNAME", // RFC 4514 recommends 'givenName'
        X500AttributeTypeOids.INITIALS to "INITIALS", // RFC 4514 recommends 'initials'
        X500AttributeTypeOids.GENERATION_QUALIFIER to "GENERATIONQUALIFIER", // RFC 4514 recommends 'generationQualifier'
        X500AttributeTypeOids.UNIQUE_IDENTIFIER to "UNIQUEIDENTIFIER", // RFC 4514 recommends 'x500UniqueIdentifier'
        X500AttributeTypeOids.DN_QUALIFIER to "DNQUALIFIER", // RFC 4514 recommends 'dnQualifier'
        X500AttributeTypeOids.PSEUDONYM to "PSEUDONYM", // RFC 4514 recommends 'pseudonym'
        X500AttributeTypeOids.DISTINGUISHED_NAME to "DN", // RFC 4514 recommends 'distinguishedName'
        X500AttributeTypeOids.UNIQUE_MEMBER to "UNIQUEMEMBER", // RFC 4514 recommends 'uniqueMember'
        X500AttributeTypeOids.HOUSE_IDENTIFIER to "HOUSEIDENTIFIER", // RFC 4514 recommends 'houseIdentifier'
        // RFC 4519 Pilot / Common LDAP Types
        X500AttributeTypeOids.UID to "UID", // RFC 4514 recommends 'uid'
        X500AttributeTypeOids.DC to "DC", // RFC 4514 recommends 'dc'
        // PKCS#9 Types
        X500AttributeTypeOids.EMAIL_ADDRESS to "E", // Common practice uses 'E' or 'EMAIL', RFC 4514 doesn't list PKCS#9. Using 'E' for brevity.
        // Alternative: X500AttributeTypeOids.EMAIL_ADDRESS to "EMAIL", // More explicit
        X500AttributeTypeOids.UNSTRUCTURED_NAME to "UnstructuredName", // Keep CamelCase as per common representation
        X500AttributeTypeOids.UNSTRUCTURED_ADDRESS to "UnstructuredAddress", // Keep CamelCase
        X500AttributeTypeOids.CHALLENGE_PASSWORD to "ChallengePassword", // Keep CamelCase (less common in final DNs)
    )
