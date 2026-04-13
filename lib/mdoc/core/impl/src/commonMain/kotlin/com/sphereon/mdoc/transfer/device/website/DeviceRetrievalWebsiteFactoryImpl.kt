/*
 * Copyright 2023-2026 Sphereon International B.V.
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
 */

package com.sphereon.mdoc.transfer.device.website

import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.mdoc.SessionDataCborCodec
import com.sphereon.mdoc.SessionEstablishmentCborCodec
import com.sphereon.mdoc.engagement.DeviceEngagementCborCodec
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("DeviceRetrievalWebsiteFactoryImpl", exact = true)
class DeviceRetrievalWebsiteFactoryImpl(
    logManager: SessionLogManager,
    private val httpClientFactory: HttpClientFactory,
    private val deviceEngagementCborCodec: DeviceEngagementCborCodec,
    private val sessionEstablishmentCborCodec: SessionEstablishmentCborCodec,
    private val sessionDataCborCodec: SessionDataCborCodec,
) : DeviceRetrievalWebsiteFactory {
    private val log = logManager.withTag("DeviceRetrievalWebsite")

    override fun createFromReaderEngagement(
        readerEngagement: ReaderEngagement,
        options: HttpClientOptions?,
    ): DeviceRetrievalWebsite =
        DeviceRetrievalWebsiteImpl(
            readerEngagement = readerEngagement,
            log = log,
            httpClientFactory = httpClientFactory,
            options = options,
            deviceEngagementCborCodec = deviceEngagementCborCodec,
            sessionEstablishmentCborCodec = sessionEstablishmentCborCodec,
            sessionDataCborCodec = sessionDataCborCodec,
        )

    override fun create(options: HttpClientOptions?): DeviceRetrievalWebsite =
        DeviceRetrievalWebsiteImpl(
            log = log,
            httpClientFactory = httpClientFactory,
            options = options,
            deviceEngagementCborCodec = deviceEngagementCborCodec,
            sessionEstablishmentCborCodec = sessionEstablishmentCborCodec,
            sessionDataCborCodec = sessionDataCborCodec,
        )

    @ContributesTo(SessionScope::class)
    interface Graph {
        val deviceRetrievalWebsiteFactory: DeviceRetrievalWebsiteFactory
    }
}
