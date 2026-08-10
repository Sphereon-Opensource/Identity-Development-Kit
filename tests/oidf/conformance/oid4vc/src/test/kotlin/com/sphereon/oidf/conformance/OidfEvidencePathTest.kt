/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class OidfEvidencePathTest {
    @Test
    fun `evidence directory name is bounded deterministic and variant sensitive`() {
        val first =
            OidfPlanModule(
                testModule = "fapi2-security-profile-final-par-ensure-pkce-code-verifier-required",
                variant =
                    mapOf(
                        "client_auth_type" to "client_attestation",
                        "fapi_profile" to "vci_haip",
                        "vci_grant_type" to "authorization_code",
                        "intentionally_long_dimension" to "x".repeat(400),
                    ),
            )
        val reordered = first.copy(variant = first.variant.entries.reversed().associate { it.toPair() })
        val different = first.copy(variant = first.variant + ("vci_grant_type" to "pre_authorization_code"))

        assertEquals(first.evidenceDirectoryName(), reordered.evidenceDirectoryName())
        assertNotEquals(first.evidenceDirectoryName(), different.evidenceDirectoryName())
        assertTrue(first.evidenceDirectoryName().length <= 90)
        assertTrue(first.evidenceDirectoryName().matches(Regex("[a-z0-9._-]+")))
    }
}
