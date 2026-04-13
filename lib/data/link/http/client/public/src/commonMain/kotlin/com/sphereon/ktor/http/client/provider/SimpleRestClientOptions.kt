package com.sphereon.ktor.http.client.provider

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName
import com.sphereon.core.api.log.LogLevel
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable

@OptIn(ExperimentalObjCName::class)

@ObjCName("SimpleRestClientOptions", exact = true)

data class SimpleRestClientOptions(val engine: HttpClientEngineType, val json: Boolean = true, val logLevel: LogLevel = LogLevel.INFO) {
    companion object {
        val DEFAULT_OPTIONS = SimpleRestClientOptions(HttpClientEngineType.CIO, true, LogLevel.INFO)
    }

    fun applyTo(options: LegacyHttpClientOptions) = options.copy(
        engine = engine,
        enableContentNegotiation = json,
        contentNegotiationConfig = {
            json(Json {
                encodeDefaults = true
                ignoreUnknownKeys = true
                prettyPrint = false
            })
        },
        loggingConfig = {
            level = io.ktor.client.plugins.logging.LogLevel.valueOf(logLevel.name)
        }
    )
}
