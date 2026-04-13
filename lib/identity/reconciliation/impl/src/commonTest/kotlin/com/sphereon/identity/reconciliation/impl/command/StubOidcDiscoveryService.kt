package com.sphereon.identity.reconciliation.impl.command

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.di.session.SessionScope
import com.sphereon.oauth2.jwt.validation.JwtValidationError
import com.sphereon.oauth2.jwt.validation.OidcDiscoveryMetadata
import com.sphereon.oauth2.jwt.validation.OidcDiscoveryService
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

/**
 * Stub OidcDiscoveryService for tests.
 *
 * Returns metadata with conventional endpoints derived from issuer URL.
 * Registered as a DI binding so the test @DependencyGraph can wire it.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<OidcDiscoveryService>())
class StubOidcDiscoveryService : OidcDiscoveryService {

    override suspend fun discover(issuer: String): IdkResult<OidcDiscoveryMetadata, JwtValidationError> {
        return getMetadata(issuer)
    }

    override suspend fun getMetadata(issuer: String): IdkResult<OidcDiscoveryMetadata, JwtValidationError> {
        val normalizedIssuer = issuer.trimEnd('/')
        return Ok(
            OidcDiscoveryMetadata(
                issuer = normalizedIssuer,
                jwksUri = "$normalizedIssuer/.well-known/jwks.json",
                authorizationEndpoint = "$normalizedIssuer/authorize",
                tokenEndpoint = "$normalizedIssuer/token",
                userinfoEndpoint = "$normalizedIssuer/userinfo",
                responseTypesSupported = listOf("code"),
                subjectTypesSupported = listOf("public"),
                idTokenSigningAlgValuesSupported = listOf("RS256"),
                scopesSupported = listOf("openid", "profile", "email")
            )
        )
    }

    override suspend fun invalidateCache(issuer: String) {
        // no-op
    }
}
