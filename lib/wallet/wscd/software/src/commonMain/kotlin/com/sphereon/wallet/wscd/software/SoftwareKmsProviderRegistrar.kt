/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wscd.software

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.KeyStoreConfig
import com.sphereon.crypto.kms.keystore.software.AppleKeyStoreConfig
import com.sphereon.crypto.kms.keystore.software.EncryptedFileKeyStoreConfig
import com.sphereon.crypto.kms.keystore.software.Pkcs12KeyStoreConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderConfig
import com.sphereon.crypto.kms.provider.software.SoftwareKmsProviderFactory
import com.sphereon.di.session.SessionScope
import com.sphereon.di.app.App
import com.sphereon.di.app.PlatformInfo
import com.sphereon.wallet.wscd.SoftwareWscdKeyStoreConfiguration
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.Named
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Injection seam for the lazy KMS-provider registration performed by
 * [SoftwareKmsProviderRegistrar]. Extracted as a fun interface so consumers take a
 * NON-NULL dependency (Metro binds the session-scoped registrar) while pure-unit tests
 * hand a no-op lambda: `KmsProviderBootstrap {}`.
 */
fun interface KmsProviderBootstrap {
    suspend fun ensureRegistered()
}

/**
 * Lazily ensures the per-session software KMS provider is registered, exactly once, no matter
 * which session-scoped consumer touches key material first.
 *
 * No product or runner bootstrap (`DefaultWalletApp`, `WalletAppBootstrap`,
 * `HeadlessWalletRunnerBootstrap`, `WalletBootstrap`) eagerly registers the software KMS provider
 * up front. Instead this class is `@SingleIn(SessionScope)`, so Metro constructs exactly ONE
 * instance per session and hands the SAME instance (and therefore the SAME registration [Mutex])
 * to every consumer that needs it:
 * [SoftwareWscd] (generateKey/generateFreshKey) AND
 * `SoftwareWscdWalletCredentialBodyProtector` (protect/open), which reaches the software KMS
 * directly rather than through [SoftwareWscd]. Without a SHARED instance, two independent
 * per-consumer registrars could race past their own empty-provider-list check and double-register
 * (or otherwise racily disagree about) the session's default KMS provider.
 *
 * [SoftwareWscdFactory.create] also forwards this SAME session-scoped registrar into every
 * [SoftwareWscd] it constructs, so a factory-created instance (fresh key cache, per the
 * factory's contract) and the direct SessionScope-injected [SoftwareWscd] singleton never
 * double-register either.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<KmsProviderBootstrap>())
class SoftwareKmsProviderRegistrar(
    private val keyManagerService: KeyManagerService,
    private val softwareKmsProviderFactory: SoftwareKmsProviderFactory,
    private val execution: SessionExecution,
    private val app: App,
    private val keyStoreConfiguration: SoftwareWscdKeyStoreConfiguration,
    @Named("sessionId") private val sessionId: String,
) : KmsProviderBootstrap {
    private val mutex = Mutex()

    /**
     * Registers the session's default software KMS provider if one is not already registered.
     * Idempotent and safe to call from every key operation entry point: the fast path (no lock)
     * covers the overwhelmingly common case where registration already happened.
     */
    override suspend fun ensureRegistered() {
        if (keyManagerService.getProviderIds().isNotEmpty()) return
        mutex.withLock {
            if (keyManagerService.getProviderIds().isNotEmpty()) return@withLock
            val providerId = "$sessionId-software-kms"
            val config = SoftwareKmsProviderConfig(id = providerId, keyStore = keyStore(providerId))
            val provider = softwareKmsProviderFactory.create(config, execution)
            keyManagerService.registerProvider(provider, makeDefaultKms = true)
        }
    }

    private fun keyStore(providerId: String): KeyStoreConfig =
        when (val configured = keyStoreConfiguration) {
            SoftwareWscdKeyStoreConfiguration.InMemoryForTestingOnly -> SoftwareKmsProviderConfig(id = providerId).keyStore
            SoftwareWscdKeyStoreConfiguration.AppleKeychain -> {
                check(app.platformInfo.osFamily == PlatformInfo.OsFamily.IOS) {
                    "software_wscd_apple_keychain_requires_ios"
                }
                AppleKeyStoreConfig(id = providerId)
            }
            is SoftwareWscdKeyStoreConfiguration.PersistentEncryptedFile ->
                when (app.platformInfo.osFamily) {
                    PlatformInfo.OsFamily.JS, PlatformInfo.OsFamily.WASM_JS ->
                        EncryptedFileKeyStoreConfig(
                            id = providerId,
                            path = configured.path,
                            password = configured.password,
                        )
                    PlatformInfo.OsFamily.JVM, PlatformInfo.OsFamily.ANDROID ->
                        Pkcs12KeyStoreConfig(
                            id = providerId,
                            path = configured.path,
                            password = configured.password,
                        )
                    else -> error("software_wscd_encrypted_file_not_supported_on_${app.platformInfo.osFamily.name.lowercase()}")
                }
        }
}
