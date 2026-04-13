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

package com.sphereon.crypto.kms.rest.server.service

import com.sphereon.crypto.core.KeyInfoType
import com.sphereon.crypto.core.ResolvedKeyInfoType
import com.sphereon.crypto.core.kms.KeyResolverService
import com.sphereon.crypto.core.kms.model.IdentifierMethod
import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

@OptIn(ExperimentalObjCName::class)
@ObjCName("ResolversRestService", exact = true)
interface ResolversRestService {
    suspend fun listResolvers(): Array<KeyResolverService>

    suspend fun getResolver(resolverId: String): KeyResolverService

    suspend fun resolveKey(
        resolverId: String,
        keyInfo: KeyInfoType<*>,
        identifierMethod: IdentifierMethod? = null,
        trustedCerts: Array<String>? = null,
        verifyX509CertificateChain: Boolean? = false,
    ): ResolvedKeyInfoType<*>
}
