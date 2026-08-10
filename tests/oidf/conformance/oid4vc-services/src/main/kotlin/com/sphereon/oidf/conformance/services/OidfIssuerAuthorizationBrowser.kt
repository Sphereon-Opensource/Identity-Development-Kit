/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance.services

// Shared TLS utility for all deployed-product conformance drivers.

import java.io.ByteArrayInputStream
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.time.Duration
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Drives the user-authentication front channel required by issuer authorization-code modules.
 *
 * The OIDF suite exposes the authorization URL through its runner API. This adapter follows that
 * URL over fully verified TLS, submits the AS's real CSRF-protected login form, and follows the
 * redirect back to the suite callback. It deliberately contains no DOM/browser-engine bypass and
 * never accepts an untrusted certificate or disables hostname verification.
 */
internal class OidfIssuerAuthorizationBrowser(
    private val stack: OidfProductStack,
) {
    private val client =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .cookieHandler(CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .sslContext(stack.productSslContext)
            .build()

    fun complete(
        testId: String,
        moduleName: String,
        frontChannelRound: Int,
        evidenceDirectory: Path,
    ): String {
        val authorizationUrl = awaitAuthorizationUrl(testId)
        val rejectAuthentication = moduleName == USER_REJECTS_AUTHENTICATION_MODULE

        Files.createDirectories(evidenceDirectory)
        val transcriptPath = evidenceDirectory.resolve("authorization-browser.log")
        val transcript = mutableListOf<String>()
        fun record(line: String) {
            transcript += line
            Files.writeString(transcriptPath, transcript.joinToString("\n", postfix = "\n"))
        }

        var logicalUri = URI.create(authorizationUrl)
        var response = sendGet(logicalUri)
        record(describe("GET", logicalUri, response))
        // BrowserControl's visited-URL history models navigation, not completion. Record the
        // visit as soon as the authorization URL has actually been opened. In particular, the
        // PAR reuse module checks that the same URL was visited twice while processing the
        // second callback; marking round two only after that callback is too late.
        stack.suite.markVisited(testId, authorizationUrl)
        var loginSubmitted = false
        var suiteCallbackSubmitted = false
        var terminalResponseReached = false

        frontChannel@ for (step in 0 until MAX_FRONT_CHANNEL_STEPS) {
            when {
                response.statusCode() in 300..399 -> {
                    val location = response.headers().firstValue("Location").orElse(null)
                        ?: error("Front-channel redirect from $logicalUri omitted Location")
                    logicalUri = logicalUri.resolve(location)
                    response = sendGet(logicalUri)
                    record(describe("GET", logicalUri, response))
                }

                response.statusCode() == 200 && response.body().contains("name=\"session_code\"") -> {
                    if (moduleName == REUSED_REQUEST_URI_BEFORE_AUTH_MODULE && frontChannelRound == 1) {
                        // This module deliberately visits the same request_uri twice. Its first
                        // browser round must stop at the login page; authenticating here makes the
                        // suite correctly reject the run because authorization completed before
                        // the second visit. Marking the original URL visited releases the suite to
                        // expose round two while retaining this browser's cookie jar. The visit
                        // itself was recorded immediately after the GET above.
                        record("VISIT ONLY: login form reached; authentication deferred to front-channel round 2")
                        return authorizationUrl
                    }
                    check(!loginSubmitted) {
                        "OIDF issuer login returned the login form again; seeded CI credentials were rejected"
                    }
                    val form =
                        if (rejectAuthentication) {
                            parseCancelForm(response.body())
                        } else {
                            parseLoginForm(response.body())
                        }
                    val action = logicalUri.resolve(form.action)
                    response =
                        if (rejectAuthentication) {
                            sendForm(action, form.hiddenFields)
                        } else {
                            sendLogin(action, form)
                        }
                    logicalUri = action
                    loginSubmitted = true
                    record(describe(if (rejectAuthentication) "POST cancel" else "POST login", logicalUri, response))
                }

                response.statusCode() == 200 && IMPLICIT_SUBMIT.find(response.body()) != null -> {
                    val callbackUri = logicalUri
                    val implicitSubmitUrl =
                        IMPLICIT_SUBMIT.find(response.body())?.groupValues?.get(1)?.decodeHtml()
                            ?: error("OIDF callback page omitted its implicit submission URL")
                    logicalUri = callbackUri.resolve(implicitSubmitUrl)
                    response = sendImplicitSubmission(logicalUri, callbackUri.rawFragment?.let { "#$it" }.orEmpty())
                    suiteCallbackSubmitted = true
                    record(describe("POST", logicalUri, response))
                }

                response.statusCode() in 200..299 -> {
                    terminalResponseReached = true
                    break@frontChannel
                }
                else -> error(
                    "Issuer authorization front channel returned HTTP ${response.statusCode()} at $logicalUri: " +
                        response.body().take(1_000),
                )
            }
        }

        check(terminalResponseReached) {
            "OIDF issuer authorization did not reach a terminal response within $MAX_FRONT_CHANNEL_STEPS steps"
        }
        check(loginSubmitted || suiteCallbackSubmitted) {
            "OIDF issuer authorization reached neither the VDX login form nor a suite callback"
        }
        return authorizationUrl
    }

    private fun awaitAuthorizationUrl(testId: String): String {
        repeat(BROWSER_URL_POLL_ATTEMPTS) {
            val browser = stack.suite.browser(testId)
            require(browser.error == null) { "OIDF suite failed before issuer authorization: ${browser.error}" }
            require(browser.browserApiRequests.isEmpty()) {
                "Issuer authorization unexpectedly requested Digital Credentials API input: ${browser.browserApiRequests}"
            }
            browser.urls.singleOrNull()?.let { return it }
            require(browser.urls.isEmpty()) { "Expected exactly one OIDF issuer authorization URL, got ${browser.urls}" }
            val info = stack.suite.info(testId)
            require(info.status !in setOf("FINISHED", "INTERRUPTED")) {
                "OIDF module became ${info.status}/${info.result} before exposing its authorization URL"
            }
            Thread.sleep(BROWSER_URL_POLL_INTERVAL_MS)
        }
        error("OIDF suite did not expose an issuer authorization URL within the bounded polling window")
    }

    private fun sendGet(logicalUri: URI): HttpResponse<String> =
        client.send(
            HttpRequest
                .newBuilder(transportUri(logicalUri))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun sendLogin(
        logicalUri: URI,
        form: LoginForm,
    ): HttpResponse<String> {
        val values =
            form.hiddenFields +
                mapOf(
                    "username" to stack.loginUsername,
                    "password" to stack.loginPassword,
                )
        return sendForm(logicalUri, values)
    }

    private fun sendForm(
        logicalUri: URI,
        values: Map<String, String>,
    ): HttpResponse<String> {
        val body = values.entries.joinToString("&") { (name, value) -> "${urlEncode(name)}=${urlEncode(value)}" }
        return client.send(
            HttpRequest
                .newBuilder(transportUri(logicalUri))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
    }

    private fun sendImplicitSubmission(
        logicalUri: URI,
        fragment: String,
    ): HttpResponse<String> =
        client.send(
            HttpRequest
                .newBuilder(transportUri(logicalUri))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(fragment))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    private fun parseLoginForm(html: String): LoginForm {
        val action =
            LOGIN_FORM_ACTION.find(html)?.groupValues?.get(1)?.decodeHtml()
                ?: error("VDX login page did not contain the expected POST form action")
        val hidden =
            REQUIRED_HIDDEN_FIELDS.associateWith { name ->
                hiddenInput(name).find(html)?.groupValues?.get(1)?.decodeHtml()
                    ?: error("VDX login page omitted required hidden field '$name'")
            }
        return LoginForm(action = action, hiddenFields = hidden)
    }

    private fun parseCancelForm(html: String): LoginForm {
        val action =
            CANCEL_FORM_ACTION.find(html)?.groupValues?.get(1)?.decodeHtml()
                ?: error("VDX login page did not contain the expected cancel form action")
        val hidden =
            CANCEL_HIDDEN_FIELDS.associateWith { name ->
                hiddenInput(name).find(html)?.groupValues?.get(1)?.decodeHtml()
                    ?: error("VDX login cancel form omitted required hidden field '$name'")
            }
        return LoginForm(action = action, hiddenFields = hidden)
    }

    /**
     * The suite advertises host.testcontainers.internal so containers can call it. The Windows
     * host browser uses the same logical URL but connects to the fixed host port through localhost.
     * The nginx certificate explicitly covers localhost, so TLS hostname verification remains on.
     */
    private fun transportUri(logicalUri: URI): URI =
        if (logicalUri.host == "host.testcontainers.internal") {
            URI(
                logicalUri.scheme,
                logicalUri.userInfo,
                "localhost",
                logicalUri.port,
                logicalUri.path,
                logicalUri.query,
                logicalUri.fragment,
            )
        } else {
            logicalUri
        }

    private fun describe(
        method: String,
        logicalUri: URI,
        response: HttpResponse<String>,
    ): String {
        val location = response.headers().firstValue("Location").orElse(null)
        return buildString {
            append(method).append(' ').append(logicalUri).append(" -> ").append(response.statusCode())
            location?.let { append(" Location: ").append(logicalUri.resolve(it)) }
        }
    }

    private data class LoginForm(
        val action: String,
        val hiddenFields: Map<String, String>,
    )

    companion object {
        const val MAX_FRONT_CHANNEL_STEPS = 12
        const val BROWSER_URL_POLL_ATTEMPTS = 60
        const val BROWSER_URL_POLL_INTERVAL_MS = 250L
        val REQUIRED_HIDDEN_FIELDS = listOf("session_id", "tab_id", "session_code", "return_url")
        val CANCEL_HIDDEN_FIELDS = listOf("session_id", "tab_id", "session_code")
        val LOGIN_FORM_ACTION = Regex("""<form[^>]*method="post"[^>]*action="([^"]+)"""", RegexOption.IGNORE_CASE)
        val CANCEL_FORM_ACTION =
            Regex(
                """<form[^>]*class="cancel"[^>]*method="post"[^>]*action="([^"]+)"""",
                RegexOption.IGNORE_CASE,
            )
        const val USER_REJECTS_AUTHENTICATION_MODULE = "fapi2-security-profile-final-user-rejects-authentication"
        const val REUSED_REQUEST_URI_BEFORE_AUTH_MODULE =
            "fapi2-security-profile-final-par-ensure-reused-request-uri-prior-to-auth-completion-succeeds"
        val IMPLICIT_SUBMIT =
            Regex(
                """xhr\.open\(\s*['"]POST['"]\s*,\s*['"]([^'"]+)['"]\s*,\s*true\s*\)""",
                RegexOption.IGNORE_CASE,
            )

        fun hiddenInput(name: String): Regex =
            Regex("""<input[^>]*name="$name"[^>]*value="([^"]*)"""", RegexOption.IGNORE_CASE)

        fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

        fun String.decodeHtml(): String =
            replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("\\/", "/")

        fun combinedTrust(vararg certificatesPem: String): SSLContext {
            val certificateFactory = CertificateFactory.getInstance("X.509")
            val keyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null) }
            certificatesPem.forEachIndexed { index, pem ->
                val certificate =
                    certificateFactory.generateCertificate(
                        ByteArrayInputStream(pem.toByteArray(StandardCharsets.US_ASCII)),
                    )
                keyStore.setCertificateEntry("oidf-browser-$index", certificate)
            }
            val trustManagers =
                TrustManagerFactory
                    .getInstance(TrustManagerFactory.getDefaultAlgorithm())
                    .apply { init(keyStore) }
                    .trustManagers
            return SSLContext.getInstance("TLS").apply { init(null, trustManagers, null) }
        }
    }
}
