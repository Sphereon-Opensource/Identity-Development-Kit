/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.core.api.Err
import com.sphereon.core.api.IdkResult
import com.sphereon.core.api.Ok
import com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource
import com.sphereon.core.api.error.IdkError
import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.jose.jws.JwtServiceImpl
import com.sphereon.crypto.resolution.IdentifierService
import com.sphereon.di.context.PrincipalType
import com.sphereon.di.session.SessionScope
import com.sphereon.ktor.http.client.FetchRequestUriCommandImpl
import com.sphereon.ktor.http.client.provider.HttpClientEngineType
import com.sphereon.ktor.http.client.provider.HttpClientFactory
import com.sphereon.ktor.http.client.provider.HttpClientOptions
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.common.Oid4vpRequestTrustMaterial
import com.sphereon.openid.oid4vp.common.Oid4vpRequestTrustMaterialProvider
import com.sphereon.openid.oid4vp.common.Oid4vpX509TrustAnchor
import com.sphereon.openid.oid4vp.common.ParseClientIdCommand
import com.sphereon.openid.oid4vp.common.ParseTransactionDataCommand
import com.sphereon.openid.oid4vp.common.impl.ValidateClientIdCommandImpl
import com.sphereon.openid.oid4vp.common.responseUri
import com.sphereon.openid.oid4vp.holder.OID4VP_STATIC_DISCOVERY_REQUEST_OBJECT_AUDIENCE
import com.sphereon.openid.oid4vp.holder.ResolvedOid4vpRequest
import com.sphereon.openid.oid4vp.holder.WalletConfig
import com.sphereon.openid.oid4vp.holder.command.ResolveClientMetadataCommand
import com.sphereon.openid.oid4vp.holder.impl.ParseAuthorizationRequestCommandImpl
import com.sphereon.openid.oid4vp.holder.impl.ResolveAuthorizationRequestCommandImpl
import com.sphereon.openid.oid4vp.universal.impl.createUniversalOid4vpTestAppGraph
import dev.zacsweers.metro.ContributesTo
import io.ktor.client.HttpClient
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.jupiter.api.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ContributesTo(SessionScope::class)
interface RequestObjectClientIdentityGraph {
    val requestObjectParseClientIdCommand: ParseClientIdCommand
    val requestObjectResolveClientMetadataCommand: ResolveClientMetadataCommand
    val requestObjectParseTransactionDataCommand: ParseTransactionDataCommand
    val requestObjectIdentifierService: IdentifierService
}

/**
 * The holder reports an authenticated client identity only for a by-value Request Object whose
 * JWS verifies with the key its client identifier designates. Real JWS verification and PKIX
 * validation run here; the test supplies only the governed X.509 trust anchor.
 */
class X509RequestObjectHolderVerificationTest {
    @Test
    fun x509SanDnsRequestObjectSignedWithItsCertificateKeyResolvesWithTheValidatedChain() =
        runTest {
            val resolved = holder().verify(byValueRequest(SAN_DNS_CLIENT_ID, signedRequestObject(SAN_DNS_CLIENT_ID, VERIFIER_LEAF_PRIVATE_KEY)))

            assertTrue(resolved.isOk, resolved.toString())
            assertTrue(resolved.value.verifierInfo.clientIdValid)
            assertEquals(ClientIdScheme.X509_SAN_DNS, resolved.value.verifierInfo.clientIdScheme)
            assertEquals(listOf(VERIFIER_LEAF_CERTIFICATE), resolved.value.verifierInfo.requestObjectCertificateChain)
            assertEquals(RESPONSE_URI, resolved.value.request.responseUri)
        }

    @Test
    fun x509HashRequestObjectSignedWithItsCertificateKeyResolvesWithTheValidatedChain() =
        runTest {
            val resolved = holder().verify(byValueRequest(HASH_CLIENT_ID, signedRequestObject(HASH_CLIENT_ID, VERIFIER_LEAF_PRIVATE_KEY)))

            assertTrue(resolved.isOk, resolved.toString())
            assertTrue(resolved.value.verifierInfo.clientIdValid)
            assertEquals(ClientIdScheme.X509_HASH, resolved.value.verifierInfo.clientIdScheme)
            assertEquals(listOf(VERIFIER_LEAF_CERTIFICATE), resolved.value.verifierInfo.requestObjectCertificateChain)
        }

    @Test
    fun unsignedRequestObjectIsRejected() =
        runTest {
            val unsigned = "${jsonSegment(header(alg = "none"))}.${jsonSegment(claims(SAN_DNS_CLIENT_ID))}."

            assertTrue(holder().verify(byValueRequest(SAN_DNS_CLIENT_ID, unsigned)).isErr)
        }

    @Test
    fun requestObjectSignedByAnotherKeyBehindTheVerifierChainIsRejected() =
        runTest {
            val forged = signedRequestObject(SAN_DNS_CLIENT_ID, ATTACKER_PRIVATE_KEY)

            assertTrue(holder().verify(byValueRequest(SAN_DNS_CLIENT_ID, forged)).isErr)
        }

    @Test
    fun requestObjectWithoutAnOuterClientIdIsNeverVerifiedWithARequestSuppliedKey() =
        runTest {
            val forged = signedRequestObject(SAN_DNS_CLIENT_ID, ATTACKER_PRIVATE_KEY)

            val result = holder().verify("openid4vp://?request=$forged&client_metadata=${ATTACKER_CLIENT_METADATA.encodeURLParameter()}")

            assertTrue(result.isErr, "a Request Object claiming an X.509 client identifier must verify with its x5c leaf key: $result")
        }

    @Test
    fun chainNotIssuedByAGovernedTrustAnchorIsRejected() =
        runTest {
            val request = byValueRequest(SAN_DNS_CLIENT_ID, signedRequestObject(SAN_DNS_CLIENT_ID, VERIFIER_LEAF_PRIVATE_KEY))

            assertTrue(holder(trustAnchor = UNRELATED_CA_CERTIFICATE).verify(request).isErr)
        }

    @Test
    fun x509SanDnsClientIdTheCertificateDoesNotNameIsNotAuthenticated() =
        runTest {
            val clientId = "x509_san_dns:other.example"

            val resolved = holder().verify(byValueRequest(clientId, signedRequestObject(clientId, VERIFIER_LEAF_PRIVATE_KEY)))

            assertTrue(resolved.isOk, resolved.toString())
            assertFalse(resolved.value.verifierInfo.clientIdValid)
            assertTrue(resolved.value.verifierInfo.requestObjectCertificateChain.isEmpty())
        }

    @Test
    fun x509HashOfAnotherCertificateIsNotAuthenticated() =
        runTest {
            val clientId = "x509_hash:${"A".repeat(43)}"

            val resolved = holder().verify(byValueRequest(clientId, signedRequestObject(clientId, VERIFIER_LEAF_PRIVATE_KEY)))

            assertTrue(resolved.isOk, resolved.toString())
            assertFalse(resolved.value.verifierInfo.clientIdValid)
            assertTrue(resolved.value.verifierInfo.requestObjectCertificateChain.isEmpty())
        }

    private fun holder(trustAnchor: String = TEST_CA_CERTIFICATE): RequestObjectHolder {
        val app = createUniversalOid4vpTestAppGraph(TestScope(), appId = "x509-request-object-holder", profile = "test", version = "1.0.0")
        DefaultPrincipalMapPropertySource.addProperties(SOFTWARE_KMS_PROPERTIES)
        app.userContextManager.destroyAll()
        val session = app.userContextManager.getAnonymous().sessionContextManager.createOrGetFromId("holder", principalType = PrincipalType.USER)
        val execution = session.asCoreApiServiceGraph().serviceExecution
        val graph: Any = session.graph
        val holderDependencies = graph as HolderDepsGraph
        val clientIdentity = graph as RequestObjectClientIdentityGraph
        val trustMaterial = GovernedTrustAnchor(trustAnchor)
        return RequestObjectHolder(
            parse =
                ParseAuthorizationRequestCommandImpl(
                    execution = execution,
                    parseUriQueryCommand = holderDependencies.parseUriQueryCommand,
                    fetchRequestUriCommand = FetchRequestUriCommandImpl(execution = execution, httpClientFactory = NoNetworkHttpClientFactory),
                    jarService = (graph as Oauth2JarGraph).jarService,
                    httpClientFactory = NoNetworkHttpClientFactory,
                    externalIdentifierService = holderDependencies.externalIdentifierService,
                    jwtService = (graph as JwtServiceImpl.Graph).jwtService,
                    requestTrustMaterialProvider = trustMaterial,
                ),
            resolve =
                ResolveAuthorizationRequestCommandImpl(
                    parseClientIdCommand = clientIdentity.requestObjectParseClientIdCommand,
                    validateClientIdCommand =
                        ValidateClientIdCommandImpl(
                            execution = execution,
                            identifierService = clientIdentity.requestObjectIdentifierService,
                            requestTrustMaterialProvider = trustMaterial,
                        ),
                    resolveClientMetadataCommand = clientIdentity.requestObjectResolveClientMetadataCommand,
                    parseTransactionDataCommand = clientIdentity.requestObjectParseTransactionDataCommand,
                    execution = execution,
                ),
        )
    }

    private fun signedRequestObject(
        clientId: String,
        signingKey: String,
    ): String {
        val signingInput = "${jsonSegment(header(alg = "ES256"))}.${jsonSegment(claims(clientId))}"
        val signature =
            Signature.getInstance("SHA256withECDSAinP1363Format").run {
                initSign(KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(signingKey))))
                update(signingInput.encodeToByteArray())
                sign()
            }
        return "$signingInput.${BASE64_URL.encodeToString(signature)}"
    }

    private fun header(alg: String): JsonObject =
        buildJsonObject {
            put("alg", alg)
            put("typ", "oauth-authz-req+jwt")
            putJsonArray("x5c") { add(VERIFIER_LEAF_CERTIFICATE) }
        }

    private fun claims(clientId: String): JsonObject =
        buildJsonObject {
            put("client_id", clientId)
            put("aud", OID4VP_STATIC_DISCOVERY_REQUEST_OBJECT_AUDIENCE)
            put("response_type", "vp_token")
            put("response_mode", "direct_post")
            put("response_uri", RESPONSE_URI)
            put("nonce", "nonce-request-object")
            put("state", "state-request-object")
        }

    private fun jsonSegment(json: JsonObject): String = BASE64_URL.encodeToString(json.toString().encodeToByteArray())

    private fun byValueRequest(
        clientId: String,
        requestObject: String,
    ): String = "openid4vp://?client_id=${clientId.encodeURLParameter()}&request=$requestObject"

    private class RequestObjectHolder(
        private val parse: ParseAuthorizationRequestCommandImpl,
        private val resolve: ResolveAuthorizationRequestCommandImpl,
    ) {
        suspend fun verify(authorizationRequestUri: String): IdkResult<ResolvedOid4vpRequest, IdkError> {
            val parsed = parse.parseAuthorizationRequest(authorizationRequestUri, WalletConfig(audience = OID4VP_STATIC_DISCOVERY_REQUEST_OBJECT_AUDIENCE))
            return if (parsed.isErr) Err(parsed.error) else resolve.resolveAuthorizationRequest(parsed.value)
        }
    }

    private class GovernedTrustAnchor(
        private val certificateDer: String,
    ) : Oid4vpRequestTrustMaterialProvider {
        override suspend fun resolve(): IdkResult<Oid4vpRequestTrustMaterial, IdkError> =
            Ok(
                Oid4vpRequestTrustMaterial(
                    x509 =
                        listOf(
                            Oid4vpX509TrustAnchor(
                                certificateDer.chunked(64).joinToString("\n", "-----BEGIN CERTIFICATE-----\n", "\n-----END CERTIFICATE-----\n"),
                            ),
                        ),
                ),
            )
    }

    private object NoNetworkHttpClientFactory : HttpClientFactory {
        override fun createClient(options: HttpClientOptions): HttpClient = error("by-value Request Object verification must not open an HTTP client")

        override fun isSupportedOptions(options: HttpClientOptions): Boolean = true

        override fun getEngineTypesSupported(): List<HttpClientEngineType> = listOf(HttpClientEngineType.CIO)

        override fun getEngineTypeDefault(): HttpClientEngineType = HttpClientEngineType.CIO
    }

    private companion object {
        const val RESPONSE_URI = "https://verifier.example/response"
        const val SAN_DNS_CLIENT_ID = "x509_san_dns:verifier.example"
        const val HASH_CLIENT_ID = "x509_hash:xtZoHbCm2hhy8i3anRKEGgysW7Ovfe1Arrw0GhcWQbg"
        val BASE64_URL: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

        val SOFTWARE_KMS_PROPERTIES =
            mapOf(
                "kms.providers.test-software.type" to "software",
                "kms.providers.test-software.id" to "test-software",
                "kms.providers.test-software.keystore.type" to "memory",
                "kms.providers.test-software.keystore.id" to "test-memory-keystore",
                "kms.providers.test-software.keystore.keyVisibility" to "private",
                "kms.providers.test-software.keystore.overwriteAlias" to "true",
            )

        const val ATTACKER_CLIENT_METADATA =
            """{"jwks":{"keys":[{"kty":"EC","crv":"P-256","x":"xZNrNXMbdI_Uj7ImFaMWBGHkli_c6GIfrlPneg62TLU","y":"MXpChijPuGECrWs2YlfmRxe8""" +
                """C92Ag7cpInPfXW2jkjU","kid":"attacker-key"}]}}"""

        const val TEST_CA_CERTIFICATE =
            "MIIBizCCATGgAwIBAgICA+kwCgYIKoZIzj0EAwIwKjEoMCYGA1UEAwwfUmVxdWVzdCBPYmplY3QgVGVzdCBWZXJpZmllciBDQTAgFw0y" +
                "NTAxMDEwMDAwMDBaGA8yMTI1MDEwMTAwMDAwMFowKjEoMCYGA1UEAwwfUmVxdWVzdCBPYmplY3QgVGVzdCBWZXJpZmllciBDQTBZMBMG" +
                "ByqGSM49AgEGCCqGSM49AwEHA0IABC3Ui1P2+iwY9CQ6uDqqPNtIvqvBllqJaaFW+6TSqZHyfBHcXDlgn+BEfi3u1M7qQCGGiBWN4dvL" +
                "DLdpSR6HqMijRTBDMBIGA1UdEwEB/wQIMAYBAf8CAQAwDgYDVR0PAQH/BAQDAgEGMB0GA1UdDgQWBBQb+rbmYg6s9t6XqqBh3NuR+XnU" +
                "WzAKBggqhkjOPQQDAgNIADBFAiEA8Z87ZX4goXuEjWK2G5iLGhQnCFNZs2v+CQgRhFo08dkCIGfaRtILt0dBMpsPv2LtC9cDE+9oWswz" +
                "04nLcwfPcumX"

        const val UNRELATED_CA_CERTIFICATE =
            "MIIBcDCCARWgAwIBAgICC7swCgYIKoZIzj0EAwIwHDEaMBgGA1UEAwwRVW5yZWxhdGVkIFRlc3QgQ0EwIBcNMjUwMTAxMDAwMDAwWhgP" +
                "MjEyNTAxMDEwMDAwMDBaMBwxGjAYBgNVBAMMEVVucmVsYXRlZCBUZXN0IENBMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEFlc6h3rq" +
                "gr0aU3Rzulx0AWDqIZEDjgJ0eP7q7EYD+C4UxrgBhr3Rj217w+QISQI7QVIoTfnrsA8dyrEHhqLaz6NFMEMwEgYDVR0TAQH/BAgwBgEB" +
                "/wIBADAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFBJS4+qWUkBTNqIaIxW195xcrvCtMAoGCCqGSM49BAMCA0kAMEYCIQCDE07I9vGs" +
                "9ka9UlCMY9WsyHBiehbXeZbCBI1oolPqSwIhAKlJ1aH8/im6BA7laKydpG/gfQ6PXkTsJ263KdHC68xt"

        /** CA-issued leaf with SAN dNSName verifier.example; its SHA-256 is the x509_hash client identifier above. */
        const val VERIFIER_LEAF_CERTIFICATE =
            "MIIBtDCCAVqgAwIBAgICB9IwCgYIKoZIzj0EAwIwKjEoMCYGA1UEAwwfUmVxdWVzdCBPYmplY3QgVGVzdCBWZXJpZmllciBDQTAgFw0y" +
                "NTAxMDEwMDAwMDBaGA8yMTI1MDEwMTAwMDAwMFowGzEZMBcGA1UEAwwQdmVyaWZpZXIuZXhhbXBsZTBZMBMGByqGSM49AgEGCCqGSM49" +
                "AwEHA0IABIOu1xW/33w8OBveSt5zU4N5aroFQcc10HdrYdwFCfXElzUUBVZ5QZBBuoSRTivhMhw5SDdDGAywm8OLk4vJTiujfTB7MAwG" +
                "A1UdEwEB/wQCMAAwDgYDVR0PAQH/BAQDAgeAMBsGA1UdEQQUMBKCEHZlcmlmaWVyLmV4YW1wbGUwHQYDVR0OBBYEFM4LEXNHEHHEbLJx" +
                "G5w1Et8OgOHzMB8GA1UdIwQYMBaAFBv6tuZiDqz23peqoGHc25H5edRbMAoGCCqGSM49BAMCA0gAMEUCIQCk+YCurB1D2dRqCe5Eeedc" +
                "XT3fDdDql4iF2VSJNtx0hQIgPARTjfL3c571Axx1rLCTpyPn3mlOEtDIT3SZx3POMvs="

        const val VERIFIER_LEAF_PRIVATE_KEY =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgQIxlC2znxT6a5J0qKMgCpcDktiDyPFBHvBID5x/cclihRANCAASDrtcV" +
                "v998PDgb3krec1ODeWq6BUHHNdB3a2HcBQn1xJc1FAVWeUGQQbqEkU4r4TIcOUg3QxgMsJvDi5OLyU4r"

        const val ATTACKER_PRIVATE_KEY =
            "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQg+OFAUoDlqBal7ezqFGuAdsCXdZ1RoqFKolxffXz9bMKhRANCAATFk2s1" +
                "cxt0j9SPsiYVoxYEYeSWL9zoYh+uU+d6DrZMtTF6QoYoz7hhAq1rNmJX5kcXvAvdgIO3KSJz311to5I1"
    }
}
