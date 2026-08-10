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

package com.sphereon.data.store.party.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * The type of identity identifier.
 * Each type may have an extension table with type-specific data.
 *
 * This is an extensible type - core identity types (DID, X509) are predefined,
 * while downstream projects can add additional types for their specific use cases
 * (e.g., VAT, LEI, EMAIL, PHONE for business identifiers).
 *
 * Usage:
 * ```kotlin
 * // Use predefined constants
 * val type = IdentifierType.DID
 *
 * // Create custom types
 * val customType = IdentifierType("vat")
 * ```
 */
@Serializable
@JvmInline
value class IdentifierType(
    val value: String,
) {
    companion object {
        /** W3C Decentralized Identifier */
        val DID = IdentifierType("did")

        /** X.509 Certificate (has identifier_x509 extension) */
        val X509 = IdentifierType("x509")

        val VAT = IdentifierType("vat")
        val NTR = IdentifierType("ntr")
        val PSD = IdentifierType("psd")
        val LEI = IdentifierType("lei")
        val LEGAL_LOCAL = IdentifierType("legal_local")

        val PAS = IdentifierType("pas")
        val IDC = IdentifierType("idc")
        val PNO = IdentifierType("pno")
        val TIN = IdentifierType("tin")
        val TAX = IdentifierType("tax")
        val EID = IdentifierType("eid")
        val NATURAL_LOCAL = IdentifierType("natural_local")

        val EORI = IdentifierType("eori")
        val EUID = IdentifierType("euid")
        val VATIN = IdentifierType("vatin")
        val LEGAL_TIN = IdentifierType("legal_tin")
        val EXCISE = IdentifierType("excise")

        val ISO6523_ORG_ID = IdentifierType("iso6523_org_id")
        val VLEI = IdentifierType("vlei")
        val BIC = IdentifierType("bic")
        val ISNI = IdentifierType("isni")
        val IBAN = IdentifierType("iban")
        val IIN = IdentifierType("iin")
        val PAN = IdentifierType("pan")
        val ISIN = IdentifierType("isin")
        val MIC = IdentifierType("mic")
        val UUID = IdentifierType("uuid")
        val OID = IdentifierType("oid")
        val ISO15459_ID = IdentifierType("iso15459_id")
        val VIN = IdentifierType("vin")
        val WMI = IdentifierType("wmi")
        val CONTAINER_ID = IdentifierType("container_id")

        val EMAIL = IdentifierType("email")
        val PHONE = IdentifierType("phone")
        val PASSKEY_CREDENTIAL_ID = IdentifierType("passkey_credential_id")
        val DOMAIN = IdentifierType("domain")
        val URL = IdentifierType("url")
        val WEBSITE = IdentifierType("website")
        val ISSUER = IdentifierType("issuer")
        val OIDC_ISSUER = IdentifierType("oidc_issuer")
        val OID4VCI_ISSUER = IdentifierType("oid4vci_issuer")
        val OIDC_FEDERATION_ENTITY = IdentifierType("oidc_federation_entity")
        val ETSI_TRUST_LIST = IdentifierType("etsi_trust_list")
        val VERIFIER = IdentifierType("verifier")
        /** Credential issuer signing key identifier (`kid`). */
        val JWK = IdentifierType("jwk")
        val JWKS_URL = IdentifierType("jwks_url")
    }

    override fun toString(): String = value
}
