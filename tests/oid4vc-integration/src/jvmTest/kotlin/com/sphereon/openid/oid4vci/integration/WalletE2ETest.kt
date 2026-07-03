/*
 * (c) 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.openid.oid4vci.integration

import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.http.GenericHttpRequest
import com.sphereon.core.api.http.GenericHttpResponse
import com.sphereon.core.api.http.HttpAdapter
import com.sphereon.core.api.http.RoutableHttpAdapter
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientFactoryJvmImpl
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.openid.oid4vc.common.QrCodeOptions
import com.sphereon.openid.oid4vci.issuer.command.BuildIssuerMetadataArgs
import com.sphereon.openid.oid4vci.issuer.command.CreateCredentialOfferArgs
import com.sphereon.openid.oid4vci.issuer.service.Oid4vciIssuerService
import com.sphereon.openid.oid4vp.dcql.DcqlClaimQuery
import com.sphereon.openid.oid4vp.dcql.DcqlCredentialQuery
import com.sphereon.openid.oid4vp.dcql.DcqlQuery
import com.sphereon.openid.oid4vp.universal.CreateAuthorizationRequestInput
import com.sphereon.openid.oid4vp.universal.GetAuthorizationRequestStatusOutput
import com.sphereon.openid.oid4vp.verifier.model.AuthorizationSessionStatus
import com.sphereon.wallet.impl.di.WalletGraph
import com.sphereon.wallet.ObtainCredentialRequest
import com.sphereon.wallet.ObtainCredentialResult
import com.sphereon.wallet.WalletConfig
import com.sphereon.wallet.credential.CredentialFormat
import com.sphereon.wallet.credential.CredentialLifecycleState
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// =========================================================================
// DI graph accessor for the Wallet facade
// =========================================================================

/**
 * Pulled from WalletGraph (contributed by lib-wallet-impl via
 * @ContributesTo(SessionScope) in WalletGraph.kt). Casting session.graph
 * to this gives access to the DI-wired Wallet.
 */
@ContributesTo(SessionScope::class)
interface WalletE2ETestGraph {
    val oid4vciIssuerService: Oid4vciIssuerService
}

// =========================================================================
// In-process HttpClientFactory replacement for wallet holder HTTP calls
//
// WalletImpl injects Oid4vciHolder and Oid4vpHolder from DI. Those holders
// use the session-scoped HttpClientFactory binding for all outgoing HTTP.
// The default binding (HttpClientFactoryJvmImpl) makes real CIO/OkHttp calls
// that can't reach an in-process issuer/verifier.
//
// We cannot inject Set<HttpAdapter> directly because some adapters transitively
// depend on FetchRequestUriCommandImpl -> HttpClientFactory -> this factory,
// creating a hard runtime cycle (StackOverflowError even if the compile-time
// cycle is broken with Provider<>).
//
// Solution: a thread-safe holder object (WalletTestAdapterHolder) is bound
// into the DI graph as a singleton. The factory reads adapters from the holder
// lazily at createClient() time. The test sets the adapters on the holder AFTER
// the graph is fully constructed (and all commands are already instantiated),
// so there is no circular construction.
//
// replaces = [HttpClientFactoryJvmImpl::class] ensures Metro picks this binding
// when this test module is on the classpath.
// =========================================================================

/**
 * Thread-safe mutable holder for the adapter set.
 * Set once after graph construction; read by WalletTestInProcessHttpClientFactory.createClient().
 */
@Inject
@SingleIn(SessionScope::class)
class WalletTestAdapterHolder {
    @Volatile
    var adapters: Set<HttpAdapter> = emptySet()
}

@ContributesTo(SessionScope::class)
interface WalletTestAdapterHolderGraph {
    val walletTestAdapterHolder: WalletTestAdapterHolder
}

@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<HttpClientFactory>(),
    replaces = [HttpClientFactoryJvmImpl::class],
)
class WalletTestInProcessHttpClientFactory(
    private val adapterHolder: WalletTestAdapterHolder,
) : HttpClientFactory {
    override fun createClient(options: HttpClientOptions): HttpClient {
        val adapters = adapterHolder.adapters
        val engine =
            MockEngine { request ->
                val url = request.url
                val path = url.encodedPath
                val method = request.method.value
                val headers = request.headers.entries().associate { (k, v) -> k to v.joinToString(",") }
                val body =
                    when (val content = request.body) {
                        is OutgoingContent.ByteArrayContent -> content.bytes().decodeToString()
                        is OutgoingContent.NoContent -> null
                        else -> null
                    }

                val host = url.host + (if (url.port != 443 && url.port != 80) ":${url.port}" else "")
                val enrichedHeaders =
                    headers +
                        mapOf(
                            "host" to host,
                            "x-forwarded-proto" to url.protocol.name,
                        )
                val queryParams = url.parameters.names().associateWith { url.parameters[it] }

                val genericRequest =
                    GenericHttpRequest(
                        method = method,
                        path = path,
                        queryParameters = queryParams,
                        headers = enrichedHeaders,
                        bodySupplier = body?.let { { it } },
                    )

                var response: GenericHttpResponse? = null
                for (adapter in adapters) {
                    if (adapter is RoutableHttpAdapter && !adapter.canHandle(genericRequest)) continue
                    val result = adapter.handleRequest(genericRequest)
                    // 404 = not found by this adapter; keep trying.
                    // COMMAND_ARG_NOT_SUPPORTED_ERROR at 400 means canHandle() was a false positive
                    // (e.g. OAuth2DiscoveryHttpAdapter claims /.well-known/* paths but can't handle
                    // /.well-known/openid-credential-issuer — it returns 400 with that error code).
                    // Continue to the next adapter so the OID4VCI issuer metadata adapter can claim it.
                    if (result.statusCode == 404) continue
                    if (result.statusCode == 400 && result.body?.contains("COMMAND_ARG_NOT_SUPPORTED_ERROR") == true) continue
                    response = result
                    break
                }
                val resp = response ?: GenericHttpResponse(404, emptyMap(), "Not found by WalletTestInProcessHttpClientFactory")

                respond(
                    content = resp.body ?: "",
                    status = HttpStatusCode.fromValue(resp.statusCode),
                    headers = headersOf(*resp.headers.map { (k, v) -> k to listOf(v) }.toTypedArray()),
                )
            }
        return HttpClient(engine)
    }

    override fun isSupportedOptions(options: HttpClientOptions): Boolean = true

    override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(HttpClientEngineType.CIO)

    override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
}

// =========================================================================
// WalletE2ETest
//
// Full issuance + presentation driven through the Wallet facade against
// the in-process issuer + verifier harness.
//
// Option A (graph wiring):
//   - lib-wallet-impl is on the test classpath; Metro auto-wires WalletImpl
//     into the session graph via @ContributesBinding(SessionScope, Wallet).
//   - WalletTestInProcessHttpClientFactory replaces HttpClientFactoryJvmImpl
//     so all holder HTTP calls route to the real server adapters in-process.
//   - wallet = (ctx.session.graph as WalletGraph).wallet
// =========================================================================

class WalletE2ETest {
    companion object {
        private const val CREDENTIAL_CONFIG_ID = "UniversityDegree"

        // Second credential configuration used by presentationViaWalletFacade: a `dc+sd-jwt`
        // credential so the holder produces a Key Binding JWT on presentation (the verifier's
        // holder-binding check then verifies KB-JWT signature + nonce + aud + sd_hash). Kept
        // separate from CREDENTIAL_CONFIG_ID so fullIssuanceViaWalletFacade's jwt_vc_json
        // assertions are untouched.
        private const val SD_JWT_CONFIG_ID = "UniversityDegreeSdJwt"
        private const val SD_JWT_VCT = "https://issuer.example.com/public/schema/vct/UniversityDegreeSdJwt"
        private const val ISSUER_SIGNING_KEY_ALIAS = "issuer-vc-signing-key"
        private const val VERIFIER_SIGNING_KEY_ALIAS = "verifier-jar-signing-key"
        private const val WALLET_CLIENT_ID = "https://wallet.example.com"
        private const val WALLET_INSTANCE_ID = "wallet-e2e"

        init {
            // Register credential configuration properties before the DI session graph is
            // constructed. GetIssuerMetadataEndpointCommand reads from
            // ConfigDrivenOid4vciIssuerConfigProvider.credentialConfigurations, which
            // resolves from ConfigService. Without these entries the metadata endpoint
            // returns an empty credential_configurations_supported map, failing
            // BuildIssuerMetadataArgs validation with HTTP 400.
            //
            // Keys use the same camelCase form that the config provider passes to
            // configService.getPropertyAsString(...); PropertyKeyNormalizerImpl normalises
            // both the stored key and the lookup key identically, so either form works.
            // Bracket-quoted segments ([UniversityDegree]) are preserved verbatim by the
            // normaliser and must match on both sides.
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentialConfigurationIds",
                "$CREDENTIAL_CONFIG_ID,$SD_JWT_CONFIG_ID",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].format",
                "jwt_vc_json",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].scope",
                "degree",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].bindingMethods",
                "did:key,did:jwk",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].signingAlgorithms",
                "ES256",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].proofTypes.jwt.signingAlgorithms",
                "ES256",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].credentialDefinition.types",
                "VerifiableCredential,UniversityDegreeCredential",
            )

            // Issuer signing key for minting the jwt_vc_json credential. The issuer resolves the
            // per-credential signing key from oid4vci.issuer.credentials.[<id>].signingKeyAlias and
            // signs under did:jwk (kid = did:jwk:...#vm). The test generates a KMS key under this
            // exact alias before requesting the credential (see ensureIssuerSigningKey()).
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].signingKeyAlias",
                ISSUER_SIGNING_KEY_ALIAS,
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$CREDENTIAL_CONFIG_ID].signingKeyMode",
                "did:jwk",
            )

            // Second credential configuration: `dc+sd-jwt`. Used by presentationViaWalletFacade so
            // the wallet/holder produces a holder-bound presentation (KB-JWT). `vct` is required by
            // the SD-JWT DC format handler; `degree` is declared as a selectively-disclosable claim
            // matching the DCQL claim path the verifier requests.
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$SD_JWT_CONFIG_ID].format",
                "dc+sd-jwt",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$SD_JWT_CONFIG_ID].vct",
                SD_JWT_VCT,
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$SD_JWT_CONFIG_ID].scope",
                "degree_sdjwt",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$SD_JWT_CONFIG_ID].bindingMethods",
                "did:key,did:jwk,jwk",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$SD_JWT_CONFIG_ID].signingAlgorithms",
                "ES256",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$SD_JWT_CONFIG_ID].proofTypes.jwt.signingAlgorithms",
                "ES256",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$SD_JWT_CONFIG_ID].signingKeyAlias",
                ISSUER_SIGNING_KEY_ALIAS,
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "oid4vci.issuer.credentials.[$SD_JWT_CONFIG_ID].signingKeyMode",
                "did:jwk",
            )

            // KV store used by KvBlobMetadataIndex (blob metadata search index).
            // TypeSuffixEntryDetection discovers store IDs by finding keys ending in ".type".
            // Store ID "blob.metadata" maps to config key path kv.stores.blob.metadata.type
            // (the dot in the store ID is already a path delimiter — no bracket quoting needed).
            DefaultPrincipalMapPropertySource.addProperty(
                "kv.stores.blob.metadata.type",
                "memory",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "kv.stores.blob.metadata.scopeBinding",
                "TENANT",
            )

            // Blob store used by BlobWalletCredentialStore -> DefaultBlobService.
            // DefaultBlobService.defaultStoreId() returns the first ID from blobStoreService.getStoreIds()
            // which reads via BlobStoreConfigBinder from blob.stores.* config.
            // BlobInfo has no storeId set, so DefaultBlobService uses the default store.
            DefaultPrincipalMapPropertySource.addProperty(
                "blob.stores.default.type",
                "memory",
            )
            DefaultPrincipalMapPropertySource.addProperty(
                "blob.stores.default.scopeBinding",
                "TENANT",
            )

            // NOTE: The verifier request-object (JAR) signing config that the presentation flow
            // needs (oid4vp.verifier.request-object.signing.*) is intentionally NOT registered
            // here. DefaultPrincipalMapPropertySource is a process-global property source, so
            // enabling verifier signing globally would leak into other tests in the same JVM
            // (e.g. WalletPresentationHttpE2ETest) and break their unsigned-request flows. The
            // ignored presentationViaWalletFacade test documents exactly which verifier signing
            // config + keys it requires; re-introduce them in a test-local (non-global) config
            // scope when that flow is enabled.
        }
    }

    private val ctx = Oid4vciTestContext(this, protocolBasePath = "/oid4vci")

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    private val issuerUrl = "https://issuer.example.com"
    private val verifierClientId = "https://verifier.example.com"
    private val walletClientId = WALLET_CLIENT_ID
    private val walletRedirectUri = "https://wallet.example.com/callback"

    // =========================================================================
    // Helper: get dispatcher (for verifier backend API)
    // =========================================================================

    private fun dispatcher() = (ctx.session.graph as com.sphereon.core.api.http.dispatch.HttpAdapterDispatcher.Graph).httpAdapterDispatcher

    // =========================================================================
    // Setup: wire adapters into the in-process factory holder
    //
    // This must be called after ctx (and thus the DI graph) is fully constructed.
    // The WalletTestInProcessHttpClientFactory reads from this holder lazily at
    // createClient() time, so all commands that depend on HttpClientFactory are
    // already fully instantiated before the adapters snapshot is set.
    // =========================================================================

    private fun wireInProcessAdapters() {
        val adapters = (ctx.session.graph as HttpAdapterTestGraph).httpAdapters
        val holder = (ctx.session.graph as WalletTestAdapterHolderGraph).walletTestAdapterHolder
        holder.adapters = adapters
    }

    // =========================================================================
    // Helper: build issuer metadata so the holder can resolve it
    // =========================================================================

    // =========================================================================
    // Helper: generate the issuer's per-credential signing key in the test KMS
    //
    // The issuer resolves the signing key for the `UniversityDegree` configuration from
    // oid4vci.issuer.credentials.[UniversityDegree].signingKeyAlias (registered in init {}).
    // The KMS must hold a key under that exact alias so the jwt_vc_json format handler can
    // mint and sign the credential under did:jwk. Without it the credential request fails at
    // the signing step with "Could not find key for alias <alias>".
    // =========================================================================

    private suspend fun ensureIssuerSigningKey() {
        val kms =
            ctx.session.graph
                .asKeyManagerServiceGraph()
                .keyManagerService
        val result =
            kms.generateKeyResult(
                alias = ISSUER_SIGNING_KEY_ALIAS,
                use = JwkUse.sig,
                alg = SignatureAlgorithm.ECDSA_SHA256,
            )
        assertTrue(
            result.isOk,
            "Issuer signing key generation should succeed: ${if (result.isErr) result.error.message.defaultMessage else ""}",
        )
    }

    // =========================================================================
    // Helper: generate the verifier's JAR signing key in the test KMS
    //
    // Request-object signing is enabled (init {}) with a did:jwk binding, so the verifier signs
    // the JAR under VERIFIER_SIGNING_KEY_ALIAS and self-identifies as decentralized_identifier:
    // did:jwk:.... The holder resolves the JAR verification key from that DID, so no client_metadata
    // is needed for the wallet to verify the request object.
    // =========================================================================

    private suspend fun ensureVerifierSigningKey() {
        val kms =
            ctx.session.graph
                .asKeyManagerServiceGraph()
                .keyManagerService
        val result =
            kms.generateKeyResult(
                alias = VERIFIER_SIGNING_KEY_ALIAS,
                use = JwkUse.sig,
                alg = SignatureAlgorithm.ECDSA_SHA256,
            )
        assertTrue(
            result.isOk,
            "Verifier JAR signing key generation should succeed: ${if (result.isErr) result.error.message.defaultMessage else ""}",
        )
    }

    private suspend fun buildIssuerMetadata() {
        // Verify the service layer can build valid CredentialIssuerMetadata.
        // The actual HTTP metadata endpoint (GetIssuerMetadataEndpointCommand) reads
        // credential configs directly from ConfigDrivenOid4vciIssuerConfigProvider —
        // it does NOT use the result of this call. This is purely a service-facade smoke test.
        val graph = ctx.session.graph as WalletE2ETestGraph
        val result =
            graph.oid4vciIssuerService.buildIssuerMetadata(
                BuildIssuerMetadataArgs(
                    issuerIdentifier = issuerUrl,
                    baseUrl = issuerUrl,
                    credentialConfigurations =
                        mapOf(
                            CREDENTIAL_CONFIG_ID to
                                com.sphereon.openid.oid4vci.common.model.CredentialConfigurationSupported(
                                    format = "jwt_vc_json",
                                    scope = "degree",
                                    cryptographicBindingMethodsSupported = listOf("did:key", "did:jwk"),
                                    credentialSigningAlgValuesSupported = listOf(kotlinx.serialization.json.JsonPrimitive("ES256")),
                                    credentialDefinition =
                                        com.sphereon.openid.oid4vci.common.model.CredentialDefinition(
                                            type = listOf("VerifiableCredential", "UniversityDegreeCredential"),
                                        ),
                                    proofTypesSupported =
                                        mapOf(
                                            "jwt" to
                                                com.sphereon.openid.oid4vci.common.model.ProofTypeSupported(
                                                    proofSigningAlgValuesSupported = listOf("ES256"),
                                                ),
                                        ),
                                ),
                        ),
                    authorizationServers = listOf(issuerUrl),
                ),
            )
        assertTrue(
            result.isOk,
            "buildIssuerMetadata should succeed: ${if (result.isErr) result.error.message.defaultMessage else ""}",
        )
    }

    // =========================================================================
    // Test: full issuance via the Wallet facade
    //
    // Step 1: Issuer builds metadata + creates pre-auth offer
    // Step 2: wallet.exchangePreAuthorizedCode -> TokenSet
    // Step 3: wallet.createHolderKey -> alias
    // Step 4: wallet.obtainCredential(count=1) -> CredentialRecord with 1 instance
    // Step 5: Assert document structure and persisted instances
    //
    // The matching presentation flow (verifier request -> wallet.present -> verifier verifies)
    // lives in presentationViaWalletFacade below.
    // =========================================================================

    @Test
    fun fullIssuanceViaWalletFacade() =
        runTest {
            // =====================================================================
            // Setup: wire the in-process adapter set into the factory holder so the
            // wallet's DI-wired holders route HTTP calls to the in-process adapters.
            // Must happen after graph construction, before any wallet HTTP call.
            // =====================================================================
            wireInProcessAdapters()

            // =====================================================================
            // Setup: AS signing key needed by token endpoint
            // =====================================================================
            ctx.ensureAsSigningKey()

            // =====================================================================
            // Setup: issuer signing key needed to mint + sign the jwt_vc_json credential
            // =====================================================================
            ensureIssuerSigningKey()

            // =====================================================================
            // Setup: verifier JAR signing key needed for the signed request object
            // =====================================================================
            ensureVerifierSigningKey()

            // =====================================================================
            // Setup: build issuer metadata so holder metadata resolution works
            // =====================================================================
            buildIssuerMetadata()

            // =====================================================================
            // Setup: get the Wallet from the DI graph
            //
            // WalletGraph is contributed by lib-wallet-impl via @ContributesTo(SessionScope).
            // WalletImpl is contributed via @ContributesBinding(SessionScope, binding<Wallet>()).
            // =====================================================================
            val wallet = (ctx.session.graph as WalletGraph).wallet

            // =====================================================================
            // Step 1: Issuer mints a pre-authorized code offer
            // =====================================================================
            val issuerGraph = ctx.session.graph as Oid4vciIssuanceTestGraph
            val issuer = issuerGraph.oid4vciIssuerService
            val offerResult =
                issuer.createCredentialOffer(
                    CreateCredentialOfferArgs(
                        issuerId = issuerUrl,
                        credentialConfigurationIds = listOf(CREDENTIAL_CONFIG_ID),
                        preAuthorizedCodeGrant = true,
                    ),
                )
            assertTrue(
                offerResult.isOk,
                "Offer creation should succeed: ${if (offerResult.isErr) offerResult.error.message.defaultMessage else ""}",
            )
            val preAuthCode =
                offerResult.value.offer.grants!!
                    .preAuthorizedCode!!
                    .preAuthorizedCode
            assertNotNull(preAuthCode, "Pre-auth code should be present")

            // =====================================================================
            // Step 2: Wallet exchanges pre-authorized code for tokens
            //
            // WalletImpl.exchangePreAuthorizedCode calls:
            //   1. oid4vciHolder.resolveIssuerMetadata -> GET /.well-known/openid-credential-issuer
            //   2. oid4vciHolder.selectAuthorizationServer -> selects token_endpoint
            //   3. oid4vciHolder.exchangePreAuthorizedCode -> POST /token
            //
            // All HTTP calls are routed in-process via WalletTestInProcessHttpClientFactory.
            // The token endpoint runs through the real OAuth2 AS HTTP adapter. The pre-auth code
            // was just minted by the issuer service, so the token endpoint consumes it and
            // returns a real JWT access token.
            // =====================================================================
            val tokenResult =
                wallet.exchangePreAuthorizedCode(
                    credentialIssuer = issuerUrl,
                    preAuthorizedCode = preAuthCode,
                )
            assertTrue(
                tokenResult.isOk,
                "exchangePreAuthorizedCode should succeed: ${if (tokenResult.isErr) tokenResult.error.message.defaultMessage else ""}",
            )
            val tokenSet = tokenResult.value
            assertNotNull(tokenSet.accessToken, "Access token should be present")
            assertTrue(tokenSet.accessToken.isNotEmpty(), "Access token should not be empty")

            // =====================================================================
            // Step 3: Wallet creates a holder key
            // =====================================================================
            val keyResult = wallet.createHolderKey(WALLET_INSTANCE_ID)
            assertTrue(
                keyResult.isOk,
                "createHolderKey should succeed: ${if (keyResult.isErr) keyResult.error.message.defaultMessage else ""}",
            )
            val holderKeyAlias = keyResult.value
            assertNotNull(holderKeyAlias, "Holder key alias should be present")
            assertTrue(holderKeyAlias.isNotEmpty(), "Holder key alias should not be empty")

            // =====================================================================
            // Step 4: Wallet obtains 1 credential instance from the issuer
            //
            // WalletImpl.obtainCredential:
            //   1. resolveIssuerMetadata -> GET /.well-known/openid-credential-issuer
            //   2. requestNonce -> POST /oid4vci/nonce (fresh c_nonce from issuer)
            //   3. createCredentialRequestProof (real KMS signing with fetched nonce)
            //   4. requestCredential -> POST /oid4vci/credential (with Bearer token)
            //
            // cNonce is passed as null: WalletImpl now fetches it from metadata.nonceEndpoint.
            // =====================================================================
            val obtainResult =
                wallet.obtainCredential(
                    ObtainCredentialRequest(
                        walletInstanceId = WALLET_INSTANCE_ID,
                        credentialIssuer = issuerUrl,
                        credentialConfigurationId = CREDENTIAL_CONFIG_ID,
                        accessToken = tokenSet.accessToken,
                        cNonce = null,
                        holderKeyAlias = holderKeyAlias,
                        signingAlgorithm = "ES256",
                        // count = 1: single credential proves the wallet SDK obtains a real credential
                        // end-to-end. Batch (count > 1) shares one c_nonce across N proofs and needs a
                        // proper per-request nonce-consume fix on the issuer (tracked separately).
                        count = 1,
                    ),
                )

            assertTrue(
                obtainResult.isOk,
                "obtainCredential should succeed: ${if (obtainResult.isErr) obtainResult.error.message.defaultMessage else ""}",
            )

            // =====================================================================
            // Step 5: Verify credential record structure and persistence
            // =====================================================================
            val doc = (obtainResult.value as ObtainCredentialResult.Stored).record
            assertEquals(
                1,
                doc.instances.size,
                "CredentialRecord should have 1 credential instance (count=1)",
            )
            assertEquals(
                CREDENTIAL_CONFIG_ID,
                doc.issuanceProvenance?.credentialConfigurationId,
                "credentialConfigurationId should match the requested configuration ID",
            )
            assertEquals(issuerUrl, doc.issuerRef.value, "issuerRef.value should match the issuer URL")
            assertTrue(doc.credentialTypeRefs.isNotEmpty(), "credentialTypeRefs should classify the credential")

            doc.instances.forEach { instance ->
                assertTrue(instance.id.isNotBlank(), "credential instance id should be present")
                assertNotNull(instance.raw, "raw credential string should be present")
                assertTrue(instance.raw?.isNotEmpty() == true, "raw credential string should not be empty")
                assertEquals(
                    CredentialFormat.JWT_VC_JSON,
                    instance.format,
                    "credential format should be jwt_vc_json",
                )
                assertEquals(
                    holderKeyAlias,
                    instance.holderKeyRef?.alias,
                    "holderKeyAlias should match the key created in Step 3",
                )
            }

            // =====================================================================
            // Step 5b: Assert metadata via listMetadata / findByCredentialTypeRef
            // =====================================================================
            val metaListResult = wallet.credentials.listMetadata(WALLET_INSTANCE_ID)
            assertTrue(
                metaListResult.isOk,
                "listMetadata should succeed: ${if (metaListResult.isErr) metaListResult.error.message.defaultMessage else ""}",
            )
            assertTrue(metaListResult.value.isNotEmpty(), "listMetadata should return at least one entry")

            val findMetaResult = wallet.credentials.findByCredentialTypeRef(WALLET_INSTANCE_ID, doc.credentialTypeRefs.first())
            assertTrue(
                findMetaResult.isOk,
                "findByCredentialTypeRef should succeed: ${if (findMetaResult.isErr) findMetaResult.error.message.defaultMessage else ""}",
            )
            assertEquals(1, findMetaResult.value.size, "Should have exactly one metadata entry for this credential type")

            val meta = findMetaResult.value.first()
            assertEquals(CREDENTIAL_CONFIG_ID, meta.credentialConfigurationId, "metadata.credentialConfigurationId should match config ID")
            assertEquals(issuerUrl, meta.issuerRef.value, "metadata.issuerRef.value should match issuer URL")
            assertEquals(CredentialFormat.JWT_VC_JSON, meta.format, "metadata.format should be JWT_VC_JSON")
            assertEquals(1, meta.instanceCount, "metadata.instanceCount should be 1")
            assertEquals(0, meta.boundInstanceCount, "metadata.boundInstanceCount should be 0 before any presentation")
            assertEquals(CredentialLifecycleState.ACTIVE, meta.lifecycleSummary.lifecycleState, "metadata lifecycle should be ACTIVE")

            // Load the full record via getCredential(credentialRecordId) and verify its structure
            val getDocResult = wallet.credentials.getCredential(WALLET_INSTANCE_ID, meta.credentialRecordId)
            assertTrue(
                getDocResult.isOk,
                "getCredential(credentialRecordId) should succeed: ${if (getDocResult.isErr) getDocResult.error.message.defaultMessage else ""}",
            )
            val persistedDoc = getDocResult.value
            assertNotNull(persistedDoc, "Persisted credential record should not be null")
            assertEquals(1, persistedDoc.instances.size, "Persisted credential record should have 1 instance")
            assertEquals(CREDENTIAL_CONFIG_ID, persistedDoc.issuanceProvenance?.credentialConfigurationId, "Persisted credential configuration id should match")

            // =====================================================================
            // Step 5c: Assert subjects field from the issued credential
            //
            // JwtVcJsonFormatHandler places the holder DID in the top-level JWT `sub`
            // claim when the proof was bound via a DID key (did:jwk / did:key). A blank
            // `vc.credentialSubject.id` is skipped (pre-auth without a real user) and the
            // extractor falls back to the JWT `sub` level. If neither carries a non-blank
            // subject identifier (e.g. no user identity in the token context), subjects
            // stays empty — that is also correct and must not crash.
            //
            // When subjects ARE populated every entry must have a non-blank value and the
            // no-op identity resolver leaves identityIdentifierId null.
            // =====================================================================
            for (subjectRef in doc.subjectRefs) {
                assertTrue(
                    subjectRef.value.isNotBlank(),
                    "Subject identifier must not be blank",
                )
                assertEquals(
                    null,
                    subjectRef.identityIdentifierId,
                    "No-op identity resolver leaves identityIdentifierId null",
                )
            }
            // If the issuer set the holder DID in `sub` (expected when a DID-bound proof
            // was used), each subject value must be a DID.
            doc.subjectRefs.forEach { subjectRef ->
                assertTrue(
                    subjectRef.value.startsWith("did:"),
                    "Subject identifier should be a DID when holder proof is DID-bound, got: ${subjectRef.value}",
                )
            }
        }

    // =========================================================================
    // Test: presentation via the Wallet facade (verifier <- wallet.present)
    //
    // Drives the OID4VP holder side of the Wallet facade end to end against the in-process
    // verifier: verifier creates a signed JAR (did:jwk) -> wallet.present parses + verifies the
    // JAR, selects the held credential, builds the DCQL vp_token object `{ "<queryId>": "<jwt>" }`
    // and submits it via direct_post. The verifier parses the DCQL object vp_token, verifies the
    // presentation, and advances the session to RESPONSE_VERIFIED with the disclosed claims.
    //
    // This exercises the DCQL-object-with-string-value path on the verifier's direct_post route
    // (the path that previously threw "Element class JsonObject is not a JsonPrimitive" before the
    // vp_token-as-JsonElement fix in lib-openid-oid4vp-common-public + verifier-impl).
    //
    // Verifier request-object (JAR) signing is enabled ONLY for this test via the DI-bound
    // WalletE2ETestRequestObjectSigningConfig flag, toggled in a try/finally so it never leaks into
    // other tests sharing the JVM (e.g. WalletPresentationHttpE2ETest's unsigned flow). We
    // intentionally do NOT enable signing through the process-global DefaultPrincipalMapPropertySource.
    //
    // The original blocker (the verifier `.jsonPrimitive` crash on the DCQL object vp_token) is
    // fixed by the vp_token-as-JsonElement change; the holder now also produces a Key Binding JWT
    // for `dc+sd-jwt` presentations (CreateAuthorizationResponseCommandImpl + PresentSdJwtCommand),
    // so the verifier's holder-binding check (KB-JWT signature + nonce + aud + sd_hash) verifies and
    // the session reaches authorization_response_verified.
    // =========================================================================

    @Test
    fun presentationViaWalletFacade() =
        runTest {
            WalletE2ETestRequestObjectSigningConfig.enableDidJwkSigning()
            try {
                wireInProcessAdapters()
                ctx.ensureAsSigningKey()
                ensureIssuerSigningKey()
                ensureVerifierSigningKey()
                buildIssuerMetadata()

                val wallet = (ctx.session.graph as WalletGraph).wallet

                val issuerGraph = ctx.session.graph as Oid4vciIssuanceTestGraph
                val issuer = issuerGraph.oid4vciIssuerService
                val offerResult =
                    issuer.createCredentialOffer(
                        CreateCredentialOfferArgs(
                            issuerId = issuerUrl,
                            credentialConfigurationIds = listOf(SD_JWT_CONFIG_ID),
                            preAuthorizedCodeGrant = true,
                        ),
                    )
                assertTrue(offerResult.isOk, "Offer creation should succeed")
                val preAuthCode =
                    offerResult.value.offer.grants!!
                        .preAuthorizedCode!!
                        .preAuthorizedCode

                val tokenResult =
                    wallet.exchangePreAuthorizedCode(
                        credentialIssuer = issuerUrl,
                        preAuthorizedCode = preAuthCode,
                    )
                assertTrue(tokenResult.isOk, "exchangePreAuthorizedCode should succeed")
                val tokenSet = tokenResult.value

                val keyResult = wallet.createHolderKey(WALLET_INSTANCE_ID)
                assertTrue(keyResult.isOk, "createHolderKey should succeed")
                val holderKeyAlias = keyResult.value

                val obtainResult =
                    wallet.obtainCredential(
                        ObtainCredentialRequest(
                            walletInstanceId = WALLET_INSTANCE_ID,
                            credentialIssuer = issuerUrl,
                            credentialConfigurationId = SD_JWT_CONFIG_ID,
                            accessToken = tokenSet.accessToken,
                            cNonce = null,
                            holderKeyAlias = holderKeyAlias,
                            signingAlgorithm = "ES256",
                            // count = 1: single credential proves the wallet SDK obtains a real credential
                            // end-to-end. Batch (count > 1) shares one c_nonce across N proofs and needs a
                            // proper per-request nonce-consume fix on the issuer (tracked separately).
                            count = 1,
                        ),
                    )
                assertTrue(
                    obtainResult.isOk,
                    "obtainCredential should succeed: ${if (obtainResult.isErr) obtainResult.error.message.defaultMessage else ""}",
                )
                val sdJwtRecord = (obtainResult.value as ObtainCredentialResult.Stored).record

                // =====================================================================
                // Step 6: Verifier creates authorization request (DCQL) for the dc+sd-jwt credential
                // =====================================================================
                val dcqlQuery =
                    DcqlQuery(
                        credentials =
                            listOf(
                                DcqlCredentialQuery(
                                    id = SD_JWT_CONFIG_ID,
                                    format = "dc+sd-jwt",
                                    claims =
                                        listOf(
                                            DcqlClaimQuery(path = listOf("degree")),
                                        ),
                                ),
                            ),
                    )

                val createReqInput =
                    CreateAuthorizationRequestInput(
                        dcqlQuery = dcqlQuery,
                        clientId = verifierClientId,
                        state = "wallet-e2e-state",
                        // Point the wallet's direct_post submission at the verifier's actual response endpoint
                        // (base `/oid4vp` + DirectPostResponseEndpointCommand `/auth/response`). Without this
                        // the response_uri defaults to `<verifier>/response`, which no adapter serves (404).
                        responseUri = "$verifierClientId/oid4vp/auth/response",
                        qrCodeOptions = QrCodeOptions(),
                    )
                val createReqJson = json.encodeToString(CreateAuthorizationRequestInput.serializer(), createReqInput)
                val createReqHttpRequest =
                    GenericHttpRequest.withTextBody(
                        method = "POST",
                        path = "/oid4vp/backend/auth/requests",
                        headers = mapOf("Content-Type" to "application/json"),
                        body = createReqJson,
                    )
                val createReqResponse = dispatcher().dispatch(createReqHttpRequest)
                assertEquals(
                    201,
                    createReqResponse.statusCode,
                    "Verifier authorization request creation should return 201. Body: ${createReqResponse.body}",
                )
                assertNotNull(createReqResponse.body, "Authorization request response body should not be null")

                val createReqOutput =
                    json.decodeFromString(
                        com.sphereon.openid.oid4vp.universal.CreateAuthorizationRequestOutput
                            .serializer(),
                        createReqResponse.body!!,
                    )
                val correlationId = createReqOutput.correlationId
                assertNotNull(correlationId, "correlationId should be present")
                val requestUri = createReqOutput.requestUri
                assertNotNull(requestUri, "requestUri should be present")
                assertTrue(requestUri.isNotEmpty(), "requestUri should not be empty")

                // =====================================================================
                // Step 7: Wallet presents credential via the Wallet facade
                //
                // WalletImpl.present:
                //   1. oid4vpHolder.parseAuthorizationRequest(requestUri) ->
                //      GET /oid4vp/request-uri/{correlationId} via in-process factory
                //   2. oid4vpHolder.resolveAuthorizationRequest -> resolves DCQL query
                //   3. wallet.credentials.findByCredentialTypeRef -> selects held credential
                //   4. oid4vpHolder.createAuthorizationResponse -> builds VP token
                //   5. oid4vpHolder.submitAuthorizationResponse ->
                //      POST /oid4vp/auth/response (direct_post) via in-process factory
                // =====================================================================
                val walletConfig =
                    WalletConfig(
                        walletInstanceId = WALLET_INSTANCE_ID,
                        clientId = walletClientId,
                        redirectUri = walletRedirectUri,
                    )
                val presentResult = wallet.present(requestUri, walletConfig)
                assertTrue(
                    presentResult.isOk,
                    "wallet.present should succeed: ${if (presentResult.isErr) presentResult.error.message.defaultMessage else ""}",
                )
                val presentationResult = presentResult.value
                assertTrue(presentationResult.submitted, "PresentationResult.submitted should be true")

                // =====================================================================
                // Step 7b: Assert boundInstanceCount incremented after presentation
                //
                // WalletImpl.present appends a presentation binding to the selected instance
                // and upserts the record. The metadata derivation counts non-empty bindingRefs
                // instances, so boundInstanceCount should now be 1 for the sd-jwt document.
                // The no-op identity resolver leaves identityIdentifierId null — that is expected and
                // verified here (the ref is still set; correlationId is just absent).
                // =====================================================================
                val postPresentMetaResult = wallet.credentials.findByCredentialTypeRef(WALLET_INSTANCE_ID, sdJwtRecord.credentialTypeRefs.first())
                assertTrue(
                    postPresentMetaResult.isOk,
                    "findByCredentialTypeRef after present should succeed: ${if (postPresentMetaResult.isErr) postPresentMetaResult.error.message.defaultMessage else ""}",
                )
                assertEquals(1, postPresentMetaResult.value.size, "Should have exactly one metadata entry for sd-jwt credential type after presentation")
                val sdJwtMeta = postPresentMetaResult.value.first()
                assertEquals(1, sdJwtMeta.boundInstanceCount, "boundInstanceCount should be 1 after presentation (instance got a binding ref)")
                assertEquals(CredentialLifecycleState.ACTIVE, sdJwtMeta.lifecycleSummary.lifecycleState, "sd-jwt credential status should remain ACTIVE after presentation")

                // Verify the full record reflects the bound instance
                val postPresentDocResult = wallet.credentials.getCredential(WALLET_INSTANCE_ID, sdJwtMeta.credentialRecordId)
                assertTrue(postPresentDocResult.isOk, "getCredential(credentialRecordId) after present should succeed")
                val postPresentDoc = postPresentDocResult.value
                assertNotNull(postPresentDoc, "Post-presentation credential record should not be null")
                val boundInstance = postPresentDoc.instances.firstOrNull { it.bindingRefs.isNotEmpty() }
                assertNotNull(boundInstance, "At least one instance should have a presentation binding after presentation")
                // identityIdentifierId is null because the no-op identity resolver does not enrich the ref
                assertEquals(null, boundInstance.bindingRefs.first().verifierRef.identityIdentifierId, "no-op resolver leaves identityIdentifierId null")

                // =====================================================================
                // Step 8: Assert the verifier session reached the RESPONSE_VERIFIED state
                //
                // After wallet.present submits the DCQL vp_token via direct_post, the verifier parses
                // the object-shaped vp_token, verifies the presentation, and advances the session to
                // RESPONSE_VERIFIED with the disclosed claims surfaced for the requested `degree` query.
                // =====================================================================
                val statusHttpRequest =
                    GenericHttpRequest(
                        method = "GET",
                        path = "/oid4vp/backend/auth/requests/$correlationId",
                    )
                val statusResponse = dispatcher().dispatch(statusHttpRequest)
                assertEquals(
                    200,
                    statusResponse.statusCode,
                    "Status check should return 200. Body: ${statusResponse.body}",
                )
                assertNotNull(statusResponse.body, "Status response body should not be null")

                val statusOutput =
                    json.decodeFromString(
                        GetAuthorizationRequestStatusOutput.serializer(),
                        statusResponse.body!!,
                    )
                assertEquals(correlationId, statusOutput.correlationId, "correlationId should match")
                assertEquals(
                    AuthorizationSessionStatus.AUTHORIZATION_RESPONSE_VERIFIED,
                    statusOutput.status,
                    "Verifier session should reach AUTHORIZATION_RESPONSE_VERIFIED after a successful DCQL-object " +
                        "vp_token presentation. Got: ${statusOutput.status}",
                )
            } finally {
                WalletE2ETestRequestObjectSigningConfig.disable()
            }
        }
}
