/*
 * Copyright 2026 Sphereon International B.V.
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

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmOverloads
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Provenance for an [IdentityIdentifier] projected from Party data or an external assertion.
 */
@JsExportCompat
@Serializable
enum class IdentityIdentifierSourceKind {
    @SerialName("manual")
    MANUAL,

    @SerialName("partyElectronicAddress")
    PARTY_ELECTRONIC_ADDRESS,

    @SerialName("partyPhysicalAddress")
    PARTY_PHYSICAL_ADDRESS,

    @SerialName("partyRegistration")
    PARTY_REGISTRATION,

    @SerialName("softwareEndpoint")
    SOFTWARE_ENDPOINT,

    @SerialName("serviceEndpoint")
    SERVICE_ENDPOINT,

    @SerialName("certificate")
    CERTIFICATE,

    @SerialName("externalAssertion")
    EXTERNAL_ASSERTION,
}

@JsExportCompat
@Serializable
data class IdentityIdentifierSourceRef
    @JvmOverloads
    constructor(
        @SerialName("sourceKind")
        val sourceKind: IdentityIdentifierSourceKind,
        @SerialName("sourcePartyId")
        val sourcePartyId: Uuid? = null,
        @SerialName("sourceRecordId")
        val sourceRecordId: String? = null,
        @SerialName("sourceField")
        val sourceField: String? = null,
        @SerialName("projectionVersion")
        val projectionVersion: String? = null,
    )
