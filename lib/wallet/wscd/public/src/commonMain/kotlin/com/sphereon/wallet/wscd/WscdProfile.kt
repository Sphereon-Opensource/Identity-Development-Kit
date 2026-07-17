/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.wallet.wscd

import com.sphereon.core.api.service.EidasAssuranceLevel
import com.sphereon.wallet.unit.WalletKeystoreSecurityLevel
import com.sphereon.wallet.unit.WalletSecureComponentType
import kotlinx.serialization.Serializable

/**
 * WSCD deployment profile per ARF 2.9 section 4.5 (four architecture types) plus a
 * Software profile for development and OSS web fallback custody. The WSCD is the
 * tamper-resistant environment protecting critical assets (CIR (EU) 2024/2981 Art. 2(5));
 * Software intentionally never claims that protection level.
 *
 * Capability metadata feeds Key Attestation claims (key_storage / user_authentication,
 * EUDI TS03 v1.5.2): values MUST be honest for the actual custody. The four properties below
 * are the per-variant ceiling this profile can honestly back; [com.sphereon.wallet.wsca.impl.LocalWsca]
 * derives absent-caller-value KA claim defaults from them and rejects any caller-supplied claim
 * that exceeds them.
 *
 * Value sources:
 * - [keyStorageSecurityLevel] / [secureComponent]: reuse the SAME typed enums the TS03
 *   `key_storage` claim (`Ts03KeyStorageClaim`) is already built from elsewhere in this class
 *   hierarchy ([WalletKeystoreSecurityLevel], [WalletSecureComponentType] - both already on this
 *   module's compile path via `lib-wallet-unit-public`) rather than duplicating that vocabulary as
 *   free-form strings. `security_level`/`secure_component` wire values are `name.lowercase()` of
 *   these enums (e.g. `ISO_18045_HIGH` -> `"iso_18045_high"`), matching
 *   `KeyAttestationEvidenceEnforcer`'s canonical value sets
 *   (`lib-openid-oid4vci-issuer-impl`'s production policy: only `iso_18045_high` +
 *   `remote_wsca`/`remote_wscd` pass).
 * - [nonExportable]: TS03 `key_storage.non_exportable` - whether this profile's custody
 *   genuinely prevents private-key export.
 * - [userAuthAssuranceLevel]: TS03 `user_authentication.assurance_level`, using
 *   [EidasAssuranceLevel] (eIDAS LoA / NIST AAL, already on this module's compile path via
 *   `lib-core-api-public`); wire value is [EidasAssuranceLevel.serializedValue].
 *
 * [WalletKeystoreSecurityLevel] and [EidasAssuranceLevel] both declare their entries in a
 * meaningful ceiling order ([WalletKeystoreSecurityLevel.ordinal] ascends from strongest
 * `ISO_18045_HIGH` to weakest `NONE`; [EidasAssuranceLevel.ordinal] ascends from weakest `LOW` to
 * strongest `HIGH`) - `LocalWsca` uses those orderings directly for its ceiling check.
 * [secureComponent] has no such natural ordering (it is a categorical classification, not a
 * strength scale), so `LocalWsca` enforces it as an exact-match allow-list instead.
 */
@Serializable
sealed interface WscdProfile {
    /** TS03 `key_storage.security_level` ceiling this profile can honestly claim. */
    val keyStorageSecurityLevel: WalletKeystoreSecurityLevel

    /** TS03 `key_storage.secure_component` ceiling: this profile's real custody classification. */
    val secureComponent: WalletSecureComponentType

    /** TS03 `key_storage.non_exportable` ceiling: whether this profile's custody actually prevents key export. */
    val nonExportable: Boolean

    /** TS03 `user_authentication.assurance_level` ceiling this profile can honestly claim. */
    val userAuthAssuranceLevel: EidasAssuranceLevel

    /**
     * Provider-side HSM reached over a network. Inside the certification perimeter: the only
     * profile whose capabilities meet `KeyAttestationEvidenceEnforcer`'s production policy.
     */
    @Serializable
    data object Remote : WscdProfile {
        override val keyStorageSecurityLevel = WalletKeystoreSecurityLevel.ISO_18045_HIGH
        override val secureComponent = WalletSecureComponentType.REMOTE_WSCD
        override val nonExportable = true
        override val userAuthAssuranceLevel = EidasAssuranceLevel.HIGH
    }

    /**
     * External device presented by the user, e.g. a smartcard. Not implemented yet: no [Wscd]
     * backs this profile, so these are conservative placeholders (never a stronger claim than a
     * real implementation could later honestly back), documented as future work.
     */
    @Serializable
    data object LocalExternal : WscdProfile {
        override val keyStorageSecurityLevel = WalletKeystoreSecurityLevel.ISO_18045_BASIC
        override val secureComponent = WalletSecureComponentType.EXTERNAL_CERTIFIED_PROVIDER
        override val nonExportable = false
        override val userAuthAssuranceLevel = EidasAssuranceLevel.LOW
    }

    /**
     * Component inside the user device: eSIM/eUICC/embedded Secure Element. Not implemented yet:
     * no [Wscd] backs this profile, so these are conservative placeholders, documented as future
     * work. [WalletSecureComponentType] has no dedicated "embedded SE" entry; [secureComponent]
     * reuses [WalletSecureComponentType.LOCAL_WSCD] as the closest available local-device
     * classification pending a real implementation.
     */
    @Serializable
    data object LocalInternal : WscdProfile {
        override val keyStorageSecurityLevel = WalletKeystoreSecurityLevel.ISO_18045_BASIC
        override val secureComponent = WalletSecureComponentType.LOCAL_WSCD
        override val nonExportable = false
        override val userAuthAssuranceLevel = EidasAssuranceLevel.LOW
    }

    /**
     * Device keystore via OS API: Android StrongBox / iOS Secure Enclave (mobile KMS).
     * [keyStorageSecurityLevel] deliberately stays below [WalletKeystoreSecurityLevel.ISO_18045_HIGH]:
     * G10 (see [com.sphereon.wallet.wscd.mobile.LocalNativeWscd] KDoc "Key attestation") is still
     * open - `MobileKmsProvider` does not yet plumb a caller-supplied attestation challenge through
     * `generateKeyAsync`, so this profile cannot back an ISO_18045_HIGH claim with real platform
     * key-attestation evidence, even though the underlying hardware (StrongBox/Secure Enclave)
     * could support it once that evidence is wired up. [nonExportable] stays `true`: the platform
     * keystore genuinely never exposes private key material to this layer regardless of the
     * attestation-evidence gap.
     */
    @Serializable
    data object LocalNative : WscdProfile {
        override val keyStorageSecurityLevel = WalletKeystoreSecurityLevel.ISO_18045_MODERATE
        override val secureComponent = WalletSecureComponentType.MOBILE_PLATFORM_WSCD
        override val nonExportable = true
        override val userAuthAssuranceLevel = EidasAssuranceLevel.SUBSTANTIAL
    }

    /**
     * Software keys (software KMS, browser WebCrypto). Development and OSS web fallback: the
     * lowest honest ceiling of every profile - [keyStorageSecurityLevel] is
     * [WalletKeystoreSecurityLevel.NONE] (never an ISO 18045 protection-profile claim),
     * [nonExportable] is `false` (an unprotected software KMS cannot honestly claim
     * non-exportability), and [userAuthAssuranceLevel] is [EidasAssuranceLevel.LOW] (no external
     * Signature Activation Module; user authentication is supplied by the local WSCA platform
     * adapter and fails closed when no protected PIN/biometric adapter is installed).
     */
    @Serializable
    data object Software : WscdProfile {
        override val keyStorageSecurityLevel = WalletKeystoreSecurityLevel.NONE
        override val secureComponent = WalletSecureComponentType.LOCAL_WSCD
        override val nonExportable = false
        override val userAuthAssuranceLevel = EidasAssuranceLevel.LOW
    }
}
