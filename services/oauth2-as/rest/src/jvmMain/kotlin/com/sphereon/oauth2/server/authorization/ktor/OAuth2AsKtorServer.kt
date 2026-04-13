package com.sphereon.oauth2.server.authorization.ktor

import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.di.app.AbstractAppGraph
import com.sphereon.di.app.AppGraph
import com.sphereon.di.app.RootScopeProvider
import com.sphereon.ktor.server.inject.KotlinInjectPlugin
import com.sphereon.ktor.server.inject.installUniversalHttpAdapters
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondRedirect
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun main() {
    println("Starting OAuth2 Authorization Server...")

    val appGraph =
        createOAuth2AsAppGraph(
            application = Unit,
            appId = "oauth2-as",
            profile = System.getenv("APP_PROFILE") ?: "development",
            version = "1.0.0",
        )

    embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
        configureOAuth2As(appGraph)
    }.start(wait = true)
}

/**
 * Configure the Ktor application with OAuth2 Authorization Server routes.
 *
 * Uses [installUniversalHttpAdapters] to auto-discover the OAuth2HttpAdapter
 * contributed via DI.
 */
fun Application.configureOAuth2As(appGraph: AppGraph) {
    install(KotlinInjectPlugin) {
        this.appGraph = appGraph
    }
    log.info("KotlinInject plugin installed - full DI enabled")

    install(ContentNegotiation) {
        json()
    }

    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.respondText(
                text = """{"error": "${cause.message}"}""",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }

    routing {
        get("/health") {
            call.respondText("OK")
        }
        get("/login") {
            val sessionId = call.request.queryParameters["session_id"] ?: ""
            val returnUrl = call.request.queryParameters["return_url"] ?: "/"
            call.respondText(
                contentType = ContentType.Text.Html,
                text =
                    """
                    <!DOCTYPE html>
                    <html>
                    <head><title>IDK Test Login</title>
                    <style>body{font-family:sans-serif;max-width:400px;margin:80px auto;padding:20px}
                    input{display:block;width:100%;padding:8px;margin:8px 0;box-sizing:border-box}
                    button{padding:10px 20px;background:#0066cc;color:#fff;border:none;cursor:pointer;width:100%}</style>
                    </head>
                    <body>
                    <h2>IDK Test Login</h2>
                    <p>Test credentials: <code>testuser</code> / <code>testpass</code></p>
                    <form method="POST" action="login">
                    <input type="hidden" name="session_id" value="$sessionId"/>
                    <input type="hidden" name="return_url" value="$returnUrl"/>
                    <input type="text" name="username" placeholder="Username" autofocus/>
                    <input type="password" name="password" placeholder="Password"/>
                    <button type="submit">Login</button>
                    </form>
                    </body></html>
                    """.trimIndent(),
            )
        }
        post("/login") {
            val params = call.receiveParameters()
            val username = params["username"] ?: ""
            val password = params["password"] ?: ""
            val sessionId = params["session_id"] ?: ""
            val returnUrl = params["return_url"] ?: "/"

            if (username == "testuser" && password == "testpass" && sessionId.isNotBlank()) {
                // Register the authenticated session so TestUserAuthenticationProvider.getAuthenticatedUser() returns it
                com.sphereon.oauth2.server.authorization.ktor.test.TestUserAuthenticationProvider.testAuthenticatedSessions[sessionId] = username
                call.respondRedirect(returnUrl)
            } else {
                call.respondText(
                    contentType = ContentType.Text.Html,
                    text =
                        """
                        <!DOCTYPE html>
                        <html><head><title>Login Failed</title></head>
                        <body style="font-family:sans-serif;max-width:400px;margin:80px auto">
                        <h2>Invalid credentials</h2>
                        <p><a href="login?session_id=$sessionId&return_url=$returnUrl">Try again</a></p>
                        </body></html>
                        """.trimIndent(),
                )
            }
        }
    }

    // Auto-discover and expose OAuth2 HttpAdapter
    installUniversalHttpAdapters()
}

@DependencyGraph(AppScope::class)
abstract class OAuth2AsAppGraph : AbstractAppGraph() {
    @DependencyGraph.Factory
    fun interface Factory {
        fun create(
            @Provides application: Any,
            @Provides @Named("version") version: String,
            @Provides @Named("appId") appId: String,
            @Provides @Named("profile") profile: String,
            @Provides rootScopeProvider: RootScopeProvider,
        ): OAuth2AsAppGraph
    }
}

fun createOAuth2AsAppGraph(
    application: Any,
    appId: String = "oauth2-as",
    profile: String = "production",
    version: String = "1.0.0",
): AppGraph {
    val graph =
        createGraphFactory<OAuth2AsAppGraph.Factory>().create(
            application = application,
            version = version,
            appId = appId,
            profile = profile,
            rootScopeProvider = DefaultRootScopeProvider(),
        )
    graph.initRootScopeProvider()
    return graph
}
