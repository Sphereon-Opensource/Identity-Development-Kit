package com.sphereon.oauth2.server.authorization.impl.http

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OAuth2RestDependencyBoundaryTest {
    @Test
    fun neutralRestKeepsAuthorizationImplementationInExecutableAssemblies() {
        val build = Files.readString(workspaceRoot().resolve("lib/oauth2/server/rest/build.gradle.kts"))
        val production = sourceSetBlock(build, "commonMain", "commonTest")

        assertFalse(production.contains("libOauth2ServerAuthorizationImpl"))
        assertTrue(production.contains("libOauth2ServerAuthorizationPublic"))
        assertTrue(production.contains("libOauth2ServerResourceImpl"))
        assertTrue(production.contains("libOauth2ClientImpl"))
        assertTrue(production.contains("libOauth2JwtValidationImpl"))
    }

    private fun sourceSetBlock(
        build: String,
        sourceSet: String,
        nextSourceSet: String,
    ): String {
        val start = build.indexOf("val $sourceSet by getting")
        val end = build.indexOf("val $nextSourceSet by getting")
        assertTrue(start >= 0 && end > start, "expected $sourceSet to be declared before $nextSourceSet")
        return build.substring(start, end)
    }

    private fun workspaceRoot(): Path =
        generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .first {
                Files.isRegularFile(it.resolve("settings.gradle.kts")) &&
                    Files.isDirectory(it.resolve("lib/oauth2/server/rest"))
            }
}
