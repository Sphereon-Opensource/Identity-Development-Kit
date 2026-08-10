/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.sphereon.oidf.conformance

enum class OidfProtocol {
    OID4VCI,
    OID4VP,
}

enum class OidfRole {
    ISSUER,
    VERIFIER,
    WALLET,
}

enum class OidfProfile {
    FINAL_1_0,
    HAIP_1_0,
}

data class OidfRequiredPlan(
    val planName: String,
    val protocol: OidfProtocol,
    val role: OidfRole,
    val profile: OidfProfile,
)

/**
 * Non-negotiable plan-level completion matrix for the VDX OID4VC program.
 *
 * A plan is complete only when every module discovered from the pinned live
 * suite passes for every variant selected by the role adapter. This list is
 * intentionally independent from the product E2E lane: exactly one VDX role
 * is the system under test while the OIDF suite supplies its counterparty.
 */
object OidfConformanceMatrix {
    val requiredPlans: List<OidfRequiredPlan> =
        listOf(
            OidfRequiredPlan(
                planName = "oid4vci-1_0-issuer-test-plan",
                protocol = OidfProtocol.OID4VCI,
                role = OidfRole.ISSUER,
                profile = OidfProfile.FINAL_1_0,
            ),
            OidfRequiredPlan(
                planName = "oid4vci-1_0-issuer-haip-test-plan",
                protocol = OidfProtocol.OID4VCI,
                role = OidfRole.ISSUER,
                profile = OidfProfile.HAIP_1_0,
            ),
            OidfRequiredPlan(
                planName = "oid4vp-1final-verifier-test-plan",
                protocol = OidfProtocol.OID4VP,
                role = OidfRole.VERIFIER,
                profile = OidfProfile.FINAL_1_0,
            ),
            OidfRequiredPlan(
                planName = "oid4vp-1final-verifier-haip-test-plan",
                protocol = OidfProtocol.OID4VP,
                role = OidfRole.VERIFIER,
                profile = OidfProfile.HAIP_1_0,
            ),
            OidfRequiredPlan(
                planName = "oid4vci-1_0-wallet-test-plan",
                protocol = OidfProtocol.OID4VCI,
                role = OidfRole.WALLET,
                profile = OidfProfile.FINAL_1_0,
            ),
            OidfRequiredPlan(
                planName = "oid4vci-1_0-wallet-haip-test-plan",
                protocol = OidfProtocol.OID4VCI,
                role = OidfRole.WALLET,
                profile = OidfProfile.HAIP_1_0,
            ),
            OidfRequiredPlan(
                planName = "oid4vp-1final-wallet-test-plan",
                protocol = OidfProtocol.OID4VP,
                role = OidfRole.WALLET,
                profile = OidfProfile.FINAL_1_0,
            ),
            OidfRequiredPlan(
                planName = "oid4vp-1final-wallet-haip-test-plan",
                protocol = OidfProtocol.OID4VP,
                role = OidfRole.WALLET,
                profile = OidfProfile.HAIP_1_0,
            ),
        )

    fun requireAvailablePlans(availablePlanNames: Set<String>) {
        val missing = requiredPlans.map { it.planName }.filterNot(availablePlanNames::contains)
        require(missing.isEmpty()) {
            "Pinned OIDF suite is missing required OID4VC plans: ${missing.sorted().joinToString()}"
        }
    }
}
