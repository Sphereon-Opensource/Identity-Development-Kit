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

package com.sphereon.mdoc.transport.oid4vp

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.core.api.log.LoggerConfig
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.mdoc.MdocRole
import com.sphereon.mdoc.data.device.DeviceRequestCborCodec
import com.sphereon.mdoc.data.device.DeviceResponseCborCodec
import com.sphereon.mdoc.engagement.EngagementData
import com.sphereon.mdoc.oid4vp.MdocOid4vpService
import com.sphereon.mdoc.transport.ConnectionMethod
import com.sphereon.mdoc.transport.ConnectionMethodBase
import com.sphereon.mdoc.transport.MdocTransport
import com.sphereon.mdoc.transport.MdocTransportFactory
import com.sphereon.mdoc.transport.TransportType
import com.sphereon.openid.oid4vp.holder.Oid4vpHolderService
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Transport factory for OID4VP connections (ISO 18013-7 Annex B).
 *
 * This factory creates OID4VP transfer instances that handle the holder side
 * of the OpenID4VP credential presentation protocol.
 *
 * ## Usage
 *
 * The factory is automatically registered via dependency injection and will be
 * used when an Oid4vpConnectionMethod is encountered.
 *
 * ## Dependencies
 *
 * - `HttpClient`: For HTTPS communication with verifier
 * - `MdocOid4vpService`: For OID4VP operations (signing, matching)
 * - `SessionExecution`: For logging and context
 *
 * @param httpClient Ktor HTTP client for HTTPS requests
 * @param oid4vpService Service for OID4VP operations
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesIntoSet(SessionScope::class, binding = binding<MdocTransportFactory>())
@OptIn(ExperimentalObjCName::class)
@ObjCName("Oid4vpTransportFactory", exact = true)
class Oid4vpTransportFactory(
    private val httpClientFactory: HttpClientFactory,
    private val oid4vpService: MdocOid4vpService,
    private val oid4vpHolder: Oid4vpHolderService,
    private val deviceRequestCborCodec: DeviceRequestCborCodec,
    private val deviceResponseCborCodec: DeviceResponseCborCodec,
) : MdocTransportFactory {
    override val transportType: TransportType = TransportType.OID4VP

    override fun supports(connectionMethod: ConnectionMethod): Boolean = connectionMethod is Oid4vpConnectionMethod

    override fun getConnectionMethodFactory(): ConnectionMethodBase.Factory = Oid4vpConnectionMethod.Companion

    override fun createTransfer(
        connectionMethod: ConnectionMethod,
        execution: SessionExecution,
        role: MdocRole,
        engagementData: EngagementData?,
    ): MdocTransport<*> {
        require(connectionMethod is Oid4vpConnectionMethod) {
            "Oid4vpTransportFactory only supports Oid4vpConnectionMethod, got: ${connectionMethod::class.simpleName}"
        }

        return Oid4VpTransport(
            connectionMethod = connectionMethod,
            execution = execution,
            httpClient =
                httpClientFactory.createClient(
                    HttpClientOptions.createDefault(LoggerConfig()).copy(engine = httpClientFactory.getEngineTypeDefault()),
                ),
            oid4vpService = oid4vpService,
            oid4vpHolder = oid4vpHolder,
            deviceRequestCborCodec = deviceRequestCborCodec,
            deviceResponseCborCodec = deviceResponseCborCodec,
            engagementData = engagementData,
        )
    }

    override fun toString(): String = "Oid4vpTransportFactory(supports=Oid4vpConnectionMethod)"
}
