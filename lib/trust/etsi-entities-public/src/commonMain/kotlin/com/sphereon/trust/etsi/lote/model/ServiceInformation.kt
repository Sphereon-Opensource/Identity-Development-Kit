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

package com.sphereon.trust.etsi.lote.model

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlin.time.Instant

/**
 * Service information per ETSI TS 119 602.
 */
@JsExportCompat
@Serializable
data class LoTEServiceInformation(
    @SerialName("ServiceTypeIdentifier")
    val serviceTypeIdentifier: String,
    @SerialName("ServiceName")
    val serviceName: List<MultiLangString>,
    @SerialName("ServiceDigitalIdentity")
    val serviceDigitalIdentity: LoTEServiceDigitalIdentity,
    @SerialName("ServiceStatus")
    val serviceStatus: String? = null,
    @SerialName("StatusStartingTime")
    val statusStartingTime: Instant? = null,
    @SerialName("ServiceSupplyPoints")
    val serviceSupplyPoints: List<String> = emptyList(),
    @SerialName("SchemeServiceDefinitionURI")
    val schemeServiceDefinitionURI: List<MultiLangURI> = emptyList(),
    @SerialName("TEServiceDefinitionURI")
    val teServiceDefinitionURI: List<MultiLangURI> = emptyList(),
    @SerialName("ServiceInformationExtensions")
    val serviceInformationExtensions: List<JsonElement> = emptyList(),
)

/**
 * Service history instance — reuses the LoTEServiceInformation shape.
 */
typealias ServiceHistoryInstance = LoTEServiceInformation
