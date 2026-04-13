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

package com.sphereon.mdoc.transfer.device.website

import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.mdoc.SessionData
import com.sphereon.mdoc.SessionEstablishment
import com.sphereon.mdoc.engagement.DeviceEngagement
import com.sphereon.mdoc.transfer.device.RestApiOptions
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRetrievalWebsiteFactory", exact = true)
interface DeviceRetrievalWebsiteFactory {
    fun createFromReaderEngagement(
        readerEngagement: ReaderEngagement,
        options: HttpClientOptions? = null,
    ): DeviceRetrievalWebsite

    fun create(options: HttpClientOptions? = null): DeviceRetrievalWebsite
}

@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRetrievalWebsite", exact = true)
interface DeviceRetrievalWebsite : AutoCloseable {
    var options: HttpClientOptions
    val readerEngagement: ReaderEngagement?

    suspend fun sendDeviceEngagement(
        deviceEngagement: DeviceEngagement,
        options: HttpClientOptions? = null,
        restApiOptions: RestApiOptions? = null,
    ): SessionEstablishment

    suspend fun sendSessionData(sessionData: SessionData): SessionData
}
