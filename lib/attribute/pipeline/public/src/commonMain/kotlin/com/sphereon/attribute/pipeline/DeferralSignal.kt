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
import kotlin.time.Duration

/**
 * A source self-declaring that it will NOT produce (all of) its attributes synchronously — its
 * real answer arrives later via callback / push.
 *
 * When a source returns this on its [SourceContribution], the engine skips any synchronous-wait
 * window for that source (no point holding the request open for a source that already said
 * "not now") and goes straight to the deferred path if required attributes are still missing.
 * Null on a [SourceContribution] means the source answered (or failed) synchronously.
 */
@JsExportCompat
@Serializable
data class DeferralSignal(
    /** Hint, forwarded to the wallet, for the `/deferred_credential` poll cadence. */
    val expectedInterval: Duration? = null,
    /** Operational note; not shown to the wallet. */
    val reason: String? = null,
)
