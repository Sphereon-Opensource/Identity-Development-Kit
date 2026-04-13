import type { ThemeMode } from './types';
/**
 * Resolve the initial theme mode from cookies (server-side).
 * Used in layout.tsx to set the correct initial mode before hydration.
 */
export declare function resolveInitialMode(cookieStore: {
    get: (name: string) => {
        value: string;
    } | undefined;
}): ThemeMode;
//# sourceMappingURL=server-utils.d.ts.map