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
 *
 */

package com.sphereon.crypto.core.kms.model

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.core.compat.Uuid
import kotlinx.serialization.Serializable
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * Represents the settings required for configuring a key provider.
 *
 * @property id A unique identifier for the key provider instance.
 * @property config Configuration settings specific to the type of key provider.
 * @property passwordInputCallback Optional callback function for password input, used for certain key providers.
 */
@JsExportCompat
@Serializable
@OptIn(ExperimentalObjCName::class)
@ObjCName("KeyProviderSettings", exact = true)
data class KeyProviderSettings(
    val id: String = Uuid.v4String(),
    val config: KeyProviderConfig,
    val passwordInputCallback: PasswordInputCallback? = null,
)
