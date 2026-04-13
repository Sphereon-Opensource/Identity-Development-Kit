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

package com.sphereon.crypto.kms.provider.software

import com.sphereon.crypto.core.generic.KeyOperations
import com.sphereon.crypto.core.generic.KeyTypeMapping
import com.sphereon.crypto.core.generic.ManagedKeyPair
import com.sphereon.crypto.core.generic.SignatureAlgorithm
import com.sphereon.crypto.core.jose.JwkUse
import com.sphereon.crypto.kms.keystore.software.AppleKeychainKeyGeneration

/**
 * iOS-specific key generation that uses native keychain instead of cryptography library.
 * This is called from the common SoftwareKmsProvider on iOS platforms.
 */
internal actual suspend fun generateKeyPairNative(
    alias: String,
    keyType: KeyTypeMapping,
    algorithm: SignatureAlgorithm,
    keyUse: JwkUse,
    keyOperations: Array<out KeyOperations>,
    overwriteAlias: Boolean,
): ManagedKeyPair? =
    AppleKeychainKeyGeneration.generateNativeKeyPair(
        alias = alias,
        keyType = keyType,
        algorithm = algorithm,
        keyUse = keyUse,
        keyOperations = keyOperations,
        overwriteAlias = overwriteAlias,
    )
