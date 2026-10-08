/*
 * (c) 2026 Sphereon International B.V.
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
 *
 */

package com.sphereon.crypto.kms.provider.mobile

import at.asitplus.signum.supreme.os.JKSProvider
import at.asitplus.signum.supreme.os.SigningProvider
import com.sphereon.crypto.core.kms.KmsProviderConfigBase
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

private const val JKS_EPHEMERAL = "jks.ephemeral"
private const val JKS_PATH = "jks.path"
private const val JKS_PASSWORD = "jks.password"
private const val JKS_PROVIDER = "jks.provider"
private const val JKS_READ_ONLY = "jks.readOnly"
private const val JKS_CREATE_IF_MISSING = "jks.createIfMissing"
private const val JKS_STORE_TYPE = "jks.storeType"
private const val DEFAULT_JKS_PASSWORD = "changeit"

internal actual fun createMobileSigningProvider(config: KmsProviderConfigBase): SigningProvider {
    val values = config.defaultConfigValues
    if (values.boolean(JKS_EPHEMERAL, defaultValue = false)) {
        return JKSProvider.Ephemeral(type = values[JKS_STORE_TYPE] ?: "pkcs12", provider = values[JKS_PROVIDER]).getOrThrow()
    }

    val keystorePath = values[JKS_PATH]?.let { Paths.get(it) } ?: defaultKeystorePath(config.id)
    keystorePath.parent?.let { Files.createDirectories(it) }

    return JKSProvider {
        file {
            this.file = keystorePath
            password = (values[JKS_PASSWORD] ?: DEFAULT_JKS_PASSWORD).toCharArray()
            values[JKS_STORE_TYPE]?.let { storeType = it }
            provider = values[JKS_PROVIDER]
            readOnly = values.boolean(JKS_READ_ONLY, defaultValue = false)
            createIfMissing = values.boolean(JKS_CREATE_IF_MISSING, defaultValue = true)
        }
    }.getOrThrow()
}

private fun Map<String, String>.boolean(
    key: String,
    defaultValue: Boolean,
): Boolean = this[key]?.toBooleanStrict() ?: defaultValue

private fun defaultKeystorePath(providerId: String): Path =
    Paths.get(
        System.getProperty("user.home"),
        ".sphereon",
        "vdx",
        "kms",
        "mobile",
        "${providerId.safeFileName()}.p12",
    )

private fun String.safeFileName(): String = replace(Regex("[^A-Za-z0-9._-]"), "_")
