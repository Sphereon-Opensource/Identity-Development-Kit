/*
 * Copyright 2026 Sphereon International B.V.
 * Licensed under the Apache License, Version 2.0.
 */

package com.sphereon.crypto.kms.provider.azure

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.jose.JwkType
import kotlin.test.Test
import kotlin.test.assertEquals

class AzureKeyVaultRoutingTest {
    @Test
    fun aliasWinsWhenAliasAndKidConflict() {
        val keyInfo = KeyInfo<JwkType>(alias = "operator-alias", kid = "different-versioned-kid")

        assertEquals("operator-alias", azureKeyReference(keyInfo))
    }

    @Test
    fun kidIsPreservedForKidOnlyVersionedResolution() {
        val keyInfo = KeyInfo<JwkType>(kid = "https://vault.example/keys/signing/version-7")

        assertEquals(keyInfo.kid, azureKeyReference(keyInfo))
    }
}
