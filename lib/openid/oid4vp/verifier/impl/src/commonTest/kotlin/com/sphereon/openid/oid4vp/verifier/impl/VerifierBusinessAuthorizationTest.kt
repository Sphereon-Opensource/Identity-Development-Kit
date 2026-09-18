package com.sphereon.openid.oid4vp.verifier.impl

import com.sphereon.openid.oid4vp.verifier.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class VerifierBusinessAuthorizationTest {
    private val validProof = ValidationResult(valid = true, matchedCredentials = emptyList(), errors = emptyList())
    @Test fun validProofDoesNotBypassMissingBusinessAuthority() = runTest {
        assertTrue(authorizeVerifierBusinessAction(true, null, validProof, emptySet()).isErr)
    }
    @Test fun explicitOrdinaryPolicyPreservesProtocolOnlyVerification() = runTest {
        assertTrue(authorizeVerifierBusinessAction(false, null, validProof, emptySet()).isOk)
    }
}
