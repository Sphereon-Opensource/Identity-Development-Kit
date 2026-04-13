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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * X.509 certificate identifier, extending [CorrelationIdentifier] with certificate-specific data.
 *
 * This uses composition to include all base identifier fields plus X.509-specific extensions.
 * The [identifierType] of the embedded [correlationIdentifier] should be [IdentifierType.X509].
 *
 * Usage:
 * ```kotlin
 * val x509Id = IdentifierX509(
 *     correlationIdentifier = CorrelationIdentifier(
 *         correlationId = Uuid.random(),
 *         identityId = identityUuid,
 *         tenantId = tenantUuid,
 *         identifierType = IdentifierType.X509,
 *         value = "CN=...",
 *         validFrom = Clock.System.now(),
 *         createdAt = Clock.System.now(),
 *         updatedAt = Clock.System.now()
 *     ),
 *     issuerDn = "CN=Issuer",
 *     subjectDn = "CN=Subject",
 *     serialNumber = "123456"
 * )
 *
 * // Access base fields via correlationIdentifier
 * val id = x509Id.id // Delegates to correlationIdentifier.id
 * val value = x509Id.correlationIdentifier.value
 * ```
 */
@Serializable
data class IdentifierX509(
    /** The base correlation identifier containing common fields */
    @SerialName("correlationIdentifier")
    val correlationIdentifier: CorrelationIdentifier,
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
    /** Delegates to the embedded correlation identifier's ID */
    override val id: String get() = correlationIdentifier.id

    /** The correlation identifier's UUID */
    val correlationId: Uuid get() = correlationIdentifier.correlationId

    /** Convenience accessor for the identity this identifier belongs to */
    val identityId: Uuid get() = correlationIdentifier.identityId

    /** Convenience accessor for the tenant */
    val tenantId: String get() = correlationIdentifier.tenantId

    /** Convenience accessor for the identifier value (typically subject DN or fingerprint) */
    val value: String get() = correlationIdentifier.value
}
