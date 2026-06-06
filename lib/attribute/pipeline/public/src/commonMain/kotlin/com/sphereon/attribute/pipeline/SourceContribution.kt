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

import com.sphereon.attribute.flow.AttributeRecord
import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.JsExportIgnoreCompat
import kotlinx.serialization.Serializable

/**
 * What an [AttributeSource] returns from a single `contribute()` call.
 *
 * A source can yield any number of attributes and lookup keys from one invocation (a single
 * lookup key fed in often yields a whole row of attributes), and may signal that the rest of
 * its answer is deferred.
 */
@JsExportCompat
@Serializable
data class SourceContribution(
    @JsExportIgnoreCompat
    val attributes: List<AttributeRecord> = emptyList(),
    @JsExportIgnoreCompat
    val lookupKeys: List<LookupKey> = emptyList(),
    /** Non-null when the source declares (part of) its answer will arrive later. */
    val deferralSignal: DeferralSignal? = null,
)
