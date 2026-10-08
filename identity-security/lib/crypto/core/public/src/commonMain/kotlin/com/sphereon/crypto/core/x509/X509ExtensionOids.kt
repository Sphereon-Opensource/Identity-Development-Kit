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
 * Defines constants for common X.509 Certificate Extension Object Identifiers (OIDs).
 * Based primarily on RFC 5280, with additions from related standards.
 * The main arc for standard extensions is id-ce: joint-iso-itu-t(2) ds(5) certificateExtension(29)
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc5280">RFC 5280 - Section 4.2</a>
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc3739">RFC 3739 - Qualified Certificate Statements</a>
 */
object X509ExtensionOids {
    // --- Standard Extensions (id-ce arc: 2.5.29) ---

    /** OID for Subject Directory Attributes Extension (2.5.29.9). */
    const val SUBJECT_DIRECTORY_ATTRIBUTES = "2.5.29.9"

    /** OID for Subject Key Identifier Extension (2.5.29.14). */
    const val SUBJECT_KEY_IDENTIFIER = "2.5.29.14"

    /** OID for Key Usage Extension (2.5.29.15). */
    const val KEY_USAGE = "2.5.29.15"

    /** OID for Private Key Usage Period Extension (2.5.29.16) - OBSOLETE by RFC 5280. */
    const val PRIVATE_KEY_USAGE_PERIOD = "2.5.29.16" // Obsolete

    /** OID for Subject Alternative Name Extension (2.5.29.17). */
    const val SUBJECT_ALTERNATIVE_NAME = "2.5.29.17"

    /** OID for Issuer Alternative Name Extension (2.5.29.18). */
    const val ISSUER_ALTERNATIVE_NAME = "2.5.29.18"

    /** OID for Basic Constraints Extension (2.5.29.19). */
    const val BASIC_CONSTRAINTS = "2.5.29.19"

    /** OID for CRL Number Extension (2.5.29.20) - Used in CRLs. */
    const val CRL_NUMBER = "2.5.29.20"

    /** OID for Reason Code Extension (2.5.29.21) - Used in CRL entries. */
    const val REASON_CODE = "2.5.29.21"

    /** OID for Hold Instruction Code Extension (2.5.29.23) - Used in CRL entries (Obsolete by RFC 5280). */
    const val HOLD_INSTRUCTION_CODE = "2.5.29.23" // Obsolete

    /** OID for Invalidity Date Extension (2.5.29.24) - Used in CRL entries. */
    const val INVALIDITY_DATE = "2.5.29.24"

    /** OID for Delta CRL Indicator Extension (2.5.29.27) - Used in CRLs. */
    const val DELTA_CRL_INDICATOR = "2.5.29.27"

    /** OID for Issuing Distribution Point Extension (2.5.29.28) - Used in CRLs. */
    const val ISSUING_DISTRIBUTION_POINT = "2.5.29.28"

    /** OID for Certificate Issuer Extension (2.5.29.29) - Used in CRL entries. */
    const val CERTIFICATE_ISSUER = "2.5.29.29"

    /** OID for Name Constraints Extension (2.5.29.30). */
    const val NAME_CONSTRAINTS = "2.5.29.30"

    /** OID for CRL Distribution Points Extension (2.5.29.31). */
    const val CRL_DISTRIBUTION_POINTS = "2.5.29.31"

    /** OID for Certificate Policies Extension (2.5.29.32). */
    const val CERTIFICATE_POLICIES = "2.5.29.32"

    /** OID for Policy Mappings Extension (2.5.29.33). */
    const val POLICY_MAPPINGS = "2.5.29.33"

    /** OID for Policy Constraints Extension (2.5.29.36). */
    const val POLICY_CONSTRAINTS = "2.5.29.36"

    /** OID for Authority Key Identifier Extension (2.5.29.35). */
    const val AUTHORITY_KEY_IDENTIFIER = "2.5.29.35"

    /** OID for Extended Key Usage Extension (2.5.29.37). */
    const val EXTENDED_KEY_USAGE = "2.5.29.37"

    /** OID for Freshest CRL (Delta CRL Distribution Point) Extension (2.5.29.46). */
    const val FRESHEST_CRL = "2.5.29.46"

    /** OID for Inhibit anyPolicy Extension (2.5.29.54). */
    const val INHIBIT_ANY_POLICY = "2.5.29.54"

    // --- Private Internet Extensions (id-pe arc: 1.3.6.1.5.5.7.1) ---
    // Defined in RFC 5280 and other RFCs

    /** OID for Authority Information Access Extension (1.3.6.1.5.5.7.1.1). */
    const val AUTHORITY_INFORMATION_ACCESS = "1.3.6.1.5.5.7.1.1"

    /** OID for Subject Information Access Extension (1.3.6.1.5.5.7.1.11). */
    const val SUBJECT_INFORMATION_ACCESS = "1.3.6.1.5.5.7.1.11"

    /** OID for OCSP No Check Extension (1.3.6.1.5.5.7.48.1.5). */
    const val OCSP_NO_CHECK = "1.3.6.1.5.5.7.48.1.5" // Defined in RFC 6960

    // --- Qualified Certificate Statements (id-pe arc: 1.3.6.1.5.5.7.1) ---
    // Defined in RFC 3739

    /** OID for Qualified Certificate Statements Extension (1.3.6.1.5.5.7.1.3). */
    const val QC_STATEMENTS = "1.3.6.1.5.5.7.1.3"

    // --- Other potentially relevant OIDs often seen ---

    /** OID for Microsoft Certificate Template extension (1.3.6.1.4.1.311.21.7). Non-standard but common in AD CS. */
    const val MS_CERTIFICATE_TEMPLATE = "1.3.6.1.4.1.311.21.7" // Microsoft specific

    /** OID for Signed Certificate Timestamp List (for Certificate Transparency) (1.3.6.1.4.1.11129.2.4.2). */
    const val CT_PRECERT_SCTS = "1.3.6.1.4.1.11129.2.4.2" // Defined in RFC 6962

    /** OID for CA Issuers Access Method (1.3.6.1.5.5.7.48.2) */
    const val CA_ISSUERS = "1.3.6.1.5.5.7.48.2"
}
