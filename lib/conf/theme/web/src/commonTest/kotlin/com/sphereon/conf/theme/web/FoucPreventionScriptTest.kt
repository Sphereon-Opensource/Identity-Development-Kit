/*
 * Copyright 2023-2026 Sphereon International B.V.
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

package com.sphereon.conf.theme.web

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class FoucPreventionScriptTest {
    @Test
    fun generatesValidScript() {
        val script = FoucPreventionScript.generate()
        assertContains(script, "localStorage.getItem")
        assertContains(script, "sphereon-theme-mode")
        assertContains(script, "data-theme")
        assertContains(script, "colorScheme")
        assertTrue(script.startsWith("(function()"))
    }

    @Test
    fun usesCustomParameters() {
        val script =
            FoucPreventionScript.generate(
                defaultMode = "dark",
                cookieName = "my-cookie",
                storageKey = "my-key",
            )
        assertContains(script, "my-cookie")
        assertContains(script, "my-key")
        assertContains(script, "'dark'")
    }
}
