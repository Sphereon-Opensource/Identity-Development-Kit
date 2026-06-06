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

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.di.session.SessionScope
import com.sphereon.did.methods.jwk.JwkDidProviderImpl
import com.sphereon.openid.oid4vp.common.ClientIdScheme
import com.sphereon.openid.oid4vp.verifier.impl.http.DefaultRequestObjectSigningConfigBinding
import com.sphereon.openid.oid4vp.verifier.requesturi.RequestObjectSigningConfig
import com.sphereon.openid.oid4vp.verifier.requesturi.VerifierSignerBinding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Test-scoped binding for OID4VP verifier request-object (JAR) signing in the
 * oid4vc-integration test JVM.
 *
 * Replaces the production [DefaultRequestObjectSigningConfigBinding] (which reads
 * `oid4vp.verifier.request-object.signing.*` from the config service). It is gated by a
 * process-static flag that defaults to OFF, so only the test that explicitly opts in
 * (WalletE2ETest.presentationViaWalletFacade) sees signing enabled. Every other test in this
 * JVM, notably WalletPresentationHttpE2ETest's unsigned request flow, keeps the disabled
 * default.
 *
 * This deliberately avoids touching the process-global [com.sphereon.core.api.conf.DefaultPrincipalMapPropertySource]:
 * enabling verifier signing through that global property source would leak across every test
 * sharing the JVM. Toggling this DI-bound flag in a try/finally keeps the signing config
 * scoped to a single test invocation.
 */
@SingleIn(SessionScope::class)
@ContributesBinding(
    SessionScope::class,
    binding = binding<RequestObjectSigningConfig>(),
    replaces = [DefaultRequestObjectSigningConfigBinding::class],
)
@Inject
class WalletE2ETestRequestObjectSigningConfig(
    private val kms: KeyManagerService,
) : RequestObjectSigningConfig {
    override val audience: String = "https://wallet.example.com"
    override val expirationSeconds: Long = 60

    override val enabled: Boolean
        get() = signingEnabled

    override val includeIss: Boolean
        get() = signingEnabled

    override suspend fun resolveSigningKey(): KeyInfoType<*> = KeyInfo<Nothing>(alias = SIGNING_KEY_ALIAS)

    override suspend fun resolveSignerBinding(scheme: ClientIdScheme?): VerifierSignerBinding? {
        if (!signingEnabled) return null
        val keyResult = kms.getKeyResult(KeyInfo<Nothing>(alias = SIGNING_KEY_ALIAS))
        check(keyResult.isOk) { "Failed to load signing key '$SIGNING_KEY_ALIAS' from KMS: ${keyResult.error}" }
        val managedKey = keyResult.value.key
        val jwk = (managedKey?.key as? Jwk) ?: error("Signing key '$SIGNING_KEY_ALIAS' is not a JWK")
        val did = JwkDidProviderImpl.didFromJwk(jwk)
        return VerifierSignerBinding.Did(did = did, verificationMethodId = "$did#0")
    }

    companion object {
        const val SIGNING_KEY_ALIAS: String = "verifier-jar-signing-key"

        @Volatile
        private var signingEnabled: Boolean = false

        /** Enable did:jwk JAR signing for the duration of a single test. */
        fun enableDidJwkSigning() {
            signingEnabled = true
        }

        /** Disable JAR signing (the default). */
        fun disable() {
            signingEnabled = false
        }
    }
}
