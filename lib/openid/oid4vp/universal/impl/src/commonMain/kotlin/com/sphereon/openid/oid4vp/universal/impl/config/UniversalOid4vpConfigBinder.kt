/*
 * Copyright 2025 Sphereon International B.V.
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

package com.sphereon.openid.oid4vp.universal.impl.config

import com.sphereon.core.api.conf.ConfigLevel
import com.sphereon.core.api.conf.PrincipalConfigService
import com.sphereon.core.api.context.SessionExecution
import com.sphereon.di.session.SessionScope
import com.sphereon.openid.oid4vp.universal.UniversalOid4vpConfig
import com.sphereon.openid.oid4vp.universal.UniversalOid4vpConfigProvider
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn

/**
 * Binds Universal OID4VP configuration from IDK's ConfigService.
 *
 * Configuration properties use the prefix "oid4vp.universal":
 *
 * ```properties
 * oid4vp.universal.external-base-url=https://verifier.example.com
 * ```
 */
@Inject
@SingleIn(SessionScope::class)
@ContributesBinding(SessionScope::class, binding = binding<UniversalOid4vpConfigProvider>())
class UniversalOid4vpConfigBinder(
    private val execution: SessionExecution
) : UniversalOid4vpConfigProvider {

    private val configService: PrincipalConfigService
        get() = execution.conf.conf(ConfigLevel.PRINCIPAL) as PrincipalConfigService

    override fun getConfig(): UniversalOid4vpConfig {
        val prefix = UniversalOid4vpConfig.CONFIG_PREFIX

        return UniversalOid4vpConfig(
            externalBaseUrl = configService.getPropertyAsString("$prefix.external-base-url", null),
            responseUri = configService.getPropertyAsString("$prefix.response-uri", null)
        )
    }
}
