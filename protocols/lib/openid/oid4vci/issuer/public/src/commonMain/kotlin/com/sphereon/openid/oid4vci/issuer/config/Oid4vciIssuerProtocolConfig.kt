/*
 * Copyright 2026 Sphereon International B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */

package com.sphereon.openid.oid4vci.issuer.config

import com.sphereon.core.api.conf.AppConfigService

object Oid4vciIssuerProtocolConfig {
    const val BASE_PATH_KEY: String = "oid4vci.issuer.protocol.base-path"

    fun resolveBasePath(appConfig: AppConfigService): String =
        normalizeBasePath(
            appConfig
                .getPropertyAsString(key = BASE_PATH_KEY, defaultValue = null)
                ?.trim()
                .orEmpty(),
        )

    fun normalizeBasePath(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        return when {
            trimmed.isEmpty() -> ""
            trimmed.startsWith("/") -> trimmed
            else -> "/$trimmed"
        }
    }

    fun appendBasePath(
        baseUrl: String,
        basePath: String,
    ): String {
        val normalizedBase = baseUrl.trimEnd('/')
        val normalizedPath = normalizeBasePath(basePath)
        return when {
            normalizedPath.isEmpty() -> normalizedBase
            normalizedBase.endsWith(normalizedPath) -> normalizedBase
            else -> normalizedBase + normalizedPath
        }
    }
}
