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
        val script = FoucPreventionScript.generate(
            defaultMode = "dark",
            cookieName = "my-cookie",
            storageKey = "my-key",
        )
        assertContains(script, "my-cookie")
        assertContains(script, "my-key")
        assertContains(script, "'dark'")
    }
}
