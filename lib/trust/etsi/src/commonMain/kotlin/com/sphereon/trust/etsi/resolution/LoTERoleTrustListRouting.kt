/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.trust.etsi.resolution

import com.sphereon.trust.etsi.lote.model.EidasRole
import com.sphereon.trust.etsi.lote.model.EtsiTrustListProfile
import com.sphereon.trust.etsi.lote.model.LoTEType
import com.sphereon.trust.etsi.model.ETSILoTE
import com.sphereon.trust.etsi.model.ETSIOtherLoTEPointer

/**
 * The single routing policy shared by role verification and its callers.
 *
 * TS 119 612 member-state lists are selected by territory and never by a
 * TS 119 602 LoTEType qualifier. TS 119 602 roles require an exact LoTEType
 * qualifier and an optional exact territory match. There is deliberately no
 * cross-profile or cross-territory fallback.
 */
internal object LoTERoleTrustListRouting {
    private const val TS_119_602_LOTE_TYPE_PREFIX = "http://uri.etsi.org/19602/LoTEType/"
    private val recognizedLoTETypeQualifiers =
        setOf(
            LoTEType.EU_PID_PROVIDERS,
            LoTEType.EU_WALLET_PROVIDERS,
            LoTEType.EU_WRPAC_PROVIDERS,
            LoTEType.EU_WRPRC_PROVIDERS,
            LoTEType.EU_PUB_EAA_PROVIDERS,
        )

    fun findPointersForRole(
        lotl: ETSILoTE,
        role: EidasRole,
        territory: String?,
    ): List<ETSIOtherLoTEPointer> =
        when (role.trustListProfile) {
            EtsiTrustListProfile.TS_119_612_MEMBER_STATE ->
                lotl.pointersToOtherLoTE
                    .asSequence()
                    .filter { pointer -> inspectOtherInformation(pointer) is PointerProfile.MemberState612 }
                    .filter { territory == null || it.schemeTerritory.equals(territory, ignoreCase = true) }
                    .toList()

            EtsiTrustListProfile.TS_119_602_LOTE -> {
                val loTEType = role.loTEType ?: return emptyList()
                lotl.pointersToOtherLoTE
                    .asSequence()
                    .filter { pointer ->
                        inspectOtherInformation(pointer) == PointerProfile.LoTE602(loTEType)
                    }
                    .filter { territory == null || it.schemeTerritory.equals(territory, ignoreCase = true) }
                    .toList()
            }
        }

    private fun inspectOtherInformation(pointer: ETSIOtherLoTEPointer): PointerProfile {
        val values = pointer.additionalInformation?.otherInformation ?: return PointerProfile.MemberState612
        if (values.any { it.isBlank() }) return PointerProfile.Malformed

        val recognized = values.filter { it in recognizedLoTETypeQualifiers }
        val malformed602 =
            values.any {
                it.startsWith(TS_119_602_LOTE_TYPE_PREFIX) && it !in recognizedLoTETypeQualifiers
            }

        if (malformed602 || recognized.size > 1) return PointerProfile.Malformed
        return recognized.singleOrNull()?.let(PointerProfile::LoTE602) ?: PointerProfile.MemberState612
    }

    private sealed interface PointerProfile {
        data object MemberState612 : PointerProfile

        data class LoTE602(val loTEType: String) : PointerProfile

        data object Malformed : PointerProfile
    }

    /** Only an issuance service can establish issuer trust. */
    fun buildServiceTypeFilter(role: EidasRole): List<String> = listOf(role.issuanceServiceType)
}
