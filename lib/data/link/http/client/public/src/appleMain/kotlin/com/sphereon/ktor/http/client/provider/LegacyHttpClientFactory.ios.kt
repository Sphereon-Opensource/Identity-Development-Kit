@file:OptIn(ExperimentalForeignApi::class)


package com.sphereon.ktor.http.client.provider

import com.sphereon.ktor.http.client.config.LegacyIosSslProvider
import io.ktor.client.*
import io.ktor.client.engine.darwin.*
import io.ktor.client.plugins.contentnegotiation.*
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

    actual fun createClient(options: LegacyHttpClientOptions): HttpClient {
        return HttpClient(Darwin) {

            if (options.enableContentNegotiation) {
                install(ContentNegotiation) {
                    options.contentNegotiationConfig?.invoke(this)
                }
            }

            options.sslConfig?.let { sslConfig ->
                val sslProvider = sslConfig as LegacyIosSslProvider

                // Collect client identities
                val identities: List<SecIdentityRef> = runBlocking {
                    sslProvider.getCertificates().map { it.key }
                }

                engine {
                    handleChallenge(({
                            session: NSURLSession,
                            task: NSURLSessionTask,
                            challenge: NSURLAuthenticationChallenge,
                            completion: (NSURLSessionAuthChallengeDisposition, NSURLCredential?) -> Unit ->
                        when (challenge.protectionSpace.authenticationMethod) {
                            NSURLAuthenticationMethodClientCertificate -> {
                                identities.firstOrNull()?.let { identity ->
                                    completion(
                                        NSURLSessionAuthChallengeUseCredential,
                                        NSURLCredential.credentialWithIdentity(
                                            identity,
                                            null,
                                            NSURLCredentialPersistence.NSURLCredentialPersistenceForSession
                                        )
                                    )
                                } ?: completion(NSURLSessionAuthChallengeCancelAuthenticationChallenge, null)
                            }
                            else -> {
                                completion(NSURLSessionAuthChallengePerformDefaultHandling, null)
                            }
                        }
                    } as Any) as ChallengeHandler) // due to KTOR/Darwin interop bug KTOR-6353
                }
            }
        }
    }

    actual fun getSupportedEngineTypes(): List<HttpClientEngineType> =
        listOf(HttpClientEngineType.DARWIN)

    actual companion object {
        actual fun newInstance(): LegacyHttpClientFactory = LegacyHttpClientFactory()
        actual fun createClient(options: LegacyHttpClientOptions): HttpClient =
            newInstance().createClient(options)
    }
}
