/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import javax.net.ssl.SSLContext

data class OidfPlan(
    val id: String,
    val name: String,
    val selectionVariant: Map<String, String>,
    val modules: List<OidfPlanModule>,
) {
    val scenarioKeys: List<String> = modules.map { it.scenarioKey(name, selectionVariant) }
}

data class OidfPlanModule(
    val testModule: String,
    val variant: Map<String, String>,
) {
    fun scenarioKey(
        planName: String,
        planVariant: Map<String, String> = emptyMap(),
    ): String =
        buildString {
            append(planName)
            planVariant.toSortedMap().forEach { (name, value) ->
                append("|planVariant:")
                append(name)
                append('=')
                append(value)
            }
            append('|')
            append(testModule)
            variant.toSortedMap().forEach { (name, value) ->
                append("|moduleVariant:")
                append(name)
                append('=')
                append(value)
            }
        }
}

data class OidfStartedTest(
    val id: String,
    val name: String,
    val url: String?,
)

data class OidfBrowserState(
    val urls: List<String>,
    val browserApiRequests: List<OidfBrowserApiRequest>,
    val error: String?,
)

data class OidfBrowserApiRequest(
    val request: JsonObject,
    val submitUrl: String,
)

data class OidfWaitState(
    val state: String?,
    val timeout: Boolean,
)

data class OidfTestInfo(
    val status: String?,
    val result: String?,
    val clientId: String?,
    val redirectUri: String?,
)

/**
 * API-only client for the OIDF conformance suite.
 *
 * TLS verification is always enabled. A local suite must either expose a
 * publicly trusted certificate or supply its extracted CA through [sslContext]
 * (or the JVM trust store). There is intentionally no trust-all switch.
 */
class OidfConformanceSuiteClient(
    private val baseUrl: String,
    sslContext: SSLContext? = null,
    private val requestTimeout: Duration = Duration.ofSeconds(30),
    originAliases: Map<String, String> = emptyMap(),
    browserOrigin: String? = null,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val baseUri = URI.create(baseUrl.trimEnd('/'))
    val browserOrigin: String = normalizeOrigin(browserOrigin ?: baseUrl)
    private val originAliases = originAliases.mapKeys { (origin, _) -> origin.lowercase() }
    private val client: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .apply { sslContext?.let(::sslContext) }
            .build()

    fun createPlan(
        planName: String,
        configJson: String,
        variant: JsonObject,
    ): OidfPlan {
        val element =
            postJson(
                path = "/api/plan?planName=${urlEncode(planName)}&variant=${urlEncode(variant.toString())}",
                body = configJson,
            )
        val obj = element.jsonObject
        return OidfPlan(
            id = obj.getValue("id").jsonPrimitive.content,
            name = obj.getValue("name").jsonPrimitive.content,
            selectionVariant = variant.mapValues { (_, value) -> value.jsonPrimitive.content },
            modules = obj["modules"].asPlanModules(),
        )
    }

    fun deletePlan(planId: String) {
        request(HttpRequest.newBuilder(uri("/api/plan/${urlEncode(planId)}")).DELETE().build())
    }

    fun startModule(
        testName: String,
        planId: String,
        variant: JsonObject = JsonObject(emptyMap()),
    ): OidfStartedTest {
        val variantParameter =
            if (variant.isEmpty()) {
                ""
            } else {
                "&variant=${urlEncode(variant.toString())}"
            }
        val element =
            post(
                path = "/api/runner?test=${urlEncode(testName)}&plan=${urlEncode(planId)}$variantParameter",
                body = null,
            )
        val obj = element.jsonObject
        return OidfStartedTest(
            id = obj.getValue("id").jsonPrimitive.content,
            name = obj.getValue("name").jsonPrimitive.content,
            url = obj["url"]?.jsonPrimitive?.contentOrNull,
        )
    }

    fun waitForState(
        testId: String,
        states: List<String>,
        timeoutMs: Long,
    ): OidfWaitState {
        val element =
            get(
                "/api/runner/${urlEncode(testId)}/wait-state?states=${urlEncode(states.joinToString(","))}&timeoutMs=$timeoutMs",
                timeout = Duration.ofMillis(timeoutMs).plusSeconds(5),
            )
        val obj = element.jsonObject
        return OidfWaitState(
            state = obj["state"]?.jsonPrimitive?.contentOrNull,
            timeout = obj["timeout"]?.jsonPrimitive?.booleanOrNull == true,
        )
    }

    fun browser(testId: String): OidfBrowserState {
        val obj = runner(testId)
        val browser = obj["browser"]?.jsonObject ?: JsonObject(emptyMap())
        return OidfBrowserState(
            urls = browser["urls"].asStringList(),
            browserApiRequests = browser["browserApiRequests"].asBrowserApiRequestList(),
            error = obj["error"].asErrorString(),
        )
    }

    fun exposed(testId: String): Map<String, String> =
        runner(testId)["exposed"]
            ?.jsonObject
            ?.mapNotNull { (name, value) ->
                (value as? JsonPrimitive)?.contentOrNull?.let { name to it }
            }?.toMap()
            ?: emptyMap()

    /**
     * Performs the user-agent GET needed by suite-owned callback endpoints such as the
     * OID4VCI issuer test's credential-offer and transaction-code endpoints.
     */
    fun visit(url: String): String {
        val target = requireSuiteOrigin(url, "OIDF user-agent URL")
        val request =
            HttpRequest
                .newBuilder(target)
                .timeout(requestTimeout)
                .GET()
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) {
            "OIDF suite ${request.method()} ${request.uri()} returned ${response.statusCode()}: ${response.body()}"
        }
        return response.body()
    }

    /**
     * Drives a suite-owned browser authorization chain and returns the final callback URI. Redirects
     * are followed manually so every hop remains constrained to the running suite origin.
     */
    fun visitFollowingRedirects(
        url: String,
        terminalRedirectUri: String? = null,
        maxRedirects: Int = 10,
    ): String {
        var target = requireSuiteOrigin(url, "OIDF authorization URL")
        val terminal = terminalRedirectUri?.let(URI::create)
        repeat(maxRedirects + 1) { redirectCount ->
            val request =
                HttpRequest
                    .newBuilder(target)
                    .timeout(requestTimeout)
                    .GET()
                    .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() in 200..299) return target.toString()
            require(response.statusCode() in 300..399) {
                "OIDF suite ${request.method()} ${request.uri()} returned ${response.statusCode()}: ${response.body()}"
            }
            require(redirectCount < maxRedirects) { "OIDF authorization exceeded $maxRedirects redirects" }
            val location = response.headers().firstValue("Location").orElse(null)
                ?: error("OIDF authorization redirect omitted Location")
            val resolved = target.resolve(location)
            if (terminal != null && resolved.sameEndpointAs(terminal)) {
                return resolved.toString()
            }
            target = requireSuiteOrigin(resolved.toString(), "OIDF authorization redirect")
        }
        error("OIDF authorization redirect loop ended unexpectedly")
    }

    private fun URI.sameEndpointAs(other: URI): Boolean =
        scheme.equals(other.scheme, ignoreCase = true) &&
            host.equals(other.host, ignoreCase = true) &&
            effectivePort() == other.effectivePort() &&
            path == other.path

    private fun URI.effectivePort(): Int =
        if (port >= 0) port else if (scheme.equals("https", ignoreCase = true)) 443 else 80

    /**
     * Returns a Digital Credentials API result to the exact one-time callback issued by the suite.
     * The callback is same-origin constrained so suite-provided browser state cannot turn the CI
     * runner into an arbitrary HTTPS client.
     */
    fun submitBrowserApiResponse(
        submitUrl: String,
        responseJson: JsonObject,
    ) {
        val target = requireSuiteOrigin(submitUrl, "OIDF Browser API submit URL")
        request(
            HttpRequest
                .newBuilder(target)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(responseJson.toString()))
                .build(),
        )
    }

    fun markVisited(
        testId: String,
        url: String,
    ) {
        post(path = "/api/runner/browser/${urlEncode(testId)}/visit?url=${urlEncode(url)}", body = null)
    }

    fun info(testId: String): OidfTestInfo {
        val obj = infoJson(testId)
        val clientConfig = obj["config"]?.jsonObject?.get("client")?.jsonObject
        return OidfTestInfo(
            status = obj["status"]?.jsonPrimitive?.contentOrNull,
            result = obj["result"]?.jsonPrimitive?.contentOrNull,
            clientId = clientConfig?.get("client_id")?.jsonPrimitive?.contentOrNull,
            redirectUri = clientConfig?.get("redirect_uri")?.jsonPrimitive?.contentOrNull,
        )
    }

    fun runner(testId: String): JsonObject = get("/api/runner/${urlEncode(testId)}").jsonObject

    fun infoJson(testId: String): JsonObject = get("/api/info/${urlEncode(testId)}").jsonObject

    /** Returns the suite's live condition log for diagnostic evidence collected before test cleanup. */
    fun log(testId: String): JsonArray = get("/api/log/${urlEncode(testId)}").jsonArray

    /** Counts live suite-condition invocations without depending on transient test-status changes. */
    fun logSourceInvocationCount(
        testId: String,
        source: String,
    ): Int =
        log(testId)
            .count { entry ->
                entry.jsonObject["src"]?.jsonPrimitive?.contentOrNull == source
            }

    fun plan(planId: String): JsonObject = get("/api/plan/${urlEncode(planId)}").jsonObject

    fun availablePlanNames(): Set<String> {
        val names = linkedSetOf<String>()
        collectPlanNames(get("/api/plan/available"), names)
        return names
    }

    fun requireRequiredOid4vcPlans() {
        OidfConformanceMatrix.requireAvailablePlans(availablePlanNames())
    }

    fun exportPlanHtml(
        planId: String,
        targetZip: Path,
    ): Path {
        val request =
            HttpRequest
                .newBuilder(uri("/api/plan/exporthtml/${urlEncode(planId)}?public=false"))
                .timeout(requestTimeout)
                .GET()
                .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofByteArray())
        require(response.statusCode() in 200..299) {
            "OIDF suite ${request.method()} ${request.uri()} returned ${response.statusCode()}"
        }
        targetZip.parent?.let(Files::createDirectories)
        Files.write(targetZip, response.body())
        return targetZip
    }

    fun healthCheck(): Boolean =
        runCatching {
            get("/api/plan/available")
            true
        }.getOrDefault(false)

    private fun get(
        path: String,
        timeout: Duration = requestTimeout,
    ): JsonElement = request(HttpRequest.newBuilder(uri(path)).GET().build(), timeout)

    private fun postJson(
        path: String,
        body: String,
    ): JsonElement = post(path, body, "application/json")

    private fun post(
        path: String,
        body: String?,
        contentType: String = "application/json",
    ): JsonElement {
        val builder = HttpRequest.newBuilder(uri(path))
        body?.let { builder.header("Content-Type", contentType) }
        return request(
            builder
                .POST(body?.let { HttpRequest.BodyPublishers.ofString(it) } ?: HttpRequest.BodyPublishers.noBody())
                .build(),
        )
    }

    private fun request(
        request: HttpRequest,
        timeout: Duration = requestTimeout,
    ): JsonElement {
        val timedRequest =
            HttpRequest
                .newBuilder(request, { _, _ -> true })
                .timeout(timeout)
                .build()
        val response = client.send(timedRequest, HttpResponse.BodyHandlers.ofString())
        require(response.statusCode() in 200..299) {
            "OIDF suite ${request.method()} ${request.uri()} returned ${response.statusCode()}: ${response.body()}"
        }
        return json.parseToJsonElement(response.body().ifBlank { "{}" })
    }

    private fun collectPlanNames(
        element: JsonElement,
        names: MutableSet<String>,
    ) {
        when (element) {
            is JsonArray -> element.forEach { collectPlanNames(it, names) }
            is JsonObject -> {
                (element["planName"] ?: element["plan"])
                    ?.let { it as? JsonPrimitive }
                    ?.contentOrNull
                    ?.let(names::add)
                element.values.forEach { collectPlanNames(it, names) }
            }
            else -> Unit
        }
    }

    private fun uri(path: String): URI = URI.create(baseUrl.trimEnd('/') + path)

    private fun normalizeOrigin(value: String): String {
        val parsed = URI.create(value)
        require(parsed.scheme.equals("https", ignoreCase = true)) { "OIDF browser origin must use HTTPS: $value" }
        require(!parsed.authority.isNullOrBlank()) { "OIDF browser origin must have an authority: $value" }
        return URI(parsed.scheme.lowercase(), parsed.authority, null, null, null).toString()
    }

    private fun requireSuiteOrigin(
        value: String,
        label: String,
    ): URI {
        val target = URI.create(value)
        if (
            target.scheme.equals(baseUri.scheme, ignoreCase = true) &&
            target.authority.equals(baseUri.authority, ignoreCase = true)
        ) {
            return target
        }
        val targetOrigin = "${target.scheme}://${target.authority}".lowercase()
        val localOrigin = originAliases[targetOrigin]
        require(localOrigin != null) { "$label must use a configured suite origin: $target" }
        val rawSuffix =
            buildString {
                append(target.rawPath.ifEmpty { "/" })
                target.rawQuery?.let {
                    append('?')
                    append(it)
                }
                target.rawFragment?.let {
                    append('#')
                    append(it)
                }
            }
        // URI's component constructor quotes '%' again. Concatenate the already-raw suffix so
        // values such as credential_offer_uri retain their single percent-encoding layer.
        return URI.create(localOrigin.trimEnd('/') + rawSuffix)
    }
}

private fun JsonElement?.asStringList(): List<String> =
    (this as? JsonArray)
        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        ?: emptyList()

internal fun JsonElement?.asErrorString(): String? =
    when (this) {
        null, JsonNull -> null
        is JsonPrimitive -> contentOrNull
        else -> toString()
    }

private fun JsonElement?.asBrowserApiRequestList(): List<OidfBrowserApiRequest> =
    (this as? JsonArray)
        ?.map { element ->
            val obj = element.jsonObject
            OidfBrowserApiRequest(
                request = obj.getValue("request").jsonObject,
                submitUrl = obj.getValue("submitUrl").jsonPrimitive.content,
            )
        }
        ?: emptyList()

private fun JsonElement?.asPlanModules(): List<OidfPlanModule> =
    (this as? JsonArray)
        ?.map { element ->
            val module = element.jsonObject
            OidfPlanModule(
                testModule = module.getValue("testModule").jsonPrimitive.content,
                variant =
                    module["variant"]
                        ?.jsonObject
                        ?.mapValues { (_, value) -> value.jsonPrimitive.content }
                        ?: emptyMap(),
            )
        }
        ?: emptyList()

private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())
