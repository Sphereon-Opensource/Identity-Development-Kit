package com.sphereon.openid.oid4vci.issuer.impl.proof

import com.sphereon.core.api.conf.DefaultAppMapPropertySource
import com.sphereon.core.defaults.app.DefaultRootScopeProvider
import com.sphereon.crypto.jose.jws.JwsCompact
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.jose.jws.command.VerifyJwsArgs
import com.sphereon.openid.oid4vci.issuer.ktor.Oid4vciIssuerAppGraph
import dev.zacsweers.metro.createGraphFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

class DidJwkProofVerificationTest {
    private val walletProofJwt =
        "eyJ0eXAiOiJvcGVuaWQ0dmNpLXByb29mK2p3dCIsImFsZyI6IkVTMjU2Iiwia2lkIjoiZGlkOmp3azpleUpo" +
            "YkdjaU9pSkZVekkxTmlJc0luVnpaU0k2SW5OcFp5SXNJbXQwZVNJNklrVkRJaXdpWTNKMklqb2lVQzB5TlRZ" +
            "aUxDSjRJam9pVm1wRlFWOXVUVGxaUkdobFZXUXliMlJKVVZweGNrMUZWMGx6U0hscVZ6bEdielEzWm1WVFJV" +
            "WnBTU0lzSW5raU9pSk1lbE50TmpoSFFXWnNUMGRGU2twZldUWkxkVFk1ZVVGVWJqSkhSRnBaYUVNMlRVZHla" +
            "WHB1ZVVwbkluMCMwIn0." +
            "eyJhdWQiOiJodHRwczovL2ZiMThlNzFmY2IyOC5uZ3Jvay5hcHAiLCJpYXQiOjE3NzU0OTA2NDQsImV4cCI6" +
            "MTc3NTQ5MTMwNCwibm9uY2UiOiIzM2w4WmwtX3JwUW9VYVFxVHNWaVM3Z0VNSDRpU0tsMWIyM2Y3Z2lXY0Y4" +
            "IiwianRpIjoiMGNkNTZiNGQtMzIzMi00ZWJkLWI4ZTMtMTM0YzQyZmJlNGViIn0." +
            "Qy0XdxYLGXGOYHErexahvAMzd5QG2XfjN6yPGxylVvXgUuMklJ7nqpubVd1jTByaOyL-wQxHlfhISP2-V08ZGA"

    @BeforeTest
    fun setupKms() {
        DefaultAppMapPropertySource.getSource().clear()
        DefaultAppMapPropertySource.addProperties(
            mapOf(
                "kms.providers.software.type" to "software",
                "kms.providers.software.id" to "software",
                "kms.providers.software.keystore.type" to "memory",
                "kms.providers.software.keystore.id" to "test-keystore",
                "kms.providers.software.keystore.keyVisibility" to "private",
            ),
        )
    }

    /**
     * Captured wallet-produced did:jwk proof JWT used to regression-test verification.
     *
     * The fixture's embedded JWK is not a valid P-256 point (manual decode confirms
     * `x`/`y` do not satisfy the curve equation), so signature verification rightfully
     * fails. Disabling until a fresh, valid wallet-captured JWT is recorded — the
     * test can't validate anything against a broken fixture.
     */
    @Ignore
    @Test
    fun verifyDidJwkProofJwt() =
        runTest {
            val graph =
                createGraphFactory<Oid4vciIssuerAppGraph.Factory>().create(
                    application = Unit,
                    version = "test",
                    appId = "oid4vci-issuer",
                    profile = "test",
                    rootScopeProvider = DefaultRootScopeProvider(),
                )
            graph.initRootScopeProvider()

            val context = graph.userContextManager.getAnonymous()
            val session = context.sessionContextManager.createOrGetFromId("did-jwk-test")

            val jwtService = (session.graph as JwtServiceImpl.Graph).jwtService

            val result = jwtService.verifyJws(VerifyJwsArgs(jws = JwsCompact(walletProofJwt)))

            assertTrue(
                result.isOk,
                "Command should succeed: ${if (result.isErr) {
                    result.error.message.defaultMessage
                } else {
                    ""
                }}"
            )

            val jwsResult = result.value
            println("isValid: ${jwsResult.isValid}")
            jwsResult.errorMessages.forEach { println("  ERROR: $it") }

            assertTrue(jwsResult.isValid, "did:jwk proof JWT signature should be valid. Errors: ${jwsResult.errorMessages}")
        }
}
