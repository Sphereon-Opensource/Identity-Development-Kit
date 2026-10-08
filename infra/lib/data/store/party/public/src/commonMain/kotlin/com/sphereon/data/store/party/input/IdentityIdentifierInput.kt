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

@file:OptIn(ExperimentalUuidApi::class)

package com.sphereon.data.store.party.input

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.data.store.party.model.IdentityIdentifierSourceRef
import com.sphereon.data.store.party.model.ProtectedIdentifierValue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Input for creating a new identity identifier.
 *
 * The [id] is optional - if not provided, the system will generate one.
 */
@JsExportCompat
@Serializable
data class IdentityIdentifierCreateInput
    @JvmOverloads
    constructor(
        /** Optional ID - if null, system generates one */
        val id: Uuid? = null,
        /** The identity this identifier belongs to */
        @SerialName("identityId")
        val identityId: Uuid,
        /** The type of identifier (DID, X509, VAT, etc.) */
        @SerialName("identifierType")
        val identifierType: IdentifierType,
        /** Stored lookup token: normalized plaintext for plaintext policy, or blind-index value for protected policy. */
        @SerialName("lookupValue")
        val lookupValue: String,
        /** Whether this is the primary identifier for the identity */
        @SerialName("isPrimary")
        val isPrimary: Boolean = false,
        /** Whether this identifier has been verified */
        @SerialName("isVerified")
        val isVerified: Boolean = false,
        /** When the identifier was verified */
        @SerialName("verifiedAt")
        val verifiedAt: Instant? = null,
        /** When this identifier becomes valid */
        @SerialName("validFrom")
        val validFrom: Instant,
        /** When this identifier expires (null = no expiry) */
        @SerialName("validUntil")
        val validUntil: Instant? = null,
        /** X.509 extension data (required for X509 identifier type) */
        @SerialName("x509Extension")
        val x509Extension: IdentifierX509CreateInput? = null,
        /** Registration extension data (for VAT, LEI, etc.) */
        @SerialName("registrationExtension")
        val registrationExtension: IdentifierRegistrationCreateInput? = null,
        /** Electronic address extension data (for EMAIL, PHONE, URL) */
        @SerialName("electronicExtension")
        val electronicExtension: IdentifierElectronicCreateInput? = null,
        /**
         * Optional at-rest protection envelope for the identifier value. When present, the
         * persistence layer stores the protection mode plus whichever representations the
         * envelope carries (plaintext, ciphertext, blind-index HMAC and key references).
         * The IDK lite model performs no crypto itself; callers obtain the envelope from an
         * identifier protector in a higher layer.
         */
        @SerialName("protectedValue")
        val protectedValue: ProtectedIdentifierValue? = null,
        /** Optional source reference when this identifier is projected from Party data. */
        @SerialName("sourceRef")
        val sourceRef: IdentityIdentifierSourceRef? = null,
    )

/**
 * Input for updating an existing identity identifier.
 *
 * The [id] is required to identify which identifier to update.
 * All other fields are optional - only non-null values will be updated.
 */
@JsExportCompat
@Serializable
data class IdentityIdentifierUpdateInput
    @JvmOverloads
    constructor(
        /** Required - the identifier to update */
        val id: Uuid,
        /** New primary flag (null = keep current) */
        @SerialName("isPrimary")
        val isPrimary: Boolean? = null,
        /** New verified flag (null = keep current) */
        @SerialName("isVerified")
        val isVerified: Boolean? = null,
        /** New verification time (null = keep current) */
        @SerialName("verifiedAt")
        val verifiedAt: Instant? = null,
        /** New expiry time (null = keep current) */
        @SerialName("validUntil")
        val validUntil: Instant? = null,
    )

/**
 * X.509 certificate extension data for creating an identifier.
 */
@JsExportCompat
@Serializable
data class IdentifierX509CreateInput
    @JvmOverloads
    constructor(
        /** Distinguished Name of the certificate issuer */
        @SerialName("issuerDn")
        val issuerDn: String? = null,
        /** Distinguished Name of the certificate subject */
        @SerialName("subjectDn")
        val subjectDn: String? = null,
        /** Certificate serial number */
        @SerialName("serialNumber")
        val serialNumber: String? = null,
        /** PEM-encoded certificate */
        @SerialName("certificatePem")
        val certificatePem: String? = null,
        /** Certificate validity start */
        @SerialName("notBefore")
        val notBefore: Instant? = null,
        /** Certificate validity end */
        @SerialName("notAfter")
        val notAfter: Instant? = null,
    )

/**
 * Business registration extension data for creating an identifier.
 */
@JsExportCompat
@Serializable
data class IdentifierRegistrationCreateInput
    @JvmOverloads
    constructor(
        /** Type of registration (e.g., "VAT", "LEI", "KVK") */
        @SerialName("registrationType")
        val registrationType: String,
        /** Authority that issued the registration */
        @SerialName("issuingAuthority")
        val issuingAuthority: String? = null,
        /** Country where registration is valid (ISO 3166-1 alpha-2) */
        @SerialName("jurisdictionCountry")
        val jurisdictionCountry: String? = null,
    )

/**
 * Electronic address extension data for creating an identifier.
 */
@JsExportCompat
@Serializable
data class IdentifierElectronicCreateInput
    @JvmOverloads
    constructor(
        /** Type of electronic address (e.g., "work", "personal", "support") */
        @SerialName("electronicType")
        val electronicType: String,
        /** User-friendly label (e.g., "Work Email", "Mobile Phone") */
        val label: String? = null,
    )
