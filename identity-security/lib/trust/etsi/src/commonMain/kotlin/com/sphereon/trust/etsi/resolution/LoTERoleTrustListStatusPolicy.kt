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

import com.sphereon.trust.core.model.TrustStatus
import com.sphereon.trust.etsi.model.ETSIServiceStatus
import com.sphereon.trust.etsi.model.ETSIServiceInformation
import com.sphereon.trust.etsi.model.ETSILoTE
import com.sphereon.trust.etsi.model.GenericTrustAnchorExportPolicy
import com.sphereon.trust.etsi.model.LoTE612Converter.toETSILoTE
import com.sphereon.trust.etsi.lote.model.LoTEProfileValidation
import kotlin.time.Instant
import kotlin.time.Clock
import com.sphereon.trust.etsi.lote.model.EidasRole
import com.sphereon.trust.etsi.lote.model.EtsiTrustListProfile
import com.sphereon.trust.etsi.lote.model.LoTEServiceStatus

/** Fail-closed interpretation of ETSI service status values. */
object LoTERoleTrustListStatusPolicy {
    /** Exact role/profile context is mandatory before absence can mean current membership. */
    fun evaluate(service: ETSIServiceInformation, role: EidasRole, list: ETSILoTE, at: Instant = Clock.System.now()): Pair<Boolean, TrustStatus> {
        if (role.trustListProfile == EtsiTrustListProfile.TS_119_612_MEMBER_STATE) {
            if (!GenericTrustAnchorExportPolicy.permits(list) || service.sourceLoTEInformation != null ||
                service.serviceTypeIdentifier != role.issuanceServiceType ||
                service.serviceStatus !in setOf(ETSIServiceStatus.GRANTED, ETSIServiceStatus.RECOGNISED_NATIONAL_LEVEL)
            ) return false to TrustStatus.UNKNOWN
            return evaluate(service.serviceStatus)
        }
        if (list.type != role.loTEType || service.serviceTypeIdentifier != role.issuanceServiceType) return false to TrustStatus.UNKNOWN
        val sourceList = list.sourceLoTE ?: return false to TrustStatus.UNKNOWN
        try {
            LoTEProfileValidation.validate(sourceList, requireNotNull(role.loTEType), at)
            if (sourceList.toETSILoTE() != list) return false to TrustStatus.UNKNOWN
            if (list.trustedEntities.none { entity -> entity.trustedEntityServices.any { it.serviceInformation == service } }) return false to TrustStatus.UNKNOWN
        } catch (_: IllegalArgumentException) {
            return false to TrustStatus.UNKNOWN
        }
        val source = service.sourceLoTEInformation ?: return false to TrustStatus.UNKNOWN
        if (service.serviceStatus != (source.serviceStatus ?: "") ||
            service.statusStartingTime != (source.statusStartingTime ?: kotlin.time.Instant.fromEpochMilliseconds(0)) ||
            service.serviceTypeIdentifier != (source.serviceTypeIdentifier ?: "") ||
            service.serviceDigitalIdentity.x509Certificates != source.serviceDigitalIdentity.x509Certificates.map { it.value }
        ) return false to TrustStatus.UNKNOWN
        if (role == EidasRole.PUB_EAA_PROVIDER) {
            if (source.statusStartingTime == null) return false to TrustStatus.UNKNOWN
            return when (source.serviceStatus) {
                LoTEServiceStatus.NOTIFIED -> true to TrustStatus.TRUSTED
                LoTEServiceStatus.WITHDRAWN -> false to TrustStatus.UNTRUSTED
                else -> false to TrustStatus.UNKNOWN
            }
        }
        return if (source.serviceStatus == null && source.statusStartingTime == null) true to TrustStatus.TRUSTED
        else false to TrustStatus.UNKNOWN
    }

    fun evaluate(serviceStatus: String): Pair<Boolean, TrustStatus> =
        when (serviceStatus) {
            ETSIServiceStatus.NOTIFIED -> true to TrustStatus.TRUSTED
            ETSIServiceStatus.WITHDRAWN_602 -> false to TrustStatus.UNTRUSTED
            ETSIServiceStatus.GRANTED,
            ETSIServiceStatus.RECOGNISED_NATIONAL_LEVEL,
            -> true to TrustStatus.TRUSTED
            ETSIServiceStatus.REVOKED -> false to TrustStatus.REVOKED
            ETSIServiceStatus.WITHDRAWN,
            ETSIServiceStatus.SUSPENDED,
            -> false to TrustStatus.UNTRUSTED
            else -> false to TrustStatus.UNKNOWN
        }
}
