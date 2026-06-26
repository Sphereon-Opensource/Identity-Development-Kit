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
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Optional binding from an Identity to any Party specialization.
 *
 * This is deliberately separate from the identifier layer: Party owns readable profile data,
 * while identifiers own matching and protected lookup data.
 */
@JsExportCompat
@Serializable
data class IdentityPartyBinding
    @JvmOverloads
    constructor(
        @SerialName("identityId")
        val identityId: Uuid,
        @SerialName("partyId")
        val partyId: Uuid,
        @SerialName("partyType")
        val partyType: PartyType,
        @SerialName("bindingType")
        val bindingType: String,
        @SerialName("validFrom")
        val validFrom: Instant,
        @SerialName("validUntil")
        val validUntil: Instant? = null,
    )
