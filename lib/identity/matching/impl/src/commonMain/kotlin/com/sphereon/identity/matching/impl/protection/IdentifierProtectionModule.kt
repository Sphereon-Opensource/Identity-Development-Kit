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

package com.sphereon.identity.matching.impl.protection

import com.sphereon.crypto.core.kms.KeyManagerService
import com.sphereon.crypto.core.kms.command.GenerateMacCommand
import com.sphereon.di.session.SessionScope
import com.sphereon.identity.matching.protection.DefaultIdentifierProtectionPolicyService
import com.sphereon.identity.matching.protection.IdentifierProtectionPolicyService
import com.sphereon.identity.matching.protection.IdentifierProtector
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

/**
 * Session-scoped DI module for identifier protection.
 *
 * Provides the KMS-backed [IdentifierProtector] and the built-in
 * [IdentifierProtectionPolicyService]. Mirrors the reconciliation crypto module so identifier
 * protection wires up automatically wherever the matching impl is on the classpath.
 */
@ContributesTo(SessionScope::class)
interface IdentifierProtectionModule {
    @Provides
    @SingleIn(SessionScope::class)
    fun provideIdentifierProtectionPolicyService(): IdentifierProtectionPolicyService = DefaultIdentifierProtectionPolicyService()

    @Provides
    @SingleIn(SessionScope::class)
    fun provideIdentifierProtector(
        generateMacCommand: GenerateMacCommand,
        keyManagerService: KeyManagerService,
    ): IdentifierProtector =
        KmsBackedIdentifierProtector(
            generateMacCommand = generateMacCommand,
            keyManagerService = keyManagerService,
        )
}
