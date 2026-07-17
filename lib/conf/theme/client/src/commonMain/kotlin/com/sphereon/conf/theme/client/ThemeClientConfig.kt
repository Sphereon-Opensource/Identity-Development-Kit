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

package com.sphereon.conf.theme.client

import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding

/**
 * Typed configuration for the remote theme client.
 *
 * @property baseUrl Base URL of the theme service hosting `/api/theme/v1` (no trailing slash),
 *   or null when unconfigured. An unconfigured base URL makes every remote resolution fail fast
 *   so callers fall back to their neutral defaults.
 */
data class ThemeClientConfig(
    val baseUrl: String?,
)

/**
 * Provides the effective [ThemeClientConfig] for the current session.
 */
interface ThemeClientConfigProvider {
    fun getConfig(): ThemeClientConfig
}

/**
 * Config binder reading the `theme.client.*` property group at app scope. The base URL is a
 * deployment concern (where the theme service runs), so tenant or principal overrides do not
 * apply.
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<ThemeClientConfigProvider>())
class ThemeClientConfigBinder(
    private val execution: SessionExecution,
) : ThemeClientConfigProvider {
    override fun getConfig(): ThemeClientConfig =
        ThemeClientConfig(
            baseUrl =
                execution.conf.app
                    .getPropertyAsString(BASE_URL_KEY)
                    ?.trim()
                    ?.trimEnd('/')
                    ?.takeIf { it.isNotEmpty() },
        )

    companion object {
        const val BASE_URL_KEY: String = "theme.client.base-url"
    }
}
