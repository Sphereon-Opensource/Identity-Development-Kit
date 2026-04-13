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

import com.sphereon.core.compat.JsExportCompat

/**
 * Generates a self-contained blocking inline script that prevents FOUC
 * (Flash of Unstyled Content) by setting data-theme and color-scheme
 * on <html> before React/framework hydration.
 */
@JsExportCompat
object FoucPreventionScript {
    private const val DEFAULT_STORAGE_KEY = "theme-mode"
    private const val DEFAULT_COOKIE_NAME = "sphereon-theme-mode"

    /**
     * Generate the inline script source string.
     *
     * @param defaultMode Default mode if nothing stored ("system", "light", "dark")
     * @param cookieName Cookie name to read from (defaults to "sphereon-theme-mode")
     * @param storageKey localStorage key to read from (defaults to "theme-mode")
     */
    fun generate(
        defaultMode: String = "system",
        cookieName: String = DEFAULT_COOKIE_NAME,
        storageKey: String = DEFAULT_STORAGE_KEY,
    ): String =
        buildString {
            append("(function(){try{")
            append("var m=localStorage.getItem('$storageKey');")
            append("if(!m){var c=document.cookie.match(new RegExp('(?:^|; )$cookieName=([^;]*)'));m=c?c[1]:null}")
            append("m=m||'$defaultMode';")
            append("var r=m;")
            append("if(m==='system'){r=window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light'}")
            append("document.documentElement.setAttribute('data-theme',r);")
            append("document.documentElement.style.colorScheme=r")
            append("}catch(e){}})()")
        }
}
