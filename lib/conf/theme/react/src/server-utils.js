const COOKIE_NAME = 'sphereon-theme-mode';
/**
 * Resolve the initial theme mode from cookies (server-side).
 * Used in layout.tsx to set the correct initial mode before hydration.
 */
export function resolveInitialMode(cookieStore) {
    const cookie = cookieStore.get(COOKIE_NAME);
    if (cookie?.value === 'light' || cookie?.value === 'dark' || cookie?.value === 'system') {
        return cookie.value;
    }
    return 'system';
}
//# sourceMappingURL=server-utils.js.map