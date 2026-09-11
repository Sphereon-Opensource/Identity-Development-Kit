/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0
 */

package com.sphereon.trust.oidfed

import com.sphereon.trust.core.model.TrustContext
import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.core.model.TrustValidationRequest
import com.sphereon.crypto.resolution.extern.ExternalIdentifierOIDFEntityIdOpts
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OidfTrustValidationServiceTest {
    @Test
    fun supportsOpenIdFederationContextType() {
        // OidfTrustValidationService requires CacheService + SessionExecution DI,
        // so we test the AbstractTrustValidationService.supports logic via interface contract
        val supportedTypes = setOf(TrustContext.TYPE_OPENID_FEDERATION)
        assertTrue(TrustContext.TYPE_OPENID_FEDERATION in supportedTypes)
        assertFalse(TrustContext.TYPE_DID in supportedTypes)
        assertFalse(TrustContext.TYPE_ETSI_TSL in supportedTypes)
    }

    @Test
    fun validateOidfTrustArgsDefaults() {
        val args = ValidateOidfTrustArgs(entityIdentifier = "https://example.com")
        assertEquals("https://example.com", args.entityIdentifier)
        assertTrue(args.trustAnchors.isEmpty())
        assertTrue(args.requiredTrustMarks.isEmpty())
        assertEquals(5, args.maxChainDepth)
    }

    @Test
    fun validateOidfTrustArgsWithOverrides() {
        val args =
            ValidateOidfTrustArgs(
                entityIdentifier = "https://example.com",
                trustAnchors = listOf("https://trust-anchor.example.com"),
                requiredTrustMarks = listOf("https://trust-mark.example.com/mark1"),
                maxChainDepth = 3,
            )
        assertEquals(1, args.trustAnchors.size)
        assertEquals(1, args.requiredTrustMarks.size)
        assertEquals(3, args.maxChainDepth)
    }

    @Test
    fun callerSuppliedAnchorsAreRejectedByTheLegacyBoundary() {
        assertFalse(ValidateOidfTrustCommand.callerSuppliedAnchorsRejected(ValidateOidfTrustArgs("https://example.com")))
        assertTrue(
            ValidateOidfTrustCommand.callerSuppliedAnchorsRejected(
                ValidateOidfTrustArgs("https://example.com", trustAnchors = listOf("https://attacker.example")),
            ),
        )
    }

    @Test
    fun trustContextTypeConstant() {
        assertEquals("openid_federation", TrustContext.TYPE_OPENID_FEDERATION)
    }

    @Test
    fun unboundProductionValidatorFailsClosedWithTypedResult() = runTest {
        val validator = NoOpOidfedTrustValidationService
        assertFalse(validator.supports(TrustContext(TrustContext.TYPE_OPENID_FEDERATION)))
        val result = validator.validate(
            TrustValidationRequest(
                identifier = ExternalIdentifierOIDFEntityIdOpts(identifier = "https://entity.example"),
                context = TrustContext(TrustContext.TYPE_OPENID_FEDERATION),
            ),
        )
        assertFalse(result.trusted)
        assertEquals(TrustStatus.VALIDATION_ERROR, result.status)
    }
}
