/*
 * Copyright 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.wallet.wscd

/**
 * Platform-supplied persistence policy for the software WSCD. It deliberately exposes no KMS
 * API: the software-WSCD implementation alone translates this contract to its keystore backend.
 */
sealed interface SoftwareWscdKeyStoreConfiguration {
    /** Explicitly test-only. Product composition roots must never select this mode. */
    data object InMemoryForTestingOnly : SoftwareWscdKeyStoreConfiguration

    /** Non-exportable signing keys held by the Apple Keychain/Secure Enclave implementation. */
    data object AppleKeychain : SoftwareWscdKeyStoreConfiguration

    data class PersistentEncryptedFile(
        val path: String,
        val password: String,
    ) : SoftwareWscdKeyStoreConfiguration {
        init {
            require(path.isNotBlank()) { "software_wscd_keystore_path_blank" }
            require(password.isNotBlank()) { "software_wscd_keystore_password_blank" }
        }
    }
}
