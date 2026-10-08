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

package com.sphereon.data.store.credential.design.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.error.IdkError
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.ktor.http.client.provider.UrlValidationException
import com.sphereon.ktor.http.client.provider.UrlValidationPolicy
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType

/**
 * Result of fetching an external design source.
 */
data class DesignFetchResult(
    val data: ByteArray,
    val contentType: String?,
    val etag: String?,
    val notModified: Boolean = false,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is DesignFetchResult) {
            return false
        }
        return contentType == other.contentType && etag == other.etag &&
            notModified == other.notModified && data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + (contentType?.hashCode() ?: 0)
        result = 31 * result + (etag?.hashCode() ?: 0)
        result = 31 * result + notModified.hashCode()
        return result
    }
}

/**
 * Fetches design content from external URLs.
 * SSRF protection is enforced by [UrlValidationPolicy.BLOCK_PRIVATE] on the HTTP client.
 */
interface DesignExternalFetcher {
    suspend fun fetch(
        url: String,
        ifNoneMatch: String? = null,
        maxSizeBytes: Long = DEFAULT_MAX_SIZE_BYTES,
    ): IdkResult<DesignFetchResult, IdkError>

    companion object {
        const val DEFAULT_MAX_SIZE_BYTES: Long = 10 * 1024 * 1024 // 10MB
    }
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<DesignExternalFetcher>())
class DefaultDesignExternalFetcher(
    private val httpClientFactory: HttpClientFactory,
) : DesignExternalFetcher {
    private val clientOptions =
        HttpClientOptions.createDefault().copy(
            urlValidation = UrlValidationPolicy.BLOCK_PRIVATE,
        )

    override suspend fun fetch(
        url: String,
        ifNoneMatch: String?,
        maxSizeBytes: Long,
    ): IdkResult<DesignFetchResult, IdkError> {
        val httpClient =
            try {
                httpClientFactory.createClient(clientOptions)
            } catch (expected: Exception) {
                return Err(IdkError.UNKNOWN_ERROR(message = "Failed to create HTTP client: ${expected.message}", exception = expected))
            }

        return try {
            val response =
                httpClient.get(url) {
                    header("User-Agent", "Sphereon-CredentialDesign/1.0")
                    if (ifNoneMatch != null) {
                        header("If-None-Match", "\"$ifNoneMatch\"")
                    }
                }

            when (response.status) {
                HttpStatusCode.NotModified -> {
                    Ok(DesignFetchResult(data = ByteArray(0), contentType = null, etag = ifNoneMatch, notModified = true))
                }

                HttpStatusCode.OK -> {
                    val data = response.bodyAsBytes()
                    if (data.size > maxSizeBytes) {
                        return Err(
                            IdkError.ILLEGAL_ARGUMENT_ERROR(
                                message = "External design source exceeds max size: ${data.size} bytes > $maxSizeBytes bytes",
                            ),
                        )
                    }
                    val contentType = response.contentType()?.toString()
                    val etag = response.headers["ETag"]?.trim('"')
                    Ok(DesignFetchResult(data = data, contentType = contentType, etag = etag))
                }

                else -> {
                    Err(
                        IdkError.UNKNOWN_ERROR(
                            message = "Failed to fetch external design source from $url: HTTP ${response.status.value}",
                        ),
                    )
                }
            }
        } catch (e: UrlValidationException) {
            Err(IdkError.ILLEGAL_ARGUMENT_ERROR(message = "URL validation failed: ${e.message}"))
        } catch (expected: Exception) {
            Err(IdkError.UNKNOWN_ERROR(message = "Failed to fetch external design source from $url: ${expected.message}", exception = expected))
        } finally {
            try {
                httpClient.close()
            } catch (_: Exception) {
                // Ignored: best-effort HTTP client cleanup
            }
        }
    }
}
