/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance.services

import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.util.Base64
import java.util.UUID
import javax.net.ssl.SSLContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Production operator authorization and RFC 8693 tenant token exchange used by deployed drivers. */
internal class OidfEnterpriseOperatorTokenClient(
    platformUrl: String,
    private val tenantId: String,
    private val operatorUsername: String,
    private val operatorPassword: String,
    sslContext: SSLContext,
) {
    private val platformUrl = platformUrl.trimEnd('/')
    private val redirectUri = "$platformUrl/admin-console/callback"
    private val client =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .cookieHandler(CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .sslContext(sslContext)
            .build()

    fun tenantRuntimeToken(audiences: Set<String>): String {
        require(audiences.isNotEmpty()) { "OIDF wallet runtime token requires at least one audience" }
        val operatorToken = operatorToken()
        val fields =
            listOf(
                "grant_type" to TOKEN_EXCHANGE_GRANT,
                "subject_token" to operatorToken,
                "subject_token_type" to ACCESS_TOKEN_TYPE,
                "requested_token_type" to ACCESS_TOKEN_TYPE,
                "resource" to "urn:sphereon:tenant:$tenantId",
                "client_id" to CLIENT_ID,
            ) + audiences.sorted().map { audience -> "audience" to audience }
        return tokenResponse(sendForm(URI.create("$platformUrl/token"), fields), "tenant token exchange")
    }

    private fun operatorToken(): String {
        val verifier = PKCE_VERIFIER
        val challenge =
            Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)))
        val state = "oidf-wallet-${UUID.randomUUID()}"
        val authorizationUri =
            URI.create(
                "$platformUrl/authorize?" +
                    formEncode(
                        listOf(
                            "response_type" to "code",
                            "client_id" to CLIENT_ID,
                            "redirect_uri" to redirectUri,
                            "scope" to "openid",
                            "state" to state,
                            "prompt" to "login",
                            "code_challenge" to challenge,
                            "code_challenge_method" to "S256",
                        ),
                    ),
            )
        var response = sendGet(authorizationUri)
        var logicalUri = authorizationUri
        repeat(MAX_LOGIN_REDIRECTS) {
            if (response.statusCode() !in 300..399) return@repeat
            val location = response.headers().firstValue("Location").orElseThrow {
                IllegalStateException("Operator authorization redirect omitted Location")
            }
            logicalUri = logicalUri.resolve(location)
            response = sendGet(logicalUri)
        }
        require(response.statusCode() == 200 && logicalUri.path.startsWith("/login")) {
            "Operator authorization did not reach the production login form: HTTP ${response.statusCode()} at $logicalUri"
        }
        val loginFields =
            OidfIssuerAuthorizationBrowser.REQUIRED_HIDDEN_FIELDS.associateWith { name ->
                OidfIssuerAuthorizationBrowser.hiddenInput(name)
                    .find(response.body())
                    ?.groupValues
                    ?.get(1)
                    ?.let { encoded -> with(OidfIssuerAuthorizationBrowser) { encoded.decodeHtml() } }
                    ?: error("Production operator login form omitted '$name'")
            }
        val loginAction =
            OidfIssuerAuthorizationBrowser.LOGIN_FORM_ACTION
                .find(response.body())
                ?.groupValues
                ?.get(1)
                ?.let { encoded -> with(OidfIssuerAuthorizationBrowser) { encoded.decodeHtml() } }
                ?: error("Production operator login form omitted its POST action")
        response =
            sendForm(
                logicalUri.resolve(loginAction),
                loginFields.entries.map { it.key to it.value } +
                    listOf("username" to operatorUsername, "password" to operatorPassword),
            )
        val continuation = response.redirectLocation(logicalUri.resolve(loginAction), "operator login")
        response = sendGet(continuation)
        val callback = response.redirectLocation(continuation, "operator authorization callback")
        val callbackParameters = callback.rawQuery.orEmpty().formParameters()
        require(callbackParameters["state"] == state) { "Operator authorization callback state mismatch" }
        val code = callbackParameters["code"] ?: error("Operator authorization callback omitted code")
        return tokenResponse(
            sendForm(
                URI.create("$platformUrl/token"),
                listOf(
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to redirectUri,
                    "client_id" to CLIENT_ID,
                    "code_verifier" to verifier,
                ),
            ),
            "operator authorization-code exchange",
        )
    }

    private fun sendGet(uri: URI): HttpResponse<String> =
        client.send(
            HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun sendForm(
        uri: URI,
        fields: List<Pair<String, String>>,
    ): HttpResponse<String> =
        client.send(
            HttpRequest
                .newBuilder(uri)
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(fields)))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun tokenResponse(
        response: HttpResponse<String>,
        operation: String,
    ): String {
        require(response.statusCode() in 200..299) {
            "$operation failed with HTTP ${response.statusCode()}: ${response.body().take(800)}"
        }
        return Json.parseToJsonElement(response.body()).jsonObject["access_token"]?.jsonPrimitive?.content
            ?: error("$operation response omitted access_token")
    }

    private fun HttpResponse<String>.redirectLocation(
        base: URI,
        operation: String,
    ): URI {
        require(statusCode() in 300..399) { "$operation expected a redirect, got HTTP ${statusCode()}" }
        return base.resolve(
            headers().firstValue("Location").orElseThrow {
                IllegalStateException("$operation redirect omitted Location")
            },
        )
    }

    private fun formEncode(fields: List<Pair<String, String>>): String =
        fields.joinToString("&") { (name, value) -> "${encode(name)}=${encode(value)}" }

    private fun String.formParameters(): Map<String, String> =
        split('&')
            .filter(String::isNotBlank)
            .associate { pair ->
                val separator = pair.indexOf('=')
                val name = if (separator < 0) pair else pair.substring(0, separator)
                val value = if (separator < 0) "" else pair.substring(separator + 1)
                java.net.URLDecoder.decode(name, StandardCharsets.UTF_8) to
                    java.net.URLDecoder.decode(value, StandardCharsets.UTF_8)
            }

    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

    private companion object {
        const val CLIENT_ID = "platform-operator-cli"
        const val PKCE_VERIFIER = "edk-e2e-operator-pkce-verifier-0123456789abcdefghijklmnopqrstuv"
        const val TOKEN_EXCHANGE_GRANT = "urn:ietf:params:oauth:grant-type:token-exchange"
        const val ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token"
        const val MAX_LOGIN_REDIRECTS = 8
    }
}
