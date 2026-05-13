package com.sphereon.openid.oid4vci.integration

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyType
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.di.app.AppGraph
import com.sphereon.di.session.SessionInstance
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKey
import com.sphereon.oauth2.server.authorization.storage.OAuth2SigningKeyState
import com.sphereon.oauth2.server.authorization.storage.SigningKeyStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import kotlin.time.Clock

const val OID4VCI_TEST_ISSUER_URL = "https://issuer.example.com"
const val OID4VCI_TEST_AS_SIGNING_KEY_ALIAS = "oauth2-server-signing"
const val OID4VCI_TEST_TENANT_ID = "default"

class Oid4vciTestContext(
    testInstance: Any,
) {
    init {
        // The OID4VCI integration tests run a single hosted AS that issues access tokens for
        // the credential endpoint. The AS's `OAuth2ServersConfigBinder` reads these properties
        // at session-start time, so they have to be in place before the AppGraph touches its
        // session graph. Without them, `CreateAccessTokenCommand` fails with "OAuth2 server has
        // no issuer configured".
        DefaultPrincipalMapPropertySource.addProperty(
            "${com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig.CONFIG_PREFIX}.default.issuer",
            OID4VCI_TEST_ISSUER_URL,
        )
        DefaultPrincipalMapPropertySource.addProperty(
            "${com.sphereon.oauth2.common.config.OAuth2ServerInstanceConfig.CONFIG_PREFIX}.default.mode",
            "HOSTED",
        )
        // The OID4VCI Issuer adapter's `descriptorFor(configProvider.issuerIdentifier)` is
        // evaluated during DI graph construction (constructor arg of HttpEndpointCommandAdapter),
        // so the identifier MUST be present before any session graph touches the issuer
        // metadata command — otherwise graph construction throws.
        DefaultPrincipalMapPropertySource.addProperty(
            "oid4vci.issuer.identifier",
            OID4VCI_TEST_ISSUER_URL,
        )
    }

    val app: AppGraph = createOid4vciTestAppGraph(application = testInstance)
    val context = app.userContextManager.getAnonymous()
    val session: SessionInstance = context.sessionContextManager.createOrGetFromId("oid4vci-e2e-test")
    val execution = session.asCoreApiServiceGraph().serviceExecution
    val signingKeyStore: SigningKeyStore = (app as Oid4vciSigningKeyStoreGraph).signingKeyStore

    init {
        // Register software KMS provider for crypto operations in tests
        val config = SoftwareKmsProviderConfig(id = "oid4vci-test-kms")
        val factory = (app as SoftwareKmsProviderFactoryImpl.Graph).softwareKmsProvider
        val provider = factory.create(config, execution)
        val kms = session.graph.asKeyManagerServiceGraph().keyManagerService
        kms.registerProvider(provider, makeDefaultKms = true)
    }

    /**
     * Generate the AS signing key in KMS and register it with the [SigningKeyStore] so the
     * AS sign paths (`CreateAccessTokenCommand`, `CreateIdTokenCommand`) and `GetJwksCommand`
     * resolve the same active key. Returns the alias.
     */
    suspend fun ensureAsSigningKey(
        alias: String = OID4VCI_TEST_AS_SIGNING_KEY_ALIAS,
        alg: SignatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256,
        tenantId: String = OID4VCI_TEST_TENANT_ID,
    ): String {
        val kms = session.graph.asKeyManagerServiceGraph().keyManagerService
        val gen =
            kms.generateKeyResult(
                alias = alias,
                use = com.sphereon.crypto.core.jose.JwkUse.sig,
                alg = alg,
            )
        check(gen.isOk) { "AS signing key generation failed: ${if (gen.isErr) gen.error.message.defaultMessage else "<unknown>"}" }
        val keyPair = checkNotNull(gen.value.keyPair) { "generateKeyResult succeeded but keyPair is null" }
        val now = Clock.System.now()
        val signingKey =
            OAuth2SigningKey(
                tenantId = tenantId,
                keyInfo =
                    KeyInfo<KeyType>(
                        kid = keyPair.kid ?: keyPair.alias,
                        alias = alias,
                        providerId = keyPair.providerId,
                        signatureAlgorithm = alg,
                    ),
                state = OAuth2SigningKeyState.ACTIVE,
                priority = 1,
                createdAt = now,
                notBefore = now,
            )
        val register = signingKeyStore.register(signingKey)
        check(register.isOk) { "Failed to register AS signing key: ${if (register.isErr) register.error else "<unknown>"}" }
        return alias
    }
}

/**
 * Graph extension exposing the AppScope-bound [SigningKeyStore] to the integration test
 * fixtures so they can register a signing key before AS sign paths run.
 */
@ContributesTo(AppScope::class)
interface Oid4vciSigningKeyStoreGraph {
    val signingKeyStore: SigningKeyStore
}
