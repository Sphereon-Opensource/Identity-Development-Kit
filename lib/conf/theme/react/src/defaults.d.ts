/**
 * System default token maps — M3 baseline light and dark themes.
 *
 * These are identical to IDK's SystemDefaults.kt and are the root of the
 * resolution chain: every resolved theme starts from these values.
 *
 * In future, when the Kotlin/JS packages are published, this can be replaced
 * with a re-export from @sphereon/theme-web. For now, it's a standalone
 * TypeScript implementation to avoid the Kotlin/JS bundle cost.
 */
import type { ThemeTokenMap, ThemeVariant } from './types';
/** Get the full M3 baseline token set for a given variant */
export declare function getSystemDefaults(variant: ThemeVariant): ThemeTokenMap;
//# sourceMappingURL=defaults.d.ts.map