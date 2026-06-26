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

package com.sphereon.crypto.jose.jwe

import com.sphereon.core.api.session.asCoreApiServiceGraph
import com.sphereon.crypto.core.KeyVisibility
import com.sphereon.crypto.core.ManagedKeyReference
import com.sphereon.crypto.core.ResolvedKeyInfo
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.interop.derPrivateKeyToJwk
import com.sphereon.crypto.core.interop.derPublicKeyToJwk
import com.sphereon.crypto.core.jose.Jwk
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.asKeyManagerServiceGraph
import com.sphereon.crypto.core.testutil.createCryptoTestAppGraph
import com.sphereon.crypto.kms.keystore.software.Pkcs12KeyStoreConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactoryImpl
import com.sphereon.crypto.resolution.IdentifierContext
import com.sphereon.crypto.resolution.managed.ManagedOptsKeyInfo
import com.sphereon.di.app.AppGraph
import com.sphereon.di.session.SessionInstance
import dev.whyoleg.cryptography.CryptographyProvider
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Reproduces the live license-import failure end to end against a REAL, file-backed PKCS12 `license`
 * keystore wired through proper DI — never an in-memory keystore.
 *
 * Production shape: the bundle import durably stores the recipient encryption key into the on-disk
 * `license` PKCS12 in one session; a SEPARATE, reused decrypt session unwraps the license JWE with
 * that key. The decrypt session's keystore was loaded earlier (with the platform's self-generated
 * recipient key under the same alias), so before the fix it resolved the stale self-generated key and
 * the JWE unwrap failed.
 *
 * Each side here is its own [AppGraph] session (a separate process/session) with a software KMS
 * provider whose keystore config points at the SAME `license.p12` file, so the cross-session sharing
 * the bug broke is exercised directly. The decrypt session is created FIRST, mirroring the reused
 * decrypt session that has already loaded the file before the import writes to it.
 */
class LicenseImportToDecryptHandoffTest {
    private val recipientAlias = "license-recipient"
    private val recipientKid = "license-recipient"
    private val licensePlaintext = "compact-license-jws-payload"

    /** Builds a software KMS provider over the file-backed `license` PKCS12 keystore in [session]. */
    private suspend fun registerFileLicenseProvider(
        app: AppGraph,
        session: SessionInstance,
        keystorePath: String,
    ): KeyManagerService {
        val config =
            SoftwareKmsProviderConfig(
                id = PROVIDER_ID,
                cryptographyProvider = CryptographyProvider.Default.name,
                autoCreateCertificate = true,
                keyStore =
                    Pkcs12KeyStoreConfig(
                        id = PROVIDER_ID,
                        password = "test-password",
                        path = keystorePath,
                        persist = true,
                        overwriteAlias = true,
                        keyVisibility = KeyVisibility.PRIVATE.keyVisibility,
                    ),
            )
        app as SoftwareKmsProviderFactoryImpl.Graph
        val provider = app.softwareKmsProvider.create(config, session.asCoreApiServiceGraph().serviceExecution)
        val kms = session.graph.asKeyManagerServiceGraph().keyManagerService
        kms.registerProvider(provider, makeDefaultKms = true)
        return kms
    }

    /** A real EC P-256 recipient encryption key as a private JWK, mirroring a bundle recipient key. */
    private fun recipientPrivateJwk(kid: String = recipientKid): Jwk {
        val pair =
            KeyPairGenerator
                .getInstance("EC")
                .apply { initialize(ECGenParameterSpec("secp256r1")) }
                .generateKeyPair()
        val priv = derPrivateKeyToJwk(pair.private.encoded)
        val pub = derPublicKeyToJwk(pair.public.encoded)
        return priv.copy(x = pub.x, y = pub.y, crv = priv.crv ?: pub.crv, kid = kid)
    }

    private fun decryptorOpts(kid: String = recipientKid): ManagedOptsKeyInfo =
        ManagedOptsKeyInfo(
            identifier = ManagedKeyReference(providerId = PROVIDER_ID, alias = recipientAlias, kid = kid),
            context = IdentifierContext(clientId = "license", clientIdScheme = "jwe", issuer = "urn:sphereon:license"),
        )

    private fun newSession(name: String): Pair<AppGraph, SessionInstance> {
        val app = createCryptoTestAppGraph(this)
        val session =
            app.userContextManager
                .getAnonymous()
                .sessionContextManager
                .createOrGetFromId(name)
        return app to session
    }

    /**
     * Builds a license-shaped compact JWE (ECDH-ES+A256KW / A256GCM) to the recipient key already
     * stored in [kms], using the real DI-resolved JweService, then returns the serialized compact JWE.
     */
    private suspend fun encryptLicenseJweTo(
        session: SessionInstance,
        kms: KeyManagerService,
        kid: String = recipientKid,
    ): String {
        val jweService = (session.graph as JweServiceImpl.Graph).jweService
        val recipient = decryptorOpts(kid = kid)
        val prepared =
            jweService.prepareJwe(
                PrepareJweArgs(
                    plaintext = licensePlaintext.encodeToByteArray(),
                    recipient = recipient,
                    keyEncryptionAlg = "ECDH-ES+A256KW",
                    contentEncryptionAlg = "A256GCM",
                ),
            )
        assertTrue(prepared.isOk, "prepare license JWE should succeed: ${if (prepared.isErr) prepared.error else ""}")
        val created = jweService.createJweCompact(CreateJweCompactArgs(preparedJwe = prepared.value))
        assertTrue(created.isOk, "create compact license JWE should succeed: ${if (created.isErr) created.error else ""}")
        return created.value.serialize()
    }

    @Test
    fun bundleRecipientKeyDecryptsLicenseJweAcrossReusedDecryptSession() =
        runTest {
            val dir = Files.createTempDirectory("license-import-decrypt").toFile().also { it.deleteOnExit() }
            val keystorePath = dir.resolve("license.p12").absolutePath

            // The reused DECRYPT session: built first, loads the (empty) `license` keystore now so the
            // stale-in-memory precondition the bug needs is established before the import writes.
            val (decryptApp, decryptSession) = newSession("license-decrypt-session")
            val decryptKms = registerFileLicenseProvider(decryptApp, decryptSession, keystorePath)
            decryptKms.listKeysResult(PROVIDER_ID)

            // The IMPORT session: a separate session/process that durably stores the bundle recipient
            // private key into the SAME on-disk `license` keystore.
            val (importApp, importSession) = newSession("license-import-session")
            val importKms = registerFileLicenseProvider(importApp, importSession, keystorePath)
            val recipientJwk = recipientPrivateJwk()
            importKms.storeKey(
                keyInfo = ResolvedKeyInfo.fromKey(recipientJwk),
                providerId = PROVIDER_ID,
                alias = recipientAlias,
            )

            // The import session encrypts a license JWE to the recipient public key it just stored.
            val licenseJwe = encryptLicenseJweTo(importSession, importKms)

            // The reused decrypt session must now unwrap that JWE with the durably-imported recipient
            // private key. Before the fix it served its stale (empty) in-memory keystore and failed.
            val jweService = (decryptSession.graph as JweServiceImpl.Graph).jweService
            val decrypted =
                jweService.decryptJwe(
                    DecryptJweArgs(jwe = JweCompact.parse(licenseJwe), decryptor = decryptorOpts()),
                )
            // A successful unwrap proves the reused decrypt session resolved the durably-imported recipient
            // PRIVATE key across the shared on-disk keystore — the exact handoff the live bug broke.
            assertTrue(decrypted.isOk, "reused decrypt session must unwrap the license JWE with the imported recipient key: ${if (decrypted.isErr) decrypted.error else ""}")
            assertEquals(licensePlaintext, decrypted.value.plaintext?.decodeToString())
        }

    @Test
    fun portalGeneratedRecipientKidDecryptsThroughStableAliasWhenConfiguredKidDiffers() =
        runTest {
            val dir = Files.createTempDirectory("license-import-decrypt-portal-kid").toFile().also { it.deleteOnExit() }
            val keystorePath = dir.resolve("license.p12").absolutePath
            val portalRecipientKid = "portal-recipient-8322a460-87a3-40ab-8499-f13c14a832bf"
            val configuredRecipientKid = "qa-license-recipient"

            val (decryptApp, decryptSession) = newSession("license-decrypt-portal-kid-session")
            val decryptKms = registerFileLicenseProvider(decryptApp, decryptSession, keystorePath)
            decryptKms.listKeysResult(PROVIDER_ID)

            val (importApp, importSession) = newSession("license-import-portal-kid-session")
            val importKms = registerFileLicenseProvider(importApp, importSession, keystorePath)
            importKms.storeKey(
                keyInfo = ResolvedKeyInfo.fromKey(recipientPrivateJwk(kid = portalRecipientKid)),
                providerId = PROVIDER_ID,
                alias = recipientAlias,
            )

            val licenseJwe = encryptLicenseJweTo(importSession, importKms, kid = portalRecipientKid)

            val jweService = (decryptSession.graph as JweServiceImpl.Graph).jweService
            val decrypted =
                jweService.decryptJwe(
                    DecryptJweArgs(
                        jwe = JweCompact.parse(licenseJwe),
                        decryptor = decryptorOpts(kid = configuredRecipientKid),
                    ),
                )

            assertTrue(
                decrypted.isOk,
                "configured kid '$configuredRecipientKid' must not block decrypting a bundle JWE encrypted to '$portalRecipientKid' when the bundle key was imported under the stable alias: ${if (decrypted.isErr) decrypted.error else ""}",
            )
            assertEquals(licensePlaintext, decrypted.value.plaintext?.decodeToString())
        }

    @Test
    fun staleSelfGeneratedRecipientKeyDoesNotDecryptBundleEncryptedLicense() =
        runTest {
            val dir = Files.createTempDirectory("license-import-decrypt-stale").toFile().also { it.deleteOnExit() }
            val keystorePath = dir.resolve("license.p12").absolutePath

            val (app, session) = newSession("license-stale-session")
            val kms = registerFileLicenseProvider(app, session, keystorePath)

            // The platform's self-generated recipient key, stored under a distinct alias to stand in for
            // the stale key the decrypt session would otherwise resolve.
            val staleAlias = "license-recipient-self-generated"
            kms.storeKey(
                keyInfo = ResolvedKeyInfo.fromKey(recipientPrivateJwk().copy(kid = staleAlias)),
                providerId = PROVIDER_ID,
                alias = staleAlias,
            )

            // The bundle recipient key, stored under the real license alias; the license JWE is encrypted
            // to THIS key.
            kms.storeKey(
                keyInfo = ResolvedKeyInfo.fromKey(recipientPrivateJwk()),
                providerId = PROVIDER_ID,
                alias = recipientAlias,
            )
            val licenseJwe = encryptLicenseJweTo(session, kms)

            // Decrypting with the STALE self-generated key must NOT recover the license: it is the wrong
            // recipient key, so the ECDH-derived KEK cannot unwrap the CEK.
            val jweService = (session.graph as JweServiceImpl.Graph).jweService
            val staleDecryptor =
                ManagedOptsKeyInfo(
                    identifier = ManagedKeyReference(providerId = PROVIDER_ID, alias = staleAlias, kid = staleAlias),
                    context = IdentifierContext(clientId = "license", clientIdScheme = "jwe", issuer = "urn:sphereon:license"),
                )
            val failed =
                try {
                    jweService
                        .decryptJwe(
                            DecryptJweArgs(jwe = JweCompact.parse(licenseJwe), decryptor = staleDecryptor),
                        ).isErr
                } catch (expected: Exception) {
                    true
                }
            assertTrue(failed, "the stale self-generated recipient key must not decrypt a license JWE encrypted to the bundle key")

            // ... and the correct bundle key DOES decrypt it, proving the JWE itself is sound.
            val ok =
                jweService.decryptJwe(
                    DecryptJweArgs(jwe = JweCompact.parse(licenseJwe), decryptor = decryptorOpts()),
                )
            assertTrue(ok.isOk, "the bundle recipient key must decrypt the license JWE: ${if (ok.isErr) ok.error else ""}")
            assertEquals(licensePlaintext, ok.value.plaintext?.decodeToString())
        }

    private companion object {
        const val PROVIDER_ID: String = "license"
    }
}
