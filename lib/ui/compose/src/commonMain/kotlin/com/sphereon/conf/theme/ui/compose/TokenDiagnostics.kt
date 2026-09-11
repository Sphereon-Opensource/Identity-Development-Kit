/*
 * Copyright 2023-2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.sphereon.conf.theme.ui.compose

import kotlin.concurrent.Volatile

/**
 * Sink for token diagnostics. Hosts wire this to their own logging.
 *
 * This component library is a leaf that ships to JVM, Android, iOS, JS and wasm, so it does not
 * depend on the core logging module: pulling a session-scoped log manager into a composable would
 * drag the whole core API stack into every consumer, and there is no ambient session at paint time
 * anyway. Injecting the sink is the same contract at a lower cost. A host with real logging wires
 * it once at startup, for example to `logManager.withTag(...).warn(...)`.
 *
 * Nothing is printed when no sink is installed. Never print from this library directly.
 */
fun interface TokenWarningSink {
    fun warn(message: String)
}

/**
 * Diagnostics for token values the renderer cannot honour.
 *
 * The render path stays silent by design: a composable cannot usefully throw at paint time, and
 * falling back to the flat brand colour is a strictly better failure than a blank button. The
 * loudness lives here instead, on the resolve path, so an unsupported override is diagnosable
 * rather than invisible.
 *
 * Warnings are emitted once per distinct value, not once per recomposition. A benign race can warn
 * twice; that is acceptable, and it mirrors how the theme client reports its own misconfiguration.
 */
object TokenDiagnostics {
    /** Ceiling on remembered values so a pathological token map cannot grow this without bound. */
    private const val MAX_REMEMBERED = 128

    @Volatile
    private var sink: TokenWarningSink? = null

    @Volatile
    private var warned: Set<String> = emptySet()

    /** Install the host's logging. Pass null to silence. */
    fun setWarningSink(warningSink: TokenWarningSink?) {
        sink = warningSink
    }

    /** Forget which values have already been reported. Intended for tests. */
    fun resetWarnings() {
        warned = emptySet()
    }

    /**
     * Report a value that looks like a gradient but that the renderer could not read, so the caller
     * fell back to a flat colour. Silent for anything that is not gradient-shaped, because a plain
     * colour failing to parse as a gradient is the normal case, not a problem.
     */
    internal fun reportUnrenderableGradient(
        tokenValue: String,
        reason: String,
    ) {
        if (!looksLikeGradient(tokenValue)) {
            return
        }
        val current = sink ?: return
        if (tokenValue in warned) {
            return
        }
        warned = if (warned.size >= MAX_REMEMBERED) setOf(tokenValue) else warned + tokenValue
        current.warn(
            "Token value '$tokenValue' looks like a gradient but cannot be rendered ($reason); " +
                "falling back to a flat colour",
        )
    }

    /** True when the value announces itself as a gradient, whatever the renderer can do with it. */
    internal fun looksLikeGradient(value: String): Boolean {
        val trimmed = value.trimStart().lowercase()
        return trimmed.startsWith("gradient") || trimmed.startsWith("linear-") || trimmed.startsWith("radial-")
    }
}
