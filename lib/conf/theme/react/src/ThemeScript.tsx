import { getThemeScriptSource } from './theme-script'

const COOKIE_NAME = 'sphereon-theme-mode'

interface ThemeScriptProps {
  defaultMode?: string
}

/**
 * Renders a blocking inline script in <head> that sets data-theme
 * before React hydrates, preventing FOUC (flash of unstyled content).
 *
 * Usage in layout.tsx:
 *   <head>
 *     <ThemeScript />
 *   </head>
 */
export function ThemeScript({ defaultMode = 'system' }: ThemeScriptProps) {
  return (
    <script
      dangerouslySetInnerHTML={{
        __html: getThemeScriptSource(defaultMode, COOKIE_NAME),
      }}
    />
  )
}
