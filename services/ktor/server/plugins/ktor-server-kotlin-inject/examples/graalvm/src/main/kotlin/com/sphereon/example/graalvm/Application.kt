package com.sphereon.example.graalvm

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.resolver.FixedTenantResolver
import com.sphereon.ktor.server.inject.getAppService
import com.sphereon.ktor.server.inject.getUserService
import com.sphereon.ktor.server.inject.getSessionService
import com.sphereon.ktor.server.inject.userInstance
import com.sphereon.ktor.server.inject.sessionInstance
import com.sphereon.core.api.conf.AppConfigEnvironment
import com.sphereon.core.api.log.UserContextLogManager
import com.sphereon.core.api.log.SessionLogManager
import kotlinx.serialization.Serializable

fun main() {
    embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
        configureKotlinInject()
        configureSerialization()
        configureRouting()
    }.start(wait = true)
}

fun isNativeImage(): Boolean {
    return System.getProperty("org.graalvm.nativeimage.imagecode") != null
}

fun Application.configureKotlinInject() {
    // Initialize AppGraph using the init() method
    val appGraph = GraalVMExampleAppGraph.init(
        application = this,
        appId = "graalvm-example",
        profile = System.getenv("APP_PROFILE") ?: "production",
        version = "1.0.0"
    )

    // Install KotlinInject plugin
    install(KotlinInjectPlugin) {
        this.appGraph = appGraph
        tenantResolver = FixedTenantResolver("default")
    }

    log.info("KotlinInject plugin installed with native image support: ${isNativeImage()}")
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json()
    }
}

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello from GraalVM Native Image!")
        }

        get("/health") {
            call.respond(
                HttpStatusCode.OK, HealthResponse(
                    status = "UP",
                    nativeImage = isNativeImage()
                )
            )
        }

        get("/config") {
            val config = call.getAppService<AppConfigEnvironment>()
            call.respond(
                ConfigResponse(
                    appName = config.getAppName(),
                    profile = config.getActiveProfile(),
                    nativeImage = isNativeImage()
                )
            )
        }

        get("/user/{userId}") {
            val userId = call.parameters["userId"] ?: "unknown"

            // Access user-scoped service
            val userLogManager = call.getUserService<UserContextLogManager>()
            userLogManager.withTag("USER").info("User $userId accessed endpoint")

            val userInstance = call.userInstance

            call.respond(
                UserResponse(
                    userId = userId,
                    tenant = userInstance.context.tenant.toString(),
                    principal = userInstance.context.principal.toString(),
                    contextId = userInstance.contextId
                )
            )
        }

        get("/session") {
            // Access session-scoped service
            val sessionLogManager = call.getSessionService<SessionLogManager>()
            val sessionInstance = call.sessionInstance

            sessionLogManager.withTag("SESSION").info("Session endpoint accessed")

            call.respond(
                SessionResponse(
                    sessionId = sessionInstance.sessionId,
                    nativeImage = isNativeImage()
                )
            )
        }

        get("/info") {
            val runtime = Runtime.getRuntime()
            call.respond(
                SystemInfoResponse(
                    nativeImage = isNativeImage(),
                    totalMemoryMB = runtime.totalMemory() / 1024 / 1024,
                    freeMemoryMB = runtime.freeMemory() / 1024 / 1024,
                    maxMemoryMB = runtime.maxMemory() / 1024 / 1024,
                    processors = runtime.availableProcessors(),
                    javaVersion = System.getProperty("java.version"),
                    osName = System.getProperty("os.name"),
                    osVersion = System.getProperty("os.version")
                )
            )
        }
    }
}

@Serializable
data class HealthResponse(
    val status: String,
    val nativeImage: Boolean
)

@Serializable
data class ConfigResponse(
    val appName: String,
    val profile: String,
    val nativeImage: Boolean
)

@Serializable
data class UserResponse(
    val userId: String,
    val tenant: String,
    val principal: String,
    val contextId: String
)

@Serializable
data class SessionResponse(
    val sessionId: String,
    val nativeImage: Boolean
)

@Serializable
data class SystemInfoResponse(
    val nativeImage: Boolean,
    val totalMemoryMB: Long,
    val freeMemoryMB: Long,
    val maxMemoryMB: Long,
    val processors: Int,
    val javaVersion: String,
    val osName: String,
    val osVersion: String
)
