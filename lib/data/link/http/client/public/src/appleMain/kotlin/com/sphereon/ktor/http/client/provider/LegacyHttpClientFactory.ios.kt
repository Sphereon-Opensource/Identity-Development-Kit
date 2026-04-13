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

@file:OptIn(ExperimentalForeignApi::class)

package com.sphereon.ktor.http.client.provider

import com.sphereon.ktor.http.client.config.LegacyIosSslProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.ChallengeHandler
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.runBlocking
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLAuthenticationMethodClientCertificate
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLCredentialPersistence
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengeDisposition
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.NSURLSessionTask
import platform.Foundation.credentialWithIdentity
import platform.Security.SecIdentityRef

actual class LegacyHttpClientFactory private constructor() {
    actual fun createClient(options: LegacyHttpClientOptions): HttpClient =
        HttpClient(Darwin) {
            if (options.enableContentNegotiation) {
                install(ContentNegotiation) {
                    options.contentNegotiationConfig?.invoke(this)
                }
            }

            options.sslConfig?.let { sslConfig ->
                val sslProvider = sslConfig as LegacyIosSslProvider

                // Collect client identities
                val identities: List<SecIdentityRef> =
                    runBlocking {
                        sslProvider.getCertificates().map { it.key }
                    }

                engine {
                    handleChallenge(
                        (
                            {
                                session: NSURLSession,
                                task: NSURLSessionTask,
                                challenge: NSURLAuthenticationChallenge,
                                completion: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit,
                                ->
                                when (challenge.protectionSpace.authenticationMethod) {
                                    NSURLAuthenticationMethodClientCertificate -> {
                                        identities.firstOrNull()?.let { identity ->
                                            completion(
                                                NSURLSessionAuthChallengeUseCredential,
                                                NSURLCredential.credentialWithIdentity(
                                                    identity,
                                                    null,
                                                    NSURLCredentialPersistence.NSURLCredentialPersistenceForSession,
                                                ),
                                            )
                                        } ?: completion(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                                    }

                                    else -> {
                                        completion(NSURLSessionAuthChallengePerformDefaultHandling, null)
                                    }
                                }
                            } as Any
                        ) as ChallengeHandler,
                    ) // due to KTOR/Darwin interop bug KTOR-6353
                }
            }
        }

    actual fun getSupportedEngineTypes(): List<HttpClientEngineType> = listOf(HttpClientEngineType.DARWIN)

    actual companion object {
        actual fun newInstance(): LegacyHttpClientFactory = LegacyHttpClientFactory()

        actual fun createClient(options: LegacyHttpClientOptions): HttpClient = newInstance().createClient(options)
    }
}
