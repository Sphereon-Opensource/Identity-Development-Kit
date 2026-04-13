/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.mdoc.transfer.device.website

import com.sphereon.core.api.log.LogService
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.mdoc.SessionData
import com.sphereon.mdoc.SessionEstablishment
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.transfer.device.RestApiOptions
import com.sphereon.mdoc.transfer.device.website.DeviceRetrievalWebsiteFactory.Defaults.defaultHttpClientOptions
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.cbor.*
import kotlinx.serialization.cbor.Cbor
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName


@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRetrievalWebsiteFactory", exact = true)
interface DeviceRetrievalWebsiteFactory {
    val log: LogService
    fun createFromReaderEngagement(readerEngagement: ReaderEngagement, options: HttpClientOptions = defaultHttpClientOptions(log)): DeviceRetrievalWebsite

    fun create(options: HttpClientOptions = defaultHttpClientOptions(log)): DeviceRetrievalWebsite

    companion object Defaults {
        fun defaultHttpClientOptions(log: LogService) = HttpClientOptions(
            enableHttpCache = false,
            enableLogging = true,
            loggingConfig = LoggerConfig.Default,
            enableContentNegotiation = true,
            contentNegotiationConfig = {
                cbor(Cbor {
                    encodeDefaults = true
                    ignoreUnknownKeys = true
                })
            },
            defaultRequest = {
                contentType(ContentType.Application.Cbor)
                accept(ContentType.Application.Cbor)
            }
        )
    }
}


@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRetrievalWebsite", exact = true)
interface DeviceRetrievalWebsite : AutoCloseable {
    val log: LogService
    var options: HttpClientOptions
    val readerEngagement: ReaderEngagement?
    suspend fun sendDeviceEngagement(
        deviceEngagement: DeviceEngagement,
        options: HttpClientOptions = defaultHttpClientOptions(log),
        restApiOptions: RestApiOptions? = null
    ): SessionEstablishment

    suspend fun sendSessionData(sessionData: SessionData): SessionData
}
