/*
 * Â© 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.crypto.kms.provider.azure

import com.sphereon.crypto.core.KeyInfo
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkType
import kotlin.test.Test
import kotlin.test.assertEquals

class AzureKeyVaultStructuredSigningTest {
    @Test
    fun explicitAlgorithmReplacesKeyInfoAlgorithmForDocumentSigning() {
        val keyInfo =
            KeyInfo<JwkType>(
                alias = "signing-key",
                signatureAlgorithm = SignatureAlgorithm.RSA_SHA256,
            )

        val requested = withExplicitSignatureAlgorithm(keyInfo, SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1)

        assertEquals(SignatureAlgorithm.RSA_SSA_PSS_SHA512_MGF1, requested.signatureAlgorithm)
    }
}
