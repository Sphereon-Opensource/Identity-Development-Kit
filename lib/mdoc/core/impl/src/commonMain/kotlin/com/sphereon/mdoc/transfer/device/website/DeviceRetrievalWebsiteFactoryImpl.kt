package com.sphereon.mdoc.transfer.device.website

import com.sphereon.core.api.log.AppLogManager
import com.sphereon.core.api.log.SessionLogManager
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.mdoc.transfer.reader.ReaderEngagement
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
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
    private val httpClientFactory: HttpClientFactory
) : DeviceRetrievalWebsiteFactory {

    @ContributesTo(SessionScope::class)
    @OptIn(ExperimentalObjCName::class)
    @ObjCName("Component", exact = true)
    interface Component {
        val deviceRetrievalWebsiteFactory: DeviceRetrievalWebsiteFactory
    }

    override val log = logManager.withTag("DeviceRetrievalWebsite")



    override fun createFromReaderEngagement(readerEngagement: ReaderEngagement, options: HttpClientOptions): DeviceRetrievalWebsite =
        DeviceRetrievalWebsiteImpl(readerEngagement = readerEngagement, log = log, httpClientFactory = httpClientFactory, options = options)

    override fun create(options: HttpClientOptions): DeviceRetrievalWebsite =
        DeviceRetrievalWebsiteImpl(log = log, httpClientFactory = httpClientFactory, options = options)

}
