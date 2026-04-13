/*
 * © 2026 Sphereon International B.V.
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

package com.sphereon.core.api.conf

import com.sphereon.core.api.session.CommandId
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Represents a module/service/command scope for hierarchical configuration resolution.
 *
 * Uses the `cmd.<module>.<service>.<command>` prefix convention with `default`
 * as the wildcard meaning "all". Global defaults use bare config keys (no prefix).
 *
 * Resolution order (least-specific → most-specific, for merging):
 * 1. `<configSuffix>` — bare key, global default
 * 2. `cmd.<module>.default.default.<configSuffix>` — module level
 * 3. `cmd.<module>.<service>.default.<configSuffix>` — service level
 * 4. `cmd.<module>.<service>.<command>.<configSuffix>` — command level
 *
 * Example for scope(kms, keys, get) and suffix "http.client":
 * ```
 * http.client                              → global default
 * cmd.kms.default.default.http.client      → KMS module override
 * cmd.kms.keys.default.http.client         → KMS keys service override
 * cmd.kms.keys.get.http.client             → specific command override
 * ```
 */
@OptIn(ExperimentalObjCName::class)
@ObjCName("CommandConfigScope", exact = true)
data class CommandConfigScope(
    val module: String = DEFAULT,
    val service: String = DEFAULT,
    val command: String = DEFAULT,
) {
    /**
     * Returns config prefixes from least-specific to most-specific (for merge order).
     * The global bare prefix is always first; scoped `cmd.*` prefixes follow
     * only when the corresponding segment is not `default`.
     */
    fun prefixesFor(configSuffix: String): List<String> =
        buildList {
            add(configSuffix)
            if (module != DEFAULT) {
                add("cmd.$module.$DEFAULT.$DEFAULT.$configSuffix")
                if (service != DEFAULT) {
                    add("cmd.$module.$service.$DEFAULT.$configSuffix")
                    if (command != DEFAULT) {
                        add("cmd.$module.$service.$command.$configSuffix")
                    }
                }
            }
        }

    companion object {
        const val DEFAULT = "default"
        val GLOBAL = CommandConfigScope()

        fun fromCommandId(commandId: CommandId) =
            CommandConfigScope(
                module = commandId.module,
                service = commandId.service,
                command = commandId.command,
            )

        fun fromCommandId(commandId: String) = fromCommandId(CommandId(commandId))

        fun module(module: String) = CommandConfigScope(module = module)

        fun service(
            module: String,
            service: String,
        ) = CommandConfigScope(module, service)
    }
}
