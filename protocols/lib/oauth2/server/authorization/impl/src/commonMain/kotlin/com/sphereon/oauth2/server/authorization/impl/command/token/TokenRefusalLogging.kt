/* Copyright 2026 Sphereon International B.V. Licensed under the Apache License, Version 2.0. */
package com.sphereon.oauth2.server.authorization.impl.command.token

import com.sphereon.core.api.log.Log
import com.sphereon.core.api.log.LogLevel
import com.sphereon.core.api.log.LogMessage
import com.sphereon.core.api.log.SessionLogService

/** Complete bounded refusal diagnostics before the HTTP request returns. No grant values belong here. */
internal suspend fun logTokenRefusal(sessionLog: SessionLogService, message: String) {
    val sessionId = sessionLog.sessionContext.sessionId
        .takeIf { it.matches(Regex("[A-Za-z0-9_.:-]{1,160}")) } ?: "unavailable"
    Log.app().withTagAsync("OAuth2Token").execute(
        LogMessage(level = LogLevel.WARN, message = message, metadata = mapOf("session_id" to sessionId)),
    )
}
