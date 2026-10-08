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

package com.sphereon.mdoc.transport.restapi

import com.sphereon.cbor.CborEncoder
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.cose.CoseKeyCborCodec
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.ktor.http.client.provider.UrlValidationPolicy
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.SessionDataCborCodec
import com.sphereon.mdoc.SessionEstablishmentCborCodec
import com.sphereon.mdoc.SessionTranscriptCborCodec
import com.sphereon.mdoc.data.device.DeviceRequestCborCodec
import com.sphereon.mdoc.engagement.DeviceEngagementCborCodec
import com.sphereon.mdoc.engagement.EngagementData
import com.sphereon.mdoc.transfer.reader.ReaderEngagementCborCodec
import com.sphereon.mdoc.transport.ConnectionMethod
import com.sphereon.mdoc.transport.ConnectionMethodBase
import com.sphereon.mdoc.transport.MdocTransport
import com.sphereon.mdoc.transport.MdocTransportFactory
import com.sphereon.mdoc.transport.TransportType
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.HttpClient
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Factory for creating REST API transport instances.
 *
 * This factory is automatically registered via dependency injection when the
 * REST API transport module is on the classpath. It provides REST API-specific
 * transport creation and connection method parsing.
 *
 * ## Automatic Registration
 *
 * The `@ContributesBinding` annotation ensures that when this module is included
 * in the application, it will automatically register itself with the transport
 * registry without any manual configuration.
 *
 * ## SessionScope Rationale
 *
 * This factory is scoped to `SessionScope` (tenant-specific) rather than `AppScope` because:
 * - It requires `HttpClientFactory` which needs access to tenant-specific KMS for mTLS certificates
 * - Different tenants may have different SSL/TLS certificate requirements
 * - The transport registry that collects these factories is also SessionScope
 *
 * ## Usage
 *
 * ```kotlin
 * // In application code - the factory is auto-discovered
@OptIn(ExperimentalObjCName::class)
@ObjCName("MdocReaderEngagementManagerImpl", exact = true)
 * class MdocReaderEngagementManagerImpl(
 *     private val transportRegistry: MdocTransportRegistry
 * ) {
 *     init {
 *         // REST API factory is automatically available if module is on classpath
 *         val restApiSupported = transportRegistry.isSupported(TransportType.REST_API)
 *     }
 *
 *     suspend fun connect(deviceEngagement: DeviceEngagement) {
 *         val connectionMethod = extractConnectionMethod(deviceEngagement)
 *         val factory = transportRegistry.getFactory(connectionMethod)
 *         val transfer = factory.createTransfer(connectionMethod, execution, MdocRole.MDOC_READER)
 *         transfer.open(readerKey, RestApiHandle(uri))
 *     }
 * }
 * ```
 *
 * ## REST API Characteristics
 *
 * Unlike BLE/NFC, REST API is stateless:
 * - No persistent connection
 * - Single HTTP POST request
 * - No modes (central/peripheral)
 * - Holder POSTs to reader's server
 *
 * ## ISO 18013-7 Compliance
 *
 * This implements "Device Retrieval to Website" as specified in ISO 18013-7 Annex A.
 * The reader provides a URI in the device engagement, and the holder POSTs the
 * DeviceResponse to that URI via HTTPS.
 *
 * ## HTTP Client Configuration
 *
 * The factory creates its own HTTP client instance configured for mdoc REST API:
 * - HTTPS only (enforced by RestApiConnectionMethod)
 * - Content negotiation with CBOR support
 * - Proper timeouts for network operations
 * - Certificate validation (platform-specific)
 * - mTLS support via tenant-specific certificates from KMS
 *
 * This avoids polluting the application's DI graph with a singleton HttpClient
 * that might conflict with application-specific HTTP client configuration.
 *
 * @param httpClientFactory Factory for creating platform-specific HTTP clients with tenant-specific SSL config
 * @param logManager Logging service
 */
@Inject
@ContributesIntoSet(SessionScope::class, binding = binding<MdocTransportFactory>())
@SingleIn(SessionScope::class)
@OptIn(ExperimentalObjCName::class)
@ObjCName("RestApiTransportFactory", exact = true)
class RestApiTransportFactory(
    private val httpClientFactory: HttpClientFactory,
    private val cborEncoder: CborEncoder,
    private val deviceRequestCborCodec: DeviceRequestCborCodec,
    private val deviceEngagementCborCodec: DeviceEngagementCborCodec,
    private val readerEngagementCborCodec: ReaderEngagementCborCodec,
    private val sessionEstablishmentCborCodec: SessionEstablishmentCborCodec,
    private val sessionDataCborCodec: SessionDataCborCodec,
    private val sessionTranscriptCborCodec: SessionTranscriptCborCodec,
    private val coseKeyCborCodec: CoseKeyCborCodec,
) : MdocTransportFactory {
    /**
     * HTTP client configured for REST API transport.
     * Created lazily on first use.
     */
    private val httpClient: HttpClient by lazy {
        httpClientFactory.createClient(
            HttpClientOptions.createDefault().copy(
                engine = httpClientFactory.getEngineTypeDefault(),
                enableHttpCache = false,
                enableLogging = true,
                followRedirects = false,
                urlValidation = UrlValidationPolicy.BLOCK_PRIVATE,
            ),
        )
    }

    override val transportType: TransportType = TransportType.REST_API

    override fun supports(connectionMethod: ConnectionMethod): Boolean = connectionMethod is RestApiConnectionMethod

    override fun getConnectionMethodFactory(): ConnectionMethodBase.Factory = RestApiConnectionMethod.Companion

    /**
     * Creates a REST API transfer instance.
     *
     * REST API transfers are stateless - there's no persistent connection.
     * The holder makes a single HTTP POST request to the reader's server.
     *
     * ## Data Transfer Flow
     *
     * **Holder Side**:
     * 1. Scan QR code → get URI
     * 2. Create DeviceResponse
     * 3. POST to URI
     *
     * **Reader Side**:
     * 1. Generate QR code with URI
     * 2. Wait for POST at URI
     * 3. Parse DeviceResponse
     *
     * ## Security
     *
     * - HTTPS only (enforced by RestApiConnectionMethod)
     * - Certificate validation by Ktor
     * - DeviceResponse encrypted at session level
     * - Optional certificate pinning (TODO)
     *
     * @param connectionMethod The REST API connection method
     * @param execution Session execution context
     * @param role The role of this party (MDOC or MDOC_READER)
     * @return REST API transfer instance
     * @throws IllegalArgumentException if connectionMethod is not RestApiConnectionMethod
     */
    override fun createTransfer(
        connectionMethod: ConnectionMethod,
        execution: SessionExecution,
        role: MdocRole,
        engagementData: EngagementData?,
    ): MdocTransport<*> {
        require(connectionMethod is RestApiConnectionMethod) {
            "RestApiTransportFactory requires RestApiConnectionMethod, got ${connectionMethod::class.simpleName}"
        }

        val log = execution.log
        log.info("Creating REST API transfer: role=$role, uri=${connectionMethod.options.uri}")

        return RestApiTransport(
            connectionMethod = connectionMethod,
            execution = execution,
            httpClient = httpClient,
            cborEncoder = cborEncoder,
            deviceRequestCborCodec = deviceRequestCborCodec,
            deviceEngagementCborCodec = deviceEngagementCborCodec,
            readerEngagementCborCodec = readerEngagementCborCodec,
            sessionEstablishmentCborCodec = sessionEstablishmentCborCodec,
            sessionDataCborCodec = sessionDataCborCodec,
            sessionTranscriptCborCodec = sessionTranscriptCborCodec,
            coseKeyCborCodec = coseKeyCborCodec,
            role = role,
            engagementData = engagementData,
        )
    }
}
