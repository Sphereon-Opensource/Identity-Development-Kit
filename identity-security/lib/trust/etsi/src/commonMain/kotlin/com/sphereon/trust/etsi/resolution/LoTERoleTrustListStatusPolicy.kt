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

/** Fail-closed interpretation of ETSI service status values. */
object LoTERoleTrustListStatusPolicy {
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
