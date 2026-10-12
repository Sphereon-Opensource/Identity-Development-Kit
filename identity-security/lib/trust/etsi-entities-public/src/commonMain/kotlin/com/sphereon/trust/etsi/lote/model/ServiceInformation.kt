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
    val serviceTypeIdentifier: String? = null,
    @SerialName("ServiceName")
    val serviceName: List<MultiLangString>,
    @SerialName("ServiceDigitalIdentity")
    val serviceDigitalIdentity: LoTEServiceDigitalIdentity,
    @SerialName("ServiceStatus")
    val serviceStatus: String? = null,
    @SerialName("StatusStartingTime")
    val statusStartingTime: Instant? = null,
    @SerialName("ServiceSupplyPoints")
    val serviceSupplyPoints: List<LoTEServiceSupplyPoint> = emptyList(),
    @SerialName("SchemeServiceDefinitionURI")
    val schemeServiceDefinitionURI: List<MultiLangURI> = emptyList(),
    @SerialName("ServiceDefinitionURI")
    val teServiceDefinitionURI: List<MultiLangURI> = emptyList(),
    @SerialName("ServiceInformationExtensions")
    val serviceInformationExtensions: List<JsonElement> = emptyList(),
)

/** A typed supply point preserves its optional service discriminator. */
@JsExportCompat
@Serializable
data class LoTEServiceSupplyPoint(
    val uriValue: String,
    @SerialName("ServiceType")
    val serviceType: String? = null,
)

/** Historical entries require status and time and have no supply-point fields. */
@JsExportCompat
@Serializable
data class ServiceHistoryInstance(
    @SerialName("ServiceName")
    val serviceName: List<MultiLangString>,
    @SerialName("ServiceDigitalIdentity")
    val serviceDigitalIdentity: LoTEServiceDigitalIdentity,
    @SerialName("ServiceStatus")
    val serviceStatus: String,
    @SerialName("StatusStartingTime")
    val statusStartingTime: Instant,
    @SerialName("ServiceTypeIdentifier")
    val serviceTypeIdentifier: String? = null,
    @SerialName("ServiceInformationExtensions")
    val serviceInformationExtensions: List<JsonElement> = emptyList(),
) {
    fun toServiceInformation(): LoTEServiceInformation = LoTEServiceInformation(
        serviceTypeIdentifier = serviceTypeIdentifier,
        serviceName = serviceName,
        serviceDigitalIdentity = serviceDigitalIdentity,
        serviceStatus = serviceStatus,
        statusStartingTime = statusStartingTime,
        serviceInformationExtensions = serviceInformationExtensions,
    )
}

fun LoTEServiceInformation.toHistoryInstance(): ServiceHistoryInstance = ServiceHistoryInstance(
    serviceTypeIdentifier = serviceTypeIdentifier,
    serviceName = serviceName,
    serviceDigitalIdentity = serviceDigitalIdentity,
    serviceStatus = requireNotNull(serviceStatus) { "Historical ServiceStatus is required" },
    statusStartingTime = requireNotNull(statusStartingTime) { "Historical StatusStartingTime is required" },
    serviceInformationExtensions = serviceInformationExtensions,
)
