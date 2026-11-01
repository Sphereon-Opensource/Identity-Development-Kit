/*
 * (c) 2026 Sphereon International B.V.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.sphereon.oauth2.server.authorization.impl.time

/**
 * Qualifier for clocks used to expire short-lived, server-issued opaque artifacts. The clock itself is provided by the
 * authorization public module, next to the qualifier.
 */
internal const val OAUTH2_ARTIFACT_CLOCK = com.sphereon.oauth2.server.authorization.time.OAUTH2_ARTIFACT_CLOCK
