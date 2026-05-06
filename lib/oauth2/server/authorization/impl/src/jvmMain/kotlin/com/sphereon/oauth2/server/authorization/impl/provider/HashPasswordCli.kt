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

package com.sphereon.oauth2.server.authorization.impl.provider

import com.sphereon.core.api.decodeFromBase64
import kotlinx.coroutines.runBlocking

/**
 * CLI helper for [ConfigBackedUserAuthenticationProvider]. Hashes a single password using the
 * deployment-wide salt and iteration count, then prints a ready-to-paste config line:
 *
 * ```
 * oauth2.users.accounts.(username).password=(base64 hash)
 * ```
 *
 * Driven by the `:hashPassword` Gradle task in this module's `build.gradle.kts`. JVM-only because
 * it relies on `runBlocking`; the underlying [PasswordHasher] is KMP common.
 */
fun main(args: Array<String>) {
    require(args.size == EXPECTED_ARG_COUNT) {
        "Usage: HashPasswordCli <username> <password> <deploymentSaltB64> <iterations>"
    }
    val username = args[0]
    val password = args[1]
    val saltB64 = args[2]
    val iterations = args[3].toInt()

    val hasher =
        PasswordHasher(
            deploymentSalt = saltB64.decodeFromBase64(),
            iterations = iterations,
        )
    val hash = runBlocking { hasher.hash(username, password) }
    println("oauth2.users.accounts.$username.password=$hash")
}

private const val EXPECTED_ARG_COUNT: Int = 4
