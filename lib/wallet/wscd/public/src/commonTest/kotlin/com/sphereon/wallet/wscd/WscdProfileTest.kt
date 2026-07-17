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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Per-variant capability metadata table (spec 3.4): every [WscdProfile] must
 * carry HONEST TS03 key-attestation capability values, and [LocalWsca][com.sphereon.wallet.wsca.impl.LocalWsca]
 * relies on these exact values to derive absent-caller-value KA claim defaults and to enforce the
 * ceiling on caller-supplied ones. [Remote] is the only profile whose capabilities meet
 * `KeyAttestationEvidenceEnforcer`'s production policy (`iso_18045_high` + `remote_wsca`/`remote_wscd`);
 * every other profile deliberately reports a lower, honest ceiling.
 */
class WscdProfileTest {
    @Test
    fun remoteReportsProductionGradeCapabilities() {
        assertEquals(WalletKeystoreSecurityLevel.ISO_18045_HIGH, WscdProfile.Remote.keyStorageSecurityLevel)
        assertEquals(WalletSecureComponentType.REMOTE_WSCD, WscdProfile.Remote.secureComponent)
        assertTrue(WscdProfile.Remote.nonExportable)
        assertEquals(EidasAssuranceLevel.HIGH, WscdProfile.Remote.userAuthAssuranceLevel)
    }

    @Test
    fun localNativeNeverClaimsIso18045HighWhileTheAttestationChallengeGapIsOpen() {
        // G10: signum-supreme does not yet plumb a caller-supplied attestation challenge through
        // MobileKmsProvider.generateKeyAsync, so LocalNative cannot back an ISO_18045_HIGH claim
        // with real platform key-attestation evidence (see LocalNativeWscd.keyEvidence KDoc).
        assertEquals(WalletKeystoreSecurityLevel.ISO_18045_MODERATE, WscdProfile.LocalNative.keyStorageSecurityLevel)
        assertTrue(WscdProfile.LocalNative.keyStorageSecurityLevel != WalletKeystoreSecurityLevel.ISO_18045_HIGH)
        assertEquals(WalletSecureComponentType.MOBILE_PLATFORM_WSCD, WscdProfile.LocalNative.secureComponent)
        // The platform keystore genuinely never exposes private key material, independent of the
        // attestation-evidence gap above.
        assertTrue(WscdProfile.LocalNative.nonExportable)
        assertEquals(EidasAssuranceLevel.SUBSTANTIAL, WscdProfile.LocalNative.userAuthAssuranceLevel)
    }

    @Test
    fun softwareNeverClaimsProductionGradeCustody() {
        assertEquals(WalletKeystoreSecurityLevel.NONE, WscdProfile.Software.keyStorageSecurityLevel)
        assertEquals(WalletSecureComponentType.LOCAL_WSCD, WscdProfile.Software.secureComponent)
        assertFalse(WscdProfile.Software.nonExportable)
        assertEquals(EidasAssuranceLevel.LOW, WscdProfile.Software.userAuthAssuranceLevel)
    }

    @Test
    fun localExternalAndLocalInternalReportConservativeNotYetImplementedPlaceholders() {
        for (profile in listOf(WscdProfile.LocalExternal, WscdProfile.LocalInternal)) {
            assertEquals(WalletKeystoreSecurityLevel.ISO_18045_BASIC, profile.keyStorageSecurityLevel, "$profile security level")
            assertFalse(profile.nonExportable, "unimplemented profile '$profile' must not claim non-exportable custody")
            assertEquals(EidasAssuranceLevel.LOW, profile.userAuthAssuranceLevel, "$profile user auth assurance level")
        }
        assertEquals(WalletSecureComponentType.EXTERNAL_CERTIFIED_PROVIDER, WscdProfile.LocalExternal.secureComponent)
        assertEquals(WalletSecureComponentType.LOCAL_WSCD, WscdProfile.LocalInternal.secureComponent)
    }

    @Test
    fun everyNonRemoteProfileStaysBelowRemoteOnTheHonestSecurityLevelCeiling() {
        // WalletKeystoreSecurityLevel declares its entries strongest-first (ISO_18045_HIGH is
        // ordinal 0), so a lower ordinal is a STRONGER claim. Remote is the only profile allowed to
        // reach ordinal 0 - LocalWsca's ceiling check relies on this exact ordering.
        val nonRemote = listOf(WscdProfile.LocalExternal, WscdProfile.LocalInternal, WscdProfile.LocalNative, WscdProfile.Software)
        nonRemote.forEach { profile ->
            assertTrue(
                profile.keyStorageSecurityLevel.ordinal > WscdProfile.Remote.keyStorageSecurityLevel.ordinal,
                "$profile must not reach Remote's ISO_18045_HIGH ceiling",
            )
        }
    }

    @Test
    fun everyNonRemoteProfileStaysBelowRemoteOnTheHonestUserAuthCeiling() {
        // EidasAssuranceLevel declares its entries weakest-first (LOW is ordinal 0), so a higher
        // ordinal is a STRONGER claim. Remote is the only profile allowed to reach HIGH.
        val nonRemote = listOf(WscdProfile.LocalExternal, WscdProfile.LocalInternal, WscdProfile.LocalNative, WscdProfile.Software)
        nonRemote.forEach { profile ->
            assertTrue(
                profile.userAuthAssuranceLevel.ordinal < WscdProfile.Remote.userAuthAssuranceLevel.ordinal,
                "$profile must not reach Remote's HIGH user-authentication assurance level",
            )
        }
    }
}
