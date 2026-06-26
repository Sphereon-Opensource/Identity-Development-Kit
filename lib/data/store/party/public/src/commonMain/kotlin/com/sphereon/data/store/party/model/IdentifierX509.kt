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

package com.sphereon.data.store.party.model

import com.sphereon.core.api.HasId
import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * X.509 certificate identifier, extending [IdentityIdentifier] with certificate-specific data.
 *
 * This uses composition to include all base identifier fields plus X.509-specific extensions.
 * The [identifierType] of the embedded [identityIdentifier] should be [IdentifierType.X509].
 *
 * Usage:
 * ```kotlin
 * val x509Id = IdentifierX509(
 *     identityIdentifier = IdentityIdentifier(
 *         identityIdentifierId = Uuid.random(),
 *         identityId = identityUuid,
 *         tenantId = tenantUuid,
 *         identifierType = IdentifierType.X509,
 *         lookupValue = "CN=...",
 *         validFrom = Clock.System.now(),
 *         createdAt = Clock.System.now(),
 *         updatedAt = Clock.System.now()
 *     ),
 *     issuerDn = "CN=Issuer",
 *     subjectDn = "CN=Subject",
 *     serialNumber = "123456"
 * )
 *
 * // Access base fields via identityIdentifier
 * val id = x509Id.id // Delegates to identityIdentifier.id
 * val lookupValue = x509Id.identityIdentifier.lookupValue
 * ```
 */
@JsExportCompat
@Serializable
data class IdentifierX509
    @JvmOverloads
    constructor(
        /** The base identity identifier containing common fields */
        @SerialName("identityIdentifier")
        val identityIdentifier: IdentityIdentifier,
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
    ) : HasId {
        /** Delegates to the embedded identity identifier's ID */
        override val id: String get() = identityIdentifier.id

        /** The identity identifier's UUID */
        val identityIdentifierId: Uuid get() = identityIdentifier.identityIdentifierId

        /** Convenience accessor for the identity this identifier belongs to */
        val identityId: Uuid get() = identityIdentifier.identityId

        /** Convenience accessor for the tenant */
        val tenantId: String get() = identityIdentifier.tenantId

        /** Convenience accessor for the stored lookup token. */
        val lookupValue: String get() = identityIdentifier.lookupValue
    }
