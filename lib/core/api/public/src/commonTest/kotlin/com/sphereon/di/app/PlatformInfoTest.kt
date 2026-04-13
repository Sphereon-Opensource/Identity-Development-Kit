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

package com.sphereon.di.app

import com.sphereon.core.api.conf.Env
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class OsFamilyTest {
    @Test
    fun enumHasSixValues() {
        assertEquals(6, PlatformInfo.OsFamily.entries.size)
    }

    @Test
    fun iosExists() {
        assertEquals("IOS", PlatformInfo.OsFamily.IOS.name)
    }

    @Test
    fun androidExists() {
        assertEquals("ANDROID", PlatformInfo.OsFamily.ANDROID.name)
    }

    @Test
    fun jvmExists() {
        assertEquals("JVM", PlatformInfo.OsFamily.JVM.name)
    }

    @Test
    fun jsExists() {
        assertEquals("JS", PlatformInfo.OsFamily.JS.name)
    }

    @Test
    fun nativeExists() {
        assertEquals("NATIVE", PlatformInfo.OsFamily.NATIVE.name)
    }

    @Test
    fun wasmJsExists() {
        assertEquals("WASM_JS", PlatformInfo.OsFamily.WASM_JS.name)
    }
}

class PlatformInfoInterfaceTest {
    private class TestPlatformInfo(
        override val osFamily: PlatformInfo.OsFamily,
    ) : PlatformInfo

    @Test
    fun nameDefaultsToOsFamilyName() {
        val info = TestPlatformInfo(PlatformInfo.OsFamily.JVM)
        assertEquals("JVM", info.name)
    }

    @Test
    fun environmentDefaultsToEnv() {
        val info = TestPlatformInfo(PlatformInfo.OsFamily.JVM)
        assertEquals(Env, info.environment)
    }
}

class CommonPlatformInfoTest {
    @Test
    fun osFamilyIsJvm() {
        val info = CommonPlatformInfo()
        assertEquals(PlatformInfo.OsFamily.JVM, info.osFamily)
    }

    @Test
    fun nameIsJvm() {
        val info = CommonPlatformInfo()
        assertEquals("JVM", info.name)
    }

    @Test
    fun environmentIsNotNull() {
        val info = CommonPlatformInfo()
        assertNotNull(info.environment)
    }
}
