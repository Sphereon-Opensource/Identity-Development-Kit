/**
 * Blocking inline script for FOUC prevention.
 * Runs before React hydrates to set data-theme and color-scheme on <html>.
 *
 * This function is serialized to a string and injected as an inline <script>
 * in <head> by ThemeScript.tsx. It must be self-contained (no imports).
 */
export function getThemeScriptSource(defaultMode: string, cookieName: string): string {
  return `(function(){try{var m=localStorage.getItem('theme-mode');if(!m){var c=document.cookie.match(new RegExp('(?:^|; )${cookieName}=([^;]*)'));m=c?c[1]:null}m=m||'${defaultMode}';var r=m;if(m==='system'){r=window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light'}document.documentElement.setAttribute('data-theme',r);document.documentElement.style.colorScheme=r}catch(e){}})()`
}
