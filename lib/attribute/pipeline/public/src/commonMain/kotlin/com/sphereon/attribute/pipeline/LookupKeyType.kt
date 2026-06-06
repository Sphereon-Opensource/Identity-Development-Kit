/*
 * © 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */

package com.sphereon.attribute.pipeline

import com.sphereon.core.compat.JsExportCompat
import kotlinx.serialization.Serializable

/**
 * Well-known classification of a [LookupKey].
 *
 * Extensible value class — the companion constants cover the common cases; deployments may
 * construct their own. Purely advisory: the pipeline keys on [LookupKey.name], not on the type.
 */
@JsExportCompat
@Serializable
data class LookupKeyType(
    val value: String,
) {
    companion object {
        val EMAIL = LookupKeyType("email")
        val EMPLOYEE_ID = LookupKeyType("employee_id")
        val STUDENT_NR = LookupKeyType("student_nr")
        val BUSINESS_KEY = LookupKeyType("business_key")
        val PHONE = LookupKeyType("phone")
        val DID = LookupKeyType("did")
    }
}
