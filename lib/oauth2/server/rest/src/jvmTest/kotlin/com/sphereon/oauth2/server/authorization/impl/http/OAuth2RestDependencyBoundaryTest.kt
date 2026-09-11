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

        assertFalse(build.contains("libOauth2ServerAuthorizationImpl"))
        assertTrue(build.contains("libOauth2ServerAuthorizationPublic"))
        assertTrue(build.contains("libOauth2ServerResourceImpl"))
        assertTrue(build.contains("libOauth2ClientImpl"))
        assertTrue(build.contains("libOauth2JwtValidationImpl"))
    }

    private fun workspaceRoot(): Path =
        generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
            .first {
                Files.isRegularFile(it.resolve("settings.gradle.kts")) &&
                    Files.isDirectory(it.resolve("lib/oauth2/server/rest"))
            }
}
