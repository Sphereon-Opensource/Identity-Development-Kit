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
 */

package com.sphereon.oauth2.server.authorization.impl.config

import dev.zacsweers.metro.Named
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.common.config.OAuth2ServersConfigProvider
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.SingleIn

/**
 * Provides the OAuth2 issuer URL from configuration.
 *
 * Separated from DefaultOAuth2ConfigModule so it's available on all platforms
 * (the server identifier provider uses runBlocking and is JVM/native only).
 */
@ContributesTo(SessionScope::class)
interface OAuth2IssuerUrlModule {

    @Provides
    @SingleIn(SessionScope::class)
    @Named("oauth2.issuerUrl")
    fun provideDefaultIssuerUrl(configProvider: OAuth2ServersConfigProvider): String {
        return configProvider.serverConfig.issuer ?: configProvider.serverConfig.baseUrl
    }
}
