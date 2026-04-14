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

package com.sphereon.ktor.http.client.config

import com.sphereon.core.compat.JsExportCompat
import com.sphereon.crypto.core.kms.CertificateStoreService
import com.sphereon.crypto.core.kms.KeyStoreLoaderOpts
import com.sphereon.crypto.core.kms.KeyStoreService
import com.sphereon.crypto.core.sign.SimpleSignatureService
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@JsExportCompat
@OptIn(ExperimentalObjCName::class)
@ObjCName("LegacySslConfig", exact = true)
data class LegacySslConfig(
    val certificateStoreService: CertificateStoreService,
    val keyStoreService: KeyStoreService,
    val rawSignatureService: SimpleSignatureService? = null,
    val certificateAliases: List<String> = emptyList(),
    val trustStoreOpts: KeyStoreLoaderOpts? = null,
)
