/*
 * © 2025 Sphereon International B.V.
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

package com.sphereon.crypto.core

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith

/**
 * Tests for CryptoCallbacksImpl and DefaultCallbacks.
 */
class CryptoCallbacksTest {

    // =========== DefaultCallbacks Tests ===========

    @Test
    fun defaultCallbacksHasX509DefaultShouldReturnFalseWhenCleared() {
        // Store current state
        val hadX509 = DefaultCallbacks.hasX509Default()

        // Set to null
        DefaultCallbacks.setX509Default(null)

        val hasX509 = DefaultCallbacks.hasX509Default()

        assertFalse(hasX509, "hasX509Default should return false when callback is cleared")
    }

    @Test
    fun defaultCallbacksX509ShouldThrowWhenNoCallbackSet() {
        // Store current state to restore later
        val hadX509 = DefaultCallbacks.hasX509Default()

        // Clear callback
        DefaultCallbacks.setX509Default(null)

        assertFailsWith<IllegalStateException> {
            DefaultCallbacks.x509()
        }
    }

    @Test
    fun defaultCallbacksHasCoseCryptoDefaultShouldReturnFalseWhenCleared() {
        // Set to null
        DefaultCallbacks.setCoseCryptoDefault(null)

        val hasCoseCrypto = DefaultCallbacks.hasCoseCryptoDefault()

        assertFalse(hasCoseCrypto, "hasCoseCryptoDefault should return false when callback is cleared")
    }

    @Test
    fun defaultCallbacksCoseCryptoShouldThrowWhenNoCallbackSet() {
        // Clear callback
        DefaultCallbacks.setCoseCryptoDefault(null)

        assertFailsWith<IllegalStateException> {
            DefaultCallbacks.coseCrypto()
        }
    }

    // =========== CryptoCallbacksImpl Tests ===========

    @Test
    fun cryptoCallbacksImplShouldBeInstantiable() {
        val impl = CryptoCallbacksImpl()

        assertNotNull(impl)
    }

    @Test
    fun cryptoCallbacksImplHasX509DefaultShouldDelegateToDefaultCallbacks() {
        val impl = CryptoCallbacksImpl()

        // Clear state
        DefaultCallbacks.setX509Default(null)

        assertFalse(impl.hasX509Default())
    }

    @Test
    fun cryptoCallbacksImplHasCoseCryptoDefaultShouldDelegateToDefaultCallbacks() {
        val impl = CryptoCallbacksImpl()

        // Clear state
        DefaultCallbacks.setCoseCryptoDefault(null)

        assertFalse(impl.hasCoseCryptoDefault())
    }
}
