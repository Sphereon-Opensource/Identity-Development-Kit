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

package com.sphereon.data.store.party.result

import com.sphereon.data.store.party.model.CorrelationIdentifier
import com.sphereon.data.store.party.model.IdentifierType
import com.sphereon.data.store.party.model.IdentifierX509
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Result model for correlation identifier queries.
 *
 * Contains all core identifier fields plus optional extension data that can be
 * populated based on [CorrelationIdentifierFetchOptions].
 *
 * Usage:
 * ```kotlin
 * // Create from entity
 * val result = CorrelationIdentifierResult.from(identifier)
 *
 * // Create with X.509 extension
 * val resultWithX509 = CorrelationIdentifierResult.from(
 *     identifier = identifier,
 *     x509Extension = x509Data
 * )
 * ```
 */
@Serializable
data class CorrelationIdentifierResult(
    /** Unique identifier for this correlation identifier */
    @SerialName("id")
    val correlationId: Uuid,
    /** The identity this identifier belongs to (identity.party_id) */
    @SerialName("identityId")
    val identityId: Uuid,
    /** Tenant this identifier belongs to */
    @SerialName("tenantId")
    val tenantId: String,
    /** The type of identifier */
    @SerialName("identifierType")
    val identifierType: IdentifierType,
    /** The actual identifier value (DID string, email, phone number, VAT number, etc.) */
    val value: String,
    /** Whether this is the primary identifier for its type */
    @SerialName("isPrimary")
    val isPrimary: Boolean,
    /** Whether this identifier has been verified */
    @SerialName("isVerified")
    val isVerified: Boolean,
    /** When the identifier was verified */
    @SerialName("verifiedAt")
    val verifiedAt: Instant? = null,
    /** When this identifier became active */
    @SerialName("validFrom")
    val validFrom: Instant,
    /** When this identifier expired (null = current/active) */
    @SerialName("validUntil")
    val validUntil: Instant? = null,
    /** When the identifier was created */
    @SerialName("createdAt")
    val createdAt: Instant,
    /** Who created the identifier (party ID) */
    @SerialName("createdById")
    val createdById: Uuid? = null,
    /** When the identifier was last updated */
    @SerialName("updatedAt")
    val updatedAt: Instant,
    /** Who last updated the identifier (party ID) */
    @SerialName("updatedById")
    val updatedById: Uuid? = null,
    /** When the identifier was soft-deleted (null if not deleted) */
    @SerialName("deletedAt")
    val deletedAt: Instant? = null,
    /** Who deleted the identifier (party ID) */
    @SerialName("deletedById")
    val deletedById: Uuid? = null,
    /**
     * X.509 certificate extension data.
     * Populated when [CorrelationIdentifierFetchOptions.includeX509Extension] is true
     * and [identifierType] is [IdentifierType.X509].
     * Null means not fetched (vs not applicable for other identifier types).
     */
    @SerialName("x509Extension")
    val x509Extension: X509ExtensionData? = null,
    /**
     * Registration extension data (e.g., VAT registration).
     * Populated when [CorrelationIdentifierFetchOptions.includeRegistrationExtension] is true.
     */
    @SerialName("registrationExtension")
    val registrationExtension: RegistrationExtensionData? = null,
    /**
     * Electronic address extension data (e.g., PEPPOL identifiers).
     * Populated when [CorrelationIdentifierFetchOptions.includeElectronicExtension] is true.
     */
    @SerialName("electronicExtension")
    val electronicExtension: ElectronicExtensionData? = null,
) {
    companion object {
        /**
         * Create a CorrelationIdentifierResult from a CorrelationIdentifier entity.
         *
         * @param identifier The source identifier entity
         * @param x509Extension Optional X.509 extension data
         * @param registrationExtension Optional registration extension data
         * @param electronicExtension Optional electronic address extension data
         */
        fun from(
            identifier: CorrelationIdentifier,
            x509Extension: X509ExtensionData? = null,
            registrationExtension: RegistrationExtensionData? = null,
            electronicExtension: ElectronicExtensionData? = null,
        ) = CorrelationIdentifierResult(
            correlationId = identifier.correlationId,
            identityId = identifier.identityId,
            tenantId = identifier.tenantId,
            identifierType = identifier.identifierType,
            value = identifier.value,
            isPrimary = identifier.isPrimary,
            isVerified = identifier.isVerified,
            verifiedAt = identifier.verifiedAt,
            validFrom = identifier.validFrom,
            validUntil = identifier.validUntil,
            createdAt = identifier.createdAt,
            createdById = identifier.createdById,
            updatedAt = identifier.updatedAt,
            updatedById = identifier.updatedById,
            deletedAt = identifier.deletedAt,
            deletedById = identifier.deletedById,
            x509Extension = x509Extension,
            registrationExtension = registrationExtension,
            electronicExtension = electronicExtension,
        )

        /**
         * Create a CorrelationIdentifierResult from an IdentifierX509 entity.
         */
        fun fromX509(x509: IdentifierX509) =
            from(
                identifier = x509.correlationIdentifier,
                x509Extension =
                    X509ExtensionData(
                        issuerDn = x509.issuerDn,
                        subjectDn = x509.subjectDn,
                        serialNumber = x509.serialNumber,
                        certificatePem = x509.certificatePem,
                        notBefore = x509.notBefore,
                        notAfter = x509.notAfter,
                    ),
            )
    }

    /** Convert back to the core CorrelationIdentifier entity (without extensions) */
    fun toCorrelationIdentifier() =
        CorrelationIdentifier(
            correlationId = correlationId,
            identityId = identityId,
            tenantId = tenantId,
            identifierType = identifierType,
            value = value,
            isPrimary = isPrimary,
            isVerified = isVerified,
            verifiedAt = verifiedAt,
            validFrom = validFrom,
            validUntil = validUntil,
            createdAt = createdAt,
            createdById = createdById,
            updatedAt = updatedAt,
            updatedById = updatedById,
            deletedAt = deletedAt,
            deletedById = deletedById,
        )
}

/**
 * X.509 certificate extension data embedded in [CorrelationIdentifierResult].
 */
@Serializable
data class X509ExtensionData(
    /** Distinguished Name of the certificate issuer */
    @SerialName("issuerDn")
    val issuerDn: String? = null,
    /** Distinguished Name of the certificate subject */
    @SerialName("subjectDn")
    val subjectDn: String? = null,
    /** Serial number of the certificate */
    @SerialName("serialNumber")
    val serialNumber: String? = null,
    /** The full PEM-encoded certificate */
    @SerialName("certificatePem")
    val certificatePem: String? = null,
    /** Certificate validity start date */
    @SerialName("notBefore")
    val notBefore: Instant? = null,
    /** Certificate validity end date */
    @SerialName("notAfter")
    val notAfter: Instant? = null,
)

/**
 * Registration extension data embedded in [CorrelationIdentifierResult].
 * Used for identifiers like VAT numbers, LEI, DUNS, etc.
 */
@Serializable
data class RegistrationExtensionData(
    /** Registration authority (e.g., "EU VAT", "GLEIF") */
    @SerialName("authority")
    val authority: String? = null,
    /** Country code of registration */
    @SerialName("countryCode")
    val countryCode: String? = null,
    /** Registration date */
    @SerialName("registrationDate")
    val registrationDate: Instant? = null,
    /** Expiration date of the registration */
    @SerialName("expirationDate")
    val expirationDate: Instant? = null,
    /** Registration status (e.g., "ACTIVE", "REVOKED") */
    @SerialName("status")
    val status: String? = null,
)

/**
 * Electronic address extension data embedded in [CorrelationIdentifierResult].
 * Used for identifiers like PEPPOL participant IDs, AS4 endpoints, etc.
 */
@Serializable
data class ElectronicExtensionData(
    /** Electronic address scheme (e.g., "PEPPOL", "AS4") */
    @SerialName("scheme")
    val scheme: String? = null,
    /** Endpoint URL for electronic communication */
    @SerialName("endpointUrl")
    val endpointUrl: String? = null,
    /** Transport profile (e.g., "AS4", "AS2") */
    @SerialName("transportProfile")
    val transportProfile: String? = null,
    /** Document types supported */
    @SerialName("documentTypes")
    val documentTypes: List<String>? = null,
    /** Service metadata URL */
    @SerialName("serviceMetadataUrl")
    val serviceMetadataUrl: String? = null,
)
