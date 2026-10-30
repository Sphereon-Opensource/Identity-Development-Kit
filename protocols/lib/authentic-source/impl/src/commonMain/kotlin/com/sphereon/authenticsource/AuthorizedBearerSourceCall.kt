/* Copyright 2026 Sphereon International B.V. Licensed under Apache-2.0. */
package com.sphereon.authenticsource

import com.sphereon.core.api.IdkErrorResult
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.error.IdkError
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import io.ktor.client.request.post
import io.ktor.client.request.header
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException

/** Capability for a host authorization owner after it checks the binding and grant.
 * The factory must enforce its URL policy at every request. Never log request/response or token. */
class AuthorizedBearerSourceCall(
    private val factory: HttpClientFactory,
    private val allowedUrl: String,
    private val accessToken: String,
    private val options: HttpClientOptions,
) : SourceAuthorizedCall {
    override fun toString(): String = "AuthorizedBearerSourceCall(<redacted>)"
    override suspend fun postJson(url: String, body: String): IdkResult<SourceHttpResponse, IdkError> {
        if (url != allowedUrl || accessToken.isBlank() || '\r' in accessToken || '\n' in accessToken) return failed()
        val policy = options.urlValidation ?: return failed()
        if (options.followRedirects || options.enableLogging || options.enableHttpCache || options.enableContentNegotiation ||
            options.additionalConfig != null || options.defaultRequest != null ||
            policy.allowedSchemes != setOf("https") || !policy.blockUserInfo ||
            !policy.blockPrivateNetworks || !policy.blockRfc1918 || !policy.blockSharedNetworks ||
            !policy.requireResolvedAddressEnforcement || policy.exactTargetUri != allowedUrl) return failed()
        val client = try { factory.createClient(options) }
        catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { return failed() }
        return try {
            val response = client.post(url) {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Accept, ContentType.Application.Json.toString())
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                setBody(body)
            }
            IdkResult.ok(SourceHttpResponse(response.status.value, response.bodyAsText(), response.headers[HttpHeaders.ContentType]))
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { failed() }
        finally {
            try { client.close() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { /* No exception details or sensitive data leave this boundary. */ }
        }
    }
    private fun <T> failed(): IdkResult<T, IdkError> = IdkErrorResult(IdkError.fromString("Authentic source transport failed", "AUTHENTIC_SOURCE_TRANSPORT_FAILED"))
}
