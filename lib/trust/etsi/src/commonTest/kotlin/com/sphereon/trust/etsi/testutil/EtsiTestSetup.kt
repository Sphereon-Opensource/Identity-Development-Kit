/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.trust.etsi.testutil

import com.sphereon.core.api.session.asCoreApiServiceComponent
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceComponent
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.di.app.AppComponent
import com.sphereon.di.session.SessionInstance
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.trust.etsi.signature.jades.JAdESValidator
import io.ktor.client.request.*
import io.ktor.client.statement.*
import dev.zacsweers.metro.ContributesTo
import com.sphereon.trust.etsi.signature.jades.JAdESValidatorComponent
import com.sphereon.trust.etsi.signature.xades.XAdESValidator
import com.sphereon.trust.etsi.signature.xades.XAdESValidatorComponent
import dev.whyoleg.cryptography.CryptographyProvider

@ContributesTo(SessionScope::class)
interface HttpClientFactoryComponent {
    val httpClientFactory: HttpClientFactory
}

expect fun createEtsiTestAppComponent(testInstance: Any): AppComponent

class EtsiTestContext(sessionId: String, testInstance: Any) {
    val app: AppComponent = createEtsiTestAppComponent(testInstance)
    private val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId(sessionId)

    val keyManagerService: KeyManagerService =
        session.component.asKeyManagerServiceComponent().keyManagerService

    val xadesValidator: XAdESValidator
        get() = (session.component as XAdESValidatorComponent).xadesValidator

    val jadesValidator: JAdESValidator
        get() = (session.component as JAdESValidatorComponent).jadesValidator

    private val httpClient by lazy {
        (session.component as HttpClientFactoryComponent).httpClientFactory
            .createClient(HttpClientOptions())
    }

    private val fetchCache = mutableMapOf<String, String>()

    /** Fetch a URL as text using the DI-managed HTTP client, caching results */
    suspend fun fetchUrl(url: String): String {
        fetchCache[url]?.let { return it }
        val body = httpClient.get(url).bodyAsText()
        require(body.isNotEmpty()) { "Empty response from $url" }
        fetchCache[url] = body
        return body
    }

    init {
        val config = SoftwareKmsProviderConfig(
            id = "$sessionId-software-provider",
            cryptographyProvider = CryptographyProvider.Default.name
        )
        val softwareKmsProvider = (app as SoftwareKmsProviderFactoryImpl.Component)
            .softwareKmsProvider
            .create(config, session.asCoreApiServiceComponent().serviceExecution)
        keyManagerService.registerProvider(softwareKmsProvider, makeDefaultKms = true)
    }
}
