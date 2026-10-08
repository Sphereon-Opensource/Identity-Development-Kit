package com.sphereon.openid.oid4vci.issuer.impl.command

import com.sphereon.openid.oid4vci.issuer.lifecycle.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class IssuerBusinessAuthorizationTest {
    @Test fun missingCorrelationCannotSkipRequiredAuthorization() = runTest {
        val result = authorizeIssuerBusinessAction(true,
            Oid4vciBusinessAuthorizationArgs("issuer-a", "session-a", null, "credential-a"), null)
        assertTrue(result.isErr)
    }
    @Test fun legacyLifecycleHookIsNotABusinessPermit() = runTest {
        val result = authorizeIssuerBusinessAction(true,
            Oid4vciBusinessAuthorizationArgs("issuer-a", "session-a", "process-a", "credential-a"),
            object : Oid4vciIssuanceLifecycleHook {})
        assertTrue(result.isErr)
    }
    @Test fun serverPolicyMayPermitOrdinaryIssuanceWithoutBusinessHook() = runTest {
        assertTrue(authorizeIssuerBusinessAction(false,
            Oid4vciBusinessAuthorizationArgs("issuer-a", "session-a", null, "credential-a"), null).isOk)
    }
}
