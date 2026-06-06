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

package com.sphereon.openid.oid4vci.issuer.impl.http.command

import com.sphereon.crypto.jose.jwe.CreateJweCompactArgs
import com.sphereon.crypto.jose.jwe.CreateJweJsonArgs
import com.sphereon.crypto.jose.jwe.CreateJweJsonGeneralArgs
import com.sphereon.crypto.jose.jwe.DecryptJweArgs
import com.sphereon.crypto.jose.jwe.JweService
import com.sphereon.crypto.jose.jwe.PrepareJweArgs

internal object ThrowingJweService : JweService {
    override val commands: JweService.Commands
        get() = throw UnsupportedOperationException("not used in these tests")

    override suspend fun prepareJwe(args: PrepareJweArgs) = throw UnsupportedOperationException("ThrowingJweService.prepareJwe")

    override suspend fun createJweCompact(args: CreateJweCompactArgs) = throw UnsupportedOperationException("ThrowingJweService.createJweCompact")

    override suspend fun createJweJsonFlattened(args: CreateJweJsonArgs) = throw UnsupportedOperationException("ThrowingJweService.createJweJsonFlattened")

    override suspend fun createJweJsonGeneral(args: CreateJweJsonGeneralArgs) = throw UnsupportedOperationException("ThrowingJweService.createJweJsonGeneral")

    override suspend fun decryptJwe(args: DecryptJweArgs) = throw UnsupportedOperationException("ThrowingJweService.decryptJwe")
}
